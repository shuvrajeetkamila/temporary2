#!/usr/bin/env python3
"""Crop the public 1-arc-second SRTM tile around Nauru into a tiny game-ready grid."""

import argparse
import gzip
import math
import struct
from pathlib import Path

TILE_SOUTH = -1.0
TILE_WEST = 166.0
TILE_SIZE = 3601
SAMPLES_PER_DEGREE = 3600


def sample(raw: bytes, latitude: float, longitude: float) -> int:
    row = int(round((TILE_SOUTH + 1.0 - latitude) * SAMPLES_PER_DEGREE))
    column = int(round((longitude - TILE_WEST) * SAMPLES_PER_DEGREE))
    row = max(0, min(TILE_SIZE - 1, row))
    column = max(0, min(TILE_SIZE - 1, column))
    value = struct.unpack_from(">h", raw, (row * TILE_SIZE + column) * 2)[0]
    return 0 if value == -32768 else value


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--west", type=float, default=166.88)
    parser.add_argument("--south", type=float, default=-0.59)
    parser.add_argument("--east", type=float, default=167.02)
    parser.add_argument("--north", type=float, default=-0.45)
    parser.add_argument("--step-arc-seconds", type=int, default=1)
    args = parser.parse_args()

    with gzip.open(args.input, "rb") as source:
        raw = source.read()
    expected = TILE_SIZE * TILE_SIZE * 2
    if len(raw) != expected:
        raise SystemExit(f"Unexpected HGT size: {len(raw)} (expected {expected})")

    step = args.step_arc_seconds / SAMPLES_PER_DEGREE
    width = int(round((args.east - args.west) / step)) + 1
    height = int(round((args.north - args.south) / step)) + 1
    heights = []
    for row in range(height):
        latitude = args.south + row * step
        for column in range(width):
            longitude = args.west + column * step
            heights.append(sample(raw, latitude, longitude))

    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Little-endian: magic, version, dimensions, bounds, then south-to-north int16 samples.
    header = struct.pack(
        "<4sIII4d", b"NDEM", 1, width, height,
        args.west, args.south, args.east, args.north,
    )
    body = struct.pack(f"<{len(heights)}h", *heights)
    args.output.write_bytes(header + body)
    land = [height for height in heights if height > 0]
    print(
        f"Wrote {args.output}: {width}x{height}, "
        f"range={min(heights)}..{max(heights)} m, "
        f"positive mean={sum(land) / max(1, len(land)):.1f} m"
    )


if __name__ == "__main__":
    main()
