#!/usr/bin/env python3
"""Resample pinned NASA SRTM/Mapzen Skadi HGT tiles into the compact Qatar QDEM grid."""
from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import math
import re
import struct
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
QPAK=ROOT/'app/src/main/assets/world/qatar-national.qpak'
OUTPUT=ROOT/'data/qatar/source/qatar-srtm-250m.qdem'
REPORT=ROOT/'data/qatar/compiled/qatar-dem-report.json'
HEADER=struct.Struct('<4sIIIIIddddI')
TILE_RE=re.compile(r'([NS])(\d{2})([EW])(\d{3})\.hgt$')
SOURCE_BASE='https://s3.amazonaws.com/elevation-tiles-prod/skadi'


def sha256(path:Path)->str:
 h=hashlib.sha256()
 with path.open('rb') as f:
  for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
 return h.hexdigest()


def utm39_inverse(easting:float,northing:float)->tuple[float,float]:
 """Inverse WGS84 UTM zone 39N; returns longitude, latitude in degrees."""
 a=6378137.0;f=1/298.257223563;e2=f*(2-f);ep2=e2/(1-e2);k0=.9996
 x=easting-500000.0;y=northing
 m=y/k0;mu=m/(a*(1-e2/4-3*e2**2/64-5*e2**3/256))
 e1=(1-math.sqrt(1-e2))/(1+math.sqrt(1-e2))
 j1=3*e1/2-27*e1**3/32;j2=21*e1**2/16-55*e1**4/32;j3=151*e1**3/96;j4=1097*e1**4/512
 fp=mu+j1*math.sin(2*mu)+j2*math.sin(4*mu)+j3*math.sin(6*mu)+j4*math.sin(8*mu)
 sinfp=math.sin(fp);cosfp=math.cos(fp);tanfp=math.tan(fp)
 c1=ep2*cosfp*cosfp;t1=tanfp*tanfp;n1=a/math.sqrt(1-e2*sinfp*sinfp);r1=a*(1-e2)/(1-e2*sinfp*sinfp)**1.5;d=x/(n1*k0)
 lat=fp-(n1*tanfp/r1)*(d*d/2-(5+3*t1+10*c1-4*c1*c1-9*ep2)*d**4/24+(61+90*t1+298*c1+45*t1*t1-252*ep2-3*c1*c1)*d**6/720)
 lon=math.radians(51)+(d-(1+2*t1+c1)*d**3/6+(5-2*c1+28*t1-3*c1*c1+8*ep2+24*t1*t1)*d**5/120)/cosfp
 return math.degrees(lon),math.degrees(lat)


class HgtTile:
 def __init__(self,path:Path):
  match=TILE_RE.fullmatch(path.name)
  if not match:raise ValueError(f'Unrecognised HGT tile name {path.name}')
  self.path=path;self.lat0=int(match.group(2))*(-1 if match.group(1)=='S' else 1);self.lon0=int(match.group(4))*(-1 if match.group(3)=='W' else 1)
  self.data=path.read_bytes();samples=math.isqrt(len(self.data)//2)
  if samples*samples*2!=len(self.data) or samples not in (1201,3601):raise ValueError(f'Unsafe HGT dimensions for {path}')
  self.samples=samples;self.maximum=samples-1
 def value(self,row:int,column:int)->int:
  return struct.unpack_from('>h',self.data,(row*self.samples+column)*2)[0]
 def sample(self,lon:float,lat:float)->tuple[float,bool]:
  row=max(0.0,min(self.maximum,(self.lat0+1-lat)*self.maximum));column=max(0.0,min(self.maximum,(lon-self.lon0)*self.maximum))
  r0=int(math.floor(row));c0=int(math.floor(column));r1=min(self.maximum,r0+1);c1=min(self.maximum,c0+1);fr=row-r0;fc=column-c0
  weighted=[]
  for r,c,w in ((r0,c0,(1-fr)*(1-fc)),(r0,c1,(1-fr)*fc),(r1,c0,fr*(1-fc)),(r1,c1,fr*fc)):
   value=self.value(r,c)
   if value!=-32768:weighted.append((value,w))
  total=sum(w for _,w in weighted)
  return ((sum(value*w for value,w in weighted)/total,False) if total>0 else (0.0,True))


def compile_dem(tile_paths:list[Path],output:Path,report_path:Path,epoch:str):
 qpak=QPAK.read_bytes()[:64]
 magic,version,epsg,chunk_metres,mask_dim,chunks,oe,on,width,height,metadata,index=struct.unpack('<4sIIIIIddddII',qpak)
 if magic!=b'QTPK' or epsg!=32639 or chunk_metres!=2000:raise ValueError('National QTPK contract mismatch')
 spacing=250;columns=round(width/spacing)+1;rows=round(height/spacing)+1
 tiles={}
 for path in tile_paths:
  tile=HgtTile(path);tiles[(tile.lat0,tile.lon0)]=tile
 values=[];void_samples=0;minimum=32767;maximum=-32768
 for row in range(rows):
  northing=on+row*spacing
  for column in range(columns):
   easting=oe+column*spacing;lon,lat=utm39_inverse(easting,northing);key=(math.floor(lat),math.floor(lon))
   tile=tiles.get(key)
   if tile is None:raise ValueError(f'Missing HGT tile {key} for {lat},{lon}')
   metres,void=tile.sample(lon,lat);void_samples+=int(void)
   centimetres=max(-32768,min(32767,int(round(metres*100))))
   values.append(centimetres);minimum=min(minimum,centimetres);maximum=max(maximum,centimetres)
 payload=struct.pack(f'<{len(values)}h',*values);crc=binascii.crc32(payload)&0xffffffff
 header=HEADER.pack(b'QDEM',1,32639,spacing,columns,rows,oe,on,width,height,crc)
 output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(header+payload)
 tile_report=[]
 for key,tile in sorted(tiles.items()):
  tile_report.append({'name':tile.path.name,'sourceUrl':f'{SOURCE_BASE}/{tile.path.name[:3]}/{tile.path.name}.gz','bytes':tile.path.stat().st_size,'sha256':sha256(tile.path),'samplesPerSide':tile.samples})
 report={'format':'QDEM','version':1,'epoch':epoch,'dataset':'NASA SRTM 1 arc-second global elevation via Mapzen/AWS Terrain Tiles Skadi mirror','license':'NASA SRTM data are public domain in the United States; attribution requested','attribution':'NASA Shuttle Radar Topography Mission (SRTM), USGS; Mapzen/AWS Terrain Tiles distribution','epsg':32639,'sampleSpacingMetres':spacing,'columns':columns,'rows':rows,'samples':len(values),'originEasting':oe,'originNorthing':on,'worldWidth':width,'worldHeight':height,'minimumMetres':minimum/100,'maximumMetres':maximum/100,'voidSamplesAfterInterpolation':void_samples,'tiles':tile_report,'qdemBytes':output.stat().st_size,'qdemSha256':sha256(output),'notes':['Bilinear source sampling onto the deterministic QTPK 250 m grid.','Elevation is terrain-scale game data, not survey, navigation, aviation, or engineering data.']}
 report_path.parent.mkdir(parents=True,exist_ok=True);report_path.write_text(json.dumps(report,indent=2)+'\n')
 print(json.dumps(report,indent=2));return report


def validate(path:Path):
 raw=path.read_bytes();magic,version,epsg,spacing,columns,rows,oe,on,width,height,crc=HEADER.unpack_from(raw);payload=raw[HEADER.size:]
 if magic!=b'QDEM' or version!=1 or epsg!=32639 or spacing!=250:raise ValueError('QDEM header invalid')
 if len(payload)!=columns*rows*2 or (binascii.crc32(payload)&0xffffffff)!=crc:raise ValueError('QDEM payload invalid')
 vals=struct.unpack(f'<{columns*rows}h',payload)
 print(f'VALID QDEM {columns}x{rows} range={min(vals)/100:.2f}..{max(vals)/100:.2f}m bytes={len(raw)} sha256={sha256(path)}')


def main():
 parser=argparse.ArgumentParser();parser.add_argument('--tile',type=Path,action='append',default=[]);parser.add_argument('--output',type=Path,default=OUTPUT);parser.add_argument('--report',type=Path,default=REPORT);parser.add_argument('--epoch',default='unknown');parser.add_argument('--validate',type=Path)
 args=parser.parse_args()
 if args.validate:validate(args.validate);return
 if not args.tile:parser.error('at least one --tile is required')
 compile_dem(args.tile,args.output,args.report,args.epoch);validate(args.output)

if __name__=='__main__':main()
