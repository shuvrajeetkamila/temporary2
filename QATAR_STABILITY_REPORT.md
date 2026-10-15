# Qatar Android v0.5 stability milestone

## Purpose

v0.5 is a preproduction installation, lifecycle, and power-management pass over the existing full-country offline foundation. It does not claim production signing or final gameplay.

## Repeatable preproduction installation

Clean GitHub runners previously generated unrelated Android debug certificates. Android correctly refuses to install an APK signed by one certificate over the same package signed by another, which can surface as `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

Starting with version code 5, debug/preproduction APKs use the fixed public certificate documented under `app/signing/README.md`. CI verifies both the keystore file hash and final APK signing-certificate digest. This certificate is deliberately public and must never be interpreted as production trust.

Migration rule:

1. Uninstall version code 4 or older once.
2. Install v0.5.
3. Later preproduction APKs signed by this certificate can update v0.5 without another uninstall.

The emulator gate installs and then reinstalls the exact candidate with `adb install -r` before launch.

## Power and thermal changes

- Android Battery Saver maps to the WARM profile: 20 fps, shorter view distance, fewer resident OSM cells, and fewer building-collision cells.
- Android 10+ thermal status still independently selects NORMAL, WARM, or HOT, with the most conservative effective state winning.
- Continuous libGDX rendering is suspended when the activity is backgrounded and explicitly restored/requested on resume.
- Full-screen multisample anti-aliasing is disabled. This trades some edge smoothing for lower GPU bandwidth and heat.

## Lifecycle gate

After exercising human, 4×4, boat, helicopter, and aeroplane modes, Android CI now:

- sends the app to the Home screen;
- waits while it is backgrounded;
- reopens the single-task activity;
- requires the process ID to remain unchanged;
- requires PAUSED and RESUMED callbacks in the application-scoped log;
- captures and visually validates the resumed scene;
- requires all readiness, physics, camera, and fatal-log gates to remain passing.

## Signing boundary

Production signing remains excluded. A production key must be private, stored outside this repository, backed up securely, and used only after representative ARM-device thermal, battery, lifecycle, traversal, and soak gates pass.
