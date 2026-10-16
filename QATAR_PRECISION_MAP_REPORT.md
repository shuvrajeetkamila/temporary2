# Qatar v0.8.1 Precision Map & World Polish Report

Date: 2026-10-16  
Package: `ai.arena.qatar.peninsula`  
Version: code `9`, name `0.8.1-precision-map-world-polish`

## Result

v0.8.1 is built, packaged, and exercised on Android. It preserves the accepted v0.7 motion/frame-pacing implementation and the complete v0.8 ground-world renderer while replacing blind full-country point selection with a bounded precision-map workflow.

The expanded map now:

- opens at a complete-Qatar fit;
- supports anchor-preserving two-pointer pinch from ×1 through ×64;
- supports one-pointer drag pan constrained to the installed 110 km × 206 km world rectangle;
- includes large `-`, `+`, and `VIEW ALL QATAR` controls;
- distinguishes a stationary tap/long-press from pan or pinch, suppressing destination selection after a gesture;
- keeps the selected pin fixed in world coordinates while the view changes;
- displays local coordinates, EPSG:32639 coordinates, nearest named area, zoom, visible kilometres, approximate metres per pixel, placement policy, and safe-placement result;
- requires a separate `CONFIRM TELEPORT` action; and
- retains Close and Android Back as cancellation paths that resume gameplay.

## Map camera contract

`NationalMapViewport` is a pure/testable transform independent of rendering and input devices. Its state is a world-space centre and zoom. The visible extent is:

- `visibleWidth = worldWidth / zoom`
- `visibleHeight = worldHeight / zoom`

Pinch computes the world coordinate beneath the gesture focus before changing zoom, then chooses the new centre so that coordinate remains under the same normalized screen anchor. Centre clamping guarantees that all visible bounds remain inside the packaged Qatar world. Screen/world conversion uses the current visible bounds, so a destination pin is geographic state rather than a screen-space decoration.

The initial/reset state is exactly ×1, centre `(55,000 m, 103,000 m)`, with visible bounds `(0,0)` to `(110,000,206,000)`.

## Gesture safety

The modal map input state tracks primary and secondary pointers separately from the unchanged gameplay move/lift pointers.

- A second in-map pointer begins pinch mode immediately.
- A one-pointer displacement beyond 14 px begins pan mode.
- Releasing either pointer after pinch keeps gesture suppression active until all map pointers are released.
- Destination selection occurs only on release when the touch began in the map, remained stationary, and never became a pan/pinch.
- Long press therefore selects, while moving during a long press pans and does not select.
- Zoom, reset, Close, and confirmation controls are outside the map rectangle and handled independently.

## Offline close-detail renderer

The 550×1030 national atlas remains the fast complete-country overview and the temporary fallback while a close view settles. It is not used as the final close-zoom picture.

At zoom ×12 or closer, `NationalMapRoadCache`:

1. waits 180 ms for the viewport to settle;
2. computes only the 250 m QTOD cells intersecting the visible rectangle plus a one-cell margin;
3. enforces hard limits of 3,200 candidate grid cells and 120,000 road segments;
4. reads and CRC-validates those random-access blocks on one daemon worker;
5. streams road records without allocating building or road objects;
6. converts each cell-local segment into the national world coordinate system;
7. rasterizes hierarchy-aware casings and fills into one 384×720 CPU pixmap;
8. uploads the completed texture on the render thread; and
9. removes the magnified overview layer once the crisp QTOD texture is ready.

Viewport revisions invalidate stale jobs and textures. Panning/pinching cancels obsolete work; Close or teleport cancels active map work. No render frame walks the complete 88,965-cell / 1,441,102-segment QTOD pack.

The Android ×16 validation view loaded 530 indexed cells / 5,970 roads before pan and 505 cells / 5,709 roads after pan. The final screenshots visibly show distinct crisp street networks rather than enlarged national-atlas pixels.

## Teleport behavior

A map tap stores the exact current-view local coordinate and shows an anchored red pin and crosshair. Confirmation passes that coordinate to the existing mode-safe resolver:

- WALK/CAR: safe clear land/road placement;
- BOAT: navigable Gulf water;
- HELICOPTER/AEROPLANE: exact horizontal selection with mode clearance.

The final Android WALK exercise requested local `(54,229.09, 103,619.47)` and safely resolved to `(53,503.87, 103,831.63)`, an adjustment of `755.61 m`, with basis `nearest clear OSM roadside`. The UI retains the adjustment and resolver basis for the next map inspection.

## Automated verification

### Build and package

GitHub Actions build run: [38870630912](https://github.com/shuvrajeetkamila/temporary2/actions/runs/38870630912) — **passed**.

The build gate completed deterministic world/OSM/terrain/atlas checks, unit tests, fresh compilation, APK ZIP validation, uncompressed QTPK/QTOD checks, ABI checks, no-INTERNET check, and signing verification.

New/extended unit coverage verifies:

- complete-Qatar initial/reset bounds;
- anchor-preserving zoom;
- zoom limits and pan clamping;
- current-view screen/world round trips;
- camera revision semantics;
- non-overlapping accessible controls; and
- QTOD cell-local to national coordinate conversion.

### Android runtime

Final Android runtime run: [38870951584](https://github.com/shuvrajeetkamila/temporary2/actions/runs/38870951584) — **passed**.

The Android 29 x86_64 run verified:

- install and reinstall;
- launch and retained process across pause/resume;
- full-country map opening and Close-to-game;
- accessible zoom to ×16 through the same bounded viewport transform used by pinch;
- drag pan and a second asynchronous QTOD cache build;
- close-view street texture, precise pin, explicit confirmation, and safe teleport;
- no app-scoped fatal log;
- NORMAL 30 fps and Battery Saver WARM 20 fps profile transitions;
- stable WALK, CAR, BOAT, HELICOPTER, and AEROPLANE runtime states; and
- accepted single-pacer cadence (`extraRenderGate=no`, `smooth=yes`) in all five modes.

Precision screenshot analysis reported:

| View | Edge density | Quantized colour bins |
|---|---:|---:|
| Zoomed ×16 | 0.1790 | 83 |
| Panned ×16 | 0.1695 | 84 |

The overview-to-zoom mean pixel difference was `79.76`; the zoom-to-pan difference was `19.38`. Final resumed-process PSS was `67,159 KiB`, including `22,596 KiB` Java heap and `25,604 KiB` native heap.

The runtime used the accessible `+` control because ADB's standard input command does not synthesize simultaneous two-pointer pinch. Anchor-preserving pinch math is unit-tested and the two-pointer path is compiled into the APK, but subjective multitouch feel and physical ARM-device thermal/compositor behavior still require human phone testing.

## Published APK

| Item | Value |
|---|---|
| APK | `artifacts/Qatar-The-Peninsula-PRECISION-MAP-v0.8.1.apk` |
| Bytes | `38,164,179` |
| SHA-256 | `de8dd4cd1f99e099d573841df70b42ff81ec87fff60e01c38152388ec97c612c` |
| Source commit | `c5118414579f99ae78d98c12d94d17af26facf3b` |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| SDK | min 26 / target 35 |
| Certificate SHA-256 | `ccce47e341dde37e9cb94ab7cb9298ee39df967483ca6c618491cd43a7ae390c` |
| Production signed | No — stable public preproduction certificate |

Direct download:

<https://github.com/shuvrajeetkamila/temporary2/raw/refs/heads/arena/8288058f-temporary2/artifacts/Qatar-The-Peninsula-PRECISION-MAP-v0.8.1.apk>

## Preserved scope and remaining work

v0.8.1 does not change movement speeds, physics cadence, the accepted camera smoothing, package ID, signing continuity, world/data epochs, terrain, buildings, environment, streaming/thermal budgets, or the complete offline Qatar installation. No proprietary game, satellite, Google, Apple, Bing, or Street View assets are introduced.

Character and vehicle remodeling remains the planned v0.9 milestone. This release is still preproduction, and the map is game cartography—not navigation, survey, aviation, or engineering data.
