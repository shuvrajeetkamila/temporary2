#!/usr/bin/env python3
"""Validate that the expanded map visibly zooms/pans and retains street texture."""
from __future__ import annotations

import argparse
from pathlib import Path

from check_png_scene import decode_rgb

MAP_BOUNDS = (.185, .125, .405, .86)


def crop_metrics(path: Path) -> tuple[int, int, int, bytes, float, int]:
    width, height, channels, pixels = decode_rgb(path)
    left, top = int(width * MAP_BOUNDS[0]), int(height * MAP_BOUNDS[1])
    right, bottom = int(width * MAP_BOUNDS[2]), int(height * MAP_BOUNDS[3])
    stride = width * channels
    edges = comparisons = 0
    colours: set[tuple[int, int, int]] = set()
    for y in range(top, bottom - 2, 2):
        for x in range(left, right - 2, 2):
            index = y * stride + x * channels
            colour = (pixels[index], pixels[index + 1], pixels[index + 2])
            colours.add(tuple(value >> 4 for value in colour))
            for other in (index + channels * 2, index + stride * 2):
                difference = sum(abs(colour[channel] - pixels[other + channel]) for channel in range(3))
                edges += difference >= 55
                comparisons += 1
    return width, height, channels, pixels, edges / max(1, comparisons), len(colours)


def map_difference(first: tuple[int, int, int, bytes, float, int], second: tuple[int, int, int, bytes, float, int]) -> float:
    width, height, channels, a, _, _ = first
    if (width, height, channels) != second[:3]:
        raise ValueError("screenshots have different dimensions")
    b = second[3]
    left, top = int(width * MAP_BOUNDS[0]), int(height * MAP_BOUNDS[1])
    right, bottom = int(width * MAP_BOUNDS[2]), int(height * MAP_BOUNDS[3])
    stride = width * channels
    total = samples = 0
    for y in range(top, bottom, 3):
        for x in range(left, right, 3):
            index = y * stride + x * channels
            total += sum(abs(a[index + channel] - b[index + channel]) for channel in range(3))
            samples += 3
    return total / max(1, samples)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("overview", type=Path)
    parser.add_argument("zoomed", type=Path)
    parser.add_argument("panned", type=Path)
    args = parser.parse_args()
    try:
        overview = crop_metrics(args.overview)
        zoomed = crop_metrics(args.zoomed)
        panned = crop_metrics(args.panned)
        zoom_difference = map_difference(overview, zoomed)
        pan_difference = map_difference(zoomed, panned)
        if zoom_difference < 7.0:
            raise ValueError(f"zoom did not visibly change viewport: mean_difference={zoom_difference:.2f}")
        if pan_difference < 1.2:
            raise ValueError(f"pan did not visibly move viewport: mean_difference={pan_difference:.2f}")
        for label, metrics in (("zoomed", zoomed), ("panned", panned)):
            edge_density, colour_bins = metrics[4], metrics[5]
            if edge_density < .012 or colour_bins < 16:
                raise ValueError(f"{label} view lacks close street texture: edge_density={edge_density:.4f} colour_bins={colour_bins}")
            print(f"{label}: street_texture=yes edge_density={edge_density:.4f} colour_bins={colour_bins}")
        print(f"viewport: zoom_difference={zoom_difference:.2f} pan_difference={pan_difference:.2f} bounded_detail=yes")
        return 0
    except (OSError, ValueError) as error:
        print(f"precision-map: valid=no error={error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
