# NAURU: THE COUNTRYSIDE

A playable Android open-world prototype covering Nauru at real-world metre scale. Version 0.3.1 replaces the earlier invented procedural oval and settlements with offline open geospatial data for the actual island.

## Download / install

The GitHub Actions workflow produces **`Nauru-The-Countryside-MAP-ACCURATE-v0.3.1.apk`**. Install it on Android 8.0 or newer. Remove an older development build first if Android reports a signing conflict.

The APK is a debug-signed development build and does not require a Play Store account.

## Map-accurate v0.3 world

- Actual Nauru coastline projected from WGS84 into a metre-based local coordinate system
- Real 30 m SRTM elevation instead of invented hills, including Nauru's central plateau and coastal shelf
- 570 mapped OpenStreetMap road, track, footpath, runway, taxiway, pier and groyne features at their real coordinates
- 2,754 mapped buildings and infrastructure objects, including footprint-based facade windows, storage tanks, utility poles, beacons and towers
- Real Nauru International Airport runway alignment, apron, taxiways and terminal footprint
- Mapped Buada Lagoon, beaches, vegetation, residential areas, phosphate quarries and industrial land
- 363 mapped photovoltaic panel footprints with reflective solar material
- Mapped district labels and a minimap drawn from the real coastline and main road network
- Visible in-game OpenStreetMap attribution and fully offline map data
- Background world preparation screen so large GIS geometry does not freeze Android startup

## Playable systems

- Third-person walking, running, jumping, orbit camera and pinch zoom
- Original drivable **Reef Runner** with acceleration, braking/reverse, steering and exit/enter controls
- Actual-road traction, mapped centre markings and coastline collision
- Animated tropical ocean, reef shallows, atmospheric sky and day/night lighting
- Original photographic grass, asphalt and coral-limestone materials
- Dynamic shadows, material roughness and LOW/MEDIUM/HIGH graphics presets
- Island minimap, compass, real district/location names, speedometer and touch HUD
- Automatic checkpoint saving in a map-versioned save slot

This is **geographically map-accurate**, not a claim of Google-Earth-level street photogrammetry. Buildings use real footprints but procedural heights/materials where open source data has no facade detail. No Google Maps, Google Earth, Street View, GTA or other proprietary imagery/assets are included.

## Data sources and licensing

- Map geometry: © OpenStreetMap contributors, [Open Database License](https://www.openstreetmap.org/copyright)
- Elevation: NASA/NGA SRTM 1 arc-second data, obtained through AWS Terrain Tiles; public domain in the United States
- Renderer, game code, vehicle, character, generated building finishes and surface art: original project work

Detailed provenance and checksums are in [`data/geodata/SOURCES.md`](data/geodata/SOURCES.md) and [`data/geodata/SHA256SUMS`](data/geodata/SHA256SUMS).

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

Tap the graphics/FPS pill in the top-right to cycle **LOW → MEDIUM → HIGH**. Normal devices start at MEDIUM; Android low-RAM devices automatically start at LOW.

## Build and geodata pipeline

The app is native Java + OpenGL ES 2.0 and uses Android Gradle Plugin 8.7.3.

```bash
gradle :app:assembleDebug
```

The open-data import workflow downloads the Nauru OSM extract and SRTM tile. To regenerate the compact runtime map locally:

```bash
python3 tools/build_nauru_game_map.py \
  data/geodata/nauru.osm.gz app/src/main/res/raw/nauru_map.bin
cp data/geodata/nauru-dem-30m.bin app/src/main/res/raw/nauru_dem_30m.bin
```

The generated binary contains only the features needed by the game, keeping the APK small and allowing fully offline play.
