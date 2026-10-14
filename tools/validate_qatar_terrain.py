#!/usr/bin/env python3
"""Validate QDEM/QTPK terrain range, coastline policy, seams, and spawn elevations."""
from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import math
import struct
import zlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
QDEM=ROOT/'data/qatar/source/qatar-srtm-250m.qdem'
QPAK=ROOT/'app/src/main/assets/world/qatar-national.qpak'
QDEM_HEADER=struct.Struct('<4sIIIIIddddI')
QPAK_HEADER=struct.Struct('<4sIIIIIddddII')
ENTRY=struct.Struct('<iiqiiIBBBB')


def sha256(path:Path)->str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_string(data:bytes,offset:int)->tuple[str,int]:
    length=struct.unpack_from('<H',data,offset)[0];offset+=2
    return data[offset:offset+length].decode('utf-8'),offset+length


def sample(terrain:tuple[int,...],x:float,z:float,chunk_metres:int)->float:
    gx=max(0.0,min(8.0,x/chunk_metres*8));gz=max(0.0,min(8.0,z/chunk_metres*8))
    x0=min(7,int(gx));z0=min(7,int(gz));tx=gx-x0;tz=gz-z0
    a=terrain[z0*9+x0]*(1-tx)+terrain[z0*9+x0+1]*tx
    b=terrain[(z0+1)*9+x0]*(1-tx)+terrain[(z0+1)*9+x0+1]*tx
    return (a*(1-tz)+b*tz)/100


def validate(qdem_path:Path,qpak_path:Path)->dict:
    qdem=qdem_path.read_bytes()
    magic,version,epsg,spacing,columns,rows,dem_oe,dem_on,width,height,crc=QDEM_HEADER.unpack_from(qdem)
    dem_payload=qdem[QDEM_HEADER.size:]
    if (magic,version,epsg,spacing)!=(b'QDEM',1,32639,250):raise ValueError('QDEM spatial contract mismatch')
    if len(dem_payload)!=columns*rows*2 or binascii.crc32(dem_payload)&0xffffffff!=crc:raise ValueError('QDEM payload/CRC mismatch')
    dem_values=struct.unpack(f'<{columns*rows}h',dem_payload)
    if not (-10_000<min(dem_values)<0<max(dem_values)<20_000):raise ValueError('QDEM measured range is implausible')

    pack=qpak_path.read_bytes();header=QPAK_HEADER.unpack_from(pack)
    pmagic,pversion,pepsg,chunk_metres,mask_dim,chunk_count,oe,on,pwidth,pheight,metadata_bytes,index_bytes=header
    if (pmagic,pversion,pepsg,chunk_metres,mask_dim)!=(b'QTPK',1,32639,2000,16):raise ValueError('QTPK spatial contract mismatch')
    if (oe,on,pwidth,pheight)!=(dem_oe,dem_on,width,height):raise ValueError('QDEM/QTPK extents are not identical')
    metadata=pack[QPAK_HEADER.size:QPAK_HEADER.size+metadata_bytes];cursor=0
    epoch,cursor=read_string(metadata,cursor)
    if epoch!='2026-10-14-srtm-osm-v0.3':raise ValueError(f'unexpected terrain epoch {epoch}')
    cursor+=32
    municipality_count=struct.unpack_from('<H',metadata,cursor)[0];cursor+=2
    for _ in range(municipality_count):
        for _ in range(3):_,cursor=read_string(metadata,cursor)
    biome_count=struct.unpack_from('<H',metadata,cursor)[0];cursor+=2
    for _ in range(biome_count):_,cursor=read_string(metadata,cursor)
    hub_count=struct.unpack_from('<H',metadata,cursor)[0];cursor+=2
    hubs=[]
    for _ in range(hub_count):
        name,cursor=read_string(metadata,cursor);_,cursor=read_string(metadata,cursor);kind,cursor=read_string(metadata,cursor)
        lat,lon,local_x,local_z=struct.unpack_from('<dddd',metadata,cursor);cursor+=32
        hubs.append((name,kind,local_x,local_z,lat,lon))

    index_offset=QPAK_HEADER.size+metadata_bytes
    chunks={};node_min=32767;node_max=-32768;centre_min=math.inf;centre_max=-math.inf;centres=0;seams=0
    for i in range(chunk_count):
        ix,iy,offset,packed_len,raw_len,expected_crc,municipality,biome,flags,_=ENTRY.unpack_from(pack,index_offset+i*ENTRY.size)
        raw=zlib.decompress(pack[offset:offset+packed_len])
        if len(raw)!=raw_len or binascii.crc32(raw)&0xffffffff!=expected_crc or raw[:4]!=b'QCHK':raise ValueError(f'chunk integrity failure {ix},{iy}')
        land_rows=struct.unpack_from('<16H',raw,20);terrain=struct.unpack_from('<81h',raw,52)
        land=bool(flags&1)
        if land and min(terrain)<-200:raise ValueError(f'land terrain below -2 m policy at {ix},{iy}')
        if land:
            node_min=min(node_min,min(terrain));node_max=max(node_max,max(terrain))
            for row,bits in enumerate(land_rows):
                for column in range(16):
                    if bits&(1<<column):
                        elevation=sample(terrain,(column+.5)*chunk_metres/16,(row+.5)*chunk_metres/16,chunk_metres)
                        if elevation < -2.00001:raise ValueError(f'land-mask centre below policy at {ix},{iy}')
                        centre_min=min(centre_min,elevation);centre_max=max(centre_max,elevation);centres+=1
        chunks[(ix,iy)]={'land':land,'rows':land_rows,'terrain':terrain}

    for (ix,iy),chunk in chunks.items():
        if not chunk['land']:continue
        terrain=chunk['terrain']
        east=chunks.get((ix+1,iy))
        if east and east['land']:
            if [terrain[row*9+8] for row in range(9)]!=[east['terrain'][row*9] for row in range(9)]:raise ValueError(f'east terrain seam {ix},{iy}')
            seams+=1
        north=chunks.get((ix,iy+1))
        if north and north['land']:
            if list(terrain[72:81])!=list(north['terrain'][:9]):raise ValueError(f'north terrain seam {ix},{iy}')
            seams+=1

    spawn_elevations=[]
    for name,kind,local_x,local_z,lat,lon in hubs:
        ix=int(math.floor(local_x/chunk_metres));iy=int(math.floor(local_z/chunk_metres));chunk=chunks.get((ix,iy))
        if chunk is None or not chunk['land']:raise ValueError(f'hub is outside packaged land: {name}')
        cell_x=min(15,max(0,int((local_x-ix*chunk_metres)/(chunk_metres/16))));cell_z=min(15,max(0,int((local_z-iy*chunk_metres)/(chunk_metres/16))))
        land_cell=bool(chunk['rows'][cell_z]&(1<<cell_x))
        if name=='Souq Waqif' and not land_cell:raise ValueError('default spawn is outside the land mask')
        elevation=sample(chunk['terrain'],local_x-ix*chunk_metres,local_z-iy*chunk_metres,chunk_metres)
        if not math.isfinite(elevation) or not (-2.00001<=elevation<=150):raise ValueError(f'unsafe hub elevation: {name}')
        spawn_elevations.append({'name':name,'kind':kind,'elevationMetres':round(elevation,3),'landMaskCell':land_cell})
    if max(item['elevationMetres'] for item in spawn_elevations)-min(item['elevationMetres'] for item in spawn_elevations)<10:raise ValueError('spawn elevations do not demonstrate measured terrain variation')

    return {
        'format':'QatarTerrainValidation','version':1,'worldEpoch':epoch,'epsg':pepsg,
        'qdem':{'path':str(qdem_path.relative_to(ROOT)),'sha256':sha256(qdem_path),'columns':columns,'rows':rows,'spacingMetres':spacing,'minimumMetres':min(dem_values)/100,'maximumMetres':max(dem_values)/100,'crc32':f'{crc:08x}'},
        'qpak':{'path':str(qpak_path.relative_to(ROOT)),'sha256':sha256(qpak_path),'chunks':chunk_count,'landNodeMinimumMetres':node_min/100,'landNodeMaximumMetres':node_max/100,'landMaskCentreMinimumMetres':round(centre_min,6),'landMaskCentreMaximumMetres':round(centre_max,6),'landMaskCentresChecked':centres,'continuousLandSeamsChecked':seams},
        'spawns':{'default':'Souq Waqif','count':len(spawn_elevations),'landMaskSafeCount':sum(x['landMaskCell'] for x in spawn_elevations),'minimumMetres':min(x['elevationMetres'] for x in spawn_elevations),'maximumMetres':max(x['elevationMetres'] for x in spawn_elevations),'locations':spawn_elevations},
        'result':'PASS'
    }


def main()->int:
    parser=argparse.ArgumentParser();parser.add_argument('--qdem',type=Path,default=QDEM);parser.add_argument('--qpak',type=Path,default=QPAK);parser.add_argument('--report',type=Path)
    args=parser.parse_args();report=validate(args.qdem,args.qpak);text=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(text,encoding='utf-8')
    print(text,end='');return 0


if __name__=='__main__':raise SystemExit(main())
