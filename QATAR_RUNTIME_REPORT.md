# Qatar Android v0.4 runtime evidence

## Release-candidate identity

- APK: `artifacts/Qatar-The-Peninsula-QATAR-ENVIRONMENTS-v0.4.0.apk`
- Package: `ai.arena.qatar.peninsula`
- Version: code 4, `0.4.0-qatar-environments-preproduction`
- APK bytes: 37,912,335
- SHA-256: `7ed0ab3bce921a57247d627a03222e73aee26bf73f4b69ba59dc9f1f8276a544`
- APK source commit: `96104c47a223de054e1885f3abc29eaff6312e97`
- Passing clean build run: `38606105934`
- Passing Android runtime run: `38606698589`

## Android emulator result

The final x86_64 Android 29 emulator run installed and cold-started the same published APK, retained a live process, loaded measured terrain and OSM detail, initialized the authored Qatar environment and NORMAL thermal profile, and found no fatal log from the application process.

The real Education City startup resolved to UTM position `78079.345, 101953.347` on a clear source-derived OSM roadside at measured terrain elevation 10.449 m.

All five short focus exercises remained finite and passed mode-specific support, speed, and camera-separation bounds:

| Mode | Height/support above reference | Speed | Camera distance | Result |
|---|---:|---:|---:|---|
| Human | 0.960 m | ~0.000 m/s | 8.382 m | stable |
| 4×4 | 0.760 m | 0.095 m/s | 12.603 m | stable |
| Boat (land activation test) | 0.690 m | 0.098 m/s | 12.920 m | stable |
| Helicopter | 49.577 m | 2.667 m/s | 28.701 m | stable |
| Aeroplane | 20.425 m | ~0.000 m/s at capture | 39.790 m | stable |

The boat check validates safe activation on land, not hydrodynamic behaviour; water-specific physical-device testing remains required. The aeroplane check validates bounded airborne activation and recovery, not a complete takeoff/landing cycle.

## Screenshot visibility gate

Five 1280×720 screenshots were retained under `qa/qatar-runtime/`. A deterministic, standard-library PNG decoder sampled the central gameplay viewport while excluding HUD/control regions. Every mode exceeded the flat-frame rejection thresholds:

| Screenshot | Luminance standard deviation | Quantized colour bins |
|---|---:|---:|
| Human | 53.28 | 71 |
| 4×4 | 56.42 | 35 |
| Boat | 48.99 | 41 |
| Helicopter | 53.43 | 61 |
| Aeroplane | 61.60 | 62 |

This gate specifically rejects the previously observed sky-only and façade-filled frames; it does not claim artistic quality or perceptual realism.

## Startup and memory measurements

- Android ActivityManager reported a cold activity start `TotalTime` of 2,911 ms and `WaitTime` of 2,927 ms on the CI emulator.
- After the extended five-mode exercise, `dumpsys meminfo` reported total PSS 73,806 KB (Java heap 23,356 KB, native heap 30,440 KB, code 10,064 KB, private other 2,628 KB, system attribution 7,274 KB) and swap PSS 3,776 KB.
- `/proc/<pid>/status` reported 145,148 KB RSS and a 167,236 KB peak RSS. PSS is the more useful cross-process memory figure here.

These are emulator observations, not promises for ARM phones. Android `gfxinfo` saw only four Android UI frames because libGDX renders through its own OpenGL loop; its jank percentages are therefore **not** a valid gameplay-FPS measurement and are intentionally not used as a performance claim.

## Remaining production gate

Production signing must remain blocked until representative physical ARM devices complete cold/warm launch, walking/driving/flying/boating traversal, lifecycle interruption, low-memory recovery, charging, hot-ambient, thermal-throttling, battery-drain, and long soak runs. The current build is emulator-proven preproduction, not production-certified or AAA-complete.
