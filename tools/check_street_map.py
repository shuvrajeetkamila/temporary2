#!/usr/bin/env python3
"""Require high-frequency offline street detail in the local and expanded Qatar maps."""
from __future__ import annotations

import argparse
from pathlib import Path

from check_png_scene import decode_rgb


def inspect(path: Path, bounds: tuple[float, float, float, float], label: str) -> tuple[int, int, float]:
    width, height, channels, pixels = decode_rgb(path)
    x0, y0, x1, y1 = bounds
    left, top, right, bottom = int(width * x0), int(height * y0), int(width * x1), int(height * y1)
    stride = width * channels
    colours: set[tuple[int, int, int]] = set()
    edges = comparisons = 0
    for y in range(top, bottom - 2, 2):
        for x in range(left, right - 2, 2):
            i = y * stride + x * channels
            right_i = i + 2 * channels
            down_i = i + 2 * stride
            colour = (pixels[i], pixels[i + 1], pixels[i + 2])
            colours.add(tuple(value >> 4 for value in colour))
            for other in (right_i, down_i):
                difference = abs(colour[0] - pixels[other]) + abs(colour[1] - pixels[other + 1]) + abs(colour[2] - pixels[other + 2])
                edges += difference >= 70
                comparisons += 1
    density = edges / max(1, comparisons)
    minimum_density = .035 if label == "local-minimap" else .055
    minimum_colours = 18 if label == "local-minimap" else 35
    if density < minimum_density or len(colours) < minimum_colours:
        raise ValueError(f"{label} lacks street detail: edge_density={density:.4f} colour_bins={len(colours)}")
    return edges, len(colours), density


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("gameplay", type=Path)
    parser.add_argument("expanded", type=Path)
    args = parser.parse_args()
    checks = (
        (args.gameplay, (.855, .13, .982, .37), "local-minimap"),
        (args.expanded, (.185, .125, .405, .86), "expanded-map"),
    )
    failed = False
    for path, bounds, label in checks:
        try:
            edges, colours, density = inspect(path, bounds, label)
            print(f"{label}: streets=yes edges={edges} edge_density={density:.4f} colour_bins={colours}")
        except (OSError, ValueError) as error:
            failed = True
            print(f"{label}: streets=no error={error}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
