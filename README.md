# Qatar: The Peninsula

A fresh Android open-world project built from the definitive Qatar prompt book in this repository. The active application does not reuse the previous Nauru Java sources, package ID, resources, or world binaries.

## Current source milestone: v0.8.0 realistic ground-world preproduction

This incremental release preserves the physically confirmed v0.7 smooth-motion and offline street-map systems, then rebuilds the road, terrain, lighting, and building presentation with bounded mobile geometry and original procedural materials:

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
- Brighter opposite-direction fill lighting, improved window/roof materials, bounded parapets and rooftop equipment, and procedural mosque domes/minarets
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
- The expanded map overlays a deterministic 550×1030 national atlas generated from all 1,441,102 packaged OSM road segments; no Google map data, tiles, or network service is used
- The street minimap remains a touch target that opens the complete-country map with explicit CLOSE/Back navigation
- Touch destination selection with EPSG:32639 coordinate display and mode-safe teleportation: ground/road for human and car, Gulf water for boat, and exact elevated placement for aircraft
- Long-distance teleport resets stale prediction queues, evicts old local geometry, preserves bounded streaming, zeroes forces, and establishes temporary support while destination chunks arrive
- No Android `INTERNET` permission and no post-install map download
- Deterministic compilers, provenance/checksums, coverage tests, certificate checks, reinstall/lifecycle checks, all-five-mode emulator physics/camera smoke testing, and SHA-256 publication

## Honest status

v0.8.0 is a playable **incremental preproduction prototype**, not a claim that the prompt book’s multi-year AAA end state is complete. It uses real current OSM road vectors/building footprints, measured terrain, original procedural materials and geometry, authored (not live) Qatar weather presets, automatic platform thermal/power downshifts, the accepted v0.7 fixed-step/single-pacer camera presentation, locally rendered offline OSM street maps, and a public development certificate rather than production signing. Survey-grade terrain, hydrodynamic bathymetry, reclamation-by-reclamation coastline work, hero landmark models, the planned v0.9 character/vehicle overhaul, traffic/transit/activity, complete Arabic shaping/localization, audio, and broad physical-device profiling remain later milestones.

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
sha256sum -c data/qatar/SHA256SUMS
sha256sum -c data/qatar/OSM_SHA256SUMS
sha256sum -c data/qatar/DEM_SHA256SUMS
sha256sum -c data/qatar/TERRAIN_SHA256SUMS
```

`import_qatar_osm.py --validate` requires the pinned `osmium==4.1.1` Python package in the current implementation.

## Clean CI build

GitHub Actions verifies both data packs and source checksums, runs unit tests, compiles a fresh Android APK, validates ZIP/manifest/native libraries, and confirms both `.qpak` and `.qod` remain uncompressed for true asset random access. Only a passing APK is published under `artifacts/`.
