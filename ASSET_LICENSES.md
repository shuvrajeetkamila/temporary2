# Qatar application asset licenses

## Original project assets

All source code, generated geometry, procedural building/vehicle blockout meshes, user-interface shapes, launcher vector art, materials, and deterministic terrain parameters created for this repository's Qatar build are original project work and are covered by the repository `LICENSE` unless a file states otherwise.

No GTA, Google Earth, Google Maps, Street View, commercial game, proprietary photogrammetry, airline livery, copyrighted music, or copied commercial vehicle model is included.

## libGDX and gdx-bullet

- Project: libGDX
- Components: `gdx`, `gdx-backend-android`, `gdx-bullet`, platform native libraries
- Version: 1.13.1
- Source: <https://github.com/libgdx/libgdx>
- License: Apache License 2.0
- Use: Android lifecycle, OpenGL ES rendering, input, utility classes, and Bullet integration.

## Bullet Physics

- Project: Bullet Physics SDK, shipped through libGDX's gdx-bullet bindings
- Source: <https://github.com/bulletphysics/bullet3>
- License: zlib license
- Use: fixed-step rigid-body world and static streamed collision foundation.

## Built-in libGDX bitmap font

The early diagnostic HUD uses libGDX's built-in bitmap font distributed with libGDX under its project licensing. It is not the final Arabic UI font. Final Arabic shaping/BiDi/font work remains a later localization milestone.

## Geographic data

Geographic datasets and their derivative terms are documented separately in `DATA_SOURCES.md`. Required attribution remains visible in the application HUD and will be expanded into the final Credits screen.
