# Qatar Android v0.6 camera-map milestone

## Scope

v0.6 addresses two reported gameplay problems before further content work:

1. visible screen rattle that becomes more noticeable from human through car, boat, helicopter, and aeroplane speeds;
2. the inability to expand the minimap and travel directly to a selected Qatar location.

This remains preproduction. Emulator validation cannot substitute for representative physical ARM-device motion, thermal, battery, and soak testing.

## Camera and physics presentation

Bullet remains authoritative at a fixed 60 Hz. The renderer no longer consumes only the latest discrete rigid-body transform. It retains the two most recent completed physics states and interpolates position and shortest-path heading using the remaining fixed-step accumulator. Both history samples are shifted together during a floating-origin rebase.

The chase-camera position and its look target now use independent frame-rate-independent exponential damping. The response is tuned by movement family: faster aircraft receive stronger look-target filtering while retaining their original force, speed, collision, and streaming behaviour. Terrain/building camera obstruction checks still run after filtering, so the fix does not hide collision by clipping the camera through geometry.

Camera state is reset on mode change, resize, lifecycle resume, and teleport. Physics speed was not reduced as a workaround.

## Touchable national map

The in-game minimap is now an explicit `OPEN MAP` touch target. It opens a modal full-country window that includes:

- the complete installed 5,665-cell Qatar pack extent;
- municipality-coloured land coverage, detailed national polygon outlines, all public hubs, and north-up orientation;
- current-player and selected-destination markers;
- local pack coordinates and absolute EPSG:32639 easting/northing;
- a large `CLOSE` button, Android Back handling, and a large `TELEPORT` button.

Opening the map releases movement pointers and freezes gameplay forces. Closing it returns to gameplay without moving the player.

## Safe teleport contract

A touched point is resolved entirely from packaged offline data:

- human and car use retained land and then the nearest safe OSM road/roadside where available;
- boat uses navigable Gulf water and moves to the nearest water cell when land was touched;
- helicopter and aeroplane preserve the exact selected horizontal coordinate and start with 60 m or 120 m surface clearance;
- destination terrain comes from the measured national QTPK;
- body forces and linear/angular velocity are cleared;
- old terrain/detail prediction queues and resident geometry are evicted with generation guards against stale asynchronous decoder completions;
- the floating origin is relocated to the destination;
- temporary ground support protects human/car while local terrain arrives, while a bounded water-support grace protects a boat destination;
- camera history is reset rather than smoothing across a country-scale discontinuity.

Any land/water adjustment is reported in metres and logged with requested and resolved coordinates.

## Automated gates

The source tests cover fixed-step midpoint/rebase interpolation, shortest-path heading interpolation, frame-rate-independent camera filtering, high-speed vertical-noise attenuation, national map projection round trips, north-up orientation, button separation, ground/water/air teleport resolution, and post-teleport finite Bullet state.

The extended Android workflow additionally opens and closes the map, selects northern Qatar, captures the open and selected windows, performs a WALK teleport, validates the destination scene, then continues through all five physics modes, Battery Saver, lifecycle, screenshot, and fatal-log gates.

Final APK hash and passing build/runtime identifiers are recorded only after those workflows complete.

## Signing boundary

The APK continues to use the fixed public preproduction certificate introduced in v0.5. It is update-compatible with v0.5, but it is not production signing or an assertion of store readiness.
