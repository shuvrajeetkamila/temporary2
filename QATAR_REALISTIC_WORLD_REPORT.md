# Qatar Android v0.8 realistic ground-world milestone

## Purpose

v0.8 upgrades the visible ground-world foundation—roads, terrain, lighting, and buildings—without replacing the accepted v0.7 movement/frame-pacing implementation or expanding into the planned v0.9 character and vehicle overhaul. All imagery and geometry remain original procedural rendering of packaged open-data vectors and measured elevation; no satellite tiles, proprietary game assets, or network map service are used.

## Preserved systems

- 60 Hz fixed-step physics, interpolated poses, one Android/libGDX foreground frame pacer, direct horizontal camera tracking, and vertical-only terrain/contact damping.
- Existing WALK, CAR, BOAT, HELICOPTER, and AEROPLANE speeds and physics.
- Complete installed Qatar `.qpak` and `.qod` coverage, 250 m random-access OSM detail streaming, local street minimap, national street atlas, touch selection, and mode-safe teleport.
- Measured Mapzen/SRTM/ETOPO1-derived elevation, legal-land terrain collision, Gulf support, thermal profiles, lifecycle handling, attribution, package ID, and stable preproduction certificate.

## Road presentation

`QatarWorldRenderer` now builds each resident 250 m OSM cell using distinct road-system layers:

- Widths remain source-derived and hierarchy-aware; motorway/trunk/primary roads receive wider aggregate shoulders than secondary/tertiary roads.
- Endpoint coordinates are quantized to 0.25 m only for visual node matching. Intersections are filled with deterministic eight-sided pavement caps, with intersections prioritised before dangling ends.
- Junction generation is capped at 384 nodes per cell.
- Long strips are split at a target 32 m interval, capped at 16 subdivisions per source segment, and each generated vertex samples the unchanged measured terrain. If a very long OSM segment extends beyond currently resident national terrain, its visual height clamps to the nearest measured point in its resident detail cell instead of falling to an artificial 0 m plane. This removes vertical/floating ribbons without altering collision or source vectors.
- Multi-lane roads receive bounded dashed lane/centre separators; major roads receive edge lines.
- Runways receive dashed centre and solid edge markings; taxiways receive centre lines.
- Paths/tracks use a separate earth surface. Rails use a wider path bed and two reflective narrow rails.
- Marking work is capped at 2,400 strips per cell and 48 dashes per source segment.
- Asphalt was lifted from near-black to a sun-readable charcoal with restrained specular response.

## Terrain presentation

Five deterministic 128×128 procedural surface families are generated at startup:

1. dune/beach sand with low-frequency wind ripples,
2. limestone/gravel with broad tonal variation and aggregate grain,
3. sabkha/salt crust with sparse deterministic veins,
4. urban/industrial aggregate with subtle seams,
5. farm/mangrove vegetation variation.

The families map across all ten installed Qatar biome classes. They use seamless 100 m repeating UVs and trilinear mipmapped minification (`MipMapLinearLinear`) to reduce distant shimmer. Multi-directional tile-periodic low-frequency variation replaces axis-aligned texture bands. Their harmonised Qatar palette reduces hard colour steps while keeping urban, limestone, dune, sabkha, tidal, farm, industrial, rock, and beach identities. Terrain vertices and heights still come from the measured QDEM and the collision path is unchanged.

## Building and lighting presentation

- A deterministic source-ID/archetype rule assigns seven façade families: limestone, sandstone, white plaster, glass tower, civic, industrial, and farm.
- Façades are brighter and retain source OSM outer footprints and heights.
- Two generated mipmapped façade patterns add repeated floor/bay-scale windows and mullions to stone/civic/industrial and glass families, including complex footprints that intentionally skip extra trim geometry.
- Glass has stronger but bounded highlights plus a very low emissive floor so windows remain legible on faces turned away from the sun; tower window coverage is increased while retaining an eight-floor display cap for ordinary buildings and a twelve-floor cap for towers.
- Bounded pale roofline bands and corner trim break up otherwise blank extruded walls without changing footprint silhouettes.
- A calibrated opposite-direction sky fill complements reduced ambient/sun intensity, preserving façade colour and ground texture without clipping sun-facing terrain. Clear-weather distance fog was removed after screenshot inspection showed it flattening nearby regional façade palettes into the sky colour.
- Source footprints up to 16 points receive low parapets and façade trim; more complex footprints retain their exact silhouette without unbounded decoration.
- Eligible roofs receive one or two small deterministic equipment housings.
- Eligible mosque footprints receive bounded generated domes and minarets. No landmark mesh is copied.
- Streaming, source geometry, collision registration, and country coverage remain unchanged.

## Mobile bounds

| Work item | Bound |
|---|---:|
| Road junction caps per 250 m cell | 384 |
| Junction radial divisions | 8 |
| Road terrain subdivisions per source segment | 16 |
| Target terrain-following strip interval | 32 m |
| Marking strips per cell | 2,400 |
| Dashes per source segment | 48 |
| Procedural terrain textures | 5 × 128×128 RGBA with mipmaps |
| Procedural façade patterns | 2 × 64×64 RGBA with mipmaps |
| Parapet/trim footprint size | at most 16 points |
| Ordinary/tower displayed window floors | 8 / 12 |

The largest packaged OSM cell contains 1,011 road segments and the largest contains 368 buildings. The exhaustive source-data geometry audit reports a worst road-material stream of 11,958 vertices including the full junction allowance, a 14,400-vertex marking cap, and maxima of 12,906 roof, 13,008 façade-trim, 6,212 window, 1,680 equipment, and 768 mosque-detail vertices. Every audited stream remains below the conservative 16,384-vertex mobile batching threshold.

## Validation contract

The clean GitHub build must:

1. verify the national, OSM, DEM, terrain, and atlas checksums;
2. pass all unit tests, including deterministic façade and bounded-road rules;
3. compile and package version code 8 / `0.8.0-realistic-world-preproduction`;
4. verify all three Android ABIs, random-access stored world packs, package ID, and signing-certificate continuity;
5. install and reinstall on the Android emulator;
6. emit `QatarVisual: V08_PIPELINE` with five mipmapped surface families and the bounded junction cap;
7. retain local/national street-map, teleport, lifecycle, thermal, and all-five-mode runtime gates;
8. retain `extraRenderGate=no smooth=yes` cadence evidence in every movement mode; and
9. produce non-flat gameplay screenshots for visual inspection.

## Final validated artifact

| Item | Result |
|---|---|
| Source commit compiled | `35fd496546faa5419828bcb44977f5aa628a04f7` |
| GitHub build run | `38794894452` — passed |
| GitHub Android runtime run | `38795289999` — passed |
| APK | `artifacts/Qatar-The-Peninsula-REALISTIC-WORLD-v0.8.0.apk` |
| APK bytes | `38,151,103` |
| APK SHA-256 | `17da8a2ee366d4f13b02189306e5040045c94a24109ee0ae33201ea50ce0e6ba` |
| Package/version | `ai.arena.qatar.peninsula`, code `8`, `0.8.0-realistic-world-preproduction` |
| Android SDK / ABIs | minimum 26, target 35; `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Signing continuity | v2 signature; certificate SHA-256 `ccce47e341dde37e9cb94ab7cb9298ee39df967483ca6c618491cd43a7ae390c` |
| Visual geometry audit | passed |
| Unit/assemble/package gates | passed |

The final emulator run installed and then reinstalled the APK, retained the same process through pause/resume, entered Battery Saver’s WARM profile, opened/selected/closed the national map, teleported, exercised all five modes, found no app-scoped fatal log, and emitted:

```text
QatarVisual: V08_PIPELINE mipmappedSurfaces=5 mipmappedFacades=2 roadSegments=81 joinedCaps=78 markingStrips=0 roadHeightFallback=residentClamp buildings=4 roofDetails=16 boundedJointCap=384
```

The first streamed residential/service-road cell legitimately had no eligible lane-marking strips; the complete-source audit and renderer tests cover their bounded generation on higher road classes.

Final settled cadence samples retained the accepted v0.7 single-pacer contract:

| Mode | Average frame interval | Gate |
|---|---:|---|
| WALK | 31.16 ms | `extraRenderGate=no smooth=yes` |
| CAR | 34.92 ms | `extraRenderGate=no smooth=yes` |
| BOAT | 35.81 ms | `extraRenderGate=no smooth=yes` |
| HELICOPTER | 32.13 ms | `extraRenderGate=no smooth=yes` |
| AEROPLANE | 30.13 ms | `extraRenderGate=no smooth=yes` |

All nine captured scenes passed the non-flat viewport check. Final gameplay recorded `luminance_sd=35.56` and 49 colour bins. Offline map checks recorded `edges=2,820`, density `0.2049` for the local minimap and `edges=18,177`, density `0.2459` for the expanded atlas. The screenshots were inspected during implementation; lighting was reduced after an overexposed intermediate run, distance fog was removed after it flattened façade colours, road heights were clamped after long-segment artifacts were observed, and façade bay contrast was enlarged for distance legibility.

Runtime run `38793852904` is not an application failure: its runner stopped in `sdkmanager` before APK installation. The ordinary marker retry produced the passing authoritative run above.

## Honest remaining limits

This is a bounded mobile procedural foundation, not photorealistic final architecture. OSM footprints/heights remain the source silhouette, relation inner holes are still absent in QTOD v1, generic façade patterns do not replace future original hero-landmark models, and emulator screenshots cannot prove subjective ARM-phone appearance, heat, or compositor behaviour. The user’s physical confirmation establishes v0.7 motion smoothness; v0.8 still requires physical-device confirmation for its added visual load and thermal comfort. Character and vehicle model work remains deliberately deferred to v0.9.
