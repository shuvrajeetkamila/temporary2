#!/usr/bin/env python3
"""Convert the licensed Nauru OSM extract into a compact Android game-map binary.

Coordinates are projected to local metres with +X east and +Z north. The binary keeps
real coastline, roads, airport surfaces, building footprints, land cover and labels while
avoiding a large runtime XML parser on Android.
"""

from __future__ import annotations

import argparse
import gzip
import math
import struct
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

ORIGIN_LAT = -0.5252306
ORIGIN_LON = 166.9324426
METRES_PER_DEGREE = 111_319.49079327358
LON_SCALE = METRES_PER_DEGREE * math.cos(math.radians(ORIGIN_LAT))
LAT_SCALE = METRES_PER_DEGREE


@dataclass
class Way:
    ident: int
    refs: list[int]
    tags: dict[str, str]


def project(latitude: float, longitude: float) -> tuple[float, float]:
    return ((longitude - ORIGIN_LON) * LON_SCALE, (latitude - ORIGIN_LAT) * LAT_SCALE)


def tags(element: ET.Element) -> dict[str, str]:
    return {tag.attrib["k"]: tag.attrib["v"] for tag in element.findall("tag")}


def perpendicular_distance(point, start, end) -> float:
    x, z = point
    x1, z1 = start
    x2, z2 = end
    dx, dz = x2 - x1, z2 - z1
    if dx * dx + dz * dz < 1e-9:
        return math.hypot(x - x1, z - z1)
    t = max(0.0, min(1.0, ((x - x1) * dx + (z - z1) * dz) / (dx * dx + dz * dz)))
    return math.hypot(x - (x1 + t * dx), z - (z1 + t * dz))


def simplify(points: list[tuple[float, float]], tolerance: float) -> list[tuple[float, float]]:
    if len(points) <= 2:
        return points
    closed = points[0] == points[-1]
    work = points[:-1] if closed else points
    if len(work) <= 3:
        return work

    # Iterative Douglas-Peucker avoids Python recursion limits on detailed coastlines.
    keep = {0, len(work) - 1}
    stack = [(0, len(work) - 1)]
    while stack:
        first, last = stack.pop()
        best_distance, best_index = 0.0, -1
        for index in range(first + 1, last):
            distance = perpendicular_distance(work[index], work[first], work[last])
            if distance > best_distance:
                best_distance, best_index = distance, index
        if best_distance > tolerance and best_index >= 0:
            keep.add(best_index)
            stack.append((first, best_index))
            stack.append((best_index, last))
    result = [work[index] for index in sorted(keep)]
    return result if len(result) >= 3 or not closed else work


def assemble_rings(member_ways: Iterable[Way]) -> list[list[int]]:
    remaining = [way.refs[:] for way in member_ways if len(way.refs) >= 2]
    rings: list[list[int]] = []
    while remaining:
        ring = remaining.pop(0)
        changed = True
        while changed and ring[0] != ring[-1]:
            changed = False
            for index, candidate in enumerate(remaining):
                if ring[-1] == candidate[0]:
                    ring.extend(candidate[1:])
                elif ring[-1] == candidate[-1]:
                    ring.extend(reversed(candidate[:-1]))
                elif ring[0] == candidate[-1]:
                    ring = candidate[:-1] + ring
                elif ring[0] == candidate[0]:
                    ring = list(reversed(candidate[1:])) + ring
                else:
                    continue
                remaining.pop(index)
                changed = True
                break
        if len(ring) >= 4:
            rings.append(ring)
    return rings


def polygon_points(refs: list[int], nodes: dict[int, tuple[float, float]]) -> list[tuple[float, float]]:
    result = [project(*nodes[ref]) for ref in refs if ref in nodes]
    if len(result) > 1 and result[0] == result[-1]:
        result.pop()
    return result


def road_definition(tag: dict[str, str]) -> tuple[int, float] | None:
    aeroway = tag.get("aeroway")
    if aeroway == "runway":
        return 4, parse_number(tag.get("width"), 45.0)
    if aeroway == "taxiway":
        return 5, parse_number(tag.get("width"), 11.0)
    if tag.get("man_made") in {"pier", "groyne"}:
        return 6, parse_number(tag.get("width"), 4.0)
    highway = tag.get("highway")
    widths = {
        "primary": 9.5, "primary_link": 8.0,
        "secondary": 8.0, "secondary_link": 7.0,
        "tertiary": 7.0, "tertiary_link": 6.5,
        "residential": 5.7, "unclassified": 5.5, "living_street": 5.0,
        "service": 4.3, "track": 3.2, "path": 1.5, "footway": 1.4,
    }
    if highway not in widths:
        return None
    surface = tag.get("surface", "").lower()
    unpaved = surface in {"unpaved", "gravel", "ground", "dirt", "sand", "fine_gravel", "compacted"}
    if highway in {"path", "footway"}:
        material = 3
    elif highway == "track" or unpaved:
        material = 2
    elif highway in {"primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link"}:
        material = 0
    else:
        material = 1
    return material, parse_number(tag.get("width"), widths[highway])


def parse_number(value: str | None, default: float) -> float:
    if not value:
        return default
    try:
        return float(value.lower().replace("metres", "").replace("meters", "").replace("m", "").strip())
    except ValueError:
        return default


def building_definition(tag: dict[str, str]) -> tuple[int, float]:
    kind = tag.get("building", "yes")
    if tag.get("man_made") == "storage_tank":
        category, default_height = 3, 5.5
    elif kind in {"industrial", "hangar", "warehouse", "container"}:
        category, default_height = 1, 7.5
    elif kind in {"church", "school", "university", "retail", "commercial", "hotel", "transportation", "fire_station"}:
        category, default_height = 2, 8.0
    elif kind in {"cabin", "hut", "carport", "roof"}:
        category, default_height = 0, 3.2
    else:
        category, default_height = 0, 5.6
    levels = parse_number(tag.get("building:levels"), 0.0)
    height = parse_number(tag.get("height"), default_height)
    if levels > 0:
        height = max(height, levels * 3.05 + 0.5)
    return category, max(2.5, min(35.0, height))


def area_definition(tag: dict[str, str]) -> int | None:
    if tag.get("aeroway") == "apron":
        return 6
    if tag.get("power") == "generator" and tag.get("generator:source") == "solar":
        return 7
    natural = tag.get("natural")
    landuse = tag.get("landuse")
    leisure = tag.get("leisure")
    if natural == "water" or tag.get("water"):
        return 0
    if natural == "wood" or landuse == "forest":
        return 1
    if natural in {"scrub", "grassland"} or landuse in {"meadow", "grass"} or leisure in {"park", "garden", "pitch"}:
        return 2
    if natural in {"bare_rock", "rock", "fell"} or landuse in {"quarry", "landfill", "brownfield", "construction"}:
        return 3
    if natural in {"beach", "sand"}:
        return 4
    if landuse in {"residential", "industrial", "commercial", "cemetery", "village_green"}:
        return 5
    return None


def write_points(output, points: list[tuple[float, float]]) -> None:
    if len(points) > 65535:
        raise ValueError("feature has too many points")
    output.write(struct.pack(">H", len(points)))
    for x, z in points:
        output.write(struct.pack(">ff", x, z))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("osm", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    opener = gzip.open if args.osm.suffix == ".gz" else open
    with opener(args.osm, "rb") as source:
        root = ET.parse(source).getroot()

    nodes = {
        int(node.attrib["id"]): (float(node.attrib["lat"]), float(node.attrib["lon"]))
        for node in root.findall("node")
    }
    node_tags = {int(node.attrib["id"]): tags(node) for node in root.findall("node")}
    ways = {
        int(way.attrib["id"]): Way(
            int(way.attrib["id"]),
            [int(nd.attrib["ref"]) for nd in way.findall("nd")],
            tags(way),
        )
        for way in root.findall("way")
    }

    coastline_ways = [way for way in ways.values() if way.tags.get("natural") == "coastline"]
    coast_rings = assemble_rings(coastline_ways)
    if not coast_rings:
        raise SystemExit("No Nauru coastline ring could be assembled")
    coast_refs = max(coast_rings, key=len)
    coast = simplify(polygon_points(coast_refs, nodes), 1.25)

    roads: list[tuple[int, float, list[tuple[float, float]]]] = []
    buildings: list[tuple[int, float, list[tuple[float, float]]]] = []
    areas: list[tuple[int, list[tuple[float, float]]]] = []

    for way in ways.values():
        points = polygon_points(way.refs, nodes)
        road = road_definition(way.tags)
        if road and len(points) >= 2:
            material, width = road
            roads.append((material, width, simplify(points, 0.55 if material < 2 else 1.0)))
        is_structure = "building" in way.tags or way.tags.get("man_made") == "storage_tank"
        if is_structure and len(points) >= 3 and way.refs[0] == way.refs[-1]:
            category, height = building_definition(way.tags)
            footprint = simplify(points, 0.12)
            if len(footprint) >= 3:
                buildings.append((category, height, footprint))
        area = area_definition(way.tags)
        if area is not None and len(points) >= 3 and way.refs[0] == way.refs[-1]:
            polygon = simplify(points, 0.8)
            if len(polygon) >= 3:
                areas.append((area, polygon))

    # Point-mapped utility poles, beacons and towers become slender real-position structures.
    for ident, (latitude, longitude) in nodes.items():
        tag = node_tags[ident]
        man_made = tag.get("man_made")
        if tag.get("power") != "pole" and man_made not in {"flagpole", "beacon", "tower"}:
            continue
        x, z = project(latitude, longitude)
        radius = .20 if tag.get("power") == "pole" else .28
        height = parse_number(tag.get("height"), 8.5 if tag.get("power") == "pole" else 12.0)
        footprint = [
            (x + math.cos(index * math.pi / 3) * radius,
             z + math.sin(index * math.pi / 3) * radius)
            for index in range(6)
        ]
        buildings.append((4, max(4.0, min(35.0, height)), footprint))

    # Multipolygon water/land-cover relations, notably the real Buada Lagoon outline.
    for relation in root.findall("relation"):
        relation_tags = tags(relation)
        area = area_definition(relation_tags)
        if area is None or relation_tags.get("type") != "multipolygon":
            continue
        outer_ways = []
        for member in relation.findall("member"):
            if member.attrib.get("type") == "way" and member.attrib.get("role", "outer") == "outer":
                way = ways.get(int(member.attrib["ref"]))
                if way:
                    outer_ways.append(way)
        for ring in assemble_rings(outer_ways):
            polygon = simplify(polygon_points(ring, nodes), 0.8)
            if len(polygon) >= 3:
                areas.append((area, polygon))

    places: list[tuple[int, float, float, str]] = []
    seen_names: set[tuple[int, str]] = set()
    # District relation labels are more useful in-game than hundreds of tiny locality names.
    for relation in root.findall("relation"):
        relation_tags = tags(relation)
        if relation_tags.get("admin_level") != "6" or not relation_tags.get("name"):
            continue
        points = []
        for member in relation.findall("member"):
            if member.attrib.get("type") == "way":
                way = ways.get(int(member.attrib["ref"]))
                if way:
                    points.extend(polygon_points(way.refs, nodes))
        if points:
            x = sum(point[0] for point in points) / len(points)
            z = sum(point[1] for point in points) / len(points)
            name = relation_tags.get("name:en", relation_tags["name"]).replace(" District", "")
            key = (0, name)
            if key not in seen_names:
                places.append((0, x, z, name))
                seen_names.add(key)

    for ident, (latitude, longitude) in nodes.items():
        tag = node_tags[ident]
        name = tag.get("name:en", tag.get("name"))
        if not name:
            continue
        place = tag.get("place")
        important = tag.get("tourism") or tag.get("amenity") or tag.get("aeroway") or tag.get("historic")
        if place not in {"village", "hamlet"} and not important:
            continue
        category = 1 if place else 2
        key = (category, name)
        if key in seen_names:
            continue
        x, z = project(latitude, longitude)
        places.append((category, x, z, name))
        seen_names.add(key)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("wb") as output:
        output.write(struct.pack(
            ">4sIddIIIII",
            b"NMAP", 1, ORIGIN_LAT, ORIGIN_LON,
            len(coast), len(roads), len(buildings), len(areas), len(places),
        ))
        for x, z in coast:
            output.write(struct.pack(">ff", x, z))
        for material, width, points in roads:
            output.write(struct.pack(">Bf", material, width))
            write_points(output, points)
        for category, height, points in buildings:
            output.write(struct.pack(">Bf", category, height))
            write_points(output, points)
        for category, points in areas:
            output.write(struct.pack(">B", category))
            write_points(output, points)
        for category, x, z, name in places:
            encoded = name.encode("utf-8")[:65535]
            output.write(struct.pack(">BffH", category, x, z, len(encoded)))
            output.write(encoded)

    min_x = min(point[0] for point in coast)
    max_x = max(point[0] for point in coast)
    min_z = min(point[1] for point in coast)
    max_z = max(point[1] for point in coast)
    print(
        f"Wrote {args.output} ({args.output.stat().st_size:,} bytes): "
        f"coast={len(coast)}, roads={len(roads)}, buildings={len(buildings)}, "
        f"areas={len(areas)}, places={len(places)}, "
        f"bounds=({min_x:.0f},{min_z:.0f})..({max_x:.0f},{max_z:.0f}) m"
    )


if __name__ == "__main__":
    main()
