# Qatar v0.9.0 Character, Vehicle, Coastal & World Activity Report

## Release identity

- Package: `ai.arena.qatar.peninsula`
- Version code: `10`
- Version name: `0.9.0-character-vehicle-coastal-activity`
- APK: `artifacts/Qatar-The-Peninsula-CHARACTER-WORLD-v0.9.0.apk`
- APK bytes: `38,191,995`
- SHA-256: `ccbecfb5ed7c6ff207c2d4d10c5e85b937c10430d16797d0d19557e182dae90f`
- APK source commit: `81e37789626d99baee2a97ab9d26e1c5173df162`
- GitHub build run: `38902242577`
- Android runtime run: `38902600978`
- Final runtime evidence commit: `bc08e7b`

## Scope delivered

### 1. Articulated player

The player is an original procedural character assembled from smoothly subdivided capsules and ellipsoids. It has improved human proportions, separate pelvis, torso, shoulders, neck, head, hair, face, arms, hands, legs, and shoes. Walking drives counter-swinging arms and legs, attached hand/foot trajectories, and bounded torso motion. A close camera and thermal-scalable contact shadow improve grounding without changing movement speed or fixed-step pacing.

### 2. Original 4×4

The 4×4 now has a rounded multi-surface body, glazed cabin, visible seating and driver, roof and rails, mirrors, bumpers, four animated wheels and hubs, suspension struts, spare wheel, front/rear lamps, and rear plate. Steering and wheel rotation use live physics controls and velocity. Optional small details and contact shadows downshift under the HOT thermal profile.

### 3. Original boat

The boat has a curved hull, colour stripe, deck, glazed cabin, roof, rails, outboard motor, animated propeller and rudder, navigation lamps, pilot, and a speed-scaled translucent wake. Switching to BOAT resolves packaged navigable Gulf water and now keeps the hull at least 14 metres inside its selected water-mask cell rather than straddling a block edge.

### 4. Original helicopter

The helicopter includes a smoothly subdivided fuselage and cockpit, tapered tail, fin, doors, dual pilots, metal skids, mast, animated main and tail rotors, beacon, navigation lights, and landing light. Rotor-clearance placement and a mode-specific filtered camera are retained.

### 5. Original aeroplane

The aeroplane includes a smooth fuselage, rounded wings and elevators, fin, glazed cockpit and pilot, engine cowl, spinner, animated propeller, main and nose landing gear, wheels, and red/green wingtip lamps. Switching to AEROPLANE resolves a packaged 60-metre-wide OSM runway at Hamad International Airport and boards on corrected runway support.

### 6. Safe five-mode transitions

Ground, water, and runway transitions use installed offline national/QTOD data. Runtime certification proved WALK→CAR roadside placement, CAR→BOAT Gulf placement, BOAT→HELICOPTER rotor clearance, HELICOPTER→AEROPLANE runway placement, and AEROPLANE→WALK return to clear land. The accepted v0.7 60 Hz fixed-step interpolation, single foreground frame pacer, and movement speeds remain in place.

### 7. Roads and transport

Road flags and surfaces now survive QTOD decode. The renderer adds corrected permanent-transport elevations, reclaimed support pads, bridge clearance and bounded barriers, tunnel portals, joined junctions, shoulders/curbs, lane and edge markings, streetlights, direction signs, runway/taxiway markings, twin rail presentation, and original port geometry.

### 8. Buildings and landmarks

All streamed buildings use shared coastal foundation heights for rendering and Bullet collision. Existing façade families, windows, trim, roofs, equipment, domes, and minarets remain. Five original bounded hero model families provide Legtaifiya Metro, West Bay/Lusail skyline, Hamad International Airport, Hamad Port, and Al Zubarah silhouettes with distance and thermal caps.

### 9. Terrain and coast

Coastal water is generated only for water cells in the packaged 16×16 national land mask rather than as a full coast-chunk plane. Dry land is therefore not visually flooded. Beaches, inset deep water, translucent shallow-water bands, deterministic waves, island components, dry building foundations, and permanent-transport pads distinguish land, reclamation, shallows, and Gulf water. Player ground sampling recognises corrected elevated transport surfaces.

### 10. Bounded world activity

A deterministic non-colliding ambience layer derives routes only from resident QTOD cells. NORMAL/WARM/HOT caps govern moving cars, pedestrians, parked cars, boats, aircraft, and hero landmarks. Route refresh is throttled, resident collections are capped, and activity is disabled or reduced under heat without changing player movement.

## Deterministic coastal audit

`tools/audit_qatar_coastal_features.py` validates the packaged QTPK, QTOD, ADM0 components, runtime support rules, and a dedicated 2 km Legtaifiya Metro neighbourhood. CI executes its `--verify` mode before Android compilation.

| Audit family | Packaged features | Outside/touching water mask | Below dry level | Runtime correction | Unresolved |
|---|---:|---:|---:|---:|---:|
| Buildings | 189,446 | 297 centres; 424 footprints | 1,819 | 2,146 foundations | 0 |
| Roads | 1,441,102 | 10,467 sampled; 119 bridges | 39,631 | 30,715 pads | 0 |
| Runways | 147 | 0 | 22 | 22 pads | 0 |
| Legtaifiya 2 km | 957 buildings; 15,442 roads | 48 buildings; 672 roads | included above | 744 features | 0 |

Legtaifiya Metro Station is on retained land at packaged terrain elevation `7.582066 m`. The audit retains mainland plus six ADM0 island components. The runtime policy uses `0.55 m` design high water, `0.75 m` permanent dry-feature support, and `4.5 m` bridge clearance. These are game-rendering/collision rules, not cadastral, survey, aviation, or marine-navigation claims.

## Build and Android validation

GitHub build run `38902242577` passed:

- deterministic country, DEM, road-atlas, visual-geometry, and coastal audits;
- Java/JUnit compilation and tests, including Bullet transitions and runway resolution;
- fresh multi-ABI APK assembly;
- manifest, package/version, ZIP integrity, random-access asset storage, and no-INTERNET checks;
- reproducible preproduction signing-certificate verification;
- APK publication and SHA-256 generation.

Android emulator run `38902600978` passed:

- install and reinstall;
- launch, background/resume with the same live process, and no fatal app log;
- all five articulated rigs and all five stable physics states;
- smooth cadence markers for WALK, CAR, BOAT, HELICOPTER, and AEROPLANE with no second render gate;
- boat-to-water, aeroplane-to-runway, and aircraft-to-land transitions;
- bounded world-activity and original-landmark markers;
- Battery Saver WARM thermal downshift;
- minimap, complete-Qatar map, ×16 precision vectors, pan, pin, confirmation, and safe teleport;
- screenshot scene, street-map, and precision-map checks.

## Honest status

v0.9.0 is a playable major preproduction update, not a claim that a small procedural Android project has reached the prompt book's multi-year photorealistic AAA end state. Models, materials, activity, and landmarks are original and mobile-bounded rather than proprietary replicas. The emulator proves deterministic correctness and execution; subjective graphics, touch feel, sustained heat, and compositor behaviour still need testing on multiple physical ARM phones. Production signing, audio, full Arabic shaping/localisation, denser authored landmark detail, and broad device profiling remain future work.
