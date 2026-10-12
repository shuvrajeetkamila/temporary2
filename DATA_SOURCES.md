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
- ADM1 SHA-256: `8021dc98ea5a92564b87a69633bca4d17374226fabe40e4de64721403e`

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
- Terrain: biome-specific deterministic microheight only. It is not yet a DEM.

## Data not yet claimed in v0.1.0

Version 0.1.0 does **not** claim complete OpenStreetMap roads/building footprints, measured DEM terrain, bathymetry, or final reclaimed-coast epoch accuracy. Those are subsequent compiler milestones and must be added with source timestamps, checksums, and licenses before corresponding release claims are made.
