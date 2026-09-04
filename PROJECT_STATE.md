# Project State — physicsduel

Repo: https://github.com/devavona/physicsduel (private)
Local: C:\Apps\dev\physicsduel

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
  games are judged on.
- **Build plumbing first, no specific mechanic yet.** Deliberately avoiding
  Asteroids/Gravity-Wars/etc.-specific assumptions leaking into `core`.
- **ECS via Ashley** is the planned composition pattern for Phase 4 (not yet
  wired up). Flag: Ashley (`com.badlogicgames.ashley:ashley`) hasn't had a
  release since Feb 2021 — small, pure-JVM, functionally fine, but no upstream
  compatibility guarantees. Worth a final sanity check at Phase 4.
- **`android` launcher module is plain Java, `core` is Kotlin.** AGP 9's built-in
  Kotlin support conflicts with the old `org.jetbrains.kotlin.android` plugin;
  `core` is a plain Kotlin/JVM module (not Android) so it never touches that
  machinery. Keeping the launcher as a thin Java bootstrap sidesteps the
  conflict entirely.
- **minSdk = targetSdk = compileSdk = 36** (Android 16), matching the physical
  test device — same standing convention as the native-android-compose template.
- **Offline only.** No network calls, no accounts. Local persistence only
  (not yet built — Phase 6).
- **Debug-only signing.** Private/personal project, not distributed via Play
  Store, so no release keystore.
- **Dismiss Android Studio's "AGP Upgrade Assistant" prompt** — staying pinned
  to AGP 9.2.0 deliberately. Boo will flag if/when an upgrade is actually
  warranted; don't accept it reflexively from the popup.
- **Fixed timestep physics** (1/60s accumulator, decoupled from render
  framerate) — the standing convention for physics stability, applied in
  `PhysicsDuelGame.stepPhysics()`, carries forward to any future mechanic
  built on this core.

## Version pins (researched Sept 2026 — re-check if picking this back up much later)

- AGP 9.2.0, Gradle 9.4.1 (via wrapper, reused from the native-android-compose
  template), Kotlin 2.3.20, JDK 17
- LibGDX (`com.badlogicgames.gdx:gdx` / `gdx-backend-android` / `gdx-platform`) 1.14.2
- Box2D: `com.badlogicgames.gdx:gdx-box2d` / `gdx-box2d-platform` **1.14.2**
  (the classic, core-matched wrapper — NOT the independently-versioned
  `3.1.1-0` Box2D-v3 rewrite, which is over a year stale and shouldn't be mixed
  with the 1.14.2 natives in the same build). Wired up and working as of Phase 2.
- Ashley for later phases: `com.badlogicgames.ashley:ashley:1.7.4` (not yet added)

## Real bugs hit and fixed along the way

- **Phase 1**: `core/build.gradle.kts` originally declared the LibGDX dependency
  as `implementation(libs.gdx)`. That hides `com.badlogic.gdx.*` types from any
  module depending on `:core` (Gradle's `implementation` deliberately doesn't
  propagate to consumers). `android`'s `AndroidLauncher` extends
  `AndroidApplication`, which needs `com.badlogic.gdx.Application` on its own
  compile classpath — compile failed with "class file for
  com.badlogic.gdx.Application not found". Fix: changed to `api(libs.gdx)`,
  matching how LibGDX's own official templates declare it.

## Known risks to verify, not yet resolved

- **16 KB native page-size alignment.** There are open/ambiguous LibGDX GitHub
  issues (#7695, #7701) about `.so` files in gdx-platform/gdx-box2d-platform not
  being aligned for Android's 16 KB page size requirement on 64-bit. Not
  confirmed fixed in 1.14.2, and now genuinely in play since Phase 2 added
  box2d natives. Check via Android Studio's APK Analyzer at some point.
- Don't mix box2d 1.14.2 natives with the 3.1.1-0 box2d artifact in the same
  build — duplicate/mismatched native symbols.

## Fixed vs. the native-android-compose template

- That template's `.gitignore` uses `/build/` (leading slash — anchors to repo
  root only), which won't actually ignore a nested module's `build/` dir (e.g.
  `app/build/`). This project's `.gitignore` uses unanchored `build/` so it
  matches at any depth — worth backporting to the shared template.

## Phase plan (foundation-first, each phase is one on-device-testable build)

1. **Bare bones** — boots, builds, installs, launches to a blank cleared screen.
   ✅ **DONE** — confirmed on-device, committed and pushed to GitHub.
2. **Physics + render pipeline** — Box2D world, fixed timestep, camera/viewport,
   static ground + one falling/settling circle, debug wireframe render.
   ✅ **DONE** — confirmed on-device (circle falls, lands on ground, small
   bounce). Committed locally; push pending as of this writing.
3. Input pipeline — touch/drag applies force/impulse to the test shape. ← **next up**
4. ECS refactor (Ashley) — test shape becomes an entity built from components,
   driven by systems.
5. Scene/state manager — menu → play → pause → game-over as real stub screens.
6. Persistence layer — local save/load, corruption-safe reads.
7. HUD/UI overlay + audio hooks, wired into the scene manager.

Once Phase 7 is done, `core` is a proven, tested foundation with no specific game
built on it yet — any future mechanic becomes a module plugged into it.

## Working notes / environment quirks

- The device bridge's local shell (`device_bash`) was down for the Phase 1 and
  Phase 2 sessions — all file writes to Boo's PC went through stage/commit
  instead, and git commands were run manually by Boo in Git Bash rather than
  by Claude directly. If it's back in a future session, Claude can go back to
  running git directly (still following the standing rule: init/commit only,
  never push — Boo always runs the actual push).
- Boo prefers step-by-step pacing with no assumed familiarity with dev tool
  UIs (Android Studio menus, git terminal) — see Claude's memory for the
  full standing preference and the "SBS" shorthand.
- This file is kept in sync in two places: here in the repo (so a fresh clone
  tells the whole story on its own) and in the "Physics Dual" Claude Project's
  docs (so a brand-new chat can pick up context without touching Boo's PC at
  all). Keep both updated together at each phase checkpoint.
