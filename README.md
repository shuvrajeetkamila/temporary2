# Qatar: The Peninsula

A fresh Android open-world project built from the definitive Qatar prompt book in this repository. The active application does not reuse the previous Nauru Java sources, package ID, resources, or world binaries.

## Current source milestone: v0.9.1 streaming, boat-placement & palette corrective

This certified corrective release preserves the complete v0.9 character/coastal/world update, the physically confirmed v0.7 smooth-motion implementation, and the approved v0.8.1 precision map. It adds:

- Exact regression coverage and Android runtime activation for reported OSM detail cell `378,362`
- Isolated per-cell geometry builders, atomic detail activation, bounded retries, reduced-geometry recovery, and useful exception-class diagnostics instead of `failed: null`
- BOAT mode cycling at the current visible X/Z with zero hidden teleport and no streamer reset; explicit confirmed map teleport remains mode-aware
- Neutral-grey transport surfaces with dull-red accents and white clothing for the WALK character

The retained major-release feature set includes:

- Original smooth articulated WALK character with animated limbs, attached hands/feet, face and material detail, terrain-aware placement, contact shadow, white clothing, and thermal LOD
- Original rounded 4×4 with visible cabin/driver, animated steering wheels and hubs, suspension, mirrors, roof rails, spare wheel, and lights
- Original curved boat with cabin, rails, outboard propulsion, animated rudder/propeller, navigation lights, wake, local land/water mode boarding, and explicit-map shore clearance
- Original helicopter with dual pilots, glazed cockpit, tapered tail, skids, animated rotors, beacon, navigation and landing lights
- Original aeroplane with pilot/cockpit, rounded wings, animated propeller/control presentation, landing gear, wingtip lamps, and OSM-runway boarding
- Safe WALK→CAR→BOAT→HELICOPTER→AEROPLANE→WALK placement: BOAT and HELICOPTER stay local, aeroplane boards a packaged runway, and ground return uses installed land/road data while preserving accepted movement pacing
- Mask-aligned coastal water, beaches, translucent shallows, dry building foundations, reclaimed transport pads, bridge clearance/barriers, tunnel portals, signs, and streetlights
- Original thermally capped Legtaifiya Metro, skyline, airport, port, and heritage hero geometry
- Deterministic resident-route ambience for traffic, pedestrians, parked vehicles, boats, and aircraft, with NORMAL/WARM/HOT caps and throttled route rebuilding
- Full-country coastal audit covering every packaged building, road, runway, mainland/island component, and a dedicated 2 km Legtaifiya Metro neighbourhood; unresolved after policy: zero

- New package: `ai.arena.qatar.peninsula`
- Android API 26–35, libGDX/OpenGL ES 3, and gdx-bullet
- WGS84 / UTM zone 39N (`EPSG:32639`) at true 1:1 metre scale with a floating origin
- Complete installed Qatar national pack: 5,665 indexed 2 km safety/terrain chunks
- Measured 250 m Mapzen/SRTM/ETOPO1 elevation grid aligned to all 5,665 chunks
- Deterministic clear, shamal, dust, and rain environment presets with bounded daylight, wind, visibility, and compressed Gulf tide models
- Coast/water-only animated tide surfaces with deterministic wave normals; terrain-aware boat buoyancy and wind-aware aircraft
- Android thermal listener with automatic NORMAL 30 fps, WARM 20 fps, and HOT 15 fps streaming/render/collision budgets
- Battery Saver automatically selects the WARM budget; backgrounding suspends continuous rendering and resume restores it
- Full-screen MSAA is disabled to reduce GPU bandwidth, battery drain, and heat on modest phones
- A fixed, explicitly public preproduction certificate lets v0.5+ APKs update each other across clean CI builds
- Joined OSM road endpoints and intersections using deterministic capped junction geometry, hierarchy-aware shoulders, terrain-following subdivisions, lane/centre dashes, major-road edge lines, runway/taxiway markings, path beds, and twin rails
- Five generated 128×128 mipmapped surface families for dune sand, limestone/gravel, sabkha/salt, urban aggregate, and vegetation, mapped into all ten Qatar biome classes without replacing measured elevation
- Seven deterministic Qatar façade families: limestone, sandstone, white plaster, glass tower, civic, industrial, and farm
- Calibrated opposite-direction fill lighting, two mipmapped procedural façade patterns, improved window/roof materials, bounded parapets and rooftop equipment, and procedural mosque domes/minarets
- Complete installed Qatar OpenStreetMap derivative: 88,965 indexed 250 m microcells
- 182,110 source road ways represented by 1,441,102 road segments
- 189,446 source building footprints, including 356 relation outer rings
- Original 3D rendering from road vectors and extruded source footprints; no satellite imagery or map-tile “picture mode”
- All eight municipalities, seven ADM0 polygon components, country land, islands, both coasts, border region, and an 8 km gameplay-water buffer
- Random-access `.qpak` and `.qod` reads with per-block decompression and CRC validation
- Mode-sensitive nearby/predictive activation, delayed eviction, a hard 64-cell visual cap, and a smaller capped Bullet collision set
- Explicit 30 fps foreground budget to avoid wasteful 60/90/120 Hz rendering on modest phones
- Human, 4×4, boat, helicopter, and aeroplane physical prototype modes
- 60 Hz fixed-step pose interpolation with direct interpolated horizontal camera tracking and vertical-only contact damping; physics speed is not reduced
- A single Android/libGDX frame pacer replaces the previous second manual render gate, which could irregularly turn a nominal 30 Hz presentation into roughly 15 Hz
- The gameplay minimap now renders nearby OSM streets from a throttled 256×256 vector cache, with water/land context and a heading arrow rather than a blind country pin
- The expanded map opens at a complete-Qatar fit using the deterministic 550×1030 national atlas generated from all 1,441,102 packaged OSM road segments; no Google map data, tiles, or network service is used
- Two-pointer anchor-preserving pinch zoom from ×1 to ×64, one-pointer bounded pan, large zoom controls, and an explicit VIEW ALL QATAR reset
- At ×12 and closer, a debounced single background worker reads only intersecting 250 m QTOD cells and rasterizes a cached 384×720 vector layer; the magnified overview is removed once crisp detail is ready
- Hard precision-map limits of 3,200 candidate cells and 120,000 road segments prevent any HUD frame from decoding or traversing the installed 1.44 million-road set
- Tap or stationary long-press places a persistent geographically anchored pin; pan/pinch movement suppresses accidental selection and CONFIRM TELEPORT remains a separate action
- The map displays local and EPSG:32639 coordinates, nearest named area, zoom, view size, scale, detail-cell/road counts, mode-safe placement policy, and safe-placement adjustment
- The street minimap remains a touch target that opens the complete-country map with explicit CLOSE/Back navigation
- Destination confirmation uses mode-safe teleportation: ground/road for human and car, Gulf water for boat, and exact elevated placement for aircraft
- Long-distance teleport resets stale prediction queues, evicts old local geometry, preserves bounded streaming, zeroes forces, and establishes temporary support while destination chunks arrive
- No Android `INTERNET` permission and no post-install map download
- Deterministic compilers, provenance/checksums, coverage tests, certificate checks, reinstall/lifecycle checks, all-five-mode emulator physics/camera smoke testing, and SHA-256 publication

## Validated Android APK

- File: `artifacts/Qatar-The-Peninsula-CORRECTIVE-v0.9.1.apk`
- Size: `38,195,643` bytes
- SHA-256: `e11230ec9da473ea332bdd4724386e209bf541f532b9aecb47553f89f0cf8c8e`
- Package/version: `ai.arena.qatar.peninsula`, code `11`, `0.9.1-streaming-boat-palette-fix`
- APK source: `6a9551820e6e6e01dd772a03696b571df36005ab`
- Build/runtime: GitHub Actions `38910393950` / `38910741046`
- Direct GitHub download: <https://github.com/shuvrajeetkamila/temporary2/raw/refs/heads/arena/8288058f-temporary2/artifacts/Qatar-The-Peninsula-CORRECTIVE-v0.9.1.apk>

## Honest status

v0.9.1 is a playable **corrective preproduction prototype**, not a claim that the prompt book’s multi-year photorealistic AAA end state is complete. It uses real current OSM roads/building footprints, measured terrain, original procedural models/materials, authored weather, automatic thermal/power downshifts, accepted v0.7 fixed-step/single-pacer motion, and a public development certificate. Automated Android validation proves install/reinstall, exact cell `378,362` activation without fallback, all five rigs and stable mode states, current-screen BOAT switching, runway/land transitions, bounded ambience, precision mapping, lifecycle, thermal switching, cadence, and fatal-free execution. Subjective graphics, touch feel, sustained heat, and compositor behaviour still require testing across multiple physical ARM phones. Production signing, audio, complete Arabic shaping/localisation, denser authored landmark detail, and broad device profiling remain future work.

See:

- `QATAR_DEFINITIVE_FULL_OFFLINE_MAP_LOCAL_STREAMING_AAA_PROMPT_BOOK_v2.txt`
- `QATAR_BOUNDARY_REPORT.md`
- `QATAR_WORLD_EPOCH.md`
- `QATAR_ENVIRONMENT_REPORT.md`
- `QATAR_PHYSICS_REPORT.md`
- `QATAR_RUNTIME_REPORT.md`
- `QATAR_STABILITY_REPORT.md`
- `QATAR_CAMERA_MAP_REPORT.md`
- `QATAR_SMOOTH_STREET_MAP_REPORT.md`
- `QATAR_REALISTIC_WORLD_REPORT.md`
- `QATAR_PRECISION_MAP_REPORT.md`
- `QATAR_V09_CHARACTER_COAST_ACTIVITY_REPORT.md`
- `QATAR_V091_STREAMING_BOAT_CORRECTIVE_REPORT.md`
- `DATA_SOURCES.md`
- `ASSET_LICENSES.md`
- `data/qatar/QATAR_OSM_IMPORT.json`
- `data/qatar/QATAR_DEM_IMPORT.json`
- `data/qatar/compiled/qatar-dem-report.json`
- `data/qatar/compiled/qatar-osm-report.json`
- `data/qatar/compiled/qatar-world-report.json`

## Deterministic validation

```bash
python3 tools/compile_qatar_world.py --verify
python3 tools/import_qatar_osm.py --validate app/src/main/assets/world/qatar-osm-current.qod
python3 tools/validate_qatar_terrain.py
python3 tools/audit_qatar_visual_geometry.py
python3 tools/audit_qatar_coastal_features.py --verify
python3 tools/audit_qatar_corrective_release.py
sha256sum -c data/qatar/SHA256SUMS
sha256sum -c data/qatar/OSM_SHA256SUMS
sha256sum -c data/qatar/DEM_SHA256SUMS
sha256sum -c data/qatar/TERRAIN_SHA256SUMS
```

`import_qatar_osm.py --validate` requires the pinned `osmium==4.1.1` Python package in the current implementation.

## Clean CI build

GitHub Actions verifies both data packs and source checksums, runs unit tests plus the cell `378,362`/BOAT corrective audit, compiles a fresh Android APK, validates ZIP/manifest/native libraries, and confirms both `.qpak` and `.qod` remain uncompressed for true asset random access. The Android workflow then targets the reported cell and proves current-screen BOAT switching before certification. Only a passing APK is published under `artifacts/`.
