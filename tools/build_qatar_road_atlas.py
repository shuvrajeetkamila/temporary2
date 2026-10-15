#!/usr/bin/env python3
"""Build the deterministic offline national street-atlas overlay from packaged QTOD vectors."""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import tempfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/world/qatar-osm-current.qod"
OUTPUT = ROOT / "app/src/main/assets/ui/qatar-road-atlas.png"
REPORT = ROOT / "data/qatar/compiled/qatar-road-atlas-report.json"
WIDTH = 550
HEIGHT = 1030
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def chunk(kind: bytes, payload: bytes) -> bytes:
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)


def blend(pixels: bytearray, x: int, y: int, colour: tuple[int, int, int, int]) -> None:
    if x < 0 or y < 0 or x >= WIDTH or y >= HEIGHT:
        return
    i = (y * WIDTH + x) * 4
    r, g, b, a = colour
    inverse = 255 - a
    pixels[i] = (r * a + pixels[i] * inverse) // 255
    pixels[i + 1] = (g * a + pixels[i + 1] * inverse) // 255
    pixels[i + 2] = (b * a + pixels[i + 2] * inverse) // 255
    pixels[i + 3] = min(255, a + pixels[i + 3] * inverse // 255)


def line(pixels: bytearray, x0: int, y0: int, x1: int, y1: int, colour: tuple[int, int, int, int], width: int) -> None:
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    error = dx + dy
    radius = max(0, width - 1)
    while True:
        for oy in range(-radius, radius + 1):
            for ox in range(-radius, radius + 1):
                if ox * ox + oy * oy <= radius * radius + 1:
                    blend(pixels, x0 + ox, y0 + oy, colour)
        if x0 == x1 and y0 == y1:
            return
        twice = 2 * error
        if twice >= dy:
            error += dy
            x0 += sx
        if twice <= dx:
            error += dx
            y0 += sy


def parse_header(data: bytes) -> tuple[int, float, float, int, int]:
    magic, version, epsg, cell, mask, count, _oe, _on, world_width, world_height, metadata, index_bytes = struct.unpack_from(
        "<4sIIIIIddddII", data, 0
    )
    if magic != b"QTOD" or version != 1 or epsg != 32639 or mask != 16 or index_bytes != count * 32:
        raise ValueError("unsupported QTOD contract")
    return count, world_width, world_height, metadata, cell


def entries(data: bytes, count: int, metadata: int):
    start = 64 + metadata
    for index in range(count):
        yield struct.unpack_from("<iiqiiIHH", data, start + index * 32)


def render(source: Path, output: Path) -> dict[str, object]:
    data = source.read_bytes()
    count, world_width, world_height, metadata, cell_metres = parse_header(data)
    pixels = bytearray(WIDTH * HEIGHT * 4)
    class_counts = [0] * 12
    styles = {
        8: ((150, 133, 105, 90), 1), 7: ((156, 139, 107, 100), 1), 9: ((71, 75, 76, 150), 1),
        6: ((238, 235, 221, 155), 1), 5: ((248, 245, 229, 180), 1), 11: ((142, 145, 142, 130), 1),
        4: ((247, 207, 112, 210), 1), 3: ((244, 185, 82, 225), 1), 2: ((239, 158, 67, 235), 2),
        1: ((227, 128, 58, 245), 2), 0: ((211, 91, 54, 250), 2), 10: ((96, 101, 102, 190), 2),
    }
    layers = ({7, 8, 9}, {5, 6, 11}, {0, 1, 2, 3, 4, 10})
    for layer_number, accepted in enumerate(layers):
        for cell_x, cell_y, offset, packed, raw, crc, road_count, _buildings in entries(data, count, metadata):
            if not road_count:
                continue
            decoded = zlib.decompress(data[offset : offset + packed])
            if len(decoded) != raw or zlib.crc32(decoded) & 0xFFFFFFFF != crc:
                raise ValueError(f"QTOD block validation failed at {cell_x},{cell_y}")
            position = 16
            for _ in range(road_count):
                _source_id, x1, z1, x2, z2, road_class, _lanes, _surface, _flags, _width = struct.unpack_from(
                    "<qiiiiBBBBH", decoded, position
                )
                position += 30
                if layer_number == 0 and road_class < len(class_counts):
                    class_counts[road_class] += 1
                if road_class not in accepted:
                    continue
                global_x1 = cell_x * cell_metres + x1 / 10.0
                global_z1 = cell_y * cell_metres + z1 / 10.0
                global_x2 = cell_x * cell_metres + x2 / 10.0
                global_z2 = cell_y * cell_metres + z2 / 10.0
                px1 = round(global_x1 / world_width * (WIDTH - 1))
                py1 = HEIGHT - 1 - round(global_z1 / world_height * (HEIGHT - 1))
                px2 = round(global_x2 / world_width * (WIDTH - 1))
                py2 = HEIGHT - 1 - round(global_z2 / world_height * (HEIGHT - 1))
                colour, stroke = styles.get(road_class, ((225, 220, 205, 120), 1))
                line(pixels, px1, py1, px2, py2, colour, stroke)
    raw_rows = b"".join(b"\x00" + pixels[y * WIDTH * 4 : (y + 1) * WIDTH * 4] for y in range(HEIGHT))
    png = PNG_SIGNATURE + chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw_rows, 9)) + chunk(b"IEND", b"")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(png)
    return {
        "format": "Qatar offline OSM road atlas PNG",
        "width": WIDTH,
        "height": HEIGHT,
        "epsg": 32639,
        "sourceQodSha256": hashlib.sha256(data).hexdigest(),
        "atlasSha256": hashlib.sha256(png).hexdigest(),
        "atlasBytes": len(png),
        "roadSegments": sum(class_counts),
        "roadClassCounts": {str(index): value for index, value in enumerate(class_counts)},
        "license": "Open Database License 1.0; © OpenStreetMap contributors",
        "use": "offline game UI street overview; not navigation",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    if args.verify:
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory) / "atlas.png"
            report = render(SOURCE, temporary)
            if not OUTPUT.exists() or temporary.read_bytes() != OUTPUT.read_bytes():
                raise SystemExit("qatar-road-atlas.png is stale; run tools/build_qatar_road_atlas.py")
            if not REPORT.exists() or json.loads(REPORT.read_text()) != report:
                raise SystemExit("qatar-road-atlas-report.json is stale")
            print(f"VERIFIED {report['roadSegments']} OSM road segments -> {report['atlasBytes']} bytes {report['atlasSha256']}")
            return 0
    report = render(SOURCE, OUTPUT)
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
