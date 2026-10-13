# Qatar world epoch

## Target

- Project snapshot target: **2026-10-13**
- National runtime-pack identifier: `2026-10-12-coarse-foundation`
- OSM detail retrieval epoch: `2026-10-13T16:30:08Z`
- Current release stage: **v0.2.0 incremental real-OSM microstreaming milestone**

## Source epochs and known mismatches

| Layer | Current v0.2.0 basis | Status |
|---|---|---|
| National boundary/coast envelope | geoBoundaries v6 lineage, retrieved 2026-10-12 | Complete coarse envelope; reclamation-by-reclamation review pending |
| Municipal divisions | Eight geoBoundaries ADM1 units | Packaged and covered |
| Natural Earth cross-check | Pinned 1:10m QAT geometry | Validation only |
| Roads, rail, runway/taxiway geometry | Geofabrik/OpenStreetMap extract retrieved 2026-10-13 | 182,110 ways / 1,441,102 source segments packaged; game geometry, not navigation |
| Building footprints | Geofabrik/OpenStreetMap extract retrieved 2026-10-13 | 189,446 outer footprints packaged; relation inner holes are not rendered in QTOD v1 |
| Elevation | Biome microheight only | SRTM/Copernicus DEM milestone pending |
| Bathymetry/tide | Analytic flat Gulf foundation | Open bathymetry and bounded tide model pending |
| Doha Metro/Lusail Tram | OSM rail geometry where tagged; no authored transit simulation | Current operational review and simulation pending |
| Airports/ports | OSM public geometry plus public regional hubs | Detailed surface/operations/architecture work pending |
| Stadiums/landmarks | OSM footprints where tagged; no copied commercial models | Original hero-asset milestone pending |

The program never labels these layers as navigation or survey data. Version 0.2.0 proves a clean Android/libGDX/Bullet toolchain, complete-country installed index, EPSG:32639 metre coordinate system, local random-access streaming, all-municipality coverage, and bounded source-derived OSM rendering. Later releases must update this document rather than silently mixing dates.
