# Qatar: The Peninsula

A fresh Android open-world project built from the definitive Qatar prompt book in this repository. The active application does not reuse the previous Nauru Java sources, package ID, resources, or world binaries.

## Current build: v0.1.0 national streaming foundation

This first compiled milestone establishes the architecture required before dense country content:

- New package: `ai.arena.qatar.peninsula`
- Android API 26–35
- libGDX/OpenGL ES 3 renderer
- gdx-bullet fixed-step rigid-body simulation
- WGS84 / UTM zone 39N (`EPSG:32639`), true 1:1 metre scale
- Floating-origin support
- Complete installed Qatar national pack with 5,665 indexed chunks
- All eight municipalities and seven ADM0 polygon components
- 125 m coast masks, ten regional biome classes, 25 public hubs
- Random-access `.qpak` reads with per-chunk decompression and CRC validation
- Nearby/predictive chunk activation and hysteretic eviction
- Original generated 3D terrain, roads, buildings, Gulf water, human, 4×4, boat, helicopter, and aeroplane blockouts
- Force-driven Bullet foundation and five switchable focus modes
- No Android INTERNET permission
- Deterministic compiler, coverage tests, APK structure checks, and SHA-256 publication

## Important status

v0.1.0 is a real installable incremental APK, but it is not falsely described as the finished multi-year AAA-scale country. It proves Milestones 0 and the coarse portion of Milestone 1. Complete current OSM road/building geometry, DEM terrain, detailed coast/reclamations, final vehicle models, landmarks, traffic, localization, audio, and physical-phone optimization remain subsequent milestones.

See:

- `QATAR_DEFINITIVE_FULL_OFFLINE_MAP_LOCAL_STREAMING_AAA_PROMPT_BOOK_v2.txt`
- `QATAR_BOUNDARY_REPORT.md`
- `QATAR_WORLD_EPOCH.md`
- `DATA_SOURCES.md`
- `ASSET_LICENSES.md`
- `data/qatar/compiled/qatar-world-report.json`

## Deterministic world validation

```bash
python3 tools/compile_qatar_world.py --verify
```

## Clean CI build

GitHub Actions runs compiler verification, source checksums, unit tests, Android compilation, APK ZIP/manifest/native-library validation, and ensures the `.qpak` remains uncompressed for random access. Only a passing APK is published under `artifacts/`.
