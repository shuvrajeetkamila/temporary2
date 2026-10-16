# Qatar v0.9.2 Full-Screen Precision Map & Arrival Pin Report

Date: 2026-10-16  
Status: **PASSED — GitHub-built APK and live Android runtime certified**

## Release

- Package: `ai.arena.qatar.peninsula`
- Version code: `12`
- Version name: `0.9.2-fullscreen-map-arrival-pin`
- APK: `artifacts/Qatar-The-Peninsula-FULLSCREEN-MAP-v0.9.2.apk`
- Size: `38,197,847` bytes
- SHA-256: `cab1934d6bb99331e2c8addf600c1328fc64d65d2315b1365e966b91d7f3d31a`
- Source commit: `428c222fc0840988004e7ba24629e3b4e3757f0b`
- Build run: `38941901370`
- Publication commit: `d878c20`
- Runtime request: `69ee86b`
- Runtime run: `38942340627`
- Runtime evidence commit: `c46f8c1`

## Delivered behavior

### True full-screen map

Touching the street minimap now enters an edge-to-edge map mode. The map owns the complete display rather than appearing as a floating dialog. It retains:

- the complete packaged Qatar overview;
- offline OSM streets and close-view QTOD vectors;
- pinch zoom and accessible zoom buttons;
- one-finger bounded pan;
- VIEW ALL QATAR reset;
- local and UTM 39N coordinates;
- nearest named place and placement policy;
- a fixed destination crosshair and explicit confirmation.

The runtime marker was:

```text
MAP_OPEN ... fullQatar=yes fullScreen=yes interactive=yes crossClose=yes
```

### Cross return control

A large visible X is fixed in the top-right safe area with a RETURN TO GAME label. Touching it or using Android Back exits the map without moving the player. Runtime certification explicitly opened the map, touched X, resumed gameplay, and recorded:

```text
MAP_CLOSE gameplay=resumed fullScreenExited=yes teleport=no
```

### Exact final arrival preview

A touch first records the requested map coordinate. Before confirmation, the offline safe-placement resolver calculates the actual final destination for the active mode. The map pin moves to that resolved point and displays its adjustment and basis. Confirmation uses the cached preview directly rather than resolving a second potentially different point.

The certification exercised two destinations. Machine-readable comparison in `qa/qatar-runtime/teleport-pin-validation.txt` proved both preview/final pairs matched gameplay to within `0.01 m`:

```text
pair=1 preview_matches_gameplay=yes requested=(94642.609375, 90611.6875) final=(94642.61957789572, 90609.78463075665)
pair=2 preview_matches_gameplay=yes requested=(54911.51171875, 103752.1484375) final=(55260.30288155898, 103796.35389749092)
```

This preserves safety for buildings, coastlines, roads, water, and aircraft while eliminating a hidden post-confirmation destination change.

### Point visible in gameplay

After confirmation:

- the map closes automatically;
- the player appears at the exact previewed safe coordinate;
- a pulsing red arrival ring is rendered in the 3D world for 30 seconds;
- the same final destination is retained as a ring/crosshair on the street minimap;
- the destination remains available when the full-screen map is reopened.

Runtime evidence logged:

```text
ARRIVAL_PIN_VISIBLE ... seconds=30 minimap=yes world=yes
```

Both the target-cell and second teleport screenshots visibly contain the world arrival ring and minimap pin.

## Reported-cell regression retained

The full-country overview regression tap lands inside QTOD cell `378,362`. Android certification selected `(94642.61, 90611.69)`, previewed the final safe point, entered it, displayed its arrival ring, and activated the reported cell atomically:

```text
OSM_REGRESSION_CELL_READY cell=378,362 roads=31 buildings=2 activation=atomic
```

No detail fallback, skipped cell, fatal exception, or process restart occurred.

## Automated validation

Build run `38941901370` passed:

- all deterministic Qatar data, terrain, road-atlas, visual, coastal, and corrective audits;
- full-screen panel geometry and top-right X hit-region tests;
- deterministic Android-runtime target mapping to QTOD cell `378,362`;
- all existing physics, streaming, map, motion, coastal, and mode tests;
- Android compilation, package/version, ABI, signing, ZIP, random-access asset, and no-INTERNET checks.

Runtime run `38942340627` passed:

- install and reinstall;
- full-screen map open;
- X return without teleport;
- final safe pin preview;
- exact preview-to-gameplay coordinate equality for two destinations;
- 3D and minimap arrival markers;
- map zoom, pan, close-detail streets, selection, and confirmation;
- exact cell `378,362` activation;
- all five rigs and stable runtime states;
- current-screen BOAT behavior with no stream reset;
- all-five-mode smooth cadence with the accepted v0.7 frame-pacing path;
- Battery Saver WARM profile and same-process lifecycle resume;
- screenshot scene, street-map, and precision-map validators;
- fatal-free app-scoped logs.

## Preserved behavior

v0.9.2 does not reduce movement speed or alter the accepted v0.7 fixed-step/single-pacer implementation. It preserves complete-Qatar offline coverage, all map detail, explicit confirmation, BOAT current-location switching, white WALK clothing, muted transport colours, exact coastal masks, landmarks, activity, and thermal budgets.
