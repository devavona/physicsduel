# Project State — physicsduel

## Concept

A reusable physics-game engine foundation, not a single game. Homage target:
Asteroids, Gravity Wars, Angry Birds, and DOS-era Catapult — games whose appeal
comes down to physics that *feels* real. The immediate goal is a solid, modular
`core` (plumbing only) that any specific mechanic can plug into later, so picking
one direction now doesn't foreclose the others.

## Key decisions

- **Engine: LibGDX + Box2D**, not Compose Canvas or a hand-rolled SurfaceView/OpenGL
  loop. Reasoning: Box2D's rigid-body solver (collision response, restitution,
  friction, gravity) is exactly the hard-to-get-right-by-hand quality bar these
  games are judged on. Compose Canvas would mean re-deriving that math by hand;
  LibGDX is the de facto standard for this genre on Android.
- **Build plumbing first, no specific mechanic yet.** Deliberately avoiding
  Asteroids/Gravity-Wars/etc.-specific assumptions leaking into `core`.
- **ECS via Ashley** is the planned composition pattern for Phase 4 (not yet
  wired up) — lets different mechanics share movement/render/collision systems
  and differ only in which components get attached. Flag: Ashley
  (`com.badlogicgames.ashley:ashley`) hasn't had a release since Feb 2021. Small,
  pure-JVM, functionally fine, but no upstream compatibility guarantees going
  forward — worth a final sanity check when we get to Phase 4, or handling it
  with a small hand-rolled ECS if it becomes a real problem.
- **`android` launcher module is plain Java, `core` is Kotlin.** AGP 9's built-in
  Kotlin support conflicts with the old `org.jetbrains.kotlin.android` plugin, and
  `core` (a plain Kotlin/JVM module, not an Android module at all) never touches
  that machinery. Keeping the launcher as a thin Java bootstrap sidesteps the
  conflict entirely rather than fighting AGP 9's new wiring for one small class.
- **minSdk = targetSdk = compileSdk = 36** (Android 16), matching the physical
  test device — same standing convention as the native-android-compose template.
  No core library desugaring needed since minSdk 36 already has full modern APIs.
- **Offline only.** No network calls, no accounts. Local persistence only
  (not yet built — Phase 6).
- **Debug-only signing.** Private/personal project, not distributed via Play
  Store, so no release keystore.

## Version pins (researched Sept 2026 — re-check if picking this back up much later)

- AGP 9.2.0, Gradle 9.4.1 (via wrapper, reused from the native-android-compose
  template), Kotlin 2.3.20, JDK 17
- LibGDX (`com.badlogicgames.gdx:gdx` / `gdx-backend-android` / `gdx-platform`) 1.14.2
- Box2D for later phases: `com.badlogicgames.gdx:gdx-box2d` / `gdx-box2d-platform`
  **1.14.2** (the classic, core-matched wrapper — NOT the independently-versioned
  `3.1.1-0` Box2D-v3 rewrite, which is over a year stale and shouldn't be mixed
  with the 1.14.2 natives in the same build)
- Ashley for later phases: `com.badlogicgames.ashley:ashley:1.7.4`

## Known risks to verify, not yet resolved

- **16 KB native page-size alignment.** There are open/ambiguous LibGDX GitHub
  issues (#7695, #7701) about `.so` files in gdx-platform/gdx-box2d-platform not
  being aligned for Android's 16 KB page size requirement on 64-bit. Not
  confirmed fixed in 1.14.2. Once there's a built APK, check this in Android
  Studio's APK Analyzer before assuming it's fine.
- Don't mix box2d 1.14.2 natives with the 3.1.1-0 box2d artifact in the same
  build — duplicate/mismatched native symbols.

## Fixed vs. the native-android-compose template

- That template's `.gitignore` uses `/build/` (leading slash — anchors to repo
  root only), which won't actually ignore a nested module's `build/` dir (e.g.
  `app/build/`). This project's `.gitignore` uses unanchored `build/` so it
  matches at any depth — worth backporting to the shared template.

## Phase plan (foundation-first, each phase is one on-device-testable build)

1. **Bare bones** — boots, builds, installs, launches to a blank cleared screen. ← **current phase, scaffolded, not yet built/tested on device**
2. Physics + render pipeline — Box2D world, fixed timestep, camera/viewport, one
   falling test shape.
3. Input pipeline — touch/drag applies force/impulse to the test shape.
4. ECS refactor (Ashley) — test shape becomes an entity built from components,
   driven by systems.
5. Scene/state manager — menu → play → pause → game-over as real stub screens.
6. Persistence layer — local save/load, corruption-safe reads.
7. HUD/UI overlay + audio hooks, wired into the scene manager.

Once Phase 7 is done, `core` is a proven, tested foundation with no specific game
built on it yet — any future mechanic becomes a module plugged into it.

## Manual steps outstanding (see chat for full checklist when each phase lands)

- GitHub repo: not yet created. Boo to create a private repo via github.com's
  web UI (no initial files) once Phase 1 is confirmed working; Claude will then
  give the exact `git remote add` / push commands.
- First Gradle sync + on-device run happens in Android Studio, per Boo's
  standard physical-device-over-USB workflow.
