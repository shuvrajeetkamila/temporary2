#!/usr/bin/env python3
"""Compile a current OpenStreetMap PBF extract into the random-access QTOD pack.

The output preserves every accepted road segment and complete simple building
footprint in compact per-2km compressed blocks. It is deterministic for a
fixed PBF byte stream and compiler version. Runtime loads only focus-near
blocks; the entire QTOD file remains installed inside the APK.
"""
from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import math
import re
import struct
import sys
import zlib
from collections import defaultdict
from pathlib import Path

try:
    import osmium
except ImportError as exc:
    raise SystemExit("pyosmium is required: python3 -m pip install osmium==4.1.1") from exc

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from compile_qatar_world import normalize_polygons,point_in_polygons,distance_to_coast,utm39  # noqa:E402

MAGIC=b'QTOD';BLOCK_MAGIC=b'QDET';VERSION=1;EPSG=32639
HEADER_FORMAT='<4sIIIIIddddII';INDEX_FORMAT='<iiqiiIHH'
CHUNK_METRES=2000
SOURCE_URL='https://download.geofabrik.de/asia/gcc-states-latest.osm.pbf'

HIGHWAY_CLASS={
 'motorway':0,'motorway_link':0,'trunk':1,'trunk_link':1,'primary':2,'primary_link':2,
 'secondary':3,'secondary_link':3,'tertiary':4,'tertiary_link':4,'residential':5,
 'living_street':5,'unclassified':5,'service':6,'road':6,'track':7,'path':8,
 'footway':8,'cycleway':8,'pedestrian':8,'steps':8,'bridleway':8,
}
DEFAULT_WIDTH={0:28.0,1:22.0,2:16.0,3:13.0,4:10.0,5:7.2,6:5.5,7:3.5,8:2.0,9:3.0,10:45.0,11:18.0}
SURFACE={'asphalt':1,'paved':1,'concrete':2,'concrete:lanes':2,'concrete:plates':2,'compacted':3,'gravel':4,'fine_gravel':4,'sand':5,'ground':6,'dirt':6,'unpaved':6,'paving_stones':7}
ARCHETYPE={'apartments':1,'hotel':1,'office':1,'commercial':2,'retail':2,'house':3,'detached':3,'villa':3,'residential':3,'industrial':4,'warehouse':4,'greenhouse':5,'mosque':6,'school':7,'university':7,'hospital':7,'stadium':8}
HEIGHT_RE=re.compile(r'[-+]?\d+(?:\.\d+)?')


def sha256(path:Path)->str:
 h=hashlib.sha256()
 with path.open('rb') as f:
  for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
 return h.hexdigest()


def write_string(buf:bytearray,value:str):
 b=value.encode('utf8');
 if len(b)>65535:raise ValueError('metadata string too long')
 buf.extend(struct.pack('<H',len(b)));buf.extend(b)


def parse_number(value,default=None):
 if not value:return default
 m=HEIGHT_RE.search(str(value).replace(',','.'))
 if not m:return default
 try:return float(m.group())
 except ValueError:return default


def classify_building(tags):
 value=tags.get('building','yes')
 if value in ARCHETYPE:return ARCHETYPE[value]
 amenity=tags.get('amenity','')
 if amenity=='place_of_worship' or tags.get('religion')=='muslim':return 6
 if amenity in ('school','college','university','hospital','clinic'):return 7
 return 3


def building_height(tags,arch):
 height=parse_number(tags.get('height'))
 if height is None:
  levels=parse_number(tags.get('building:levels'))
  if levels is not None:height=levels*3.25+0.8
 if height is None:
  height={1:24,2:11,3:7.5,4:10,5:5,6:16,7:12,8:28}.get(arch,8)
 return max(2.5,min(350.0,height))


def road_attributes(tags):
 if tags.get('highway'):
  road_class=HIGHWAY_CLASS.get(tags.get('highway'),6)
 elif tags.get('railway'):
  road_class=9
 elif tags.get('aeroway')=='runway':
  road_class=10
 elif tags.get('aeroway') in ('taxiway','apron'):
  road_class=11
 else:return None
 lanes=int(max(1,min(16,parse_number(tags.get('lanes'),2 if road_class<=4 else 1))))
 width=parse_number(tags.get('width'),DEFAULT_WIDTH[road_class])
 surface=SURFACE.get(tags.get('surface',''),0)
 flags=(1 if tags.get('bridge') not in (None,'no') else 0)|(2 if tags.get('tunnel') not in (None,'no') else 0)|(4 if tags.get('oneway') in ('yes','1','true') else 0)
 return road_class,lanes,surface,flags,max(.8,min(80,width))


def oriented_bounds(points):
 cx=sum(p[0] for p in points)/len(points);cz=sum(p[1] for p in points)/len(points)
 xx=sum((p[0]-cx)**2 for p in points);zz=sum((p[1]-cz)**2 for p in points);xz=sum((p[0]-cx)*(p[1]-cz) for p in points)
 yaw=.5*math.atan2(2*xz,xx-zz)
 c=math.cos(yaw);s=math.sin(yaw)
 projected=[((x-cx)*c+(z-cz)*s,-(x-cx)*s+(z-cz)*c) for x,z in points]
 width=max(p[0] for p in projected)-min(p[0] for p in projected)
 depth=max(p[1] for p in projected)-min(p[1] for p in projected)
 return cx,cz,max(.5,width),max(.5,depth),yaw


def simplify_vertices(points,limit=192):
 # Remove adjacent duplicates and closing duplicate while preserving source shape.
 clean=[]
 for p in points:
  if not clean or math.hypot(p[0]-clean[-1][0],p[1]-clean[-1][1])>.05:clean.append(p)
 if len(clean)>1 and math.hypot(clean[0][0]-clean[-1][0],clean[0][1]-clean[-1][1])<.05:clean.pop()
 if len(clean)<=limit:return clean
 step=len(clean)/limit
 return [clean[int(i*step)] for i in range(limit)]


class QatarHandler(osmium.SimpleHandler):
 def __init__(self,origin_e,origin_n,nx,ny,country_polygons,coast_buffer):
  super().__init__();self.oe=origin_e;self.on=origin_n;self.nx=nx;self.ny=ny;self.polygons=country_polygons;self.buffer=coast_buffer
  self.roads=defaultdict(list);self.buildings=defaultdict(list);self.seen_building_ways=set()
  self.road_ways=0;self.road_segments=0;self.building_ways=0;self.relation_buildings=0;self.skipped_invalid=0;self.outside=0
 def accepted(self,x,z):
  if point_in_polygons(x,z,self.polygons):return True
  # Include recent coastal reclamations close to the coarse legal land envelope.
  return distance_to_coast(x,z,self.polygons)<=self.buffer
 def cell(self,x,z):
  ix=math.floor((x-self.oe)/CHUNK_METRES);iy=math.floor((z-self.on)/CHUNK_METRES)
  return (ix,iy) if 0<=ix<self.nx and 0<=iy<self.ny else None
 def way(self,w):
  tags={k:v for k,v in w.tags}
  road=road_attributes(tags)
  if road:
   points=[]
   try:
    for n in w.nodes:
     if n.location.valid():points.append(utm39(n.location.lon,n.location.lat))
   except Exception:
    self.skipped_invalid+=1;points=[]
   accepted_segments=0
   for a,b in zip(points,points[1:]):
    mx=(a[0]+b[0])/2;mz=(a[1]+b[1])/2
    if not (self.accepted(mx,mz) or self.accepted(*a) or self.accepted(*b)):continue
    cell=self.cell(mx,mz)
    if cell is None:continue
    ix,iy=cell;ox=self.oe+ix*CHUNK_METRES;oz=self.on+iy*CHUNK_METRES
    rc,lanes,surface,flags,width=road
    self.roads[cell].append((int(w.id),round((a[0]-ox)*10),round((a[1]-oz)*10),round((b[0]-ox)*10),round((b[1]-oz)*10),rc,lanes,surface,flags,round(width*10)))
    self.road_segments+=1;accepted_segments+=1
   if accepted_segments:self.road_ways+=1
  if tags.get('building') and len(w.nodes)>=4:
   points=[]
   try:
    for n in w.nodes:
     if n.location.valid():points.append(utm39(n.location.lon,n.location.lat))
   except Exception:
    self.skipped_invalid+=1;return
   if self.add_building(int(w.id),points,tags):
    self.seen_building_ways.add(int(w.id));self.building_ways+=1
 def area(self,a):
  if a.from_way():return
  tags={k:v for k,v in a.tags}
  if not tags.get('building'):return
  outer_index=0
  try:
   for ring in a.outer_rings():
    points=[utm39(n.lon,n.lat) for n in ring if n.location.valid()]
    source_id=-(int(a.orig_id())*100+outer_index+1);outer_index+=1
    if self.add_building(source_id,points,tags):self.relation_buildings+=1
  except Exception:self.skipped_invalid+=1
 def add_building(self,source_id,points,tags):
  points=simplify_vertices(points)
  if len(points)<3:self.skipped_invalid+=1;return False
  cx,cz,width,depth,yaw=oriented_bounds(points)
  if width<.5 or depth<.5 or width*depth>2_000_000:self.skipped_invalid+=1;return False
  if not self.accepted(cx,cz):self.outside+=1;return False
  cell=self.cell(cx,cz)
  if cell is None:self.outside+=1;return False
  ix,iy=cell;ox=self.oe+ix*CHUNK_METRES;oz=self.on+iy*CHUNK_METRES
  arch=classify_building(tags);height=building_height(tags,arch)
  local=[(round((x-ox)*10),round((z-oz)*10)) for x,z in points]
  flags=2 # source-derived OSM footprint
  self.buildings[cell].append((source_id,round((cx-ox)*10),round((cz-oz)*10),round(width*10),round(depth*10),round(height*10),round((yaw%math.pi)/math.pi*65535),arch,flags,local))
  return True


def compile_detail(pbf:Path,output:Path,report_path:Path,national_pack:Path,epoch:str,coast_buffer:float):
 base=national_pack.read_bytes()[:64]
 magic,version,epsg,chunk_metres,mask_dim,base_count,oe,on,ww,wh,metadata_bytes,index_bytes=struct.unpack(HEADER_FORMAT,base)
 if magic!=b'QTPK' or epsg!=EPSG or chunk_metres!=CHUNK_METRES:raise ValueError('National QTPK geometry contract mismatch')
 nx=round(ww/CHUNK_METRES);ny=round(wh/CHUNK_METRES)
 adm0=json.loads((ROOT/'data/qatar/source/geoboundaries-6-QAT-ADM0.geojson').read_text())
 polygons=normalize_polygons(adm0['features'][0]['geometry'])
 handler=QatarHandler(oe,on,nx,ny,polygons,coast_buffer)
 handler.apply_file(str(pbf),locations=True,idx='flex_mem')
 keys=sorted(set(handler.roads)|set(handler.buildings),key=lambda p:(p[1],p[0]))
 blocks=[];total_raw=0
 for ix,iy in keys:
  roads=sorted(handler.roads.get((ix,iy),[]),key=lambda r:(r[0],r[1],r[2],r[3],r[4]))
  buildings=sorted(handler.buildings.get((ix,iy),[]),key=lambda r:r[0])
  raw=bytearray(BLOCK_MAGIC);raw.extend(struct.pack('<iiI',ix,iy,len(roads)))
  for r in roads:raw.extend(struct.pack('<q4iBBBBH',*r))
  raw.extend(struct.pack('<I',len(buildings)))
  for b in buildings:
   source_id,cx,cz,width,depth,height,yaw,arch,flags,vertices=b
   raw.extend(struct.pack('<q5iHBBH',source_id,cx,cz,width,depth,height,yaw,arch,flags,len(vertices)))
   for x,z in vertices:raw.extend(struct.pack('<ii',x,z))
  packed=zlib.compress(bytes(raw),9);crc=binascii.crc32(raw)&0xffffffff
  blocks.append((ix,iy,bytes(raw),packed,crc,len(roads),len(buildings)));total_raw+=len(raw)
 source_hash=sha256(pbf)
 metadata=bytearray();write_string(metadata,epoch);write_string(metadata,SOURCE_URL);write_string(metadata,source_hash);write_string(metadata,'import_qatar_osm.py/QTOD-v1/pyosmium-4.1.1')
 metadata.extend(struct.pack('<QQQQQQQ',handler.road_ways,handler.road_segments,handler.building_ways,handler.relation_buildings,handler.skipped_invalid,handler.outside,len(keys)))
 header_size=struct.calcsize(HEADER_FORMAT);index_size=struct.calcsize(INDEX_FORMAT)*len(blocks);offset=header_size+len(metadata)+index_size
 index=bytearray()
 for ix,iy,raw,packed,crc,roads,buildings in blocks:
  if roads>65535 or buildings>65535:raise ValueError(f'Chunk {ix},{iy} exceeds index counters')
  index.extend(struct.pack(INDEX_FORMAT,ix,iy,offset,len(packed),len(raw),crc,roads,buildings));offset+=len(packed)
 header=struct.pack(HEADER_FORMAT,MAGIC,VERSION,EPSG,CHUNK_METRES,16,len(blocks),oe,on,ww,wh,len(metadata),len(index))
 output.parent.mkdir(parents=True,exist_ok=True)
 with output.open('wb') as f:
  f.write(header);f.write(metadata);f.write(index)
  for _,_,_,packed,_,_,_ in blocks:f.write(packed)
 report={
  'format':'QTOD','version':VERSION,'epoch':epoch,'sourceUrl':SOURCE_URL,'sourcePbfSha256':source_hash,'sourcePbfBytes':pbf.stat().st_size,
  'epsg':EPSG,'chunkMetres':CHUNK_METRES,'detailChunks':len(blocks),'roadWays':handler.road_ways,'roadSegments':handler.road_segments,
  'simpleBuildingFootprints':handler.building_ways,'relationBuildingOuters':handler.relation_buildings,'totalBuildingFootprints':handler.building_ways+handler.relation_buildings,
  'skippedInvalidFeatures':handler.skipped_invalid,'outsideFeatures':handler.outside,'coastReclamationBufferMetres':coast_buffer,
  'rawDetailBytes':total_raw,'qodBytes':output.stat().st_size,'qodSha256':sha256(output),
  'notes':['All accepted road segments and complete simple OSM building outer footprints are retained.','Relation inner rings are not rendered in QTOD v1 and remain a declared limitation.','Game geometry is not navigation data. © OpenStreetMap contributors, ODbL 1.0.']}
 report_path.parent.mkdir(parents=True,exist_ok=True);report_path.write_text(json.dumps(report,indent=2)+'\n')
 print(json.dumps(report,indent=2));return report


def validate(path:Path):
 b=path.read_bytes();h=struct.unpack_from(HEADER_FORMAT,b,0)
 if h[0]!=MAGIC or h[1]!=VERSION or h[2]!=EPSG:raise ValueError('QTOD header invalid')
 count=h[5];ml=h[-2];il=h[-1]
 if il!=count*struct.calcsize(INDEX_FORMAT):raise ValueError('QTOD index length invalid')
 pos=struct.calcsize(HEADER_FORMAT)+ml;last=0
 totals=[0,0]
 for i in range(count):
  e=struct.unpack_from(INDEX_FORMAT,b,pos+i*struct.calcsize(INDEX_FORMAT));x,y,off,pl,rl,crc,roads,buildings=e
  raw=zlib.decompress(b[off:off+pl])
  if len(raw)!=rl or (binascii.crc32(raw)&0xffffffff)!=crc or raw[:4]!=BLOCK_MAGIC:raise ValueError(f'QTOD block {x},{y} invalid')
  totals[0]+=roads;totals[1]+=buildings;last=off+pl
 if last!=len(b):raise ValueError('QTOD trailing or missing bytes')
 print(f'VALID QTOD chunks={count} roads={totals[0]} buildings={totals[1]} bytes={len(b)} sha256={sha256(path)}')


def main():
 ap=argparse.ArgumentParser();ap.add_argument('--input',type=Path);ap.add_argument('--output',type=Path,default=ROOT/'data/qatar/compiled/qatar-osm-current.qod');ap.add_argument('--report',type=Path,default=ROOT/'data/qatar/compiled/qatar-osm-report.json');ap.add_argument('--national-pack',type=Path,default=ROOT/'app/src/main/assets/world/qatar-national.qpak');ap.add_argument('--epoch',default='unknown');ap.add_argument('--coast-buffer',type=float,default=3500);ap.add_argument('--validate',type=Path)
 args=ap.parse_args()
 if args.validate:validate(args.validate);return
 if not args.input:ap.error('--input is required unless --validate is used')
 compile_detail(args.input,args.output,args.report,args.national_pack,args.epoch,args.coast_buffer);validate(args.output)

if __name__=='__main__':main()
