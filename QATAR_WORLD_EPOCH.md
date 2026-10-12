# Qatar world epoch

## Target

- Project snapshot target: **2026-10-12**
- Runtime pack identifier: `2026-10-12-coarse-foundation`
- Current release stage: Milestone 0/1 national streaming foundation

## Source epochs and known mismatches

| Layer | Current v0.1.0 basis | Status |
|---|---|---|
| National boundary/coast envelope | geoBoundaries v6 lineage, retrieved 2026-10-12 | Coarse foundation; reclamation-by-reclamation review pending |
| Municipal divisions | Eight geoBoundaries ADM1 units | Packaged and covered |
| Natural Earth cross-check | Pinned 1:10m QAT geometry | Validation only |
| Roads | Generated coarse connections between public regional hubs | Must be replaced by timestamped OSM geometry |
| Building footprints | Deterministic source-labelled visual massing | Must be replaced by complete licensed footprints where available |
| Elevation | Biome microheight only | SRTM/Copernicus DEM milestone pending |
| Bathymetry/tide | Analytic flat Gulf foundation | Open bathymetry and bounded tide model pending |
| Doha Metro/Lusail Tram | Not represented in v0.1.0 | Current operational epoch review pending |
| Airports/ports | Public hub position/blockout only | Public OSM geometry milestone pending |
| Stadiums/landmarks | Not hero-authored in v0.1.0 | Current status and original-art review pending |

The program never labels these coarse generated layers as real navigation data. Version 0.1.0 proves the clean Android/libGDX/Bullet toolchain, complete-country installed index, EPSG:32639 metre coordinate system, local random-access streaming, and all-municipality coverage. Later releases must update this document rather than silently mixing dates.
