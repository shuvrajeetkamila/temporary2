# NAURU: THE COUNTRYSIDE

A native Android third-person open-world prototype built as a rendered 3D interpretation of Nauru. **v0.4.0 is a fresh 1:25 gameplay rebuild**: island-scale distances are compressed while the character, Reef Runner vehicle, road widths, selected building footprints, vegetation, and street-level props remain at believable human scale.

It uses original game code and original/generated material art. It does **not** contain GTA, Google Earth, Street View, or other proprietary game or imagery assets.

## Download / install

The tested GitHub Actions build is published as:

**`Nauru-The-Countryside-1x25-THERMAL-v0.4.0.apk`**

Install it on Android 8.0 or newer. The APK is a debug-signed development build and does not require a Play Store account. This rebuild uses the fresh application identity `ai.arena.nauru.countryside.dev`, so it can install independently from the older prototype.

## Rebuilt 1:25 graphics world

- Real Nauru coastline, roads, runway alignment, districts, land cover, mapped structures, and SRTM elevation remain the geographic source
- Geographic positions and island distances are compressed to 1:25 (`1 real metre = 0.04 game metres`)
- Character, vehicle, road widths, selected architecture, trees, tanks, solar panels, and utility objects remain human-readable at gameplay scale
- 200 important/representative road, track, path, runway, taxiway, pier, and groyne features are retained from the 570-feature source network
- 310 representative structures are selected from 2,754 mapped structures; overlapping minor buildings are culled instead of stacked together
- Representative neighborhoods are streamed in compact sectors with separate near and far geometry
- Real Nauru International Airport alignment, Buada Lagoon, coastal shelf, phosphate landscape, industrial areas, and district labels
- Original weathered tropical facade, grass, asphalt, and coral-limestone materials with physically inspired sun, fog, water, and material response
- Fully rendered graphics mode; no satellite or picture-mode backdrop
- Visible in-game `© OpenStreetMap contributors` attribution and fully offline map data

This is a game-world interpretation based on real open data, not a claim of street-level photogrammetry. Where the open map has no facade information, the renderer uses original procedural geometry and material finishes.

## Thermal and battery design

The rebuild specifically avoids the old unrestricted display-refresh-rate loop:

- Demand-driven OpenGL rendering capped at about **30 FPS** normally
- Automatic **20 FPS ECO mode** during Android battery saver or moderate thermal pressure
- Automatic **15 FPS** cap during severe thermal pressure
- ECO mode also disables sampled dynamic shadows and selects the tightest sector/LOD range
- HUD redraw capped at 20 Hz
- 512×512 shadow target with throttled updates instead of continuous high-resolution shadow rendering
- 512 px RGB565 runtime materials, 2× anisotropic filtering, compact draw distances, radial sector culling, and far LOD meshes
- 310 rendered representative structures instead of submitting all 2,754 mapped structures
- Low-RAM Android devices begin on the LOW preset
- The app respects Android's screen timeout instead of forcing an unattended display/GPU session to stay awake

The top-right profile pill displays **ECO** whenever battery or thermal protection is actively overriding the selected graphics preset.

## Playable systems

- Third-person walking, running, jumping, orbit camera, and pinch zoom
- Original drivable **Reef Runner** with acceleration, braking/reverse, steering, handbrake, and enter/exit controls
- Road-sensitive traction, runway/road markings, terrain height, and coastline collision
- Animated tropical ocean, reef shallows, atmospheric sky, clouds, and day/night lighting
- LOW / MEDIUM / HIGH graphics presets plus automatic thermal ECO override
- Compact-island minimap, compass, mapped district/location names, speedometer, and touch HUD
- Versioned checkpoint saving for the rebuilt world

## Touch controls

### On foot

- **Left stick:** move
- **RUN:** hold to sprint
- **JUMP:** jump
- **CAR / ENTER:** locate or enter the nearby Reef Runner
- **Swipe open screen area:** orbit camera
- **Two-finger pinch:** camera distance

### Driving

- **Left stick:** steer
- **GO:** accelerate
- **BRAKE:** brake, then reverse
- **DRIFT:** handbrake
- **EXIT:** leave vehicle

Tap the graphics/FPS pill in the top-right to cycle **LOW → MEDIUM → HIGH**. Thermal ECO protection still takes priority while the device is hot or in battery saver.

## Data sources and licensing

- Map geometry: © OpenStreetMap contributors, [Open Database License](https://www.openstreetmap.org/copyright)
- Elevation: NASA/NGA SRTM 1 arc-second data, obtained through AWS Terrain Tiles; public domain in the United States
- Renderer, game systems, vehicle, character, generated building finishes, and material art: original project work

Detailed provenance and checksums are in [`data/geodata/SOURCES.md`](data/geodata/SOURCES.md) and [`data/geodata/SHA256SUMS`](data/geodata/SHA256SUMS).

## Build and validation

The app is native Java + OpenGL ES 2.0 and uses Android Gradle Plugin 8.7.3.

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

The publication workflow compiles the installable APK and must pass the packaged-map compact-world startup regression before it can stage or publish v0.4.0. That regression constructs the CPU world from packaged resources and verifies the 1:25 scale, selected road range, structure culling, solar representation, compact coastline, walkable airport spawn, nearby vehicle, and airport label.

To regenerate the offline runtime map from the preserved sources:

```bash
python3 tools/build_nauru_game_map.py \
  data/geodata/nauru.osm.gz app/src/main/res/raw/nauru_map.bin
cp data/geodata/nauru-dem-30m.bin app/src/main/res/raw/nauru_dem_30m.bin
```
