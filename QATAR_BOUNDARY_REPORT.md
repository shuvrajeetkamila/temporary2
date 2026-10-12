# Qatar boundary and packaged-coverage report — v0.1.0

Generated from `data/qatar/compiled/qatar-world-report.json` by `tools/compile_qatar_world.py`.

## Coordinate and scale contract

- Master CRS: WGS84 / UTM zone 39N (`EPSG:32639`)
- Runtime unit: 1 metre
- No global distance compression
- National grid origin: 466,000 m E, 2,698,000 m N
- Pack extent: 110 km east–west × 206 km south–north, including an 8 km gameplay-water buffer
- Streaming cell: 2,000 m
- Coast mask resolution: 125 m

## Installed national coverage

- Total indexed chunks: **5,665**
- Full-land chunks: **2,671**
- Mixed coast chunks: **450**
- Water-buffer chunks: **2,544**
- ADM0 polygon components represented: **7**
- Municipality records represented: **8 of 8**
- Public regional hubs: **25**

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

The complete index and every compressed chunk block are installed in `qatar-national.qpak`. Startup reads only the 64-byte header, compact metadata, and 32-byte-per-chunk index. A bounded worker performs positional reads and checksum-validated decompression only for the nearby/predictive set.

Default high-detail residency:

- Walking: current chunk and immediate disc (roughly a 3×3 neighborhood)
- Car/boat/helicopter: bounded two-chunk radius plus a forward lead set
- Aeroplane: bounded three-chunk radius plus a forward lead set
- Eviction: 4–6 second hysteresis after leaving the required set
- GPU activation budget: at most three decoded chunks per rendered frame

No network permission is present in the Android manifest.

## Integrity

- QTPK size: 1,170,493 bytes
- QTPK SHA-256: `6aa220910d0214b3d38fb5c7764534c5242b1ddab24ae6ffddb377d95a22a6b7`
- Every chunk has declared compressed/raw lengths and CRC-32.
- Startup tests require eight municipalities, 25 hubs, seven ADM0 components, minimum land/coast coverage, exact deterministic SHA-256, and successful representative chunk decompression.

## Honest limitations

This is the first newly built national foundation APK—not the finished AAA-scale release. The seven-component gbOpen geometry is a coarse redistributable country representation and needs a later authoritative/current reclamation and island audit. OSM roads/building footprints, measured DEM, detailed airports/ports, landmarks, Arabic shaping, final vehicle physics, traffic, and environmental polish remain mandatory later milestones. These limitations are tests-and-roadmap facts, not permission to reduce final country scope.
