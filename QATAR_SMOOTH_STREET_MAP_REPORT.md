# Qatar Android v0.7 smooth-motion and street-map milestone

## Reported regressions addressed

The physical-phone report after v0.6 was explicit:

- motion jerk became worse rather than better;
- the minimap/full map showed Qatar and a destination pin but did not expose enough streets to choose a destination intelligently.

v0.7 treats both as regressions and changes the implementation rather than increasing the old smoothing values.

## Motion correction

Android/libGDX already paces rendering to the active thermal profile (30, 20, or 15 fps). The world screen had a second accumulator gate that could reject a frame arriving slightly before 33.3 ms. The next frame could then arrive roughly 66 ms later, producing an irregular cadence that is especially obvious in faster modes.

The second render gate is removed. There is now one presentation pacer: `Gdx.graphics.setForegroundFPS` under the thermal governor. Bullet remains fixed at 60 Hz and the renderer still interpolates the two latest physics poses.

The camera filter is also narrowed. Interpolated horizontal camera and aim coordinates now pass through directly instead of receiving a second low-pass filter that created elastic lag. Only vertical terrain/contact noise is damped. Human, car, boat, helicopter, and aeroplane force and speed models are unchanged.

Runtime diagnostics record average and maximum presentation delta for every mode and explicitly report that no extra render gate exists.

## Real offline street maps

No Google data, tiles, visual assets, APIs, or network service are used. The map is rendered from the OpenStreetMap derivative already legally packaged inside the APK.

### Gameplay minimap

The old whole-country outline/pin is replaced by a nearby north-up street map:

- current land/water context sampled from loaded national chunks;
- actual nearby OSM motorway, trunk, primary, secondary, tertiary, residential, service, track, path, rail, runway, and taxiway vectors;
- class-sensitive road colour and width;
- a heading arrow instead of an orientation-free dot;
- a 256×256 texture cache rebuilt only after meaningful movement or streamed-road revision, at a throttled interval, so thousands of street vectors are not redrawn every gameplay frame.

### Expanded national map

`tools/build_qatar_road_atlas.py` deterministically rasterizes all 1,441,102 packaged OSM road segments into a transparent 550×1030 offline atlas. The atlas is overlaid on the complete country/municipality map. Major roads remain visually dominant while residential/service/path geometry exposes dense urban and rural networks.

The atlas is only 208,062 compressed bytes and approximately 2.27 MB as an RGBA GPU texture. Its source QOD hash, output hash, road-class counts, licence, and non-navigation limitation are recorded in `data/qatar/compiled/qatar-road-atlas-report.json`.

## Validation contract

The clean build must:

- regenerate the atlas byte-for-byte;
- verify its SHA-256;
- package the PNG alongside the uncompressed QTPK and QTOD;
- pass horizontal no-lag and vertical-noise camera tests;
- retain map projection and mode-safe teleport tests.

The Android runtime must additionally:

- detect high-frequency street detail in both the gameplay minimap and expanded map screenshots;
- require `STREET_ATLAS_READY` from the offline OSM source;
- require smooth single-pacer cadence diagnostics for all five modes;
- repeat map open/close/select/teleport, nine-scene, Battery Saver, lifecycle, process-preservation, and fatal-log gates.

Final APK identity and evidence are added only after the new candidate passes those workflows. Physical-phone confirmation remains necessary because emulator cadence cannot reproduce every handset compositor, thermal, or display-refresh interaction.
