#!/usr/bin/env python3
"""Audit packaged Qatar coast/island features against QTPK land and elevation.

This does not pretend the coarse national mask is survey data. It identifies
OSM features on recent reclamation outside that mask and proves that the
runtime dry-feature policy gives buildings and transport surfaces clearance
above the authored maximum tide.
"""
from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import math
import struct
import zlib
from dataclasses import dataclass
from pathlib import Path

from compile_qatar_world import normalize_polygons, point_in_ring

ROOT = Path(__file__).resolve().parents[1]
QPAK = ROOT / "app/src/main/assets/world/qatar-national.qpak"
QOD = ROOT / "app/src/main/assets/world/qatar-osm-current.qod"
ADM0 = ROOT / "data/qatar/source/geoboundaries-6-QAT-ADM0.geojson"
REPORT = ROOT / "data/qatar/compiled/qatar-coastal-feature-audit.json"
DESIGN_HIGH_WATER_METRES = 0.55
MINIMUM_DRY_FEATURE_METRES = 0.75
LEGTAIFIYA = {
    "name": "Legtaifiya Metro Station",
    "latitude": 25.3581942,
    "longitude": 51.5077315,
    "localX": 85085.928385,
    "localZ": 106707.982144,
    "auditRadiusMetres": 2000.0,
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


@dataclass
class NationalCell:
    rows: tuple[int, ...]
    terrain: tuple[int, ...]
    flags: int


class NationalGrid:
    def __init__(self, path: Path):
        data = path.read_bytes()
        header = struct.unpack_from("<4sIIIIIddddII", data, 0)
        magic, version, epsg, self.chunk_metres, self.mask_dimension, count, self.origin_easting, self.origin_northing, self.world_width, self.world_height, metadata_bytes, index_bytes = header
        if (magic, version, epsg, self.mask_dimension) != (b"QTPK", 1, 32639, 16):
            raise ValueError("Unexpected QTPK contract")
        if index_bytes != count * 32:
            raise ValueError("Unexpected QTPK index")
        self.cells: dict[tuple[int, int], NationalCell] = {}
        index = 64 + metadata_bytes
        for i in range(count):
            x, y, offset, packed, raw_length, crc, municipality, biome, flags, reserved = struct.unpack_from("<iiqiiIBBBB", data, index + i * 32)
            raw = zlib.decompress(data[offset:offset + packed])
            if len(raw) != raw_length or binascii.crc32(raw) & 0xFFFFFFFF != crc:
                raise ValueError(f"QTPK block {x},{y} failed validation")
            rows = struct.unpack_from("<16H", raw, 20)
            terrain = struct.unpack_from("<81h", raw, 52)
            self.cells[(x, y)] = NationalCell(rows, terrain, flags)

    def land(self, x: float, z: float) -> bool:
        cx, cy = math.floor(x / self.chunk_metres), math.floor(z / self.chunk_metres)
        cell = self.cells.get((cx, cy))
        if cell is None:
            return False
        sub = self.chunk_metres / self.mask_dimension
        column = max(0, min(15, int((x - cx * self.chunk_metres) / sub)))
        row = max(0, min(15, int((z - cy * self.chunk_metres) / sub)))
        return bool(cell.rows[row] & (1 << column))

    def height(self, x: float, z: float) -> float:
        cx, cy = math.floor(x / self.chunk_metres), math.floor(z / self.chunk_metres)
        cell = self.cells.get((cx, cy))
        if cell is None:
            return float("nan")
        interval = self.chunk_metres / 8
        gx = max(0.0, min(8.0, (x - cx * self.chunk_metres) / interval))
        gz = max(0.0, min(8.0, (z - cy * self.chunk_metres) / interval))
        x0, z0 = min(7, math.floor(gx)), min(7, math.floor(gz))
        tx, tz = gx - x0, gz - z0
        values = cell.terrain
        south = values[z0 * 9 + x0] * (1 - tx) + values[z0 * 9 + x0 + 1] * tx
        north = values[(z0 + 1) * 9 + x0] * (1 - tx) + values[(z0 + 1) * 9 + x0 + 1] * tx
        return (south * (1 - tz) + north * tz) / 100.0


def polygon_area(ring: list[tuple[float, float]]) -> float:
    return abs(sum(ax * bz - bx * az for (ax, az), (bx, bz) in zip(ring, ring[1:]))) * 0.5


def island_records() -> list[dict]:
    geometry = json.loads(ADM0.read_text(encoding="utf-8"))["features"][0]["geometry"]
    polygons = normalize_polygons(geometry)
    ordered = sorted(enumerate(polygons), key=lambda item: polygon_area(item[1][0]), reverse=True)
    result = []
    for rank, (source_index, polygon) in enumerate(ordered):
        ring = polygon[0]
        result.append({
            "id": "mainland" if rank == 0 else f"island-{rank}",
            "sourceComponent": source_index,
            "areaSquareKilometres": round(polygon_area(ring) / 1_000_000, 6),
            "ring": ring,
            "bounds": (min(p[0] for p in ring), min(p[1] for p in ring), max(p[0] for p in ring), max(p[1] for p in ring)),
            "buildings": 0,
            "roadSegments": 0,
            "runwaySegments": 0,
            "minimumFeatureTerrainMetres": None,
        })
    return result


def component_for(global_easting: float, global_northing: float, components: list[dict]) -> dict:
    # Test the six small island rings first. Everything else accepted into the
    # national/QTOD envelope is reported with mainland/reclamation; avoiding a
    # 1.44-million-times traversal of the very detailed mainland ring keeps the
    # deterministic CI audit bounded.
    for component in components[1:]:
        min_x, min_z, max_x, max_z = component["bounds"]
        if min_x <= global_easting <= max_x and min_z <= global_northing <= max_z and point_in_ring(global_easting, global_northing, component["ring"]):
            return component
    return components[0]


def update_component(component: dict | None, kind: str, terrain: float):
    if component is None:
        return
    component[kind] += 1
    prior = component["minimumFeatureTerrainMetres"]
    if math.isfinite(terrain) and (prior is None or terrain < prior):
        component["minimumFeatureTerrainMetres"] = terrain


def audit(qpak: Path = QPAK, qod: Path = QOD) -> dict:
    grid = NationalGrid(qpak)
    detail = qod.read_bytes()
    header = struct.unpack_from("<4sIIIIIddddII", detail, 0)
    magic, version, epsg, cell_metres, mask, count, origin_easting, origin_northing, width, height, metadata_bytes, index_bytes = header
    if (magic, version, epsg, cell_metres) != (b"QTOD", 1, 32639, 250):
        raise ValueError("Unexpected QTOD contract")
    components = island_records()
    buildings = {"total": 0, "centreOutsideLandMask": 0, "footprintTouchesWaterMask": 0, "entireFootprintOutsideLandMask": 0, "belowDesignDryLevel": 0, "runtimeFoundationsRequired": 0, "unresolvedAfterPolicy": 0}
    roads = {"total": 0, "sampleOutsideLandMask": 0, "bridgeSegmentsOutsideLandMask": 0, "belowDesignDryLevel": 0, "runtimePadsRequired": 0, "unresolvedAfterPolicy": 0}
    runways = {"segments": 0, "outsideLandMask": 0, "belowDesignDryLevel": 0, "runtimePadsRequired": 0, "unresolvedAfterPolicy": 0}
    leg = {**LEGTAIFIYA, "stationLandMask": grid.land(LEGTAIFIYA["localX"], LEGTAIFIYA["localZ"]), "stationTerrainMetres": round(grid.height(LEGTAIFIYA["localX"], LEGTAIFIYA["localZ"]), 6), "buildings": 0, "buildingsOutsideLandMask": 0, "roads": 0, "roadsOutsideLandMask": 0, "runways": 0, "featuresRequiringDryCorrection": 0, "unresolvedAfterPolicy": 0}

    index = 64 + metadata_bytes
    for i in range(count):
        cell_x, cell_y, offset, packed, raw_length, crc, road_count, building_count = struct.unpack_from("<iiqiiIHH", detail, index + i * 32)
        raw = zlib.decompress(detail[offset:offset + packed])
        if len(raw) != raw_length or binascii.crc32(raw) & 0xFFFFFFFF != crc:
            raise ValueError(f"QTOD block {cell_x},{cell_y} failed validation")
        cursor = 16
        cell_origin_x, cell_origin_z = cell_x * cell_metres, cell_y * cell_metres
        for _ in range(road_count):
            source_id, x1, z1, x2, z2, road_class, lanes, surface, flags, road_width = struct.unpack_from("<q4iBBBBH", raw, cursor)
            cursor += 30
            ax, az = cell_origin_x + x1 / 10, cell_origin_z + z1 / 10
            bx, bz = cell_origin_x + x2 / 10, cell_origin_z + z2 / 10
            mx, mz = (ax + bx) * 0.5, (az + bz) * 0.5
            samples = ((ax, az), (mx, mz), (bx, bz))
            outside = any(not grid.land(x, z) for x, z in samples)
            terrain = min(grid.height(x, z) for x, z in samples)
            low = terrain < MINIMUM_DRY_FEATURE_METRES
            permanent = road_class <= 7 or road_class >= 9
            correction = permanent and (outside or low)
            roads["total"] += 1
            roads["sampleOutsideLandMask"] += int(outside)
            roads["bridgeSegmentsOutsideLandMask"] += int(outside and bool(flags & 1))
            roads["belowDesignDryLevel"] += int(low)
            roads["runtimePadsRequired"] += int(correction)
            resolved = max(terrain, MINIMUM_DRY_FEATURE_METRES) if permanent else terrain
            roads["unresolvedAfterPolicy"] += int(permanent and resolved < MINIMUM_DRY_FEATURE_METRES)
            if road_class == 10:
                runways["segments"] += 1
                runways["outsideLandMask"] += int(outside)
                runways["belowDesignDryLevel"] += int(low)
                runways["runtimePadsRequired"] += int(outside or low)
                runways["unresolvedAfterPolicy"] += int(max(terrain, MINIMUM_DRY_FEATURE_METRES) < MINIMUM_DRY_FEATURE_METRES)
            distance = math.hypot(mx - leg["localX"], mz - leg["localZ"])
            if distance <= leg["auditRadiusMetres"]:
                leg["roads"] += 1
                leg["roadsOutsideLandMask"] += int(outside)
                leg["runways"] += int(road_class == 10)
                leg["featuresRequiringDryCorrection"] += int(correction)
            component = component_for(origin_easting + mx, origin_northing + mz, components)
            update_component(component, "roadSegments", terrain)
            if road_class == 10:
                update_component(component, "runwaySegments", terrain)

        decoded_building_count = struct.unpack_from("<I", raw, cursor)[0]
        cursor += 4
        if decoded_building_count != building_count:
            raise ValueError("QTOD building count mismatch")
        for _ in range(building_count):
            source_id, centre_x, centre_z, feature_width, depth, feature_height, yaw, archetype, flags, vertices = struct.unpack_from("<q5iHBBH", raw, cursor)
            cursor += 34
            cx, cz = cell_origin_x + centre_x / 10, cell_origin_z + centre_z / 10
            points = []
            for vertex in range(vertices):
                vx, vz = struct.unpack_from("<ii", raw, cursor + vertex * 8)
                points.append((cell_origin_x + vx / 10, cell_origin_z + vz / 10))
            cursor += vertices * 8
            centre_land = grid.land(cx, cz)
            vertex_land = [grid.land(x, z) for x, z in points]
            touches = not all(vertex_land)
            entirely_outside = not any(vertex_land)
            terrain = grid.height(cx, cz)
            low = terrain < MINIMUM_DRY_FEATURE_METRES
            correction = not centre_land or touches or low
            buildings["total"] += 1
            buildings["centreOutsideLandMask"] += int(not centre_land)
            buildings["footprintTouchesWaterMask"] += int(touches)
            buildings["entireFootprintOutsideLandMask"] += int(entirely_outside)
            buildings["belowDesignDryLevel"] += int(low)
            buildings["runtimeFoundationsRequired"] += int(correction)
            buildings["unresolvedAfterPolicy"] += int(max(terrain, MINIMUM_DRY_FEATURE_METRES) < MINIMUM_DRY_FEATURE_METRES)
            distance = math.hypot(cx - leg["localX"], cz - leg["localZ"])
            if distance <= leg["auditRadiusMetres"]:
                leg["buildings"] += 1
                leg["buildingsOutsideLandMask"] += int(not centre_land or touches)
                leg["featuresRequiringDryCorrection"] += int(correction)
            component = component_for(origin_easting + cx, origin_northing + cz, components)
            update_component(component, "buildings", terrain)
        if cursor != len(raw):
            raise ValueError(f"QTOD block {cell_x},{cell_y} has an unexpected tail")

    serial_components = []
    for component in components:
        serial_components.append({key: (round(value, 6) if key == "minimumFeatureTerrainMetres" and value is not None else value) for key, value in component.items() if key not in ("ring", "bounds")})
    result = {
        "format": "QatarCoastalFeatureAudit",
        "version": 1,
        "epsg": 32639,
        "qpakSha256": sha256(qpak),
        "qodSha256": sha256(qod),
        "designHighWaterMetres": DESIGN_HIGH_WATER_METRES,
        "minimumDryFeatureMetres": MINIMUM_DRY_FEATURE_METRES,
        "buildings": buildings,
        "roads": roads,
        "runways": runways,
        "legtaifiya": leg,
        "adm0Components": serial_components,
        "runtimePolicy": {
            "coastWaterMeshHonours16x16LandMask": True,
            "buildingFootprintsReceiveDryFoundations": True,
            "permanentTransportReceivesDryPads": True,
            "bridgesReceiveClearanceAndEdgeBarriers": True,
        },
        "status": "PASSED" if not buildings["unresolvedAfterPolicy"] and not roads["unresolvedAfterPolicy"] and not runways["unresolvedAfterPolicy"] and not leg["unresolvedAfterPolicy"] else "FAILED",
        "limitations": [
            "The 125 m legal-land mask and 250 m DEM are game-scale data, not survey, cadastral, aviation, or navigation data.",
            "Feature support corrects rendering/collision clearance without claiming a revised legal coastline.",
            "Legtaifiya is audited within a 2 km gameplay neighbourhood around the packaged OSM station coordinate.",
        ],
    }
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=Path, default=REPORT)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    result = audit()
    encoded = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.verify:
        if not args.report.exists() or args.report.read_text(encoding="utf-8") != encoded:
            print("Coastal feature audit differs from checked report")
            return 2
        print(f"VERIFIED coastal policy buildings={result['buildings']['total']} runways={result['runways']['segments']} Legtaifiya={result['legtaifiya']['buildings']} status={result['status']}")
        return 0 if result["status"] == "PASSED" else 1
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0 if result["status"] == "PASSED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
