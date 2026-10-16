# Qatar v0.9.1 Streaming, Boat Placement & Palette Corrective Report

Date: 2026-10-16  
Status: **PASSED — GitHub-built APK and Android 29 x86_64 runtime certified**

## Release identity

- Package: `ai.arena.qatar.peninsula`
- Version code: `11`
- Version name: `0.9.1-streaming-boat-palette-fix`
- APK: `artifacts/Qatar-The-Peninsula-CORRECTIVE-v0.9.1.apk`
- Bytes: `38,195,643`
- SHA-256: `e11230ec9da473ea332bdd4724386e209bf541f532b9aecb47553f89f0cf8c8e`
- Corrective source commit: `6a9551820e6e6e01dd772a03696b571df36005ab`
- APK publication commit: `53aee83`
- GitHub build run: `38910393950`
- Android certification request: `f3047a6`
- GitHub runtime run: `38910741046`
- Runtime evidence commit: `e4f8b7b`

## Reported cell diagnosis

The report shown on screen was `OSM detail cell 378,362 failed: null`. The old error surface printed only `Throwable.getMessage()`, so a message-less exception lost its class and stack context. The old text therefore cannot establish retrospectively whether the original throwable was a null pointer, buffer exception, or another message-less runtime failure.

The packaged cell itself is valid and remains installed:

| Property | Certified value |
|---|---:|
| Cell | `378,362` |
| Packed bytes | `461` |
| Raw bytes | `1,162` |
| CRC-32 | `d7a82d8f` (valid) |
| Roads | `31` (`8` class 5, `23` class 6) |
| Buildings | `2` |
| Footprint vertices | `18` total, `12` maximum |
| Unread payload tail | `0` bytes |

The corrective audit independently inflates, CRC-checks, fully parses, and verifies this exact fixture. The Android workflow then selected local map coordinate approximately `(94601, 90710)`, teleported to the nearest clear roadside in the same target cell, and logged:

```text
OSM_REGRESSION_CELL_READY cell=378,362 roads=31 buildings=2 activation=atomic
```

No reduced-detail fallback, skipped detail cell, fatal exception, native signal, or process restart occurred.

## Corrective implementation

### Recoverable and diagnostic OSM detail streaming

- Each full OSM detail-cell model now uses a fresh `ModelBuilder`. An exception can no longer poison the reusable builder state used by later cells.
- A failed ornamental/full detail build gets one bounded reduced-geometry path containing roads and simple building volumes.
- Cell publication is atomic: model/model-instance construction and physics registration complete before renderer maps expose the cell.
- Decode or activation failures use bounded retry backoff: two short retries, then a 60-second quarantine instead of a per-frame failure loop.
- Optional street/building detail failure is no longer promoted to a permanent national-terrain fatal state; the world remains playable.
- Diagnostics now include throwable class and an explicit `(no message)` marker rather than displaying `null`.
- Required national terrain failures remain fatal and now also preserve throwable class plus stack logging.

### BOAT mode at the current location

- Automatic mode cycling uses a tested `ModeTransitionPolicy`.
- `CAR -> BOAT` replaces the local physics/render body at the same global X/Z.
- It does not call the water destination resolver, does not reset either streamer, and does not perform a hidden teleport.
- Explicit user-confirmed map teleport remains mode-aware and may still choose navigable water for BOAT.
- Land-local boats retain normal terrain support and the existing fixed-step stability guard; water-local boats retain buoyancy.

The certified transition was:

```text
SAFE_TRANSITION from=CAR to=BOAT placement=current-screen locationDelta=0.0 water=no streamReset=no movementPacing=preserved
```

The following certified BOAT state was stable on land:

```text
STATE mode=BOAT heightAboveGround=0.6899986 supportClearance=0.6899986 water=no speed=0.67989385 cameraDistance=10.697555 stable=yes
```

### Requested colours

- WALK torso, shoulders, pelvis, articulated arms, and legs now use white cloth; skin, hair, and shoes remain distinct.
- All four transport rigs use a desaturated dark/dull-red accent with neutral grey primary/secondary surfaces and darker neutral glass.
- Navigation, brake, and safety lamps retain functional lamp colours.
- Runtime screenshots for WALK, 4×4, BOAT, HELICOPTER, and AEROPLANE were captured and passed the non-flat-scene validator.

## Build and package certification

GitHub Actions run `38910393950` passed:

- deterministic national pack and all source checksums;
- terrain, road-atlas, visual-geometry, coastal, and v0.9.1 corrective audits;
- exact cell `378,362` JUnit regression;
- BOAT current-screen policy and no-stream-reset JUnit regression;
- physics body replacement X/Z-preservation regression;
- all existing unit tests;
- Java/Android compilation and APK assembly;
- ZIP integrity, manifest, ABI, Bullet-native, signing, and uncompressed random-access pack checks;
- package/version assertion for code `11` and `0.9.1-streaming-boat-palette-fix`;
- absence of Android `INTERNET` permission.

The build record is `qa/qatar-current/build-record.json`; the machine-readable corrective audit is `qa/qatar-current/corrective-release-audit.json`.

## Android runtime certification

GitHub Actions run `38910741046` installed and reinstalled the exact published APK on Android 29 x86_64, then passed:

- launch and same-process pause/resume;
- complete-country map open/close, street atlas, ×16 zoom, pan, fixed destination pin, and explicit confirmation;
- direct cell `378,362` activation with no detail fallback or skip;
- all five articulated focus rigs;
- `CAR -> BOAT` at the current screen location with `locationDelta=0.0` and `streamReset=no`;
- BOAT stable on land, demonstrating that local appearance does not require a water teleport;
- aeroplane runway placement and aeroplane-to-WALK land return;
- stable WALK, CAR, BOAT, HELICOPTER, and AEROPLANE runtime-state samples;
- smooth cadence markers in all five modes with `extraRenderGate=no`;
- Battery Saver WARM profile, lifecycle callbacks, and process preservation;
- all 14 screenshots above the size threshold and passing scene checks;
- no app-scoped fatal exception, native signal, detail fallback/skip, or process death.

Final process evidence reported `VmRSS 155,328 kB`, `23` threads, Java heap `25,396 kB`, and native heap `33,820 kB`.

## Preserved contracts

This corrective update does not alter or reduce:

- the physically accepted v0.7 60 Hz fixed-step interpolation and single frame-pacer behavior;
- movement speeds;
- complete-Qatar 5,665-chunk terrain/safety coverage;
- the 88,965-cell offline OSM detail pack, 1,441,102 road segments, or 189,446 building footprints;
- v0.8.1 complete-country precision map controls and explicit teleport confirmation;
- measured terrain, exact coastal masks, dry infrastructure policy, landmarks, bounded activity, or thermal profiles.

## Honest limit

This is a certified corrective preproduction APK, not a claim of a photorealistic AAA final product. The emulator proves deterministic packaging, the reported-cell path, interaction contracts, physics state, lifecycle, cadence, and crash-free execution. Subjective touch feel, sustained thermal behavior, and vendor-specific rendering still merit testing on multiple physical ARM devices. The APK uses the repository's public preproduction certificate, not a private production-store key.
