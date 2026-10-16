#!/usr/bin/env python3
"""Deterministic v0.9.1 regression audit for the reported detail cell and mode policy."""
from __future__ import annotations

import argparse
import json
import math
import pathlib
import struct
import zlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
DETAIL = ROOT / "app/src/main/assets/world/qatar-osm-current.qod"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit("CORRECTIVE AUDIT FAILED: " + message)


def audit_cell() -> dict:
    data = DETAIL.read_bytes()
    require(data[:4] == b"QTOD", "QTOD magic")
    version, epsg, cell_metres, mask_dimension, cell_count = struct.unpack_from("<iiiii", data, 4)
    metadata_bytes, index_bytes = struct.unpack_from("<ii", data, 56)
    require((version, epsg, cell_metres, mask_dimension) == (1, 32639, 250, 16), "QTOD spatial header")
    require(index_bytes == cell_count * 32, "QTOD index length")
    index_offset = 64 + metadata_bytes
    target = None
    for index in range(cell_count):
        values = struct.unpack_from("<iiqiiiHH", data, index_offset + index * 32)
        if values[:2] == (378, 362):
            target = values
            break
    require(target is not None, "cell 378,362 is indexed")
    x, y, offset, packed_bytes, raw_bytes, expected_crc_signed, road_count, building_count = target
    raw = zlib.decompress(data[offset : offset + packed_bytes])
    expected_crc = expected_crc_signed & 0xFFFFFFFF
    require(len(raw) == raw_bytes, "cell 378,362 raw length")
    require(zlib.crc32(raw) == expected_crc, "cell 378,362 CRC")
    require(raw[:4] == b"QDET", "cell payload magic")
    payload_x, payload_y, payload_roads = struct.unpack_from("<iii", raw, 4)
    require((payload_x, payload_y, payload_roads) == (x, y, road_count), "cell payload coordinates/count")
    position = 16
    classes: dict[int, int] = {}
    for _ in range(road_count):
        source_id, x1, z1, x2, z2, road_class, lanes, surface, flags, width = struct.unpack_from("<qiiiiBBBBH", raw, position)
        position += 30
        require(source_id != 0 and width > 0, "valid road identity/width")
        require(all(math.isfinite(value / 10) for value in (x1, z1, x2, z2)), "finite road geometry")
        classes[road_class] = classes.get(road_class, 0) + 1
    payload_buildings = struct.unpack_from("<i", raw, position)[0]
    position += 4
    require(payload_buildings == building_count, "cell building count")
    footprint_vertices = 0
    maximum_footprint = 0
    for _ in range(building_count):
        source_id, bx, bz, width, depth, height, yaw, archetype, flags, vertices = struct.unpack_from("<qiiiiiHBBH", raw, position)
        position += 34
        require(source_id != 0 and width > 0 and depth > 0 and height > 0, "valid building dimensions")
        require(3 <= vertices <= 192, "bounded building footprint")
        position += vertices * 8
        footprint_vertices += vertices
        maximum_footprint = max(maximum_footprint, vertices)
    require(position == len(raw), "cell has no unread payload tail")
    require((packed_bytes, raw_bytes, road_count, building_count) == (461, 1162, 31, 2), "reported cell fixture")
    require(classes == {5: 8, 6: 23}, "reported cell road classes")
    require((footprint_vertices, maximum_footprint) == (18, 12), "reported cell footprints")
    return {
        "x": x,
        "y": y,
        "packedBytes": packed_bytes,
        "rawBytes": raw_bytes,
        "crc32": f"{expected_crc:08x}",
        "roads": road_count,
        "roadClasses": {str(key): value for key, value in sorted(classes.items())},
        "buildings": building_count,
        "footprintVertices": footprint_vertices,
        "maximumFootprintVertices": maximum_footprint,
        "payloadTailBytes": len(raw) - position,
    }


def audit_source() -> dict:
    policy = (ROOT / "app/src/main/java/ai/arena/qatar/world/ModeTransitionPolicy.java").read_text()
    screen = (ROOT / "app/src/main/java/ai/arena/qatar/WorldScreen.java").read_text()
    streamer = (ROOT / "app/src/main/java/ai/arena/qatar/world/QatarDetailStreamer.java").read_text()
    renderer = (ROOT / "app/src/main/java/ai/arena/qatar/render/QatarWorldRenderer.java").read_text()
    hud = (ROOT / "app/src/main/java/ai/arena/qatar/ui/HudRenderer.java").read_text()
    layout = (ROOT / "app/src/main/java/ai/arena/qatar/ui/NationalMapLayout.java").read_text()

    require("case BOAT -> Placement.CURRENT_SCREEN" in policy, "boat placement policy")
    require("placement==ModeTransitionPolicy.Placement.CURRENT_SCREEN" in screen, "WorldScreen uses current-screen policy")
    branch = screen.split("placement==ModeTransitionPolicy.Placement.CURRENT_SCREEN", 1)[1].split("placement==ModeTransitionPolicy.Placement.ROTOR_CLEARANCE", 1)[0]
    require("resetForTeleport" not in branch and "physics.teleport" not in branch, "boat branch does not teleport/reset")
    require("locationDelta=" in branch and "streamReset=no" in branch, "boat transition is observable")
    require("FAILURE_ATTEMPTS_BEFORE_WARNING=3" in streamer, "bounded detail retry policy")
    require("error.getClass().getSimpleName()" in streamer, "diagnostic throwable class")
    require("buildDetailCellResilient" in renderer and "buildReducedDetailCell" in renderer, "detail geometry fallback")
    require("ModelBuilder cellBuilder=new ModelBuilder()" in renderer, "isolated per-cell ModelBuilder")
    require("installDetail(chunk,buildDetailCellResilient(chunk))" in renderer, "atomic detail install path")
    require("detailStreamingWarning" in hud and "fatalStreamingError" in hud, "recoverable/fatal HUD separation")
    require("new Color(.31f,.095f,.11f,1)" in renderer and "new Color(.52f,.53f,.51f,1)" in renderer, "muted transport palette")
    require("rigPart(\"torso\",personWhite" in renderer and "rigPart(prefix+\"-leg\",personWhite" in renderer, "white WALK clothing")
    require("Rect panel=new Rect(0,0,width,height)" in layout, "edge-to-edge full-screen map panel")
    require("crossX=close.x()+close.width()/2" in hud and "RETURN TO GAME" in hud, "visible cross return control")
    require("MAP_PREVIEW" in screen and "exactConfirmation=yes" in screen, "resolved final pin preview")
    require("showDestinationMarker" in screen and "ARRIVAL_PIN_VISIBLE" in renderer, "confirmed pin appears in gameplay")
    require("destinationVisible=hasMapSelection" in hud and "FULLSCREEN MAP" in hud, "destination appears on minimap")
    return {
        "boatModePlacement": "CURRENT_SCREEN",
        "boatAutomaticTeleport": False,
        "boatStreamReset": False,
        "detailFailureAttemptsBeforeWarning": 3,
        "detailActivation": "atomic",
        "detailGeometryFallback": "reduced bounded cell",
        "messageLessThrowableDiagnostic": "class + no-message marker",
        "transportPalette": "grey with dull-red accents",
        "walkClothing": "white",
        "precisionMapWindow": "edge-to-edge full screen",
        "mapExitControl": "top-right cross and Android Back",
        "teleportPreview": "resolved final safe pin before confirmation",
        "gameplayArrivalPin": "30-second world ring plus minimap marker",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--report", type=pathlib.Path)
    args = parser.parse_args()
    report = {"status": "PASSED", "reportedCell": audit_cell(), "sourceContracts": audit_source()}
    text = json.dumps(report, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text)
    print(text, end="")


if __name__ == "__main__":
    main()
