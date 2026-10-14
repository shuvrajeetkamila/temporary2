# Qatar environment and thermal report — v0.4.0

## Runtime environment contract

The environment system is deterministic authored game simulation, not live meteorology, tide prediction, flood modelling, or navigation data.

| Preset | Wind | Visibility | Intended visual/use case |
|---|---:|---:|---|
| CLEAR | 4.5 m/s | 18 km | Default warm, high-visibility Qatar daylight |
| SHAMAL | 13 m/s | 9 km | Strong northwesterly-wind gameplay preset |
| DUST | 9 m/s | 3.2 km | Reduced-visibility dust preset |
| RAIN | 7 m/s | 6 km | Rare-rain/wetness foundation |

- Authored day duration: 48 real minutes.
- Compressed Gulf tide: −0.37 m to +0.47 m around the game datum; intentionally accelerated for observable gameplay.
- Water meshes exist only in coast/marine national chunks and use deterministic seamless wave geometry.
- Boat buoyancy is enabled only when the exact 125 m national mask cell is water.
- Helicopter and aeroplane drag use wind-relative velocity.
- Weather preset tests verify tide/daylight bounds and material runtime identifiers.

## Thermal contract

Android 10/API 29 and newer use `PowerManager` thermal callbacks. Older supported devices retain the conservative NORMAL cap.

| Profile | Android status | FPS cap | OSM radius | Max detail cells | Building-collision cells | View distance |
|---|---|---:|---:|---:|---:|---:|
| NORMAL | NONE/LIGHT | 30 | 3 | 64 | 6 | 100% |
| WARM | MODERATE | 20 | 2 | 32 | 3 | 78% |
| HOT | SEVERE or higher | 15 | 1 | 16 | 1 | 58% |

The governor reduces rendering, decoding/upload, detail residency, collision, and view budgets together. It does not claim that emulator testing predicts heat on a specific phone. Physical-device cold-start, 30-minute traversal, charging, hot-ambient, and soak tests remain required before production signing.

## Visual changes

- Separate terrain and tide-translated Gulf surfaces.
- Opaque specular water to avoid expensive full-country alpha overdraw.
- Paved shoulders and distinct road, track, rail, runway, and taxiway materials.
- Separate roof, wall, and bounded facade-window geometry.
- Brighter Qatar daylight palette, distance haze, and an original deterministic seamless sand/gravel microtexture (not imagery).
- Original rounded 4×4, boat, helicopter, and light-aircraft blockout meshes.
- OSM roadside spawn preference instead of placing the player on a road centreline when a clear verge exists.
