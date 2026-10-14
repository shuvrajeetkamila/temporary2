#!/usr/bin/env python3
"""Reject emulator screenshots whose gameplay viewport is effectively one flat colour."""
from __future__ import annotations

import argparse
import math
import struct
import zlib
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    return a if pa <= pb and pa <= pc else b if pb <= pc else c


def decode_rgb(path: Path) -> tuple[int, int, int, bytes]:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("not a PNG")
    offset, compressed, header = 8, bytearray(), None
    while offset + 12 <= len(data):
        length = struct.unpack_from(">I", data, offset)[0]
        kind = data[offset + 4 : offset + 8]
        payload = data[offset + 8 : offset + 8 + length]
        offset += 12 + length
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", payload)
        elif kind == b"IDAT":
            compressed.extend(payload)
        elif kind == b"IEND":
            break
    if header is None:
        raise ValueError("missing IHDR")
    width, height, depth, colour_type, compression, filtering, interlace = header
    if depth != 8 or colour_type not in (2, 6) or compression or filtering or interlace:
        raise ValueError(f"unsupported PNG format depth={depth} colour={colour_type} interlace={interlace}")
    channels = 3 if colour_type == 2 else 4
    packed = zlib.decompress(bytes(compressed))
    stride = width * channels
    if len(packed) != height * (stride + 1):
        raise ValueError("unexpected decompressed PNG length")
    output = bytearray(height * stride)
    source = 0
    for y in range(height):
        filter_type = packed[source]
        source += 1
        row = bytearray(packed[source : source + stride])
        source += stride
        previous = output[(y - 1) * stride : y * stride] if y else bytes(stride)
        for x in range(stride):
            left = row[x - channels] if x >= channels else 0
            up = previous[x]
            upper_left = previous[x - channels] if x >= channels else 0
            if filter_type == 1:
                row[x] = (row[x] + left) & 255
            elif filter_type == 2:
                row[x] = (row[x] + up) & 255
            elif filter_type == 3:
                row[x] = (row[x] + ((left + up) >> 1)) & 255
            elif filter_type == 4:
                row[x] = (row[x] + paeth(left, up, upper_left)) & 255
            elif filter_type != 0:
                raise ValueError(f"unsupported PNG filter {filter_type}")
        output[y * stride : (y + 1) * stride] = row
    return width, height, channels, bytes(output)


def validate_scene(path: Path) -> tuple[float, int, int]:
    width, height, channels, pixels = decode_rgb(path)
    # Exclude the top HUD/minimap/buttons and bottom touch controls. The remaining centre must depict a scene.
    x0, x1 = int(width * .10), int(width * .80)
    y0, y1 = int(height * .22), int(height * .76)
    stride = width * channels
    count = 0
    mean = m2 = 0.0
    colours: set[tuple[int, int, int]] = set()
    for y in range(y0, y1, 3):
        for x in range(x0, x1, 3):
            index = y * stride + x * channels
            r, g, b = pixels[index], pixels[index + 1], pixels[index + 2]
            colours.add((r >> 4, g >> 4, b >> 4))
            luminance = .2126 * r + .7152 * g + .0722 * b
            count += 1
            delta = luminance - mean
            mean += delta / count
            m2 += delta * (luminance - mean)
    deviation = math.sqrt(m2 / max(1, count))
    if deviation < 7.0 or len(colours) < 12:
        raise ValueError(f"flat/obstructed gameplay viewport: luminance_sd={deviation:.2f}, colour_bins={len(colours)}")
    return deviation, len(colours), count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("images", nargs="+", type=Path)
    args = parser.parse_args()
    failed = False
    for image in args.images:
        try:
            deviation, colours, samples = validate_scene(image)
            print(f"{image}: scene=yes luminance_sd={deviation:.2f} colour_bins={colours} samples={samples}")
        except (OSError, ValueError, zlib.error) as error:
            failed = True
            print(f"{image}: scene=no error={error}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
