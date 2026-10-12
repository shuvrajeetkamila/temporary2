#!/usr/bin/env python3
"""Deterministically compile the Qatar national streaming pack.

Edition 0.1 compiles geoBoundaries gbOpen ADM0/ADM1 geometry into a complete
2 km country grid with 125 m coast masks, a compact national outline, all
eight municipality records, regional hubs, biome parameters, coarse road
corridors, and deterministic source-labelled settlement massing. The binary
format is intentionally extensible: later milestones replace generated roads
and massing with the complete licensed OSM/DEM derivatives without changing
the runtime streaming contract.
"""
from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import math
import random
import struct
import sys
import zlib
from collections import defaultdict
from pathlib import Path
from typing import Iterable, Sequence

ROOT = Path(__file__).resolve().parents[1]
ADM0_PATH = ROOT / "data/qatar/source/geoboundaries-6-QAT-ADM0.geojson"
ADM1_PATH = ROOT / "data/qatar/source/geoboundaries-6-QAT-ADM1.geojson"
NE_PATH = ROOT / "data/qatar/source/natural-earth-10m-QAT.geojson"
OUTPUT_PATH = ROOT / "app/src/main/assets/world/qatar-national.qpak"
REPORT_PATH = ROOT / "data/qatar/compiled/qatar-world-report.json"
MANIFEST_PATH = ROOT / "app/src/main/assets/world/qatar-manifest.json"
CHECKSUM_PATH = ROOT / "data/qatar/SHA256SUMS"

MAGIC = b"QTPK"
CHUNK_MAGIC = b"QCHK"
VERSION = 1
EPSG = 32639
CHUNK_METRES = 2000
MASK_DIM = 16
WATER_BUFFER_METRES = 8000
WORLD_EPOCH = "2026-10-12-coarse-foundation"

# IDs are stable serialization values. Do not reorder in a released format.
MUNICIPALITY_CANONICAL = [
    ("QA-DA", "Doha", "الدوحة"),
    ("QA-RA", "Al Rayyan", "الريان"),
    ("QA-WA", "Al Wakrah", "الوكرة"),
    ("QA-ZA", "Al Daayen", "الظعاين"),
    ("QA-US", "Umm Salal", "أم صلال"),
    ("QA-KH", "Al Khor and Al Thakhira", "الخور والذخيرة"),
    ("QA-MS", "Al Shamal", "الشمال"),
    ("QA-SH", "Al Shahaniya", "الشحانية"),
]
MUNICIPALITY_ID = {iso: i for i, (iso, _, _) in enumerate(MUNICIPALITY_CANONICAL)}

BIOMES = [
    "GULF_WATER", "URBAN", "LIMESTONE_GRAVEL", "SAND_DUNES",
    "SABKHA", "MANGROVE_TIDAL", "FARM_RAWDA", "INDUSTRIAL_PORT",
    "ROCK_FORMATION", "BEACH_SHALLOW",
]
BIOME_ID = {name: i for i, name in enumerate(BIOMES)}

# Public place coordinates are coarse geographic anchors, not private facilities.
HUBS = [
    ("Doha Corniche", "كورنيش الدوحة", 25.3010, 51.5265, "capital"),
    ("Souq Waqif", "سوق واقف", 25.2868, 51.5333, "heritage"),
    ("West Bay", "الخليج الغربي", 25.3250, 51.5310, "skyline"),
    ("Hamad International Airport", "مطار حمد الدولي", 25.2609, 51.6138, "airport"),
    ("Al Rayyan", "الريان", 25.2919, 51.4244, "city"),
    ("Education City", "المدينة التعليمية", 25.3160, 51.4387, "campus"),
    ("Umm Salal", "أم صلال", 25.4169, 51.4039, "town"),
    ("Lusail", "لوسيل", 25.4200, 51.4900, "city"),
    ("Al Wakrah", "الوكرة", 25.1715, 51.6034, "city"),
    ("Mesaieed", "مسيعيد", 24.9900, 51.5500, "industrial"),
    ("Hamad Port", "ميناء حمد", 25.0100, 51.6200, "port"),
    ("Khor Al Adaid", "خور العديد", 24.6300, 51.3300, "dunes"),
    ("Abu Samra", "أبو سمرة", 24.7440, 50.8410, "border"),
    ("Al Khor", "الخور", 25.6839, 51.5058, "city"),
    ("Al Thakhira", "الذخيرة", 25.7340, 51.5470, "mangrove"),
    ("Ras Laffan", "رأس لفان", 25.9100, 51.5500, "industrial"),
    ("Madinat ash Shamal", "مدينة الشمال", 26.1293, 51.2009, "town"),
    ("Al Ruwais", "الرويس", 26.1390, 51.2160, "port"),
    ("Fuwairit", "فويرط", 26.0340, 51.3740, "beach"),
    ("Al Zubarah", "الزبارة", 25.9760, 51.0450, "heritage"),
    ("Al Shahaniya", "الشحانية", 25.3709, 51.2226, "town"),
    ("Dukhan", "دخان", 25.4280, 50.7850, "coast"),
    ("Zekreet", "زكريت", 25.4860, 50.8460, "rock"),
    ("Al Kiranah", "الكرعانة", 25.0100, 51.0450, "interior"),
    ("Al Daayen", "الظعاين", 25.5400, 51.4600, "town"),
]

# Coarse public connectivity graph for Milestone 1. It is explicitly labelled
# generated in metadata and is replaced, not combined, when OSM import lands.
ROUTES = [
    (0, 2, 0), (0, 3, 0), (0, 4, 0), (4, 5, 1), (4, 20, 0),
    (0, 7, 0), (7, 24, 1), (7, 13, 0), (13, 15, 0), (15, 16, 0),
    (16, 17, 1), (16, 18, 1), (16, 19, 1), (20, 21, 0), (21, 22, 1),
    (22, 19, 1), (4, 12, 0), (12, 23, 1), (23, 9, 0), (9, 10, 0),
    (9, 11, 1), (8, 9, 0), (0, 8, 0), (6, 7, 0), (6, 20, 1),
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def utm39(lon_deg: float, lat_deg: float) -> tuple[float, float]:
    """WGS84 transverse Mercator, UTM zone 39 north, metre output."""
    a = 6378137.0
    f = 1.0 / 298.257223563
    e2 = f * (2.0 - f)
    ep2 = e2 / (1.0 - e2)
    k0 = 0.9996
    lon0 = math.radians(51.0)
    lat = math.radians(lat_deg)
    lon = math.radians(lon_deg)
    sin_lat = math.sin(lat)
    cos_lat = math.cos(lat)
    tan_lat = math.tan(lat)
    n = a / math.sqrt(1.0 - e2 * sin_lat * sin_lat)
    t = tan_lat * tan_lat
    c = ep2 * cos_lat * cos_lat
    aa = cos_lat * (lon - lon0)
    m = a * (
        (1 - e2 / 4 - 3 * e2**2 / 64 - 5 * e2**3 / 256) * lat
        - (3 * e2 / 8 + 3 * e2**2 / 32 + 45 * e2**3 / 1024) * math.sin(2 * lat)
        + (15 * e2**2 / 256 + 45 * e2**3 / 1024) * math.sin(4 * lat)
        - (35 * e2**3 / 3072) * math.sin(6 * lat)
    )
    easting = 500000 + k0 * n * (
        aa + (1 - t + c) * aa**3 / 6
        + (5 - 18*t + t*t + 72*c - 58*ep2) * aa**5 / 120
    )
    northing = k0 * (m + n * tan_lat * (
        aa*aa/2 + (5 - t + 9*c + 4*c*c) * aa**4/24
        + (61 - 58*t + t*t + 600*c - 330*ep2) * aa**6/720
    ))
    return easting, northing


def normalize_polygons(geometry: dict) -> list[list[list[tuple[float, float]]]]:
    coords = geometry["coordinates"]
    if geometry["type"] == "Polygon":
        coords = [coords]
    if geometry["type"] != "MultiPolygon" and geometry["type"] != "Polygon":
        raise ValueError(f"Unsupported geometry {geometry['type']}")
    result = []
    for polygon in coords:
        rings = []
        for ring in polygon:
            rings.append([utm39(float(p[0]), float(p[1])) for p in ring])
        result.append(rings)
    return result


def point_in_ring(x: float, y: float, ring: Sequence[tuple[float, float]]) -> bool:
    inside = False
    j = len(ring) - 1
    for i, (xi, yi) in enumerate(ring):
        xj, yj = ring[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / ((yj - yi) or 1e-20) + xi):
            inside = not inside
        j = i
    return inside


def point_in_polygons(x: float, y: float, polygons: Sequence[Sequence[Sequence[tuple[float, float]]]]) -> bool:
    for polygon in polygons:
        if not polygon or not point_in_ring(x, y, polygon[0]):
            continue
        if not any(point_in_ring(x, y, hole) for hole in polygon[1:]):
            return True
    return False


def point_segment_distance(px: float, py: float, ax: float, ay: float, bx: float, by: float) -> float:
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0.0, min(1.0, ((px-ax)*dx + (py-ay)*dy) / (dx*dx + dy*dy)))
    return math.hypot(px-(ax+t*dx), py-(ay+t*dy))


def distance_to_coast(x: float, y: float, polygons) -> float:
    best = float("inf")
    for polygon in polygons:
        for ring in polygon:
            for a, b in zip(ring, ring[1:]):
                best = min(best, point_segment_distance(x, y, a[0], a[1], b[0], b[1]))
    return best


def classify_biome(x: float, y: float, land: bool, hub_xy, polygons) -> int:
    if not land:
        return BIOME_ID["GULF_WATER"]
    def near(index: int, km: float) -> bool:
        hx, hy = hub_xy[index]
        return math.hypot(x-hx, y-hy) <= km*1000
    if near(14, 7):
        return BIOME_ID["MANGROVE_TIDAL"]
    if near(11, 20):
        return BIOME_ID["SAND_DUNES"]
    if near(22, 12):
        return BIOME_ID["ROCK_FORMATION"]
    if any(near(i, 8 if i not in (0, 2, 7) else 16) for i in (0,1,2,4,5,6,7,8,13,16,20)):
        return BIOME_ID["URBAN"]
    if any(near(i, 9) for i in (3,9,10,15)):
        return BIOME_ID["INDUSTRIAL_PORT"]
    if near(24, 18) or near(6, 12):
        return BIOME_ID["FARM_RAWDA"]
    if distance_to_coast(x, y, polygons) < 2200:
        return BIOME_ID["BEACH_SHALLOW"]
    if y < hub_xy[4][1] - 25000:
        return BIOME_ID["SABKHA"]
    return BIOME_ID["LIMESTONE_GRAVEL"]


def nearest_municipality(x: float, y: float, municipalities) -> int:
    for mid, polygons in municipalities:
        if point_in_polygons(x, y, polygons):
            return mid
    # Water/islet fallback: nearest outer-boundary vertex.
    best = (float("inf"), 0)
    for mid, polygons in municipalities:
        for poly in polygons:
            for px, py in poly[0]:
                d = (px-x)*(px-x) + (py-y)*(py-y)
                if d < best[0]:
                    best = (d, mid)
    return best[1]


def split_route(a: tuple[float,float], b: tuple[float,float], step=500.0):
    distance = math.hypot(b[0]-a[0], b[1]-a[1])
    count = max(1, math.ceil(distance / step))
    previous = a
    for i in range(1, count+1):
        t = i / count
        current = (a[0] + (b[0]-a[0])*t, a[1] + (b[1]-a[1])*t)
        yield previous, current
        previous = current


def write_string(buf: bytearray, value: str):
    encoded = value.encode("utf-8")
    if len(encoded) > 65535:
        raise ValueError("String too long")
    buf.extend(struct.pack("<H", len(encoded)))
    buf.extend(encoded)


def make_buildings(cell_x, cell_y, mask_rows, biome, hub_xy, seed):
    if biome not in (BIOME_ID["URBAN"], BIOME_ID["INDUSTRIAL_PORT"], BIOME_ID["FARM_RAWDA"]):
        return []
    cx = cell_x + CHUNK_METRES/2
    cy = cell_y + CHUNK_METRES/2
    distances = sorted((math.hypot(cx-hx, cy-hy), i) for i, (hx,hy) in enumerate(hub_xy))
    nearest, hub_id = distances[0]
    if biome == BIOME_ID["URBAN"]:
        count = int(max(8, 115 - nearest / 120))
    elif biome == BIOME_ID["INDUSTRIAL_PORT"]:
        count = int(max(5, 42 - nearest / 300))
    else:
        count = int(max(3, 20 - nearest / 400))
    count = min(count, 120)
    rng = random.Random(seed ^ 0x51A7A2)
    buildings = []
    for _ in range(count * 5):
        if len(buildings) >= count:
            break
        x = rng.uniform(50, CHUNK_METRES-50)
        y = rng.uniform(50, CHUNK_METRES-50)
        mx = min(MASK_DIM-1, max(0, int(x / CHUNK_METRES * MASK_DIM)))
        my = min(MASK_DIM-1, max(0, int(y / CHUNK_METRES * MASK_DIM)))
        if not (mask_rows[my] & (1 << mx)):
            continue
        if biome == BIOME_ID["INDUSTRIAL_PORT"]:
            w, d = rng.uniform(30,95), rng.uniform(22,70)
            h, archetype = rng.uniform(8,28), 4
        elif biome == BIOME_ID["FARM_RAWDA"]:
            w, d = rng.uniform(12,45), rng.uniform(10,35)
            h, archetype = rng.uniform(4,11), 5
        else:
            highrise = hub_id in (0,2,7) and nearest < 6500 and rng.random() < 0.18
            if highrise:
                w, d = rng.uniform(24,55), rng.uniform(24,55)
                h, archetype = rng.uniform(55,190), 1
            else:
                w, d = rng.uniform(10,32), rng.uniform(10,30)
                h = rng.uniform(5,22)
                archetype = 2 if h > 12 else 3
        yaw = rng.uniform(0, math.pi)
        buildings.append((x,y,w,d,h,yaw,archetype,1)) # flag 1 = generated/coarse
    return buildings


def make_terrain(seed: int, biome: int):
    rng = random.Random(seed ^ 0xA110CA7E)
    amplitude_cm = {
        BIOME_ID["GULF_WATER"]: 0,
        BIOME_ID["URBAN"]: 25,
        BIOME_ID["LIMESTONE_GRAVEL"]: 180,
        BIOME_ID["SAND_DUNES"]: 850,
        BIOME_ID["SABKHA"]: 35,
        BIOME_ID["MANGROVE_TIDAL"]: 20,
        BIOME_ID["FARM_RAWDA"]: 70,
        BIOME_ID["INDUSTRIAL_PORT"]: 20,
        BIOME_ID["ROCK_FORMATION"]: 360,
        BIOME_ID["BEACH_SHALLOW"]: 45,
    }[biome]
    # 9x9 centimetre microheight field; base DEM arrives in Milestone 3.
    vals=[]
    phases=(rng.random()*6.28,rng.random()*6.28,rng.random()*6.28)
    for j in range(9):
        for i in range(9):
            n=(math.sin(i*.71+phases[0])+math.sin(j*.49+phases[1])+math.sin((i+j)*.31+phases[2]))/3
            vals.append(int(max(-32768,min(32767,n*amplitude_cm))))
    return vals


def compile_world(output: Path, report_path: Path, manifest_path: Path):
    for required in (ADM0_PATH, ADM1_PATH, NE_PATH):
        if not required.is_file():
            raise FileNotFoundError(required)
    adm0_json = json.loads(ADM0_PATH.read_text(encoding="utf-8"))
    adm1_json = json.loads(ADM1_PATH.read_text(encoding="utf-8"))
    adm0_polygons = normalize_polygons(adm0_json["features"][0]["geometry"])

    municipalities=[]
    input_names=[]
    for feature in adm1_json["features"]:
        iso=feature["properties"]["shapeISO"]
        if iso not in MUNICIPALITY_ID:
            raise ValueError(f"Unexpected municipality ISO {iso}")
        mid=MUNICIPALITY_ID[iso]
        municipalities.append((mid,normalize_polygons(feature["geometry"])))
        input_names.append(iso)
    if set(input_names) != set(MUNICIPALITY_ID):
        raise ValueError(f"Municipality mismatch: {sorted(input_names)}")

    all_points=[point for polygon in adm0_polygons for ring in polygon for point in ring]
    boundary_segments=[]
    for polygon in adm0_polygons:
        for ring in polygon:
            for a,b in zip(ring,ring[1:]):
                boundary_segments.append((a,b,min(a[0],b[0]),min(a[1],b[1]),max(a[0],b[0]),max(a[1],b[1])))
    min_e=min(p[0] for p in all_points)-WATER_BUFFER_METRES
    max_e=max(p[0] for p in all_points)+WATER_BUFFER_METRES
    min_n=min(p[1] for p in all_points)-WATER_BUFFER_METRES
    max_n=max(p[1] for p in all_points)+WATER_BUFFER_METRES
    grid_origin_e=math.floor(min_e/CHUNK_METRES)*CHUNK_METRES
    grid_origin_n=math.floor(min_n/CHUNK_METRES)*CHUNK_METRES
    nx=math.ceil((max_e-grid_origin_e)/CHUNK_METRES)
    ny=math.ceil((max_n-grid_origin_n)/CHUNK_METRES)

    hub_xy=[utm39(lon,lat) for _,_,lat,lon,_ in HUBS]
    road_by_chunk=defaultdict(list)
    for a_i,b_i,road_class in ROUTES:
        for a,b in split_route(hub_xy[a_i],hub_xy[b_i]):
            mx=(a[0]+b[0])/2; my=(a[1]+b[1])/2
            ix=int(math.floor((mx-grid_origin_e)/CHUNK_METRES))
            iy=int(math.floor((my-grid_origin_n)/CHUNK_METRES))
            if 0 <= ix < nx and 0 <= iy < ny:
                road_by_chunk[(ix,iy)].append((a,b,road_class,4 if road_class==0 else 2,36 if road_class==0 else 22))

    chunks=[]
    municipality_counts=defaultdict(int)
    biome_counts=defaultdict(int)
    land_chunks=coast_chunks=water_chunks=0
    for iy in range(ny):
        for ix in range(nx):
            cell_x=grid_origin_e+ix*CHUNK_METRES
            cell_y=grid_origin_n+iy*CHUNK_METRES
            # Most chunks are wholly land or water. Only rasterize 256 mask
            # samples when an ADM0 boundary segment actually crosses the cell.
            boundary_crosses=any(not (maxx < cell_x or minx > cell_x+CHUNK_METRES or maxy < cell_y or miny > cell_y+CHUNK_METRES)
                                 for _a,_b,minx,miny,maxx,maxy in boundary_segments)
            if not boundary_crosses:
                inside=point_in_polygons(cell_x+CHUNK_METRES/2,cell_y+CHUNK_METRES/2,adm0_polygons)
                rows=[(1 << MASK_DIM)-1 if inside else 0] * MASK_DIM
            else:
                rows=[]
                for sy in range(MASK_DIM):
                    row=0
                    for sx in range(MASK_DIM):
                        x=cell_x+(sx+.5)*CHUNK_METRES/MASK_DIM
                        y=cell_y+(sy+.5)*CHUNK_METRES/MASK_DIM
                        if point_in_polygons(x,y,adm0_polygons): row |= 1 << sx
                    rows.append(row)
            samples=sum(r.bit_count() for r in rows)
            land=samples>0
            full=samples==MASK_DIM*MASK_DIM
            cx=cell_x+CHUNK_METRES/2; cy=cell_y+CHUNK_METRES/2
            mid=nearest_municipality(cx,cy,municipalities)
            biome=classify_biome(cx,cy,land,hub_xy,adm0_polygons)
            flags=(1 if land else 0)|(2 if land and not full else 0)|(4 if not land else 0)
            seed=binascii.crc32(struct.pack("<ii",ix,iy)) & 0xffffffff
            terrain=make_terrain(seed,biome)
            buildings=make_buildings(cell_x,cell_y,rows,biome,hub_xy,seed)
            roads=road_by_chunk.get((ix,iy),[])
            raw=bytearray()
            raw.extend(CHUNK_MAGIC)
            raw.extend(struct.pack("<iiIBBBB",ix,iy,seed,mid,biome,flags,0))
            raw.extend(struct.pack("<16H",*rows))
            raw.extend(struct.pack("<81h",*terrain))
            raw.extend(struct.pack("<H",len(roads)))
            for a,b,road_class,lanes,width_dm in roads:
                values=(int(round((a[0]-cell_x)*10)),int(round((a[1]-cell_y)*10)),int(round((b[0]-cell_x)*10)),int(round((b[1]-cell_y)*10)))
                raw.extend(struct.pack("<4iBBH",*values,road_class,lanes,width_dm))
            raw.extend(struct.pack("<H",len(buildings)))
            for x,y,w,d,h,yaw,arch,bflags in buildings:
                raw.extend(struct.pack("<5H HBB",int(x*10),int(y*10),int(w*10),int(d*10),min(65535,int(h*10)),int(yaw/math.pi*65535),arch,bflags))
            compressed=zlib.compress(bytes(raw),9)
            crc=binascii.crc32(raw)&0xffffffff
            chunks.append({"ix":ix,"iy":iy,"mid":mid,"biome":biome,"flags":flags,"raw":bytes(raw),"data":compressed,"crc":crc,"roads":len(roads),"buildings":len(buildings)})
            municipality_counts[mid]+=1 if land else 0
            biome_counts[biome]+=1
            land_chunks += int(full); coast_chunks += int(land and not full); water_chunks += int(not land)

    metadata=bytearray()
    write_string(metadata,WORLD_EPOCH)
    source_digest=hashlib.sha256((sha256(ADM0_PATH)+sha256(ADM1_PATH)+sha256(NE_PATH)).encode()).digest()
    metadata.extend(source_digest)
    metadata.extend(struct.pack("<H",len(MUNICIPALITY_CANONICAL)))
    for iso,en,ar in MUNICIPALITY_CANONICAL:
        write_string(metadata,iso); write_string(metadata,en); write_string(metadata,ar)
    metadata.extend(struct.pack("<H",len(BIOMES)))
    for biome in BIOMES: write_string(metadata,biome)
    metadata.extend(struct.pack("<H",len(HUBS)))
    for (en,ar,lat,lon,kind),(e,n) in zip(HUBS,hub_xy):
        write_string(metadata,en); write_string(metadata,ar); write_string(metadata,kind)
        metadata.extend(struct.pack("<dddd",lat,lon,e-grid_origin_e,n-grid_origin_n))
    metadata.extend(struct.pack("<H",len(adm0_polygons)))
    for polygon in adm0_polygons:
        outer=polygon[0]
        metadata.extend(struct.pack("<I",len(outer)))
        for e,n in outer: metadata.extend(struct.pack("<ff",e-grid_origin_e,n-grid_origin_n))

    fixed_header=struct.pack("<4sIIIIIddddII",MAGIC,VERSION,EPSG,CHUNK_METRES,MASK_DIM,len(chunks),grid_origin_e,grid_origin_n,float(nx*CHUNK_METRES),float(ny*CHUNK_METRES),len(metadata),0)
    index_size=len(chunks)*32
    first_offset=len(fixed_header)+len(metadata)+index_size
    offset=first_offset
    index=bytearray()
    for c in chunks:
        index.extend(struct.pack("<iiqiiIBBBB",c["ix"],c["iy"],offset,len(c["data"]),len(c["raw"]),c["crc"],c["mid"],c["biome"],c["flags"],0))
        offset += len(c["data"])
    fixed_header=struct.pack("<4sIIIIIddddII",MAGIC,VERSION,EPSG,CHUNK_METRES,MASK_DIM,len(chunks),grid_origin_e,grid_origin_n,float(nx*CHUNK_METRES),float(ny*CHUNK_METRES),len(metadata),len(index))
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open("wb") as f:
        f.write(fixed_header); f.write(metadata); f.write(index)
        for c in chunks: f.write(c["data"])

    report={
        "format":"QTPK","version":VERSION,"worldEpoch":WORLD_EPOCH,"epsg":EPSG,
        "chunkMetres":CHUNK_METRES,"maskDimension":MASK_DIM,"grid":{"columns":nx,"rows":ny,"chunks":len(chunks)},
        "coverage":{"fullLandChunks":land_chunks,"coastChunks":coast_chunks,"waterBufferChunks":water_chunks,"adm0PolygonCount":len(adm0_polygons)},
        "municipalities":[{"id":i,"iso":iso,"name":en,"arabic":ar,"landChunks":municipality_counts[i]} for i,(iso,en,ar) in enumerate(MUNICIPALITY_CANONICAL)],
        "biomes":[{"id":i,"name":name,"chunks":biome_counts[i]} for i,name in enumerate(BIOMES)],
        "hubs":len(HUBS),"coarseRoadSegments":sum(c["roads"] for c in chunks),"generatedMassingBuildings":sum(c["buildings"] for c in chunks),
        "qpakBytes":output.stat().st_size,"qpakSha256":sha256(output),
        "sources":{"adm0":{"path":str(ADM0_PATH.relative_to(ROOT)),"sha256":sha256(ADM0_PATH)},"adm1":{"path":str(ADM1_PATH.relative_to(ROOT)),"sha256":sha256(ADM1_PATH)},"crossCheck":{"path":str(NE_PATH.relative_to(ROOT)),"sha256":sha256(NE_PATH)}},
        "limitations":["Milestone 1 uses geoBoundaries geometry and deterministic coarse road/settlement derivatives.","Complete OpenStreetMap roads and footprints plus DEM are scheduled for subsequent compiler milestones."]
    }
    if any(m["landChunks"]<=0 for m in report["municipalities"]):
        raise ValueError("A municipality has no represented land chunk")
    report_path.parent.mkdir(parents=True,exist_ok=True)
    report_path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    manifest_path.parent.mkdir(parents=True,exist_ok=True)
    manifest={k:report[k] for k in ("format","version","worldEpoch","epsg","chunkMetres","maskDimension","grid","coverage","municipalities","biomes","hubs","coarseRoadSegments","generatedMassingBuildings","qpakBytes","qpakSha256","limitations")}
    manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    return report


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--verify",action="store_true",help="recompile to temporary files and compare byte-for-byte")
    args=parser.parse_args()
    if args.verify:
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            td=Path(td)
            report=compile_world(td/OUTPUT_PATH.name,td/REPORT_PATH.name,td/MANIFEST_PATH.name)
            checks=[(td/OUTPUT_PATH.name,OUTPUT_PATH),(td/REPORT_PATH.name,REPORT_PATH),(td/MANIFEST_PATH.name,MANIFEST_PATH)]
            bad=[str(dst) for src,dst in checks if not dst.exists() or src.read_bytes()!=dst.read_bytes()]
            if bad:
                print("Generated Qatar world differs:",*bad,sep="\n  ",file=sys.stderr); return 2
            print(f"VERIFIED {report['grid']['chunks']} chunks {report['qpakBytes']} bytes {report['qpakSha256']}")
            return 0
    report=compile_world(OUTPUT_PATH,REPORT_PATH,MANIFEST_PATH)
    lines=[]
    for p in (ADM0_PATH,ADM1_PATH,NE_PATH,OUTPUT_PATH,MANIFEST_PATH,REPORT_PATH):
        lines.append(f"{sha256(p)}  {p.relative_to(ROOT)}")
    CHECKSUM_PATH.parent.mkdir(parents=True,exist_ok=True)
    CHECKSUM_PATH.write_text("\n".join(lines)+"\n")
    print(json.dumps(report,ensure_ascii=False,indent=2))
    return 0

if __name__=="__main__":
    raise SystemExit(main())
