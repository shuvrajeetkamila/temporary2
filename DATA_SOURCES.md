# Qatar data sources

This file covers the fresh **Qatar: The Peninsula** project. No Nauru world data or code is used by the Qatar application.

## geoBoundaries gbOpen — Qatar ADM0 and ADM1

- Dataset family: geoBoundaries gbOpen, release 6.0 lineage
- Country: QAT
- Levels: ADM0 and ADM1
- Upstream project: <https://github.com/wmgeolab/geoBoundaries>
- Retrieved through a non-LFS mirror in `KristjanESPERANTO/MapCraft` pinned at commit `57d132faa6c7c012635147bc8455458b39f49413`
- Mirror paths:
  - `public/data/gbOpen/QAT/ADM0/geoBoundaries-QAT-ADM0.geojson`
  - `public/data/gbOpen/QAT/ADM1/geoBoundaries-QAT-ADM1.geojson`
- Local paths:
  - `data/qatar/source/geoboundaries-6-QAT-ADM0.geojson`
  - `data/qatar/source/geoboundaries-6-QAT-ADM1.geojson`
- Retrieved: 2026-10-12
- License: geoBoundaries gbOpen is distributed under Creative Commons Attribution 4.0 International (CC BY 4.0); the mirror repository code is MIT licensed.
- Attribution: Runfola et al., geoBoundaries; William & Mary geoLab.
- Processing: WGS84/CRS84 coordinates are deterministically projected into WGS84 / UTM zone 39N (EPSG:32639), rasterized to 125 m coast masks within 2 km streaming chunks, and embedded with all eight ADM1 records and seven ADM0 polygons.
- ADM0 SHA-256: `9afaa263f5f2385de40208af9ea0de55a5bc8d9384df241efd7be86b1793eaca`
- ADM1 SHA-256: `8021dc98ea5a92564b87a69633bca4d17374226d42926fabe40e4de64721403e`

This is a redistribution-friendly coarse boundary source, not a claim that it is Qatar's definitive legal maritime boundary. The application presents surrounding water only as gameplay space.

## Natural Earth 1:10m country geometry cross-check

- Repository: <https://github.com/AshKyd/geojson-regions>
- Pinned commit: `05ef4d72013437bbafa1485e32f46ed6ad32ebef`
- Source path: `public/countries/10m/QAT.geojson`
- Local path: `data/qatar/source/natural-earth-10m-QAT.geojson`
- Retrieved: 2026-10-12
- Data origin: Natural Earth
- License: Natural Earth data is public domain; the transformation repository is released under the Unlicense.
- Use: independent country-envelope cross-check only; it is not the runtime coast authority.
- SHA-256: `f896e9634a98a81ffddd753ccdea2bad09ef8b8ca5b90a1038de8f22bf669c03`

## Generated Milestone 1 derivatives

- Compiler: `tools/compile_qatar_world.py`
- Runtime pack: `app/src/main/assets/world/qatar-national.qpak`
- CRS: EPSG:32639, metres
- Grid: 5,665 indexed 2 km chunks; 16×16 land mask per chunk
- Detail cell: 125 m coast/land samples
- Coverage: all eight municipality records, seven ADM0 polygon components, land, coastline, and an 8 km gameplay-water buffer
- Coarse routes: deterministic public-hub connectivity graph. These are explicitly marked generated and are not advertised as turn-by-turn road data.
- Settlement massing: deterministic regional visual massing marked generated/coarse.
- Terrain: 9×9 measured elevation samples per chunk from the separately compiled QDEM derivative described below.

## Mapzen Terrain Tiles / SRTM / ETOPO1 — v0.3.0 terrain

- Distribution: Mapzen Terrain Tiles through the AWS `elevation-tiles-prod` Skadi archive
- Land elevation: SRTM data courtesy of the U.S. Geological Survey
- Ocean bathymetry: global ETOPO1 terrain data from NOAA National Centers for Environmental Information
- Retrieved UTC: `2026-10-14T12:25:57Z`
- Source tiles: `N24E050`, `N24E051`, `N25E050`, `N25E051`, `N26E050`, and `N26E051`; immutable hashes are recorded in `data/qatar/compiled/qatar-dem-report.json`
- Compiler: `tools/import_qatar_dem.py`
- Compact derivative: `data/qatar/source/qatar-srtm-250m.qdem`
- QDEM SHA-256: `23f96979dfcb77cc81c11c0f65c3047a463c7cef0be8d22fc1b1be22191d54eb`
- Grid: 441×825 samples at 250 m in EPSG:32639, aligned exactly to the national QTPK
- Processing: bilinear source sampling; coarse land/coast values are clamped to at least −2 m to prevent ETOPO1 ocean depths leaking into land-mask collision
- Licensing: SRTM and ETOPO1 are U.S. government public-domain datasets; attribution is requested
- Use: terrain-scale game rendering/collision only—not survey, navigation, aviation, flood, marine, or engineering data

Exact source-tile hashes, retrieval epoch, QDEM CRC/hash, import identity, limitations, terrain seams, land-mask ranges, and spawn checks are recorded in `data/qatar/QATAR_DEM_IMPORT.json`, `data/qatar/DEM_SHA256SUMS`, `data/qatar/TERRAIN_SHA256SUMS`, `data/qatar/compiled/qatar-dem-report.json`, and `data/qatar/compiled/qatar-terrain-validation.json`.

## OpenStreetMap Qatar roads and building footprints — v0.3.0

- Provider: Geofabrik GmbH OpenStreetMap extracts
- Source: `https://download.geofabrik.de/asia/gcc-states-latest.osm.pbf`
- Qatar extraction envelope: 50.68°E–51.70°E, 24.45°N–26.25°N, using `osmium extract --strategy complete_ways`
- Retrieved UTC: `2026-10-13T16:30:08Z`
- Extracted Qatar PBF SHA-256: `f8446d4296f9a6785122de9c0e0d14415b3be2ba3c762f44777939442205a8c9`
- Compiler: `tools/import_qatar_osm.py`, pinned to pyosmium 4.1.1 by the import workflow
- Runtime derivative: `app/src/main/assets/world/qatar-osm-current.qod`
- Derivative SHA-256: `c48a62e1e8f1498bb64bde2864e4ae7894380f5185bc0a3a39ebf47012d9ba00`
- Derivative size: 27,551,391 bytes
- CRS/runtime partition: EPSG:32639; 88,965 individually compressed/indexed 250 m microcells
- Included source-derived geometry: 182,110 road ways / 1,441,102 road segments and 189,446 building footprints
- License: Open Database License 1.0 (ODbL)
- Attribution: **© OpenStreetMap contributors**
- Use: game geometry, not navigation data

The QTOD derivative is distributed in the APK and this public repository under the ODbL database terms. It retains source IDs and complete accepted simple-building outer footprints. Relation outer rings are retained; relation inner holes are a declared QTOD v1 limitation. Exact HTTP response metadata, upstream/extract sizes and checksums, tool output, and import identity are recorded in `data/qatar/QATAR_OSM_IMPORT.json`, `data/qatar/OSM_SHA256SUMS`, and `data/qatar/compiled/qatar-osm-report.json`.

## Data not yet claimed in v0.3.0

Version 0.3.0 includes measured terrain-scale elevation and coarse ocean bathymetry, but does **not** claim survey-grade elevation, hydrodynamic bathymetry, final reclaimed-coast epoch accuracy, navigation suitability, or final hand-authored landmark geometry. Those remain subsequent content/validation milestones and require source timestamps, checksums, licensing, and explicit tests before corresponding release claims are made.
