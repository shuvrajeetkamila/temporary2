# Qatar focus-physics and runtime validation report

## Scope

This document describes the bounded gameplay-physics foundation in the v0.4 preproduction APK. It is an honest prototype contract, not a claim of final vehicle simulation.

## Shared simulation contract

- Bullet runs fixed 1/60-second steps with a bounded five-step catch-up limit.
- Global positions are Qatar EPSG:32639 metres; Bullet remains near the origin and rebases in 500 m increments beyond a 2.2 km local threshold.
- The installed QDEM drives inland heightfields. Coastal retained-land cells use terrain-aligned triangular convex prisms so collision follows the same triangles that are rendered.
- Nearby OSM building collision is activation-budgeted and capped per detail cell; distant collision is evicted.
- A short-lived startup floor protects the focus body only while the first required terrain collider is activating.
- Recovery rejects non-finite transforms and extreme falls. Walking contact has a narrow terrain-following stabilization guard against numerical gaps along streamed coastal triangle seams.

## Current focus modes

| Mode | Bullet body | Current physical behaviour |
|---|---|---|
| Human | 78 kg capsule | Force-driven target speed, steering, jump impulse, upright angular constraint, ground contact |
| 4×4 | 1,850 kg box | Longitudinal drive force, speed-sensitive yaw, lateral tyre-force approximation, drag |
| Boat | 2,900 kg hull box | Water-gated buoyancy, vertical damping, forward/reverse thrust, water drag, wind contribution |
| Helicopter | 1,250 kg body | Collective around gravity-neutral hover, translational thrust, wind-relative damping, yaw |
| Aeroplane | 920 kg body | Forward thrust, airspeed-squared bounded lift, relative-air drag, yaw and lift control |

Mode changes preserve position, heading, and bounded momentum while replacing the focus collision body. Graphics are original procedural models and do not use proprietary game assets.

## Automated release-candidate gate

The Android emulator smoke workflow now preserves a grounded walking screenshot and then exercises all five focus modes. It requires, for every mode:

- a live finite Bullet state after mode activation and input;
- mode-specific altitude/support-clearance and speed bounds;
- at least 5.5 m of usable third-person camera separation;
- a non-empty screenshot;
- no fatal Android/native log;
- continued QTPK, OSM, environment, and thermal-governor readiness.

The bounds are centralized in `PhysicsHealth` and unit-tested, so a sky-only camera, terrain fall-through, non-finite body, or runaway speed cannot be silently published as a successful runtime result.

## Known limitations before production signing

- Modes are focus-body prototypes selected directly; enter/exit animations, persistent parked vehicles, seats, damage, fuel, and ownership do not yet exist.
- The 4×4 does not yet have ray-cast wheels, per-wheel suspension, differential, gearbox, tyre surfaces, or rollover recovery.
- Boat buoyancy is a centre-force model rather than multi-point displacement/hydrodynamics.
- Helicopter rotor dynamics, torque, translational lift, vortex-ring state, and autorotation remain simplified.
- Aeroplane pitch/roll attitude, control surfaces, stalls, landing gear, and runway-specific takeoff/landing logic remain simplified.
- Traffic, marine traffic, aviation traffic, pedestrians, transit, and activity simulation remain future bounded systems.
- Emulator checks prove deterministic startup and bounded short exercises only. Physical ARM-device driving/flying, long traversal, heat, charging, hot-ambient, and soak tests remain mandatory production-signing gates.
