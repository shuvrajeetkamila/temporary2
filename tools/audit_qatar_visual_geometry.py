#!/usr/bin/env python3
"""Audit v0.8 procedural road bounds against every packaged QTOD cell."""
from __future__ import annotations

import argparse
import json
import math
import re
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
QOD = ROOT / "app/src/main/assets/world/qatar-osm-current.qod"
STYLE = ROOT / "app/src/main/java/ai/arena/qatar/render/QatarVisualStyle.java"
HEADER = struct.Struct("<4sIIIII4dII")
INDEX = struct.Struct("<iiqiiIHH")
ROAD = struct.Struct("<qiiiiBBBBH")


def constant(source: str, name: str) -> float:
    match = re.search(rf"public static final (?:int|float) {name}\s*=\s*([0-9.]+)f?;", source)
    if not match:
        raise ValueError(f"missing renderer bound {name}")
    return float(match.group(1))


def pieces(length: float, step: float, maximum: int) -> int:
    return min(maximum, max(1, math.ceil(length / step)))


def audit() -> dict[str, object]:
    style = STYLE.read_text()
    joint_cap = int(constant(style, "MAX_ROAD_JOINTS_PER_CELL"))
    joint_divisions = int(constant(style, "ROAD_JOINT_DIVISIONS"))
    marking_cap = int(constant(style, "MAX_MARKING_STRIPS_PER_CELL"))
    subdivision_cap = int(constant(style, "MAX_ROAD_SUBDIVISIONS"))
    terrain_step = constant(style, "ROAD_TERRAIN_STEP_METRES")

    payload = QOD.read_bytes()
    if len(payload) < HEADER.size:
        raise ValueError("QTOD header missing")
    magic, version, _epsg, _cell, _mask, cell_count, _oe, _on, _width, _height, metadata_bytes, index_bytes = HEADER.unpack_from(payload)
    if magic != b"QTOD" or version != 1 or index_bytes != cell_count * INDEX.size:
        raise ValueError("unexpected QTOD contract")

    index_offset = HEADER.size + metadata_bytes
    maximum_roads = maximum_buildings = 0
    max_road_cell: dict[str, int] = {}
    max_building_cell: dict[str, int] = {}
    max_material_vertices = 0
    max_material_cell: dict[str, int] = {}
    indexed_roads = indexed_buildings = 0

    for cell in range(cell_count):
        x, y, offset, packed_bytes, raw_bytes, _crc, road_count, building_count = INDEX.unpack_from(payload, index_offset + cell * INDEX.size)
        indexed_roads += road_count
        indexed_buildings += building_count
        if road_count > maximum_roads:
            maximum_roads, max_road_cell = road_count, {"x": x, "y": y}
        if building_count > maximum_buildings:
            maximum_buildings, max_building_cell = building_count, {"x": x, "y": y}

        raw = zlib.decompress(payload[offset : offset + packed_bytes])
        if len(raw) != raw_bytes or raw[:4] != b"QDET":
            raise ValueError(f"invalid QTOD cell {x},{y}")
        block_x, block_y, block_roads = struct.unpack_from("<iii", raw, 4)
        if (block_x, block_y, block_roads) != (x, y, road_count):
            raise ValueError(f"QTOD index/block mismatch {x},{y}")

        by_class = [0] * 12
        position = 16
        for _ in range(road_count):
            _source, x1, z1, x2, z2, road_class, _lanes, _surface, _flags, _width_dm = ROAD.unpack_from(raw, position)
            position += ROAD.size
            if not 0 <= road_class < len(by_class):
                raise ValueError(f"unsupported road class {road_class}")
            length = math.hypot(x2 - x1, z2 - z1) / 10.0
            by_class[road_class] += pieces(length, terrain_step, subdivision_cap)

        # terrainTriangle emits three vertices; a strip quad emits two triangles.
        cap_vertices = joint_cap * joint_divisions * 3
        material_candidates = {
            "paved": sum(by_class[:7]) * 6 + cap_vertices,
            "shoulders": sum(by_class[:5]) * 6 + cap_vertices,
            "paths": (sum(by_class[7:9]) + by_class[9]) * 6,
            "rails": by_class[9] * 12,
            "runways": by_class[10] * 6,
            "taxiways": by_class[11] * 6,
        }
        name, material_vertices = max(material_candidates.items(), key=lambda item: item[1])
        if material_vertices > max_material_vertices:
            max_material_vertices = material_vertices
            max_material_cell = {"x": x, "y": y, "material": name}

    marking_vertex_cap = marking_cap * 6
    # Keep an individual generated material stream below libGDX's conservative 16K split threshold.
    conservative_vertex_limit = 16_384
    if max_material_vertices >= conservative_vertex_limit:
        raise ValueError(f"road material bound {max_material_vertices} exceeds {conservative_vertex_limit}")
    if marking_vertex_cap >= conservative_vertex_limit:
        raise ValueError(f"marking material bound {marking_vertex_cap} exceeds {conservative_vertex_limit}")

    return {
        "format": "QatarVisualGeometryAudit",
        "qodCells": cell_count,
        "indexedRoadSegments": indexed_roads,
        "indexedBuildings": indexed_buildings,
        "maximumRoadSegmentsInCell": maximum_roads,
        "maximumRoadCell": max_road_cell,
        "maximumBuildingsInCell": maximum_buildings,
        "maximumBuildingCell": max_building_cell,
        "roadJointCapPerCell": joint_cap,
        "roadJointDivisions": joint_divisions,
        "roadSubdivisionCap": subdivision_cap,
        "terrainFollowingStepMetres": terrain_step,
        "markingStripCapPerCell": marking_cap,
        "maximumEstimatedRoadMaterialVertices": max_material_vertices,
        "maximumRoadMaterialCell": max_material_cell,
        "maximumMarkingMaterialVertices": marking_vertex_cap,
        "conservativePerMaterialVertexLimit": conservative_vertex_limit,
        "status": "PASSED",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    result = audit()
    text = json.dumps(result, indent=2) + "\n"
    print(text, end="")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
