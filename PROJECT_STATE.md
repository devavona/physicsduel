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
- **Offline only.** No network calls, no accounts. Local persistence via
  `SaveManager` (Phase 6) only — no server/cloud sync.
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

- **16 KB native page-size alignment — action item, not yet done.** There are
  open/ambiguous LibGDX GitHub issues (#7695, #7701) about `.so` files in
  gdx-platform/gdx-box2d-platform not being aligned for Android's 16 KB page
  size requirement on 64-bit. Not confirmed fixed in 1.14.2, and genuinely in
  play since Phase 2 added box2d natives — every build since then ships the
  `.so` files this risk is about. **How to check, next time Boo is in Android
  Studio:** Build menu → Analyze APK... → pick the most recent debug APK →
  expand `lib/arm64-v8a/` → select each `.so` file → the analyzer's bottom
  panel reports whether it's 16 KB-aligned. No fix to apply either way yet —
  this is a confirm-or-flag check, not a blocking issue. If any `.so` comes
  back unaligned, flag it before this app ever targets a 16 KB-page device.
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
   bounce). Committed and pushed.
3. **Input pipeline** — touch/drag applies force via a Box2D MouseJoint
   (`DragInputProcessor`, reusable/generic - not hardcoded to one body).
   Also added static boundary walls (left/right/top, floor repositioned to
   the true bottom edge) once initial testing showed a dragged body could be
   flung off into unbounded space with nothing to stop it. ✅ **DONE** —
   confirmed on-device (drag works, body stays contained by the walls).
4. **ECS refactor (Ashley)** — falling circle is now an entity
   (`PhysicsBodyComponent` + `DraggableComponent`), physics stepping moved into
   `PhysicsSystem`, drag hit-testing queries the ECS (`Family.all(...)`)
   instead of scanning raw Box2D bodies. ✅ **DONE** — confirmed on-device,
   behavior unchanged from Phase 3 as expected (this phase was purely
   structural).
5. **Scene/state manager** — split into `MenuScreen` → `PlayScreen` → `PauseScreen`
   → `GameOverScreen` using LibGDX's built-in `Game`/`Screen` pattern.
   `PlayScreen` is deliberately NOT disposed on pause (only on a genuine
   "end run", triggered from `PauseScreen`'s right-hand tap zone) so the
   physics world survives a pause/resume cycle intact - see the resilience
   note in `PlayScreen`'s doc comment. Back button now pauses instead of
   quitting. ✅ **DONE** — confirmed on-device, full flow tested (menu → play
   → pause → resume-with-state-preserved → pause → end run → game over →
   menu).
6. **Persistence layer** — local save/load, corruption-safe reads. Generic
   `GameSave`/`SaveManager`, deliberately not tied to Box2D/gameplay state:
   write goes through a `.tmp` file + atomic rename with the previous save
   kept as a `.bak` first; read tries the primary file, falls back to `.bak`,
   falls back to defaults — a corrupt or half-written save can't crash
   startup. Phase 6's schema is just a `runCount` int, incremented on
   `PauseScreen`'s "end run" path — future phases add fields to `GameSave`
   (settings, best score, unlocked content, ...) rather than inventing new
   save files. No visual UI for this yet (that's Phase 7), so it's verified
   via Logcat instead of on-screen — see testing steps below.
   ✅ **DONE** — confirmed on-device: write path logged correctly across two
   consecutive runs (runCount 1, then 2), and after a full swipe-away app
   kill + relaunch, Logcat showed the save reloaded from disk matching the
   last saved value — confirms it survives real process death, not just a
   screen change.
7. HUD/UI overlay + audio hooks, wired into the scene manager. ← **next up**

Once Phase 7 is done, `core` is a proven, tested foundation with no specific game
built on it yet — any future mechanic becomes a module plugged into it.

## How to test Phase 6 on-device (Logcat, not visual — no HUD yet)

1. In Android Studio, open the Logcat panel and filter by tag `SaveManager`
   (also useful: `PhysicsDuelGame`).
2. Fresh install (or clear app data) → launch → Logcat should show something
   like "No valid save found - starting fresh (runCount=0)" then
   "Cold start - previous runCount=0".
3. Menu → Play → Back (pauses) → tap the right/red half ("end run") → Game
   Over → Logcat should show "Saved: schemaVersion=1 runCount=1".
4. Repeat step 3 once more from the Game Over screen (tap to Menu → Play →
   end run again) → runCount should increment to 2.
5. **The real test**: fully kill the app (swipe it away from Android's Recents
   screen, not just press Home) so the process actually dies, then relaunch.
   Logcat should show "Loaded save: runCount=2" (matching wherever step 4 left
   off) — proving the save survived a real process kill, not just a screen
   change within one run of the app.

## Working notes / environment quirks

- The device bridge's local shell (`device_bash`) has been down every session
  so far (Phases 1 through 6), including after a full quit/restart of both
  the Claude desktop app and Android Studio partway through — never actually
  fixed, just consistently worked around. All file writes to Boo's PC go
  through stage/commit instead, and git commands are run manually by Boo in
  Git Bash rather than by Claude directly. If it's ever back in a future
  session, Claude can go back to running git directly (still following the
  standing rule: init/commit only, never push — Boo always runs the actual
  push).
- Boo prefers step-by-step pacing with no assumed familiarity with dev tool
  UIs (Android Studio menus, git terminal) — see Claude's memory for the
  full standing preference and the "SBS" shorthand.
- This file is kept in sync in two places: here in the repo (so a fresh clone
  tells the whole story on its own) and in the "Physics Dual" Claude Project's
  docs (so a brand-new chat can pick up context without touching Boo's PC at
  all). Keep both updated together at each phase checkpoint.
