# Qatar: The Peninsula

A fresh Android open-world project built from the definitive Qatar prompt book in this repository. The active application does not reuse the previous Nauru Java sources, package ID, resources, or world binaries.

## Current source milestone: v0.4.0 Qatar-environments preproduction

This incremental release retains the complete source-derived Qatar road/building and measured-terrain layers, then adds a bounded Qatar environment and Android thermal-control pass:

- New package: `ai.arena.qatar.peninsula`
- Android API 26–35, libGDX/OpenGL ES 3, and gdx-bullet
- WGS84 / UTM zone 39N (`EPSG:32639`) at true 1:1 metre scale with a floating origin
- Complete installed Qatar national pack: 5,665 indexed 2 km safety/terrain chunks
- Measured 250 m Mapzen/SRTM/ETOPO1 elevation grid aligned to all 5,665 chunks
- Deterministic clear, shamal, dust, and rain environment presets with bounded daylight, wind, visibility, and compressed Gulf tide models
- Coast/water-only animated tide surfaces with deterministic wave normals; terrain-aware boat buoyancy and wind-aware aircraft
- Android thermal listener with automatic NORMAL 30 fps, WARM 20 fps, and HOT 15 fps streaming/render/collision budgets
- Qatar-specific road shoulders, rail/runway/taxiway surfaces, separate roofs/facades/windows, and smoother original player vehicles
- Complete installed Qatar OpenStreetMap derivative: 88,965 indexed 250 m microcells
- 182,110 source road ways represented by 1,441,102 road segments
- 189,446 source building footprints, including 356 relation outer rings
- Original 3D rendering from road vectors and extruded source footprints; no satellite imagery or map-tile “picture mode”
- All eight municipalities, seven ADM0 polygon components, country land, islands, both coasts, border region, and an 8 km gameplay-water buffer
- Random-access `.qpak` and `.qod` reads with per-block decompression and CRC validation
- Mode-sensitive nearby/predictive activation, delayed eviction, a hard 64-cell visual cap, and a smaller capped Bullet collision set
- Explicit 30 fps foreground budget to avoid wasteful 60/90/120 Hz rendering on modest phones
- Human, 4×4, boat, helicopter, and aeroplane physical prototype modes
- No Android `INTERNET` permission and no post-install map download
- Deterministic compilers, provenance/checksums, coverage tests, APK structure checks, all-five-mode emulator physics/camera smoke testing, and SHA-256 publication

## Honest status

v0.4.0 is a playable **incremental prototype**, not a claim that the prompt book’s multi-year AAA end state is complete. It uses real current OSM road vectors/building footprints, measured terrain, authored (not live) Qatar weather presets, and automatic platform thermal downshifts. Survey-grade terrain, hydrodynamic bathymetry, high-fidelity coastline/reclamation work, hero landmarks, final vehicle models/dynamics, traffic/transit/activity, complete Arabic shaping/localization, audio, and broad physical-device profiling remain later milestones.

See:

- `QATAR_DEFINITIVE_FULL_OFFLINE_MAP_LOCAL_STREAMING_AAA_PROMPT_BOOK_v2.txt`
- `QATAR_BOUNDARY_REPORT.md`
- `QATAR_WORLD_EPOCH.md`
- `QATAR_ENVIRONMENT_REPORT.md`
- `QATAR_PHYSICS_REPORT.md`
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
sha256sum -c data/qatar/SHA256SUMS
sha256sum -c data/qatar/OSM_SHA256SUMS
sha256sum -c data/qatar/DEM_SHA256SUMS
sha256sum -c data/qatar/TERRAIN_SHA256SUMS
```

`import_qatar_osm.py --validate` requires the pinned `osmium==4.1.1` Python package in the current implementation.

## Clean CI build

GitHub Actions verifies both data packs and source checksums, runs unit tests, compiles a fresh Android APK, validates ZIP/manifest/native libraries, and confirms both `.qpak` and `.qod` remain uncompressed for true asset random access. Only a passing APK is published under `artifacts/`.
