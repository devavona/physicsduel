# physicsduel

A reusable physics-game foundation for Android, built with LibGDX + Box2D. The goal
isn't one game — it's a solid, modular `core` engine layer (physics world, ECS,
input, scene management, persistence) that any future physics-arcade mechanic can
plug into later: Asteroids-style inertial flight, Gravity Wars-style orbital
gravity, Angry Birds-style drag-launch + destruction, Catapult-style projectile
arcs, or anything else.

See `PROJECT_STATE.md` for the phase plan, decisions made so far, and current status.

## Structure

- `core/` — plain Kotlin/JVM module, no Android dependency. All game logic lives
  here so it stays reusable and platform-independent.
- `android/` — thin Android launcher module. Kept in plain Java on purpose (see the
  comment in `AndroidLauncher.java`) to avoid AGP 9's built-in-Kotlin plugin conflict.

## Building

Open the project root in Android Studio for the first Gradle sync, then run the
`android` module on a physical device (Settings > Developer Options > USB debugging).
