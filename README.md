# NAURU: THE COUNTRYSIDE

A playable, original Android open-world prototype of Nauru. This repository contains the actual game source—not a design-only mock-up.

## Download / install

The GitHub Actions workflow produces **`Nauru-The-Countryside-REALISTIC-v0.2.1.apk`**. Download the `Nauru-The-Countryside-Realistic-Android` artifact from the latest **Build Android APK** run, unzip it, and install the APK on an Android 8.0+ device. Android may ask you to allow installation from your browser/files app.

The APK is a debug-signed development build so it can be installed directly without a Play Store account.

## What is playable now (Phase 1)

- Approximately 1:1 world units (`1 unit = 1 metre`) with a ~6 km × 4 km procedural Nauru silhouette
- Continuous island, surrounding animated ocean, beaches, coastal belt, central plateau, mining terrain and Buada Lagoon
- Original 1024 px photographic grass, weathered airport asphalt and coral-limestone albedo materials with mipmapped filtering
- Physically-inspired material shading with multi-scale ground detail, tone mapping, specular response and material roughness
- Dynamic 1024 px sun-shadow map, atmospheric cloud/star sky and Fresnel ocean with sun glint, reef shallows and shoreline foam
- Higher-density terrain, curved palms, rounded tropical trees, facade windows/doors/porches, detailed airport and road shoulders
- Ring road, a ~2.15 km runway, airport buildings, harbour/industrial landmarks and procedural settlements
- Streamed high/low-detail coastal sectors and three graphics presets
- Third-person walking, running, jumping, camera orbit and pinch zoom with rounded character geometry
- Smooth original **Reef Runner** vehicle model with acceleration, braking/reverse, steering, off-road traction, suspension attitude, handbrake and collisions with the coast
- Enter/exit vehicle interaction
- Day/night lighting, atmospheric distance fog and live in-game time
- Island minimap, compass, district/location names, vehicle marker, speedometer and touch HUD
- Automatic checkpoint save/restore

All geometry, shaders, UI, vehicle/building designs and surface artwork in this prototype are original and created for this project. No proprietary game assets, Google imagery, Street View imagery, or GTA assets are included.

## Touch controls

### On foot
- **Left stick:** move
- **RUN:** hold to sprint
- **JUMP:** jump
- **CAR / ENTER:** locate or enter the nearby Reef Runner
- **Swipe open screen area:** orbit camera
- **Two-finger pinch:** camera distance

### Driving
- **Left stick:** steer
- **GO:** accelerate
- **BRAKE:** brake, then reverse
- **DRIFT:** handbrake
- **EXIT:** leave vehicle

Tap the graphics/FPS pill in the top-right to cycle **LOW → MEDIUM → HIGH**. Fresh installs start at **MEDIUM** on normal devices and automatically select **LOW** when Android reports a low-RAM device.

## Build

The app is dependency-free native Java + OpenGL ES 2.0 and uses Android Gradle Plugin 8.7.3.

```bash
gradle :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

GitHub CI installs Java 17 and Gradle 8.9, compiles the project, calculates a SHA-256 checksum, and publishes the installable APK as an artifact.

## Source layout

```text
app/src/main/java/ai/arena/nauru/
├── entities/   # player and vehicle simulation
├── game/       # game state, renderer and EGL surface
├── graphics/   # shaders, GPU mesh and procedural mesh builder
├── input/      # thread-safe touch input bridge
├── save/       # checkpoint persistence
├── ui/         # multitouch HUD and geographic minimap
└── world/      # island terrain, roads, airport, sectors and landmarks
```

## Scope

This is the first playable milestone of a long-term project. Terrain and landmarks are plausible procedural approximations designed to be replaceable with appropriately licensed survey/GIS data later; they are not claimed as survey-accurate navigation data.
