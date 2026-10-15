# Qatar world epoch

## Target

- Project snapshot target: **2026-10-14**
- National runtime-pack identifier: `2026-10-14-srtm-osm-v0.3`
- OSM detail retrieval epoch: `2026-10-13T16:30:08Z`
- Terrain retrieval epoch: `2026-10-14T12:25:57Z`
- Current release stage: **v0.5.0 Qatar-stability preproduction milestone**

## Source epochs and known mismatches

| Layer | Current v0.5.0 basis | Status |
|---|---|---|
| National boundary/coast envelope | geoBoundaries v6 lineage, retrieved 2026-10-12 | Complete coarse envelope; reclamation-by-reclamation review pending |
| Municipal divisions | Eight geoBoundaries ADM1 units | Packaged and covered |
| Natural Earth cross-check | Pinned 1:10m QAT geometry | Validation only |
| Roads, rail, runway/taxiway geometry | Geofabrik/OpenStreetMap extract retrieved 2026-10-13 | 182,110 ways / 1,441,102 source segments packaged; game geometry, not navigation |
| Building footprints | Geofabrik/OpenStreetMap extract retrieved 2026-10-13 | 189,446 outer footprints packaged; relation inner holes are not rendered in QTOD v1 |
| Land elevation | Mapzen Terrain Tiles, primarily SRTM over Qatar land, retrieved 2026-10-14 | Measured source resampled to deterministic 250 m QDEM; not survey data |
| Ocean bathymetry | ETOPO1 component of Mapzen Terrain Tiles | Coarse visual/terrain foundation only; no hydrodynamic or nautical claim |
| Doha Metro/Lusail Tram | OSM rail geometry where tagged; no authored transit simulation | Current operational review and simulation pending |
| Airports/ports | OSM public geometry plus public regional hubs | Detailed surface/operations/architecture work pending |
| Stadiums/landmarks | OSM footprints where tagged; no copied commercial models | Original hero-asset milestone pending |

The program never labels these layers as navigation, survey, aviation, or engineering data. Version 0.5.0 proves a clean Android/libGDX/Bullet toolchain, complete-country installed index, EPSG:32639 metre coordinates, local random-access streaming, all-municipality coverage, bounded source-derived OSM rendering, measured terrain-scale elevation, deterministic environment presets, Gulf tide/wind coupling, adaptive Android thermal/power budgets, and repeatable preproduction installation/lifecycle validation. Later releases must update this document rather than silently mixing dates.
