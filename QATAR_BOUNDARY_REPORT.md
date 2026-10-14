# Qatar boundary and packaged-coverage report — v0.4.0

National figures come from `data/qatar/compiled/qatar-world-report.json`; source-detail figures come from `data/qatar/compiled/qatar-osm-report.json`.

## Coordinate and scale contract

- Master CRS: WGS84 / UTM zone 39N (`EPSG:32639`)
- Runtime unit: 1 metre
- No global distance compression
- National grid origin: 466,000 m E, 2,698,000 m N
- Pack extent: 110 km east–west × 206 km south–north, including an 8 km gameplay-water buffer
- Safety/terrain cell: 2,000 m
- Coast mask resolution: 125 m
- OSM detail microcell: 250 m
- Measured terrain grid: 250 m Mapzen/SRTM/ETOPO1 samples, elevation-aware rendering and bounded collision

## Installed national coverage

- Total indexed national chunks: **5,665**
- Full-land chunks: **2,671**
- Mixed coast chunks: **450**
- Water-buffer chunks: **2,544**
- ADM0 polygon components represented: **7**
- Municipality records represented: **8 of 8**
- Public regional hubs: **25**
- Indexed OSM microcells containing source geometry: **88,965**
- OSM road ways / represented segments: **182,110 / 1,441,102**
- OSM building footprints: **189,446**

Municipality land-chunk counts:

- Doha: 58
- Al Rayyan: 643
- Al Wakrah: 692
- Al Daayen: 83
- Umm Salal: 88
- Al Khor and Al Thakhira: 437
- Al Shamal: 251
- Al Shahaniya: 869

## Runtime residency contract

Every compressed country and OSM block is physically installed in `qatar-national.qpak` and `qatar-osm-current.qod`. Startup reads each compact index, not every block payload. Single low-priority workers perform positional reads and checksum-validated decompression only for nearby and predictive sets. OpenGL/Bullet activation remains on the render thread.

Bounded residency:

- National terrain: mode-dependent one-to-three 2 km-cell radius plus a forward lead set
- OSM detail: two 250 m cells while walking/aeroplane mode and three while car/boat/helicopter mode, plus a smaller lead disc
- Eviction: four-to-six second hysteresis, with an emergency hard cap of 64 resident national chunks and 64 resident OSM cells
- GPU activation budget: at most three terrain chunks and two OSM microcells per rendered frame
- Physics: OSM visuals are further reduced to at most six nearby collision cells and 160 building colliders per cell
- Frame budget: 30 Hz foreground rendering/simulation submission to prevent high-refresh phones from doing duplicate 60/90/120 Hz work

No network permission is present in the Android manifest.

## Integrity

- QTPK size: 1,410,075 bytes
- QTPK SHA-256: `014c8235725b9d2c29fc675e4db5529e01b782b12aa3312e6ccab0a185d1af1a`
- QDEM size: 727,710 bytes
- QDEM SHA-256: `23f96979dfcb77cc81c11c0f65c3047a463c7cef0be8d22fc1b1be22191d54eb`
- QTOD size: 27,551,391 bytes
- QTOD SHA-256: `c48a62e1e8f1498bb64bde2864e4ae7894380f5185bc0a3a39ebf47012d9ba00`
- Every chunk/microcell has declared compressed/raw lengths and CRC-32.
- Runtime and tests require matching EPSG/origin/extents, all eight municipalities, 25 hubs, seven ADM0 components, minimum source-feature coverage, index total agreement, and successful representative block decompression.

## Honest limitations

This is an incremental Android prototype, not the finished AAA-scale release. The seven-component gbOpen boundary is a coarse redistributable country representation and still needs a reclamation/island audit. The OSM layer is real source geometry but is not navigation data; relation inner holes are not yet rendered. Survey-grade terrain, hydrodynamic bathymetry, detailed landmark architecture, Arabic shaping, mature vehicle dynamics, traffic/activity, and wider physical-device profiling remain mandatory later milestones. These are validation/roadmap facts, not permission to reduce final country scope.
