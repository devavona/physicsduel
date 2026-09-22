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
- **Unit test setup**: the first run of the new `core` tests failed all 8
  instantly with `SharedLibraryLoadRuntimeException: Couldn't load shared
  library 'gdx64.dll'`, thrown from `HeadlessApplication`'s constructor
  before any test code ran. Root cause: `core/build.gradle.kts` only added
  `gdx-box2d-platform:natives-desktop` as a test dependency, not
  `gdx-platform:natives-desktop` — LibGDX's own base native library (which
  `HeadlessApplication` needs regardless of Box2D) was never on the test
  classpath. Fix: added `gdx-platform:natives-desktop` alongside it. Same
  "each natives classifier is a separate, explicit dependency" lesson
  `android/build.gradle.kts` already encodes for the Android ABIs — worth
  remembering if `core`'s test dependencies change again.

- **Gravity-well milestone, first on-device test**: the orbit didn't spiral
  in or out, but the whole ellipse slowly rotated in place over time
  (apsidal precession) - the closest point to the star kept drifting
  clockwise. Root cause: `GravitySystem` (as first written) applied its
  force once per *rendered frame*, but `PhysicsSystem`'s fixed-timestep loop
  only steps the actual simulation a variable number of times per frame -
  Box2D queues an applied force and clears it on the next `world.step()`,
  so the gravitational "kick" the body received per physics tick ended up
  depending on frame-rate timing rather than being a fixed, consistent
  quantity per tick. Fixed by moving gravity application from a per-frame
  Ashley system into a `beforeStep` callback `PhysicsSystem` invokes from
  *inside* its fixed-timestep loop, immediately before each `world.step()`
  - guaranteeing it runs exactly once per physics tick regardless of render
  frame rate. See `PhysicsSystem`'s `beforeStep` doc comment and
  `GravitySystem`'s class doc comment for the full writeup. Not yet
  re-tested on-device as of this note - see the gravity-well milestone
  section below.

## Known risks to verify, not yet resolved

- Don't mix box2d 1.14.2 natives with the 3.1.1-0 box2d artifact in the same
  build — duplicate/mismatched native symbols.

## Resolved risks

- **16 KB native page-size alignment — RESOLVED, confirmed OK.** Checked via
  Android Studio's APK Analyzer (Build → Generate App Bundles or APKs →
  Generate APKs → click "analyze" on the completion notification → expand
  `lib/arm64-v8a/`). Both `libgdx.so` and `libgdx-box2d.so` show **16 KB** in
  the Analyzer's Alignment column — LibGDX 1.14.2's natives are already
  aligned for Android's 16 KB page-size requirement on 64-bit. The
  open/ambiguous GitHub issues (#7695, #7701) that prompted this check don't
  apply to this build. No action needed.

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
7. **HUD/UI overlay + audio hooks.** `HudFont` (one shared `BitmapFont`
   singleton, built-in default font, no asset file) and `AudioManager` (loads
   one synthesized `audio/tap.wav` cue via LibGDX's `Sound`, fails soft to
   silence if the asset is missing) are new generic, reusable singletons,
   disposed once at real app shutdown (`PhysicsDuelGame.dispose`). Menu,
   Pause, and Game Over screens each got a small `OrthographicCamera` +
   `SpriteBatch` and now draw real text (title/prompt, "RESUME"/"END RUN",
   "GAME OVER") instead of unlabeled color zones, and every screen-transition
   tap plays the tap sound. Menu also now displays "Runs completed: N" read
   live from `SaveManager` — the first place Phase 6's persisted state is
   visible on-screen instead of only in Logcat. `PlayScreen` got a small
   always-on-top HUD label (top-left) showing the tracked body's live Y
   position each frame, queried through the ECS family rather than a direct
   Body reference — proving screen-space UI can be drawn over the world-space
   debug view every frame without disturbing it, which is the pattern any
   real future HUD (score, health, timer) reuses.
   ✅ **DONE** — confirmed on-device (Menu/Pause/Game Over text and tap
   sounds, runs-completed count visible on Menu, live Y-position HUD label
   in Play all checked out).

**Foundation complete.** All 7 phases are done and confirmed on-device.
`core` is now a proven, tested foundation — Box2D physics on a fixed
timestep, an ECS (Ashley) composition layer, generic drag/touch input,
a scene/state manager, corruption-safe local persistence, and a basic
HUD/audio layer — with no specific game mechanic built on top of it yet.
Any future direction (Asteroids-style, Gravity-Wars-style, Angry-Birds-style,
Catapult-style, or something else) becomes a module plugged into this,
per the original "don't back myself into a corner" goal.

**Next up: decided.** Boo chose the first real game direction: an orbital
gravity-well mechanic in the spirit of the classic "gravity well" genre of
games (see the Concept section's homage list) — evolving *this* project
rather than starting a separate one, per the reasoning in "Reuse across
future games" below.

**Important naming constraint (Boo, explicit):** this must NOT be branded
"Gravity Wars" or "Gravitee Wars" anywhere in code, docs, or UI — that name
belongs to a different, already-existing app, and reusing it would be a
naming collision, not a homage. The underlying mechanic (a central mass
pulling another body into a curving orbit) is exactly what's wanted; only
the proper-noun title is off-limits. Purely descriptive physics terms are
fine and already in use (`GravitySystem`, `GravitySourceComponent`,
`GravityAffectedComponent` — these describe the mechanic, not a game
title). No replacement working title has been chosen yet — PROJECT_STATE.md
and the code refer to this generically ("the gravity-well mechanic"/"the
orbital milestone") until Boo picks one, rather than guessing at a name
that might turn out to collide with something else too.

## Reuse across future games

Asked directly by Boo: when a genuinely different game gets started later,
how would it leverage this foundation? Honest answer given at the time:
reuse is currently theoretical — there's only ever been one project so far,
so nothing has actually been extracted into a separate reusable library yet.
The recommendation (which Boo then acted on by choosing the gravity-well
direction) was to build the first real game as an evolution of *this*
project rather than spinning up a second repo, and only split `core` into a
genuinely standalone/reusable module once a second, sufficiently different
game direction actually demands it — premature extraction risks guessing
wrong about what's actually shared. Everything foundational (fixed-timestep
`PhysicsSystem`, Ashley ECS wiring, `DragInputProcessor`, the
`Screen`/`Game` state machine, `SaveManager`/`GameSave`, `HudFont`/
`AudioManager`) is already written generically enough (see each class's own
doc comment) that it should carry forward into a future second game with
little to no change, whenever that split actually happens.

## First game: orbital gravity-well milestone ✅ DONE — confirmed on-device

Replaces `PlayScreen`'s original demo content (a circle falling under
uniform gravity, bouncing inside four walls) with the first real gameplay
direction: a static central "star" body that pulls a smaller body into a
curving orbit, via a brand-new custom gravity system layered on top of
Box2D (Box2D itself only provides one *uniform* gravity vector for the
whole world — no built-in concept of one body radially pulling another,
which every game in this genre needs).

- **`GravitySourceComponent(mass)`** (new, `Components.kt`) — tags an
  entity as a gravity source (the star). Carries its own tuning `mass`
  value rather than reading the body's real Box2D mass, because a *static*
  Box2D body always reports zero mass (static bodies are immovable by
  definition) — there's no physical mass to read off it.
- **`GravityAffectedComponent`** (new, `Components.kt`) — marker/tag:
  entities with this get pulled toward every gravity source each frame.
- **`GravitySystem`** (new file) — **not** an Ashley `EntitySystem` (see the
  precession bug/fix below for why) — a plain class constructed directly
  with the Ashley `Engine`, capturing the same kind of live `Family` query
  any system would. Exposes a single `applyForces()` method: for every
  affected body, sums an inverse-square force from every source
  (`F = G * sourceMass * affectedBody.mass / r²`, direction toward the
  source) and applies it via `Body.applyForceToCenter()`. `G` (a tuning
  constant, not the real physical gravitational constant — meaningless at
  Box2D's small hand-picked world-unit scale, same as Phase 2's `-9.8` was
  already "Earth-shaped" rather than literal) is intentionally public so
  `PlayScreen` can derive the demo body's initial orbital velocity from the
  exact same constant instead of duplicating the number. A minimum-distance
  clamp on the force calculation (not on the body's actual position)
  prevents the force from blowing up toward infinity if a body ever grazes
  very close to a source.
- **`PhysicsSystem`** — constructor now takes an explicit `priority: Int =
  0` parameter (passed straight to Ashley's `EntitySystem(priority)`) and
  an optional `beforeStep: (() -> Unit)? = null` callback, invoked from
  *inside* the fixed-timestep loop immediately before each individual
  `world.step()` call. `PlayScreen` wires `beforeStep` to
  `gravitySystem::applyForces`, guaranteeing gravity is computed and
  applied exactly once per physics tick — see "Real bugs hit and fixed"
  above for why that had to change from the original per-frame-system
  design (apsidal precession, caught on first on-device test). Both
  parameters default to values that make this a non-breaking change for
  anything that doesn't care about ordering/hooks.
- **`PlayScreen`** — `World`'s uniform gravity is now `(0, 0)` (GravitySystem
  is the sole source of gravity going forward); the four boundary walls
  and `createBoundaries()`/`createWall()`/`createFallingCircle()` are gone
  entirely, replaced with `createStar()` (static body at world center,
  tagged `GravitySourceComponent`) and `createOrbitingBody()` (dynamic
  body placed a fixed distance to one side of the star, tagged
  `DraggableComponent` + `GravityAffectedComponent`, launched with an
  initial *tangential* — not zero, not straight-at-the-star — velocity
  computed from the closed-form circular-orbit speed `v = sqrt(G *
  starMass / r)`, so it curves into orbit instead of falling straight in).
  The star is reused as `DragInputProcessor`'s required static anchor body
  — the exact same "reuse an existing static body, no dedicated dummy
  needed" trick the removed floor previously provided. The HUD's tracked
  body now specifically queries the `GravityAffectedComponent` family
  (the orbiting body) rather than "whichever physics body exists first",
  so it keeps showing the body that's actually moving rather than
  whichever entity happened to be the star.
- **First on-device test result**: orbit curved correctly and sped up
  noticeably near the star on each close pass — confirmed as genuinely
  correct physics (Kepler's second law: a body sweeps out equal areas in
  equal time, so it must move faster when closer in), not a bug. However,
  Boo also noticed the orbit itself slowly rotating in place over many
  passes (apsidal precession) rather than staying a clean repeating loop —
  see "Real bugs hit and fixed" above for the diagnosis and fix (gravity's
  force application now happens exactly once per physics tick via
  `PhysicsSystem`'s new `beforeStep` callback, instead of once per render
  frame).
- **Precession fix, re-tested on-device — ✅ DONE, confirmed, with an
  interesting wrinkle.** Boo watched across many passes: no visible
  rotation/drift anymore, so the fixed-timestep/render-frame timing
  mismatch was indeed the whole cause of the precession, confirming the
  underlying gravity math was already correct. But the orbit turned out to
  hold the *exact same* distance and speed at every point, all the time -
  a perfect circle, not the near/far "whip around" behavior Boo had
  originally described. That's actually mathematically correct: the
  orbiting body's launch velocity was deliberately set to the closed-form
  *circular*-orbit speed (`v = sqrt(G * starMass / r)`), and a circular
  orbit by definition never varies in distance or speed. This also
  reframes the earlier "speeds up near the star" observation (originally
  chalked up simply to correct Kepler physics): that speed-up was actually
  a side effect of the precession bug itself - the inconsistent per-frame
  force was nudging the orbit off of the perfect circle it was launched
  into, into a slightly eccentric (elliptical) shape, which is what
  produced the visible near/far variation. Fixing the bug removed that
  unintended eccentricity along with the precession.
- **Elliptical orbit, deliberately re-added.** Boo wanted the dynamic
  near/far "whip around" behavior back, but as an intentional, stable
  design choice rather than an accidental side effect of a bug. Fix:
  `PlayScreen` now launches the orbiting body at `ORBIT_SPEED_FACTOR`
  (0.85) times the circular-orbit speed instead of exactly 1.0x. Launching
  slower than circular speed (while still purely tangential) makes the
  starting point the *farthest* point of the orbit (apoapsis) instead of
  the only distance it ever reaches - gravity pulls it in closer than
  `ORBIT_RADIUS` before swinging back out, producing a real ellipse with a
  visibly closer/faster point and a visibly farther/slower point (Kepler's
  second law, now genuinely and deliberately present), while staying a
  closed, stable, repeating orbit - not decaying, not precessing. 0.85 was
  chosen to keep the close approach comfortably clear of the star's own
  radius; a much lower factor would make the ellipse thin enough for the
  near pass to graze or hit the star. ✅ **DONE** — confirmed on-device
  ("better" - the near/far speed and distance variation is now visible,
  and it stays stable pass after pass, no drift or precession noted).
- **Deliberately not modeled yet: orbital decay.** Boo asked whether the
  satellite should be losing velocity/energy on each pass and slowly
  sinking into the star, like some arcade gravity-well games do for
  tension. Answered and confirmed with Boo: no drag/friction force acts
  between the star and satellite in this simulation (Box2D friction only
  applies during an actual collision, and the two bodies never touch), so
  a stable non-decaying orbit is the physically correct result of a pure
  two-body gravity field with no other forces — real orbital decay needs
  an explicit extra loss mechanism (atmospheric drag, tidal friction, ...).
  Boo explicitly tabled this as a **future feature** — a deliberate
  design choice to add later (e.g. a small velocity-proportional drag
  force), not something to build now.

**Next up (not yet started, real decision for a future session):** the
gravity-well *physics* is done and confirmed - stable elliptical orbit,
correct speed-up near the star, no precession, no unwanted decay. There's
no actual *game* yet, though - no player goal, no win/lose condition, no
input beyond the existing drag-to-perturb-the-orbit debug interaction.
Open questions to pick up next time, none decided yet: what does the
player actually *do* (nudge the satellite into a target orbit? avoid a
second body? survive as long as possible? something else)? Is there a
score or objective? Does the tabled orbital-decay feature ever get added,
and if so, as a challenge/timer mechanic? Multiple gravity sources (a
second star/planet) instead of just one? None of this needs deciding right
now - flagging it so the next session picks up here instead of re-deriving
"what's next" from scratch.

## Game design exploration — roguelite direction (Sept 2026 session)

Boo used Gravitee Wars (2010 Flash game by FunkyPear — turn-based space
artillery where shots curve under planetary/black-hole gravity, destructible
terrain, ~10 weapon types, team-vs-team, since followed by "Gravitee Wars
Online" and a 2024+ Steam remake by the same studio) as a reference point for
this conversation — explicitly NOT as something to clone. See the existing
"Important naming constraint" note above: nothing here may be branded
"Gravity Wars"/"Gravitee Wars".

**What Boo said stuck with them about it, and why**, in his own terms:
- Turn-based pacing — easy to do a run, quit, and pick back up later; can
  play while doing other things. (Note: this is already partly true of what
  `core` provides — `PlayScreen` surviving pause/resume and `SaveManager`
  surviving real process death were both built before this conversation
  connected them explicitly to this instinct.)
- No network requirement (consistent with the standing "Offline only"
  decision above).
- Complexity that grows over time without becoming annoying/overwhelming.
- The orbital-mechanics/gravity-aiming skill itself — the part that
  "scratches the inner physics nerd."
- Tone: cartoony/lighthearted enough that it reads as detached from being a
  literal war game, despite the "war" framing.
- A (self-described "poor but real") comparison to Sid Meier's Civilization —
  not for its content, but for the same turn-based / bite-sized-session /
  gradually-unfolding-complexity *shape* of play.

**Opponent structure, confirmed:** one or more computer/AI opponents.
PvP is explicitly not being considered at this point.

**Structure, confirmed: roguelite.** Talking it through surfaced a real
design tension Boo identified himself: straightforward permanent power
growth (get strictly stronger every run, forever) eventually produces an
end-state where the player is so powerful nothing is challenging anymore
and the fun drains out. Discussed how Hades and Vampire Survivors each
solve this differently (Hades: permanent progression is mostly
access/refinement rather than raw power, and the player can opt into
harder enemies for better rewards via its Heat/Pact-of-Punishment system;
Vampire Survivors: huge in-run power growth is fine because each run is
short and enemy density/bosses escalate to match, while between-run unlocks
are mostly new characters/weapons — variety, not a stacking multiplier).

**Progression model, confirmed: three layers**, Boo's own framing:
1. **Meta-progression (persists across runs)** — unlocks *availability*:
   new base stats, power tiers, and weapon options get added to the pool of
   things that CAN show up. This is deliberately about growing the menu,
   not directly handing out raw power — the structural fix for the
   too-powerful-eventually problem above.
2. **In-run build** — of whatever is currently unlocked, which specific
   things get drafted/equipped/combined on THIS run is what actually
   determines that run's strategy and power level. Since this is
   procedurally offered each run, no two runs assemble the same way even
   from an identical unlocked pool.
3. **In-run currency** — a resource earned and spent only within a single
   run (not carried between runs), creating a live economic decision loop
   (spend now vs. bank it, this upgrade vs. that one) on top of the build
   choices, giving each run its own distinct flavor/identity.

**Not yet decided (open for a future session):**
- What a single "run" is actually made of — one continuous
  procedurally-generated battlefield, or a sequence of discrete
  procedurally-generated encounters (closer to Gravitee Wars' level-by-level
  structure)?
- Whether Hades-style opt-in difficulty scaling (challenge keeps pace with
  player choice, not just player power) gets added on top of the
  three-layer progression model, or whether the model alone is judged
  sufficient. **Explicitly deferred, not a current focus (Boo, explicit):**
  Boo hasn't thought as far as a "beat the game once, then unlock
  harder/more-rewarding replay options" structure (Hades' Pact of
  Punishment: a checklist of named difficulty modifiers, each worth some
  points toward a "Heat" score, freely re-chosen before every run with no
  permanent commitment, where higher Heat means better post-run rewards —
  see the Pact of Punishment writeup for the full mechanic if picking this
  back up). Noted for future consideration only — not to be designed or
  built now. However, Boo asked to design and build the generic scoring
  plumbing this concept would eventually need into `core` now, deliberately
  decoupled from any specific "opt-in replay" feature or real gameplay
  content, so branching into this later doesn't require a retrofit — see
  the dedicated section below once that's built.
- No working title chosen yet (see the existing naming constraint above).
- How any of this concretely attaches to the existing gravity-well `core`
  — **substantially resolved**, see "Core gameplay loop" below for the
  actual decided design (character roster/classes, HP + celestial-body-mass
  damage model, turn/movement structure, escalating level scale).

## Difficulty scoring engine (Sept 2026 session) — built, not yet tested

Generic, gameplay-agnostic scoring plumbing added to `core` now, ahead of
any real gameplay content that would use it - see "Game design exploration"
above for why. **Deliberately avoids Hades' own Pact-of-Punishment/Heat
terminology anywhere in code or docs (Boo, explicit):** this project likely
won't ever be hosted for others, but Boo wants to avoid any copyright/
trademark risk if that ever changes, so the comparison to that game's
design stays conversational/design-note context only, never baked into
class or identifier names.

- **`DifficultyModifier`** (new) — describes one selectable difficulty
  knob: id, display name, points-per-rank, max rank. Defines what a
  modifier *is*, not what it *does* - no gameplay effect lives here.
- **`DifficultySelection`** (new) — a chosen rank (0 = off) per modifier
  id, for one run/attempt. A modifier missing from the selection defaults
  to rank 0.
- **`DifficultyScore`** (new) — one pure function: sums `rank *
  pointsPerRank` across every modifier passed in, clamping each rank to
  that modifier's `[0, maxRank]` range first.
- **`DifficultyScoreTest`** (new) — 8 tests: empty selection, empty
  modifier list, single/multiple modifiers, a modifier missing from the
  selection, rank clamped above max, rank clamped below zero, and an
  unknown modifier id in the selection being silently ignored. Uses two
  clearly-labeled generic placeholder modifiers (`faster_opponents`,
  `extra_opponents`) - not a claim about what the real modifier set will
  eventually be.
- Deliberately NOT yet included: any real modifiers, any save-schema
  persistence, any reward-scaling formula - all real future work once
  actual gameplay content and an economy system exist to hang them on.
- No Gradle changes needed - pure Kotlin/JVM, no LibGDX/Box2D dependency,
  so `DifficultyScoreTest` needs no `GdxTestBootstrap` (unlike
  `PhysicsSystemTest`/`SaveManagerTest`).
- **Status: ✅ DONE — confirmed passing on Boo's PC.** All 8
  `DifficultyScoreTest` tests green after a Gradle sync + run in Android
  Studio (the local Linux VM behind Claude's device bridge couldn't run
  this itself - no cached Gradle distribution and no network route to
  services.gradle.org from that VM, plus only JDK 11 installed there vs.
  the JDK 17 this project is pinned to; confirmed by trying it directly).

**Unrelated note from this session:** `git status` also showed
`gradlew.bat` as modified - pure line-ending changes (CRLF vs LF), not
caused by adding the difficulty files above (Claude never touched that
file). Predates this session's edits; flagging for Boo rather than
fixing/reverting unasked - possibly a local git `autocrlf` difference
between machines/tools.

## Core gameplay loop — decided design (Sept 2026 session)

This resolves "what a run is made of": a **sequence of discrete,
won-or-lost encounters (levels)**, not one continuous evolving scene -
but each encounter is a bigger, more populated sandbox than the last.

**Level scaling.** Level 1: one star, two planets, fits on one screen, one
character per side. As levels progress: squad size grows (2 characters per
side by level 2, presumably more later), the field gains more stars of
varying size, planets gain moons, and eventually exotic gravity-well types
appear (dwarf stars, pulsars, black holes, red giants - "basically anything
that creates a gravity well," Boo's words). Eventually the field outgrows
one screen and needs a scrolling/zooming camera - not built yet.

**Turn structure**, per character's activation: move up to a fixed step
budget (5 as an illustrative example, not a final tuned number) to line up
a better angle → take one shot → move up to another budget of steps to get
into a defensive position → turn passes. Deliberately splits movement into
a before-shot allowance and an after-shot allowance, rather than one pooled
budget - "line up the shot" and "take cover" are two separate decisions.
**Not yet decided:** turn order once squads have multiple characters per
side (does each character get an individual interleaved turn, or does
"your turn" mean your whole squad acts before the AI's squad goes?).

**Aiming/attack:** pull-back-and-release, Angry-Birds-style - the pull
vector's angle and distance sets a missile's launch angle and power, then
gravity from nearby wells curves it in flight using the same physics
[GravitySystem] already provides for the orbiting-body milestone. This is
a new *input* scheme, distinct from [DragInputProcessor]'s existing
continuous-drag MouseJoint interaction used for that milestone's demo body.

**Character roster/classes** - directly the "menu" half of the
already-decided three-layer progression model (see "Game design
exploration" above): unlocking new classes over time via meta-progression
IS the "new weapon options become available" layer; which unlocked
classes you actually field on a given level is the in-run build layer.
Roster so far, RPG-style, not final/complete:
- **Archer** - baseline simple-missile unit.
- **Tank** - bigger missile, more health, fewer movement steps per turn
  (trades mobility for durability/power).
- **Bombardier** - lobs a bomb (bigger blast/damage); limited to one shot
  per turn.
- **Trebuchet** - named, not yet designed/described.
- **Sharpshooter** - a laser weapon largely unaffected by gravity; trades
  raw power for rewarding a clean direct line of sight over a curved
  trick shot.
- More classes expected over time; this list is a starting point, not
  exhaustive.

**Shot speed is per-weapon, not one global value (added after Phase 17).**
Boo's observation once weapons/ammo are real: "when we introduce new
ammo/weapons, the speed should be different for them" - e.g. a
Sharpshooter's laser should feel fast/direct, a Bombardier's lob should
feel slow and heavy. Decided shape for when the weapon system gets built:
[ShotSpeedTuning]'s live multiplier (currently 0.4x default, Phase 17)
stays as a single global "overall pace" feel-dial, and each weapon type
gets its own base-speed constant that the global multiplier is applied on
top of - so tuning overall game feel and differentiating weapons stay two
separate, non-conflicting knobs instead of needing a dial per weapon.
Not built - no weapon system exists in code yet (roster above is design
only). Flagging here for whenever that system actually gets built.

**Damage model:**
- A direct hit on an opposing character removes that character's health -
  health lives on characters, not on celestial bodies.
- A miss that strikes a planet/moon/asteroid instead damages that body's
  mass/structure - a small divot from a simple missile, a much bigger
  crater from a bomb (damage scale depends on weapon type).
- **Collateral/splash damage:** any character near a missed impact takes
  damage scaling with proximity (closer = more) and the weapon's power
  (bigger bombs = bigger blast radius and damage).
- Named strategic implications (Boo, explicit): deliberately cratering a
  moon to expose someone hiding on its far side; using the gravity-sling
  to curve a shot around cover instead of needing direct line of sight.

**Gravity/mass model, confirmed:**
- A celestial body's gravitational pull is a **live, mutable function of
  its current mass** - chipping away mass through combat damage measurably
  weakens its gravity well in real time, itself a strategic tool (per the
  moon-cratering example above). This means [GravitySourceComponent]'s
  `mass` - currently a fixed value set once at construction - needs to
  become something combat damage can write into over time, instead of a
  constant. Real but contained change, not yet made.
- **Deliberate simplification:** ordinary planets/moons/asteroids share
  one uniform density, so their gravity is driven purely by size - what
  you see is what you get, avoiding a hidden/unreadable stat on the most
  common object type.
- **Exotic body categories keep their own density constant:** stars, red
  giants, pulsars, black holes (and other dwarf-star-type objects - Boo
  said "swarf stars," read as "dwarf stars," to be confirmed) each get a
  distinct density multiplier, so e.g. a black hole can out-pull a star of
  the same visual size - preserves the "should feel unfairly strong"
  fantasy for named exotic types specifically, without making every
  ordinary planet a stat you have to inspect.

**What's already in `core` vs. genuinely new work** (for scoping a future
session - none of this is built yet beyond what's noted):
- Already have: [GravitySystem]/[GravitySourceComponent]/
  [GravityAffectedComponent] (inverse-square gravity, currently
  fixed-mass), fixed-timestep [PhysicsSystem], Ashley ECS,
  [DragInputProcessor] (a *different* input scheme than the pull-release
  aiming this loop needs).
- Genuinely new, not yet built: pull-back-release aiming input; a
  missile/projectile entity that spawns on a shot, flies under gravity,
  and collides with a character or celestial body; a character-health
  system; making celestial-body mass mutable and feeding it back into
  [GravitySourceComponent]; a movement-budget/turn controller (whose turn,
  remaining pre-/post-shot steps, hand-off logic); a minimal AI opponent
  that can choose an aim/power to attempt a hit; a scrolling/zooming
  camera for levels bigger than one screen; procedural level generation
  with an escalating complexity budget (more/varied bodies, moons, exotic
  well types, growing squad sizes per level); a squad/roster system for
  fielding multiple characters per side.

**Confirmed since (Sept 2026 session, continued):**
- "Dwarf stars" confirmed correct (not a mishearing).
- **Win condition:** reduce every character on the opposing squad's health
  to zero.
- **Celestial bodies can be fully destroyed** - reducing a body's mass to
  zero removes it and its gravity well from the field entirely, not just
  weakens it to some floor. Boo, explicit: in later stages this can become
  a deliberate strategy or even a necessity (e.g. eliminating a well
  that's making a shot impossible), not just an incidental side effect.
- **Attackable targets generalize beyond characters.** In later stages,
  bases and satellite weapons (and potentially other structures) may
  appear on the field. The general rule: anything capable of attacking the
  player also has its own hit points that must be reduced to zero, same as
  a character - not a separate system, just the same health/damage model
  applied to a broader category of target. Not needed for early levels or
  Phase 8 below - flagging for whenever bases/satellites actually get
  designed.

**Still genuinely open:**
- Trebuchet's actual weapon behavior - named but not yet designed.

**Damage visual, decided (Sept 2026 session, "make it playable"):** craters/
scorch marks overlaid at each hit location for now (planet keeps its
visual size); true shrink-as-mass-drops is a deliberate follow-up, not
done in the same pass - see Phase 21 below.

## Campaign progression ladder - decided design (Sept 2026 session, "make it playable")

Resolves "how does a session of play actually escalate," turning the
tech-demo scene (fixed one-star-two-planet layout, no win/loss handling)
into something with a real start/middle/end. Directly the "Level scaling"
idea already sketched in "Core gameplay loop" above, now with concrete
numbers Boo chose.

- **Planet placement:** the star stays fixed at center; both planets are
  randomly placed each new game (previously fixed `LAUNCH_PLANET_X`/
  `TARGET_PLANET_X` constants - becomes randomized geometry, still
  respecting whatever minimum-clearance-from-the-star/each-other rules
  keep a game always winnable). See Phase 20 below.
- **Progression counter: wins only, never reset by a loss.** A persistent
  counter, saved via [SaveManager]/[GameSave] (same file Phase 6's
  `runCount` already lives in - a new field, not a new save). Losing a
  game costs nothing but the game itself; you can retry immediately at
  the same difficulty tier. Explicitly NOT a roguelite streak - Boo was
  direct that a win-streak-resets-on-loss mechanic is not wanted right
  now.
- **Escalation ladder**, keyed off that win counter:
  - **Start:** 1 star (center), 1 planet per side, 1 character per side -
    today's scene, minus the fixed layout.
  - **5 wins:** the AI gets a second planet and a second character - a
    deliberate asymmetric difficulty step (harder for the player first,
    not a simultaneous both-sides addition).
  - **20 wins:** the AI gets a third planet (presumably a third
    character too, same one-character-per-planet pattern), AND the
    player gets a second planet/character - the player catches up here.
  - **30 wins:** campaign complete - a "reset progress to zero" option
    appears, letting Boo replay the whole ladder from the start
    on-demand rather than being stuck at the finished state.
- **Not yet decided / deferred to whenever it's actually reached:** exact
  squad composition/AI behavior once multiple characters exist per side
  (turn-order question already flagged as open in "Core gameplay loop"
  above - individual interleaved turns vs. whole-squad-then-whole-squad);
  where new planets/characters get placed once there's more than one per
  side (same randomization approach as the base 2-planet case,
  presumably, but not designed in detail yet).
- **Build order Boo confirmed:** avatar/AI sprite art (Phase 19, below)
  → random planet placement (Phase 20) → planet damage visuals (Phase
  21) → win/loss detection + "new game" option (Phase 22, first real use
  of the existing but never-wired-up [GameOverScreen]) → this
  progression ladder itself (Phase 23, builds on Phase 22's win/loss
  tracking). Each one is its own on-device-testable phase, same
  discipline as every phase so far - this section records the whole
  plan up front so a future session doesn't have to re-derive it, not a
  claim that Phases 20-23 are built yet.

## Phase 8: pull-and-release aiming + a gravity-curved projectile

**Status: ✅ DONE - confirmed on-device**, including a gravity-tuning
follow-up (see below). Implements the scope agreed
in "Core gameplay loop" above, deliberately minimal: [SlingshotInputProcessor]
(new) reads a pull-back-and-release drag from a fixed launch point and hands
[PlayScreen] a launch velocity; `PlayScreen.fireMissile()` spawns a small
dynamic body tagged [GravityAffectedComponent] (so the existing
[GravitySystem] curves it exactly like the orbiting-body milestone) and the
new [ProjectileComponent] marker; [ProjectileContactListener] (new) detects
the missile touching anything and removes it, logging the impact - no
health/damage/cratering yet, a hit is only *detected* for now.

Scene: one star (the only gravity source this phase), two static planets
(launch and target, no gravity pull yet - both deliberate Phase 8
simplifications, see "Core gameplay loop" above), and a fixed launch point
just above the launch planet's surface with a small always-visible cyan
marker circle (added beyond the original scope discussion - without it
there'd be no visual cue at all for where to touch, since the launch point
isn't a real Box2D body the debug renderer would draw). [DragInputProcessor]
is no longer wired into `PlayScreen` - nothing in this scene is
[DraggableComponent]-tagged anymore - but the class itself is untouched and
expected to matter again once character movement needs a drag interaction.

The Phase 7 HUD label needed no code changes at all: it already tracked
"whichever [GravityAffectedComponent] entity exists" rather than the
orbiting body specifically, so it now naturally shows the in-flight
missile's Y position instead (blank when no missile is in flight) - exactly
the reuse Phase 7 was built to prove.

**First on-device test result: gravity felt too strong / too dramatic.**
Boo's exact words - "more dramatic than feels good," not necessarily wrong
physics, just not fun. Rather than iterating on tuning constants blind by
guessing numbers from a text description back and forth, Boo asked for a
live, on-device tuning tool instead - added immediately as part of Phase 8:

- **`GravitySystem.gravityMultiplier`** (new `var`, defaults to `1f`) - a
  runtime-adjustable multiplier layered on top of the existing tuned
  G/mass math in `applyForces()`. Not a `const` like `G` - meant to change
  while the app is running, and takes effect immediately (including on an
  already-in-flight missile), since `applyForces()` reads it fresh every
  physics tick.
- **`GravityDebugController`** (new file) - two on-screen tap zones,
  top-right corner, that nudge the multiplier down/up by 0.1 per tap
  (clamped between 0.1 and 3.0). Deliberately left permanently wired in,
  not gated behind a build flag - this project has no release/Play Store
  build to worry about a debug control leaking into (see "Debug-only
  signing" above) - so it stays available as a standing tuning tool for
  future phases too, not removed after Phase 8.
- `PlayScreen` draws the two buttons plus a live "Gravity x1.0"-style
  readout in the top-right corner, and wires the controller into the input
  multiplexer ahead of the aiming input.
- **Confirmed on-device: `0.7` feels right.** Boo tuned it live with the
  buttons and landed on 0.7 - now baked in as `GravitySystem
  .gravityMultiplier`'s actual default (still a `var`, still fully
  adjustable live via the same buttons from this new baseline, not locked
  in).
- The buttons themselves needed a follow-up fix after the first on-device
  try: too small to comfortably tap (bumped from 100 to 160 reference
  pixels), and the "Gravity xN.N" label's Y math was wrong - it tried to
  position the label *above* the button row by adding to a Y value that
  was already near the top of the screen, pushing it off-screen entirely.
  Fixed by reserving vertical space for the label up front (`labelReserve`)
  and hanging the button row below that reserved space, instead of trying
  to place the label above an already-placed row.
- **Status: ✅ DONE - confirmed on-device**, including the button-size and
  label-position fixes and the final `0.7` gravity multiplier value.

### How to test Phase 8 on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. Debug wireframe view should show: a larger circle near the
   top-center (the star), two same-size circles at the same height near the
   bottom-left and bottom-right (launch and target planets), and a small
   cyan marker circle just above the left (launch) planet.
3. Touch down near the cyan marker and drag - a yellow line should appear
   from the marker to your finger, updating live as you drag.
4. Release - the yellow line disappears and a missile should launch in the
   *opposite* direction from your drag, curving as it flies (pulled toward
   the star, not going in a straight line) rather than flying dead straight.
5. Watch what happens when it hits something (either planet, or the star):
   the missile should disappear, and Logcat (filter by tag
   `ProjectileContactListener`) should show a "Missile impact" log line -
   confirms the hit was detected even though nothing visible happens yet
   (no damage/crater system exists).
6. HUD check: while a missile is in flight, the top-left label should read
   "Missile Y: <number>" and update live; it should go blank again once the
   missile is removed after impact.
7. Try a few different pull angles/distances - dragging harder should
   launch a visibly faster shot (clamped at some maximum - see
   `MAX_MISSILE_SPEED`), and touching down far from the marker should NOT
   start aiming at all (confirms `AIM_START_RADIUS` is working).
8. **Gravity tuning:** top-right corner should show two small buttons
   ("-" and "+") and a "Gravity x1.0" readout above them. Tap "-" a few
   times, then fire another shot - the curve should be noticeably gentler.
   Tap "+" past 1.0 and fire again - noticeably more dramatic. Find whatever
   multiplier value feels right, then tell me that number (the readout
   updates live) so it can become the new default - no more back-and-forth
   guessing needed.
9. If the curve looks wrong in some other way (dead straight regardless of
   the multiplier, snaps into the star instantly, or barely deflects at
   all even at high multiplier values) or the aim/fire feel is off in
   general, tell me what it looked like - `PlayScreen`'s other tuning
   constants (`PULL_POWER_SCALE`, `MAX_MISSILE_SPEED`, planet/star
   placement) are easy to adjust too, once I know which direction it's off
   in.

## Phase 9: a real avatar + movement-budget/turn structure

**Status: ✅ DONE - confirmed on-device**, including a real viewport/letterboxing bug found and fixed along the way (see below - it affected every HUD element on this screen, not just Phase 9's new controls). Implements the
movement/turn-structure half of "Core gameplay loop" above, same
deliberately-minimal spirit as Phase 8: no AI opponent, no health/damage,
no second character yet - purely proving the movement-budget/turn-boundary
mechanic feels right in isolation before anything else builds on it.

- **`AvatarMovementController`** (new file) - owns the avatar's position
  (an angle around the launch planet's center, at a fixed height above its
  surface - the avatar walks along the surface rather than floating freely)
  and the turn/budget state machine: a `Phase` of `PRE_SHOT` or
  `POST_SHOT`, a steps-remaining counter that resets to
  `MOVEMENT_STEPS_PER_PHASE` (5, the illustrative number from Boo's design
  conversation) at the start of each phase, and a turn counter. Two
  bottom-left tap zones move the avatar left/right by
  `MOVEMENT_STEP_ANGLE_DEGREES` (15°) per tap, spending one step; a
  bottom-right "Pass" zone, visible only during `POST_SHOT`, ends that
  phase early instead of using every remaining step. The post-shot budget
  hitting zero also passes automatically. Passing always returns to
  `PRE_SHOT` with a full budget and increments the turn counter - with no
  opponent yet, this just starts a fresh turn for repeated testing rather
  than handing off to anyone.
- **`PlayScreen`'s `launchPoint`** (Phase 8's fixed `Vector2`) is now
  refreshed from `avatarMovementController.position` every frame, instead
  of being set once at construction. Both `SlingshotInputProcessor` and the
  debug-overlay marker circle already held a reference to that same
  `Vector2` instance, so updating its contents in place (`.set(...)`)
  was enough to make aiming and the marker circle follow the avatar with no
  changes needed in either of those - the marker circle doubles as the
  avatar's visual position for now, there's no separate avatar sprite yet.
- **Firing is gated on `canFire`** (true only during `PRE_SHOT`) -
  `SlingshotInputProcessor`'s `onFire` callback now checks this before
  spawning a missile, and calls `avatarMovementController.onFired()`
  immediately after a shot actually launches, which is what transitions
  `PRE_SHOT` into `POST_SHOT` with a fresh budget. A touch-and-release that
  happens during `POST_SHOT` is silently ignored - no missile spawns, no
  turn-state change.
- `PlayScreen` draws the two move buttons (always) and the Pass button
  (only during `POST_SHOT`) at the bottom corners, plus a
  "Turn N - Pre-shot: X left" / "Turn N - Post-shot: X left" readout just
  below the existing "Missile Y" line, top-left.
- Deliberately NOT yet included: any AI/second character to actually pass
  the turn *to*, health/damage on a hit, and a real avatar sprite (still
  the same cyan marker circle Phase 8 introduced for the launch point) -
  all future work once this mechanic itself is confirmed to feel right.

**First on-device test result: the counterclockwise ("<") move button did
nothing, while clockwise ("&gt;") worked correctly** (budget countdown, firing
gate, post-shot movement, turn hand-off - the whole cycle worked once
using only the working button). Root cause: the "<" button sat only 16px
in from the screen's left edge - squarely inside Android's left-edge
back-gesture zone, which intercepts touches there before the app ever
receives them. The "&gt;" button, ~190px further in, happened to clear that
zone by luck. **Fix:** `AvatarMovementController.MARGIN_REFERENCE_PX`
bumped from 16 to 140 - pushes both move buttons well clear of the edge
gesture zone (the top-right gravity-tuning buttons never had this problem
since they're nowhere near a gesture-heavy edge). Not yet re-confirmed
on-device.
- **That fix (bigger margin) turned out not to be the real bug** - on
  re-test, a DIFFERENT button was found broken (the more-central one, not
  the corner one), and Logcat diagnostics (added temporarily to
  [AvatarMovementController.touchDown]) showed taps landing entirely
  outside every button's real hit-test rectangle, offset rightward by a
  consistent amount - not an edge-gesture problem at all.
- **Actual root cause, found via that Logcat data plus device info (Fold 8,
  unfolded, held in portrait - a much wider-than-9:16 aspect ratio than any
  earlier test device):** `PlayScreen`'s world camera uses a `FitViewport`
  locked to `WORLD_WIDTH:WORLD_HEIGHT` (9:16). On a screen much wider than
  that ratio, `FitViewport` letterboxes - it shrinks/centers its OpenGL
  viewport rather than using the full screen. `render()` never reset the
  GL viewport back to full-screen before drawing the HUD layer (buttons,
  text, all positioned via the separate full-screen [hudCamera]) - so
  every HUD element was actually being drawn compressed into that
  narrower letterboxed strip, while Android reports touch coordinates in
  true full-screen space. Result: HUD elements visually offset from where
  they were tappable, by an amount that grows with how far the device's
  aspect ratio departs from 9:16 - explaining why this never showed up in
  Phase 7/8 testing (presumably done on a narrower screen with little or
  no letterboxing) and only appeared now, on the Fold's much-wider main
  screen.
- **Fix:** `PlayScreen.render()` now calls `viewport.apply()` right before
  world-space rendering (guarantees the world's letterboxed rectangle is
  active for it specifically) and `Gdx.gl.glViewport(0, 0, Gdx.graphics
  .width, Gdx.graphics.height)` right before any HUD-space rendering
  (`renderHud`/`renderGravityDebugControls`/`renderMovementControls`) -
  resetting to the true full screen so HUD elements render exactly where
  they're hit-tested, regardless of the world viewport's letterboxing.
  This affects every HUD element on this screen (movement buttons, the
  gravity-tuning buttons, all HUD text), not just Phase 9's new controls -
  the gravity buttons likely only ever "worked" by coincidence, on a
  test device/orientation close enough to 9:16 that the letterboxing
  offset was small enough to still land inside a generously-sized button.
- The temporary Logcat diagnostics added to
  `AvatarMovementController.touchDown` for this investigation have been
  removed now that the viewport fix is confirmed on-device.
- **Confirmed on-device (post-fix): both move buttons, the pre-shot/post-
  shot budget countdown, firing gated to pre-shot only, the Pass button,
  and the turn counter advancing all work correctly.**
- **Known testing-only artifact, left as-is for now (Boo's call):** with
  no AI opponent yet, "turn passes" just resets your own budget instead of
  handing off to anyone - so a post-shot budget hitting zero immediately
  opens a new turn's pre-shot budget, letting you take what feels like 10
  steps in a row before firing again. Once a real opponent exists, its
  entire turn (move/shoot/move) happens in between, naturally separating
  those two budgets with real game state changing in between - nothing to
  fix here now, this goes away on its own once squads/AI are built.
- **Status: ✅ DONE - confirmed on-device**, including the viewport/
  letterboxing fix above (which also affects every other HUD element on
  this screen, not just Phase 9's controls - see that entry).

### How to test Phase 9 on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. Same scene as Phase 8 (star, two planets), but the cyan
   marker circle should now sit at the *top* of the launch (left) planet
   rather than fixed just above it.
3. Bottom-left corner: two buttons, "<" and ">". Tapping them should move
   the cyan marker around the launch planet's surface, one visible step
   per tap - "<" one direction, ">" the other.
4. Top-left, below "Missile Y": a readout reading "Turn 1 - Pre-shot: 5
   left", counting down by 1 each time you tap a move button. After 5 taps
   it should stop decreasing (movement no longer does anything) - confirms
   the pre-shot budget cap.
5. Aim and fire a shot as in Phase 8 (drag from the marker, release). The
   readout should immediately switch to "Turn 1 - Post-shot: 5 left", and
   a bottom-right "Pass" button should appear. Move buttons should work
   again, counting the post-shot budget down the same way.
6. Try firing again mid-post-shot (drag from the marker and release) -
   nothing should launch (firing is disabled outside the pre-shot phase).
7. Either use all 5 post-shot steps, or tap "Pass" early - either way the
   readout should reset to "Turn 2 - Pre-shot: 5 left", the "Pass" button
   should disappear, and you should be able to move and fire again exactly
   as in turn 1.
8. Confirm the marker (and therefore where a shot launches from/the aim
   line's anchor point) visibly moves with the avatar - fire a shot from a
   couple of different positions around the planet and confirm the launch
   point matches wherever the marker currently is, not the original Phase
   8 fixed spot.
9. If movement feels too fast/slow per tap, the arc feels wrong (e.g. steps
   look uneven in size), or the turn hand-off timing feels off in some
   way, tell me what it looked/felt like - `MOVEMENT_STEPS_PER_PHASE` and
   `MOVEMENT_STEP_ANGLE_DEGREES` are easy to retune once I know which
   direction it's off in.

### How to test the gravity-well milestone on-device

1. Sync Gradle, build and run as usual.
2. **Menu → Play.** No walls should be visible anymore — the debug
   wireframe view should show just two circles: a larger static one at the
   center of the screen (the star) and a smaller one orbiting around it.
3. **Watch the smaller circle for several seconds.** It should trace an
   *elliptical* loop, not a perfect circle: clearly closer to the star and
   visibly faster at one point of the loop, clearly farther and slower at
   the opposite point (correct, deliberate - see "Elliptical orbit,
   deliberately re-added" above) — not fall straight down, not fly
   straight off-screen, not spiral directly into the star, and not graze
   or visibly touch the star at its closest point.
4. **Watch for precession** (still relevant with an ellipse - arguably more
   visible than it was on a circle). Watch for 15-30+ seconds: the near
   point and far point of the ellipse should stay in roughly the same
   place relative to the star across many passes, not slowly rotate around
   it. Some tiny residual drift may still be visible (no discrete
   force-and-step simulation is perfectly exact), but it should be subtle,
   not an obvious steady rotation.
5. **Drag test**: touch and drag the orbiting body — it should still
   respond to the drag (MouseJoint) exactly as before, and should resume
   being pulled by gravity once released, likely settling into a
   different-looking orbit than before the drag (expected — dragging
   changes its position/velocity, which is genuinely a different orbit,
   not a bug).
6. **HUD check**: the top-left "Y: &lt;number&gt;" label should keep
   updating continuously with the orbiting body's changing Y position
   (not the star's, which never moves) — confirms the HUD is tracking the
   right entity.
7. If the orbit looks wrong in some other way (grazes/hits the star,
   flies off screen, doesn't curve at all, still precesses noticeably),
   tell me what it looked like — the tuning constants (`GravitySystem.G`,
   `PlayScreen`'s `STAR_MASS`/`ORBIT_RADIUS`/`ORBIT_SPEED_FACTOR`) are easy
   to adjust once I know which direction it's off in.

## Phase 10: health/damage

**Status: ✅ DONE - confirmed on-device.** Implements the "health lives
on characters, not celestial bodies" half of "Core gameplay loop" above,
same deliberately-minimal scoping as every phase before it: proves the
hit-damage-defeat mechanic works end to end before a real character or AI
carries it.

- **`HealthComponent`** (new, in `Components.kt`) - `maxHp`, a private-set
  `currentHp`, an `applyDamage(amount)` method (clamps at zero, doesn't go
  negative), and an `isDefeated` check. Deliberately owns its own damage
  math rather than letting callers poke `currentHp` directly.
- **`PlayScreen` now spawns a target entity** near the target planet - a
  small static body at the same clearance above the surface as the
  avatar's launch point, tagged `HealthComponent(TARGET_MAX_HP)` (100).
  Deliberately NOT a full character yet: no movement, no turn structure,
  no AI of its own - just something with HP to shoot at, so this phase can
  focus purely on the damage mechanic in isolation.
- **`ProjectileContactListener`** (Phase 8's "detect a hit" class) now
  actually applies damage: on a missile's `beginContact`, if the other
  body carries a `HealthComponent`, `MISSILE_DAMAGE` (25, illustrative -
  4 hits to defeat the 100-HP target) is subtracted from it; if that
  reduces it to zero, that entity is queued for removal through the exact
  same deferred-removal path the spent projectile already uses (both are
  just "destroy this body, remove this entity" by the time `flushRemovals`
  runs - no separate code path needed for "defeated" vs. "spent
  projectile"). A hit on something with no `HealthComponent` (a planet,
  the star) behaves exactly as it did in Phase 8 - projectile removed, no
  damage applied anywhere.
- New third HUD line, top-left (below "Missile Y" and the turn/phase
  readout): "Target HP: X/100", switching to "Target: DEFEATED" once the
  target entity has actually been removed from the engine.
- Deliberately NOT yet included: celestial-body mass/cratering (a miss
  damaging a planet instead of a character), collateral/splash damage,
  the avatar having its own HP (nothing can hit it yet - no AI exists),
  and per-weapon damage differences (only one weapon/missile type exists
  so far, so every hit does the same `MISSILE_DAMAGE`) - all future work
  once a real character roster and AI opponent exist.

### How to test Phase 10 on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. Same scene as Phase 9, plus a new small circle near the
   top of the target (right) planet - the Phase 10 target.
3. Top-left, below the turn/phase readout: a third line reading
   "Target HP: 100/100".
4. Aim and fire a shot at the target circle. On a direct hit, the HP
   readout should drop to "75/100", the target circle should still be
   there (still has HP left), and Logcat (tag `ProjectileContactListener`)
   should show a "Hit - 75/100 HP remaining" line.
5. Keep landing direct hits. After the 4th hit, the target circle should
   disappear from the debug wireframe view entirely, the HUD line should
   read "Target: DEFEATED", and Logcat should show a "Target defeated"
   line.
6. A miss (hitting a planet or the star instead) should behave exactly as
   it did in Phase 8 - missile disappears, no change to the Target HP
   line, no damage-related Logcat lines.
7. If the damage amount or target HP feels off (too tanky, dies in one
   hit, etc.), tell me what it looked/felt like -
   `ProjectileContactListener.MISSILE_DAMAGE` and `PlayScreen
   .TARGET_MAX_HP` are easy to retune once I know which direction it's
   off in.

## Phase 11: mutable celestial-body mass

**Status: ✅ DONE - confirmed on-device.** Implements the other half of
"Core gameplay loop" above (Phase 10 did character health; this does
celestial-body mass): chipping away a body's mass measurably weakens its
gravity well in real time, and reducing it to zero destroys the body
entirely - same deliberately-minimal scoping as every phase before it.

- **`GravitySourceComponent.mass` is now mutable.** Was a `val` set once at
  construction; now a private-set `var` with its own `applyDamage(amount)`
  (clamps at zero) and `isDestroyed` check, mirroring `HealthComponent`'s
  shape. `GravitySystem.applyForces()` already read `mass` fresh every
  physics tick (no change needed there) - so a hit's effect on gravity is
  immediate, including on an already-in-flight missile, same as the
  existing gravity-multiplier debug tool.
- **New `isDamageable` flag** on `GravitySourceComponent` (defaults `true`)
  so a source can opt out. The star opts out (`isDamageable = false`) -
  every design conversation about this mechanic has been about
  planets/moons specifically, and making the star destructible this early
  would remove the scene's only reliable gravity anchor without any
  design decision behind it yet.
- **Only the target planet is tagged `GravitySourceComponent` this phase**
  (`TARGET_PLANET_MASS` = 2, much weaker than the star's 9) - the launch
  planet is deliberately left as a plain non-gravity body, same Phase 8
  simplification as before, to keep the number of new gravity sources Boo
  is feeling out at once to just one. Every celestial body is still
  confirmed to eventually pull - this is a rollout order, not a final
  design line.
- **`ProjectileContactListener` now dispatches by whichever component the
  thing hit actually carries:** a `HealthComponent` (a character) takes
  `MISSILE_DAMAGE` off its HP, same as Phase 10; a `GravitySourceComponent`
  (a planet) takes `CELESTIAL_MASS_DAMAGE` (0.5, illustrative - 4 hits to
  destroy the 2-mass target planet, matching the character target's own
  "4 hits to defeat") off its mass, skipped entirely if `isDamageable` is
  false. A hit on neither (nothing right now, but future non-gravity
  celestial bodies) just removes the projectile, same as Phase 8. On mass
  reaching zero, the planet's entity/body is queued for removal through
  the exact same deferred pipeline as a spent projectile or a defeated
  character - **destroying a celestial body removes it and its gravity
  well entirely**, not just zeroes out its pull, matching Boo's explicit
  confirmation earlier in this doc.
- New fourth HUD line, top-left: "Target Planet Mass: 2.0", ticking down
  per hit, switching to "Target Planet: DESTROYED" once it's gone.
- **Still genuinely open (unchanged from the "Core gameplay loop" entry):**
  whether a damaged celestial body visibly shrinks as its mass drops -
  this phase deliberately keeps the visual radius fixed and only changes
  the internal mass value, the simpler of the two options, so the
  shrinking question can be answered later without having to undo
  anything here.
- Deliberately NOT yet included: the launch planet or the star taking
  damage, collateral/splash damage from a miss, moons/exotic bodies (no
  other celestial bodies exist yet to apply this to), and any visual cue
  besides the HUD number that a planet is losing mass (no crater/scarring
  effect, no radius change) - all future work.

### How to test Phase 11 on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. Same scene as Phase 10. Watch a missile's flight path near
   the target (right) planet now - it should curve slightly toward that
   planet too, not just the star (subtle, since its mass is much smaller
   than the star's - don't expect a dramatic effect).
3. Top-left, fourth line (below Target HP): "Target Planet Mass: 2.0".
4. Fire a shot that hits the target PLANET itself (not the small target
   character circle above it - aim slightly lower/into the planet's body).
   The mass readout should drop to "1.5", and Logcat (tag
   `ProjectileContactListener`) should show a "Celestial body hit - mass
   now 1.5" line.
5. Keep landing hits on the planet. After the 4th hit, the mass readout
   should read "Target Planet: DESTROYED", the planet's circle should
   disappear from the debug wireframe view entirely, and Logcat should
   show a "Celestial body destroyed - gravity well removed" line. (The
   small target-character circle that was sitting above it will still be
   there, now visually floating with no planet under it - a known
   cosmetic gap, not a bug, since that circle isn't attached to the
   planet's body.)
6. After destruction, fire another shot that passes near where the planet
   used to be - it should no longer curve toward that spot at all, only
   toward the star (and, if still present, toward the target character's
   `HealthComponent` hit only affecting its own HP as before).
7. Confirm hitting the target CHARACTER (Phase 10's small circle) still
   only reduces Target HP, not the planet's mass, and vice versa - the two
   damage types shouldn't cross-apply.
8. If the planet's gravity pull feels too strong/weak relative to the
   star, or the mass-to-hits ratio feels off, tell me what it looked/felt
   like - `TARGET_PLANET_MASS` (PlayScreen) and `CELESTIAL_MASS_DAMAGE`
   (ProjectileContactListener) are easy to retune once I know which
   direction it's off in.

## Phase 12: a minimal AI opponent

**Status: ✅ DONE - confirmed on-device.** First thing on the target
side that actually acts instead of just sitting there - makes the turn
structure (Phase 9), character health (Phase 10), and celestial mass
(Phase 11) all mean something together for the first time, instead of
each being testable only in isolation.

- **`AiTurnController`** (new file) - waits `AI_THINK_DELAY_SECONDS` (1s,
  pacing only) after `startTurn` is called, then fires one shot from a
  fixed point (the same spot Phase 10's target character sits at - the AI
  has no movement of its own yet) toward wherever the player's avatar was
  standing *at the moment the turn started* (a snapshot, not a moving
  target - the player can't act again until this turn ends anyway).
  Deliberately the simplest possible aim: a straight line at
  `AI_AIM_SPEED`, completely ignoring how gravity will curve the shot in
  flight. No movement, no smarter targeting - purely proving the hand-off
  itself works.
- **`AvatarMovementController` gained an `onTurnPassed` callback**
  (defaults to a no-op), fired from the existing `passTurn()` regardless of
  which of its two triggers (post-shot budget hitting zero, or an early
  Pass tap) caused it. `PlayScreen` wires this to
  `aiTurnController.startTurn(avatarMovementController.position)`.
- **Player input disabled during the AI's turn** - rather than teaching
  every input class (`AvatarMovementController`, `SlingshotInputProcessor`)
  about a "whose turn is it" flag, `PlayScreen` now builds two
  `InputMultiplexer`s once in `show()` - `fullInputProcessor` (everything)
  and `restrictedInputProcessor` (just `BackKeyHandler` and
  `GravityDebugController` - pausing and the gravity-tuning tool always
  stay available) - and swaps `Gdx.input.inputProcessor` between them on
  the turn hand-off (`onTurnPassed`) and hand-back
  (`AiTurnController.onTurnComplete`).
- Turn/phase HUD line (second line, top-left) now reads
  "Turn N - AI's turn..." while `aiTurnController.isTurnActive`, instead
  of the usual pre-shot/post-shot budget readout.
- **Deliberately, the player's avatar still cannot actually be hit.** It
  has never had a Box2D body of its own (`launchPoint`/the debug marker
  circle track `AvatarMovementController`'s position, but nothing
  physical exists there for a projectile to collide with), so an AI shot
  that reaches the avatar's location just... passes through, or hits the
  launch planet behind it. Giving the avatar a real (kinematically-moved,
  not physics-simulated) body plus its own `HealthComponent` is real,
  scoped future work - deliberately split out rather than bundled into
  this phase, so "the turn hands off and the AI can act" could be proven
  and tested on its own first.
- Known cosmetic gap: the move/Pass buttons are still drawn during the
  AI's turn (just non-functional, since their input processor isn't
  active) rather than visually greyed out or hidden - acceptable for this
  testing phase, not fixed here.
- **First on-device test found a real bug: the AI's shot visually
  originated from the player's own position, not the target planet.**
  Root cause: `PlayScreen.fireMissile()` always spawned the missile at the
  shared `launchPoint` field (continuously synced to the player avatar's
  position every frame) regardless of who called it - passing the same
  function as `onFire` to both `SlingshotInputProcessor` (player) and
  `AiTurnController` (AI) meant the AI's computed aim/velocity was correct,
  but the spawn location was wrong. **Fix:** `fireMissile` now takes an
  explicit `origin: Vector2` parameter instead of reading `launchPoint`
  itself - the player's call site passes `launchPoint`, the AI's passes
  `aiLaunchPoint`. Not yet re-confirmed on-device.
- **Status: ✅ DONE - confirmed on-device.** The origin-fix was never
  re-tested in isolation, but Phase 13's testing necessarily exercises the
  same `fireMissile` code path on every AI turn, and Boo reported no
  problem with shot origin - treated as confirmed.

### How to test Phase 12 on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. Play a turn as usual - move (optional), fire a shot,
   move again (optional), then either exhaust your post-shot budget or
   tap Pass.
3. The moment your turn ends, the turn/phase HUD line should switch to
   "Turn N - AI's turn...", and your move buttons and aiming should stop
   responding to touch entirely (Back and the gravity-tuning buttons
   should still work, though).
4. After about a second, a missile should launch on its own from near the
   target planet, heading roughly toward wherever your avatar was
   standing, curving under gravity same as your own shots do (the AI
   doesn't compensate for that curve, so it may miss more often than a
   human aiming carefully would - that's expected, not a bug).
5. Once the AI's shot resolves (hits something, or the projectile is
   otherwise removed), control should return to you: the HUD line goes
   back to "Turn N+1 - Pre-shot: 5 left", and your move/aim input works
   again.
6. Confirm the AI's shot behaves exactly like your own on impact - if it
   hits the target character or planet, existing Phase 10/11 damage
   applies (it could, in principle, damage its own side's target/planet
   if the shot curves back into them - a known quirk, not guarded against
   this phase). If it reaches roughly where your avatar is standing,
   confirm nothing happens (no damage, no HUD change) - expected per the
   "avatar can't be hit yet" scope note above.
7. If the AI's think-delay feels too long/short, or its shot speed feels
   way off (always falls hopelessly short or flies way past everything),
   tell me what it looked like - `AI_THINK_DELAY_SECONDS` and
   `AI_AIM_SPEED` (both in `PlayScreen`) are easy to retune.

## Phase 13: the player's avatar can take damage

**Status: ✅ DONE - confirmed on-device.** Closes the gap Phase 12
deliberately left open - the avatar can now actually be hit, making this
the first phase where a full player-vs-AI exchange (move, shoot, get shot
back at, take damage) is possible end to end.

- **The avatar now has a real Box2D body** (`avatarBody`, new) - Kinematic,
  not Dynamic or Static: it moves entirely under
  `AvatarMovementController`'s direct control (button taps), never under
  physics forces, so Kinematic is the body type actually meant for "moves
  via direct position control but still participates in collision
  detection." `render()` keeps its position synced to
  `AvatarMovementController`'s logical position every frame
  (`avatarBody.setTransform(...)`), the same place `launchPoint` already
  was.
- **Tagged `HealthComponent(AVATAR_MAX_HP)`** (100, matching the target) -
  since `ProjectileContactListener`'s damage dispatch (Phase 10/11) was
  already generic (whichever component the hit entity carries decides what
  happens), **no changes were needed there at all** for the avatar to
  become damageable.
- **A new self-collision problem this exposed, and its fix:** a missile
  spawns exactly at its firer's own position (`launchPoint` for the
  player, `aiLaunchPoint` for the AI) - harmless when nothing physical
  existed there, but now that both the avatar and the target character
  have real fixtures sitting exactly there, a freshly-fired missile would
  otherwise immediately, physically collide with (and bounce off) its own
  firer the instant it's created. Fixed with Box2D collision-filter
  categories (`CATEGORY_PLAYER_AVATAR`, `CATEGORY_AI_TARGET`) and a new
  `fireMissile(origin, velocity, excludeCategory)` parameter - each side's
  missile excludes only its own firer's category via `maskBits`, so it
  still collides normally with everything else (including the *other*
  side's character, planets, the star).
- Two entities now carry `HealthComponent` (target character + avatar), so
  the HUD methods that used to find "the" health/gravity-source entity via
  a family query (`renderTargetHud`, already the pattern
  `renderTargetPlanetHud` used for the star-vs-planet ambiguity) now read
  `targetCharacterEntity`/`avatarEntity` directly instead - both newly
  captured as fields (the target character's was previously created
  anonymously).
- New fifth HUD line, top-left: "Player HP: X/100", switching to
  "Player: DEFEATED" once it hits zero - same format as the target's line.
- Deliberately NOT yet included: any actual game-over/win-loss flow when
  either side is fully defeated (both sides can currently drop to 0 HP and
  just... stay that way, with a "DEFEATED" label and no further
  consequence) - that needs a real squad/roster concept first (per the
  "Core gameplay loop" entry's win condition: *every* character on a side,
  not just one) and is future work once squads exist.

### How to test Phase 13 on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. Fifth line, top-left (below Target Planet Mass): "Player
   HP: 100/100".
3. Fire a shot as usual - confirm it does NOT immediately vanish/hit
   anything the instant you release (the self-collision-with-your-own-
   avatar bug this phase's filtering prevents) - it should fly and curve
   normally, exactly as before.
4. Complete your turn (exhaust post-shot steps or tap Pass) and let the
   AI take its turn. If its shot actually reaches your avatar's current
   position, the Player HP line should drop by 25 (to "75/100") and
   Logcat (tag `ProjectileContactListener`) should show a "Hit -
   75/100 HP remaining" line - confirms the AI can now actually hurt you.
5. Keep taking hits (may take several AI turns, since its aim is simple
   and won't always connect). After Player HP reaches 0, the HUD line
   should read "Player: DEFEATED" - and, per the scope note above, nothing
   else should happen (no game-over screen, you can still technically keep
   playing) - that's expected for this phase, not a bug.
6. Confirm firing a shot yourself still only ever damages the *target*
   character/planet, never your own avatar, and vice versa for the AI's
   shots - the collision-filter fix should make each side's missile
   invisible to its own character, not to the opposing one.
7. If the avatar's hitbox feels too small/large relative to the visible
   cyan marker, or 100 HP / 25 damage-per-hit feels like the wrong pace
   for how often the AI actually lands a hit, tell me what it looked/felt
   like - `AVATAR_RADIUS`, `AVATAR_MAX_HP` (`PlayScreen`), and
   `MISSILE_DAMAGE` (`ProjectileContactListener`) are easy to retune.

**Confirmed working on-device (Sept 2026):** damage mechanic itself
lands correctly (HP drops, DEFEATED shows). Boo's feedback: "there is
not intelligence to the shot back. its just starting at the player dot
even if on other side of planet" - the AI's straight-line aim (the
Phase 12 simplification, documented above) doesn't account for the
planet potentially blocking a direct line to the player, so it takes
shots that visibly can't connect. Confirmed as real feedback, not yet
scoped into a phase - candidate for a future "smarter AI aim" phase
(options include: skip/delay firing when line-of-sight to the target is
blocked by a planet, sample a few candidate launch angles and pick one
whose *simulated* (gravity-curved) trajectory has a clear path, or both).

## Phase 14: the AI checks its shot before taking it

**Status: ✅ DONE - confirmed on-device.** Directly answers Boo's
Phase 13 feedback: "there is not intelligence to the shot back. its just
starting at the player dot even if on other side of planet." Picks the
"line-of-sight gate" direction, combined with letting the AI actually move
to try to clear that gate rather than just holding fire when blocked.

- **`AiTurnController` gains its own position and movement**, mirroring
  `AvatarMovementController`: an `angleDegrees` around the target planet's
  center, at the same fixed height above its surface the AI's spot has
  always been at, with a `position` getter using the identical formula.
  It's no longer a fixed point - it's a body that can move around its own
  planet, just like the player's avatar can move around the launch planet.
- **Before firing, it searches for a clear shot.** `reposition()` tries
  candidate angles - current position first, then ±1 step, ±2 steps, up to
  ±`stepsPerPhase` steps (reusing the player's own movement budget: 5 steps
  of 15 degrees, so up to 75 degrees either direction) - checking each
  candidate's straight-line path to the player's snapshotted position
  against three obstacles: the star, the launch planet, and the target
  planet itself. The first fully-clear candidate wins; trying closest-first
  means a shot that was already clear (the common case) costs no movement
  at all - the AI only relocates when it actually needs to. If nothing in
  range is fully clear, it fires anyway from whichever candidate had the
  least obstruction, rather than refusing to act and stalling the game.
- **The target character's body is now Kinematic, not Static** (same body
  type change Phase 13 made for the avatar, and for the same reason - it
  needs to actually move under direct control while staying collidable).
  `PlayScreen` syncs its position to `aiTurnController.position` every
  frame, same pattern as `avatarBody`.
- **`aiLaunchPoint` (the old fixed spot) is gone** - the AI's own current
  `position` is now the single source of truth for both where its body is
  drawn and where its shot spawns from, so `fireMissile`'s `onFire` lambda
  now receives an explicit `origin` from `AiTurnController` instead of
  closing over a separately-tracked field.
- **Still deliberately simple, on purpose:** the obstruction check is a
  straight-line-vs-circle test, not a simulation - it finds an angle with
  an unblocked *straight* line to the player, but the actual missile still
  flies a gravity-curved path, so a "clear" straight line doesn't guarantee
  the real shot won't clip something after launch (and conversely, a shot
  that looks blocked in a straight line might actually curve around the
  obstacle just fine - the AI doesn't know that). It also doesn't yet
  account for the target planet being partially destroyed (Phase 11's
  mutable mass/`isDestroyed`) - a destroyed planet still counts as a full
  obstacle here, a known minor gap. A genuinely smarter aim - simulating
  candidate trajectories under real gravity the way a human eventually
  learns to arc a shot around a planet - is real future work.
- **No animation yet** - repositioning happens instantly the moment the
  turn hands off (during `startTurn`, before the usual `AI_THINK_DELAY_SECONDS`
  pause even starts counting down), not as a visible step-by-step walk.
  Boo will see the target marker jump to its new spot right away, then a
  beat, then the shot - not smoothly slide there. Matches the project's
  existing style (the player's own movement isn't animated/tweened either
  - button taps jump the angle directly), just noting it since this is the
  first time the AI's position visibly changes at all.

### How to test Phase 14 on-device

1. Sync Gradle, run on-device as usual.
2. Play a turn where you deliberately move your avatar to the far side of
   the launch planet (away from the target planet) before ending your
   turn (fire or Pass) - the exact scenario Boo's feedback described.
3. Watch the target marker (on the target planet) the moment your turn
   ends: if your position is blocked by the launch planet from the AI's
   usual spot, the target marker should visibly jump to a new spot around
   the target planet before the AI fires (roughly a second later, same
   pacing as before) - confirms the AI actually repositioned instead of
   firing blind from the same place every time.
4. If you stay on the near side (already has a clear shot to the AI's
   usual spot), the target marker should NOT move at all before firing -
   confirms the AI only relocates when it actually needs to, not every
   turn.
5. Play several more turns, moving to different spots each time (near
   side, far side, extreme angles) - the AI's shot should now noticeably
   more often actually reach roughly toward you instead of just plowing
   straight into the launch planet immediately after leaving the target
   planet.
6. It won't be perfect - the AI still doesn't compensate for gravity's
   curve, so some shots will still miss even with a "clear" straight-line
   angle chosen; that's expected, not a bug, per the scope note above.
7. If the AI's reposition ever looks wrong - jumps somewhere that's
   obviously still blocked, or moves when it didn't need to, or the
   instant jump feels too jarring - tell me what it looked like.

## Phase 15: the AI's aim is gravity-aware

**Status: ✅ DONE - confirmed on-device.** The outward-centered aim-search
fix (see below) was re-tested and confirmed better. Answers the other half of
Boo's Phase 13 feedback ("there is not intelligence to the shot back") -
Phase 14 fixed the "fires blind through a planet" case, but even from a
spot with a clear *straight* line, the real missile still curves under
gravity, so a shot aimed straight at the target could still miss. This
phase gives the AI a genuine, if still bounded, aim search instead of
always computing one straight-line velocity.

- **`AiTurnController.searchAim`** (new) replaces the old one-line
  "velocity = direction to target * aimSpeed" with a real search: it
  sweeps aim angle ±45° around that same straight-line direction in 5°
  steps (19 angles), and at each angle also tries 3 speeds (0.7x/1x/1.3x
  `AI_AIM_SPEED`) - 57 candidate shots in total, all cheap enough to run
  well within the existing `AI_THINK_DELAY_SECONDS` pause.
- **Each candidate is actually simulated, not guessed.**
  `simulateClosestApproach` (new) runs a lightweight point-mass physics
  integrator - the exact same inverse-square gravity formula
  `GravitySystem.applyForces` itself uses, stepped at the same 1/60s tick
  - forward in time for each candidate, tracking how close it gets to the
  target and stopping early if it first hits an obstacle (the star or
  either planet), since a real missile would be destroyed there. No real
  Box2D body is needed for this - gravity's pull on a body doesn't depend
  on that body's own mass (a feather and a bowling ball fall at the same
  rate), so a plain Vector2-based simulation is enough to predict it.
- **`GravitySystem` gained `currentSources()`** - a snapshot of every
  active gravity source's live position and mass, read the same way
  `applyForces` itself reads them. `MIN_DISTANCE` is no longer private, so
  the simulation's clamp exactly matches the real one. Together with `G`
  and the live `gravityMultiplier`, this is everything the simulation
  needs, fetched fresh once per AI turn (not once per candidate or per
  simulated step - nothing changes mid-search) so the prediction is never
  stale relative to whatever's currently live - including Boo's own
  on-device gravity-multiplier tuning, and a target planet that's been
  partially destroyed and pulls weaker as a result.
- **Deliberately still bounded, not a full optimizer:** a fixed 57-shot
  sweep, not an exhaustive or adaptive search - a genuinely better shot
  that falls outside that angle/speed range simply won't be found. It also
  still runs *after* Phase 14's position search, not together with it - the
  AI picks where to stand using the old straight-line-only obstruction
  check, then picks how to aim from there using the new gravity-aware one;
  a combined position+aim search (potentially finding a spot AND an arc
  neither phase would find alone) is real future work, not this phase.
- **Added after first on-device look: a drawn flight trail.** Boo's first
  reaction to the aim search was "if it is curving it's very difficult to
  tell" - fair, since nothing before this drew the missile's actual path,
  only its current position. `TrailComponent` (new, in `Components.kt`)
  records each projectile's last `TRAIL_MAX_POINTS` (90, ~1.5s at 60fps)
  positions every frame; `PlayScreen.renderProjectileTrails()` draws that
  history as a solid orange line, so the curve (or lack of one) is now
  directly visible instead of only inferable. Purely visual - doesn't
  change any physics or aim logic. Also added: a one-line Logcat diagnostic
  (tag `AiTurnController`) every time the AI fires, logging exactly how far
  its chosen aim deviates from a straight line (degrees), its chosen speed,
  and the predicted closest approach - hard numbers to check against the
  trail if the visual still isn't conclusive.
- **First real on-device test found a real bug: the AI didn't try to curve
  around its own planet.** Boo confirmed the player's own shots genuinely
  curve (walked to the far side of the launch planet and fired a shot
  around the star to connect), but then moved the AI to the far side of
  *its own* planet and it "still, more or less, fired in the same
  direction... like it didn't notice the planet it was on." Root cause:
  the aim search swept +-45 degrees around a straight line drawn toward
  the target - but from the far side of its own planet, that straight
  line points directly *into* the planet, so every candidate near it
  self-collided in the first simulated step or two. None of them could
  ever beat the useless straight-line default the search started with, so
  the AI ended up firing that same straight (and wrong) shot every time
  from that stance. **Fix:** the sweep is now centered on the AI's own
  outward-facing direction (`angleDegrees` itself - away from its own
  planet's center, guaranteed clear of self-collision at the start of
  every candidate) instead of the straight line to the target, and widened
  from +-45 to +-120 degrees so it still comfortably covers the
  straight-line direction whenever *that's* actually clear (the common
  case). Re-tested on-device and confirmed better.

### How to test Phase 15 on-device

1. Sync Gradle, run on-device as usual.
2. Play several turns, varying your avatar's position each time (near
   side of the launch planet, far side, in between). Watch the orange
   trail drawn behind each missile (yours and the AI's) as it flies - it
   should visibly bend, more so on some shots than others, rather than
   staying a perfectly straight line. Watch how often the AI's shots now
   actually travel toward you and curve sensibly, versus before (Phase
   12-14) where a "clear line" shot still flew perfectly straight and
   often missed a moving/angled target.
3. Specifically try a position where a straight line to the AI would
   graze past the edge of the launch planet (not fully blocked, just
   close) - Phase 14's reposition might leave the AI there since the
   straight line is technically clear, but Phase 15's aim search should
   now be able to angle the shot to actually connect better, or at least
   visibly try a non-straight path (watch the missile's curve as it
   flies).
4. This costs a little more "thinking" time per AI turn (57 simulated
   candidate shots) - confirm the AI's turn still feels reasonably snappy
   and doesn't introduce a noticeable extra pause/stutter beyond the
   existing `AI_THINK_DELAY_SECONDS` beat.
5. It still won't hit you every time - the search is bounded (±45°, 3
   speeds - see the scope note above), and it's still choosing based on a
   fixed snapshot of where you were standing, not tracking you live. Missed
   shots are expected, not a bug - what should change is that misses now
   look like genuine attempts (curving plausibly toward you) rather than
   the old "beelines straight through the planet in front of it" failure
   mode.
6. If the AI's shots still look "dumb" in a specific way, or the extra
   think-time feels too long, tell me exactly what you saw -
   `AI_AIM_ANGLE_SEARCH_DEGREES`, `AI_AIM_ANGLE_STEP_DEGREES`,
   `AI_AIM_SPEED_MULTIPLIERS`, and `AI_TRAJECTORY_SIM_MAX_SECONDS` (all in
   `PlayScreen`) are easy to retune.

## Phase 16: aim trajectory preview

**Status: ✅ DONE - confirmed on-device.** Boo's first "visual
overlays" request - picked from a few options (health bars, hit/damage
feedback, aim preview) as the starting point, since it directly reuses the
gravity-simulation work Phase 15 just built for the AI's aim search rather
than starting a new mechanic from scratch.

- **`TrajectorySimulator`** (new file) - the point-mass gravity integrator
  that used to live only inside `AiTurnController`'s aim search, pulled out
  into its own small shared class so both the AI's aim search and this new
  player-facing preview run the exact same stepping math instead of two
  copies that could quietly drift apart over time. `AiTurnController` was
  refactored to use it too (no behavior change there - same math, just no
  longer duplicated).
- **`PlayScreen.renderAimTrajectoryPreview()`** (new) - while the player is
  actively pulling back to aim, predicts the shot's actual gravity-curved
  path *before* release (using the exact velocity formula
  `SlingshotInputProcessor.touchUp` would fire, computed here since that
  formula only actually runs on release) and draws it as a dotted line -
  small dots, not a solid line, so it's never visually confused with
  `renderProjectileTrails`'s solid trail (Phase 15) left behind by a real
  in-flight missile. "Dots" = a projection; "solid line" = this already
  happened.
- **Stops early at an obstacle.** The preview shares `celestialObstacles`
  (now a `PlayScreen` field, factored out of what used to be inline in
  `aiTurnController`'s own constructor call) with the AI's aim search, so
  if the predicted path would hit the star or a planet, the dots stop
  right there instead of continuing through it - the preview shows exactly
  as far as the real shot could actually fly.
- Only shown during `Phase.PRE_SHOT` (gated on
  `avatarMovementController.canFire`, same gate the actual fire button
  uses) - no preview during post-shot repositioning, when firing isn't
  possible anyway.
- **Deliberately not exact.** It's a prediction using the gravity sources'
  state *at the moment you're dragging*, extrapolated up to
  `AIM_PREVIEW_MAX_SECONDS` (3s) - if a celestial body's mass changes (a
  planet takes damage) between when you start dragging and when you
  release, or if the AI's projectile briefly makes a source's live state
  weird mid-simulation (it doesn't - this only reads static positions
  each frame, so this is a non-issue in practice, just noting the general
  shape of the guarantee), the preview reflects gravity as it was at prediction
  time, same caveat every "aim assist" in a physics game has.

### How to test Phase 16 on-device

1. Sync Gradle, run on-device as usual.
2. Start pulling back to aim (touch down near the launch marker and drag).
   A dotted gray line should appear, curving out from the launch point -
   confirm it updates live as you keep dragging (different pull
   direction/distance should visibly reshape the dotted arc).
3. Release the shot and compare: the real missile's solid orange trail
   (Phase 15) should closely follow wherever the dotted preview predicted,
   confirming the preview is actually accurate and not just a vague
   guess.
4. Try a pull strong/angled enough that the predicted path would fly
   through a planet or the star - confirm the dots stop right at that
   obstacle instead of continuing through or past it.
5. Confirm no preview is drawn during your post-shot repositioning phase
   (after you've already fired this turn) even if you touch near the
   launch marker - firing (and so previewing) shouldn't be possible then.
6. If the dotted line is hard to see against the background, too
   short/long to be useful, or the dot spacing looks off, tell me what
   you saw - `AIM_PREVIEW_MAX_SECONDS`, `AIM_PREVIEW_DOT_INTERVAL_STEPS`,
   and `AIM_PREVIEW_DOT_RADIUS` (all in `PlayScreen`) are easy to retune.

## Phase 17: live-tunable shot speed

**Status: ✅ DONE - confirmed on-device.** Boo dialed the Shot Speed
control down to 0.4x and confirmed it felt right - now the new default
(see below). Boo's own diagnosis after Phase 16: shots feel "very
mathmatical and precise... too fast" - gravity's
pull builds up over *time in flight*, so a shot that crosses the whole
scene in a fraction of a second barely gives gravity (or the player) time
to do anything visible, however accurate the physics is. Two directions
were on the table (retune the fixed numbers directly, or add a live
on-device dial the way gravity already has one) - Boo picked the live
dial, the same fix `GravityDebugController` already proved out for "how
strong should gravity feel."

- **`ShotSpeedTuning`** (new) - a tiny holder for one live `multiplier`
  value, the shot-speed equivalent of `GravitySystem.gravityMultiplier`.
  Starts at `DEFAULT_MULTIPLIER` - originally 0.5 as a first-guess
  starting point, retuned to **0.4** once Boo dialed it in on-device and
  confirmed it felt right (see the on-device test section below).
- **`ShotSpeedDebugController`** (new) - two +/- tap zones, structurally
  identical to `GravityDebugController`, stacked directly below its button
  row (same right-edge corner) so both live-tuning tools sit together.
  Wired into both `fullInputProcessor` and `restrictedInputProcessor` (a
  standing tool, available regardless of whose turn it is, same as gravity
  tuning).
- **Applied everywhere a shot's speed is computed:** `SlingshotInputProcessor`
  (the player's actual fired shot), `AiTurnController` (the AI's aim
  search - `effectiveAimSpeed = aimSpeed * shotSpeedMultiplier()`), and
  `PlayScreen.renderAimTrajectoryPreview` (Phase 16's dotted preview, so it
  stays accurate to whatever speed is currently dialed in). All three read
  the live value fresh at the moment they need it - adjusting mid-game
  takes effect on the very next shot, no rebuild.
- **The AI's aim-search time window and the aim preview's simulated time
  window both scale inversely with the multiplier** (`effectiveSimMaxSeconds
  = baseSimMaxSeconds / shotSpeedMultiplier`) - a slower shot takes
  proportionally longer to travel the same distance, so without this, a
  slowed-down shot would look like it "can't reach" the target (AI aim
  search) or the preview's dots would just stop mid-flight, well short of
  where the shot actually ends up.

### How to test Phase 17 on-device

1. Sync Gradle, run on-device as usual.
2. Below the existing "Gravity xN.N" controls (top-right), a new row:
   "Shot Speed xN.N" starting at "x0.5", with its own +/- buttons.
3. Fire a shot at the default 0.5x - it should already feel noticeably
   slower/weightier than before Phase 17, with more visible hang-time and
   a more obvious curve (see the class doc comments for why slower also
   means "curves more," not just "takes longer").
4. Tap the Shot Speed +/- buttons and fire a few more shots at different
   multipliers - confirm the change takes effect immediately (no
   restart/rebuild needed) and the aim preview's dotted line (Phase 16)
   still accurately predicts where the shot ends up at whatever multiplier
   is currently set.
5. Let the AI take a turn or two at a low multiplier (e.g. 0.2x) -
   confirm its shot still travels the full distance toward you (not
   cutting off early/looking like it gave up partway) and its aim search
   still finds reasonable shots, not just noticeably worse ones because it
   ran out of simulated time.
6. Find whatever multiplier actually feels right and tell me the number -
   `ShotSpeedTuning.DEFAULT_MULTIPLIER` is a one-line change to make that
   the new starting point instead of leaving it something you have to
   redial in every fresh run.

**Confirmed on-device: 0.4x is the new default.** `ShotSpeedTuning
.DEFAULT_MULTIPLIER` updated from 0.5 to 0.4 - a fresh run now starts
already at the speed Boo confirmed felt right, no redialing needed.

## Phase 18: baseline sprite art (star + two planets)

**Status: Built, awaiting on-device test.** First real graphics pass -
everything on screen so far has been [Box2DDebugRenderer] wireframes.
Deliberately narrow scope, decided with Boo before building: only the
star and the two planets get real sprite art this phase; the avatar, AI
target, and missiles all stay as debug markers/wireframes for now, so
this is testable in one on-device pass without redesigning the whole
scene at once.

**Engine decision (discussed with Boo first):** no new graphics engine
needed or added - LibGDX already ships a full 2D sprite pipeline
(`SpriteBatch`/`Texture`/`TextureRegion`), completely separate from
Box2D (physics only) and from [ShapeRenderer] (debug-shape drawing,
already in use for the aim line/trajectory preview/flight trails). This
phase is just the first real use of that already-present sprite
pipeline, not a new dependency.

**Where the art came from.** The plan discussed with Boo was to pull
free CC0 placeholder art from Kenney.nl (a "Planets" pack exists that
would've been a near-perfect fit). In practice, neither the cloud
sandbox nor the device-bridge shell has network access to kenney.nl -
both sit behind an egress allowlist that only permits a short list of
package registries/Anthropic endpoints, confirmed by a direct `curl`
attempt from both getting rejected at the proxy (HTTP 403). Rather than
block the phase on that, the three sprites (star, launch planet, target
planet) were procedurally generated instead - a small numpy/Pillow
script renders each as a shaded sphere (radial lighting from a fixed
light direction, limb darkening, a few soft crater blotches on the
planets, per-pixel noise for texture, antialiased circular alpha edge)
and the star as a hot core with a soft glowing falloff past its disc.
Genuinely usable baseline art, not just solid-color circles - but if
Boo would rather have Kenney's real pack (or any other art), that's a
files-only swap: same filenames, same folder, zero code changes, since
loading is a plain `Gdx.files.internal("textures/...")` lookup. Boo can
grab it himself from kenney.nl (which his own browser can reach fine)
and hand over the PNGs whenever.

- **`android/src/main/assets/textures/`** (new folder) - `star.png`,
  `planet_launch.png` (warm rust/orange rocky world), `planet_target.png`
  (cool teal/blue oceanic world - deliberately different from the launch
  planet so the two are distinguishable at a glance, not just by
  position). All 256x256 RGBA PNGs with a transparent circular alpha
  mask (planets) or a soft glow-falloff alpha (star), same folder
  `AudioManager` already uses for `audio/tap.wav`.
- **`PlayScreen`** - new `worldBatch: SpriteBatch` (separate from the
  existing `hudBatch`, which draws screen-space HUD via `hudCamera`;
  `worldBatch` draws in the same world units/camera as `shapeRenderer`/
  `debugRenderer`, since these are real scene objects, not HUD) plus
  three `Texture` fields (linear-filtered for smooth scaling from a
  256px source down to ~1-1.6 world units on screen). New
  `renderCelestialSprites()` draws each texture centered on its known
  world position, sized to exactly match its Box2D fixture's diameter
  (`radius * 2`) - called in `render()` right before
  `debugRenderer.render(...)`, so the debug wireframe circle still draws
  on top of each sprite this pass. Deliberate for testing: if a sprite
  doesn't line up exactly inside its wireframe outline, that's
  immediately visible on-device, rather than trusting position/size by
  eye alone. Once confirmed aligned, hiding the wireframes for just
  these three bodies (while keeping them for the avatar/AI target/
  missiles, which don't have sprites yet) is a real future step, not
  done automatically here - `Box2DDebugRenderer` draws every body
  uniformly, so exempting specific ones needs a small deliberate change,
  not a one-line toggle.
- Textures are disposed in `PlayScreen.dispose()` alongside the other
  native-backed resources (`world`, `debugRenderer`, `shapeRenderer`,
  `hudBatch`) - same "must dispose explicitly or it leaks" rule already
  documented there.

### How to test Phase 18 on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. The star and both planets should now show real sphere
   art (star: glowing yellow/orange core; launch planet: rust/orange
   rocky; target planet: teal/blue) instead of plain wireframe circles -
   with each sprite's wireframe outline still visible right on top of it
   (expected this phase, see above - confirms alignment).
3. Confirm each sprite is centered correctly and sized to match its
   wireframe exactly (no visible offset, no sprite noticeably bigger or
   smaller than the circle it's supposed to fill).
4. Confirm the avatar marker, AI target, and any fired missiles still
   look exactly as before (plain wireframe/debug shapes) - this phase
   deliberately doesn't touch them.
5. Play through a normal turn or two (move, aim, fire, let the AI go) -
   confirm nothing about gameplay/physics/AI behavior changed, this is a
   visual-only phase.
6. If the art style, colors, or sizing feel off, or you'd rather swap in
   Kenney's real pack (or something else) instead of the procedural
   placeholders, tell me - either is a quick follow-up (regenerate the
   procedural script with different parameters, or swap in real PNG
   files at the same paths).

## Phase 19: player/AI avatar sprite art

**Status: Built, awaiting on-device test.** Second half of the "make it
playable" visual pass - see "Campaign progression ladder" above for the
full plan this is step one of. Same procedural-sphere approach as Phase
18 (no network access to pull pre-made art, see that phase's writeup),
tuned for a glossier "character token" look (added specular highlight,
no craters) rather than the planets' matte cratered-rock look, so the
two read as different kinds of objects at a glance, not just different
colors.

- **`android/src/main/assets/textures/`** - two new 128x128 RGBA PNGs,
  `avatar_player.png` (blue) and `avatar_ai.png` (red), per Boo's
  explicit color choice.
- **`PlayScreen`** - two new `Texture` fields alongside Phase 18's
  three, same linear-filtering setup. New `renderCharacterSprites()`
  draws each at its **live** logical position every frame -
  `avatarMovementController.position` for the player,
  `aiTurnController.position` for the AI - the same source of truth
  `render()` already uses to sync `avatarBody`/`targetCharacterBody`'s
  real Box2D transforms, so the sprite can never drift from the real
  hitbox. Sized to each side's own existing fixture radius
  (`AVATAR_RADIUS` vs `TARGET_RADIUS` - deliberately different sizes,
  unchanged from Phase 10/13, not something this phase touches). Called
  right after `renderCelestialSprites()`, still before
  `debugRenderer.render(...)` - same "wireframe stays visible on top for
  alignment verification" approach as Phase 18, same reasoning.
- Missiles/projectiles are NOT touched this phase - they stay plain
  Box2D wireframe circles, per the narrow scope Boo confirmed when this
  whole "make it playable" push was scoped.
- Textures disposed in `PlayScreen.dispose()` alongside Phase 18's.

### How to test Phase 19 on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. The avatar (your launch-point marker) should now be a
   glossy blue sphere, and the AI's target should be a glossy red sphere
   - both with a visible wireframe circle still drawn right on top
   (expected, alignment check, same as Phase 18's planets).
3. Move the avatar around its planet (the "<"/">" buttons) - the blue
   sphere should track exactly with the wireframe circle, no lag or
   offset.
4. Let a turn play out where the AI repositions itself (Phase 14) - the
   red sphere should likewise track the AI's wireframe circle as it
   moves, not just at its starting position.
5. Confirm the star/planets from Phase 18 and everything else
   (trajectory preview, flight trails, HUD) still look/behave exactly as
   before - this phase only adds the two new sprites.
6. If the colors, gloss/shine, or sizing feel off, tell me what you'd
   change - same quick-regenerate-the-script or swap-in-real-art
   follow-up path as Phase 18.

**Phase 19b addendum: missile sprite art.** Boo asked for missiles to
look like something too - "a smaller sphere, similar to the player
spheres... a contrasting color that is not harsh." Chose a soft
violet/amethyst - distinct from every color already on screen (blue
player, red AI, orange/teal planets, warm star, navy background),
pairs well with the existing orange flight trail (Phase 15) behind it.
`android/src/main/assets/textures/missile.png` (96x96, same glossy-
sphere generator as the avatars). `PlayScreen.renderMissileSprites()`
deliberately reuses `trailFamily` (every in-flight missile is already
`TrailComponent`-tagged, see `fireMissile`) instead of a dedicated
projectile family/mapper - one less thing to keep in sync - and loops
over every entity in it, since more than one missile can be in flight
at once. Sized to the missile's real `MISSILE_RADIUS` fixture, drawn at
its live Box2D position every frame. Called in `render()` after
`renderCharacterSprites()`, still before `debugRenderer.render(...)`.
**Status: Built, awaiting on-device test** (same test steps as above,
plus: fire a shot and confirm the violet sphere tracks the missile
exactly, with its orange trail visible behind it).

## Phase 19c: UI visual pass (starfield, real buttons, graphical stats HUD)

**Status: Built, awaiting on-device test.** Boo asked for three things in
one go: a background starfield, tap-zone buttons that "look like real
buttons in a game" (neutral grey, Boo's call - "grey is fine unless you
have a better idea for now"), and the plain-text turn/HP/mass readouts
turned into "a small HUD that graphically matches the rest of the design
language." All three are pure rendering changes - no gameplay/physics
logic touched.

**Starfield.** `PlayScreen.starfieldStars` - a fixed list of
`STARFIELD_STAR_COUNT` (70) small dim dots, generated once when the
screen is created (not regenerated per game, not per frame), scattered
randomly across the world bounds. Deliberately muted - brightness
capped at 0.30-0.70 (never full white) and radii kept small (0.012-0.04
world units) - so it reads as a backdrop, per Boo's explicit "doesn't
overwhelm what we have so far." `renderStarfield()` draws them via
`shapeRenderer` in world space, first thing every frame (before
`renderCelestialSprites`), so it's always behind every sprite.

**Real button art.** Three new procedurally-generated textures (same
"no network access to pull pre-made art" situation as Phase 18/19 - see
Phase 18's writeup):
- `button.png` - a rounded rect with a top-lit/bottom-shadowed grey
  gradient (a cheap fake bevel) and a darker border ring, for an
  actual "pressable" look instead of Phase 8-17's flat single-color
  rectangles.
- `panel.png` - a flatter, darker, semi-opaque rounded rect for HUD
  backgrounds - same rounded/bordered visual language as the button but
  clearly not tappable.
- `bar_pill.png` - a plain white rounded "pill" shape, meant to be
  tinted at draw time rather than shipped as several separate colored
  textures - `NinePatch.setColor(...)` right before each `.draw(...)`
  call switches its tint (dark grey for a bar's track, then whatever
  stat color for the fill drawn on top) - see `drawStatBar` below for
  why this has to be `patch.setColor(...)`, not `batch.setColor(...)`
  (NinePatch bakes its tint into its own cached vertex colors, not the
  batch's current color state).
- All three loaded as `Texture` + `NinePatch` (margins chosen safely
  larger than each texture's corner radius, so the rounded corners never
  stretch/distort regardless of the target button/panel size).
  `renderGravityDebugControls`, `renderShotSpeedDebugControls`, and
  `renderMovementControls` all swapped their `shapeRenderer.rect(...)`
  background fill for `buttonPatch.draw(...)` - no change to any
  button's actual hit-test rect/position logic, purely how the
  background pixel gets drawn.

**Graphical stats HUD.** The three separate plain-text HUD lines
(`renderTargetHud`, `renderTargetPlanetHud`, `renderPlayerHud`) plus
`renderMovementControls`' old turn/phase text line are gone, replaced
by one `renderStatsPanel()` method: a `panelPatch` background box
containing, top to bottom, the turn/phase readout (same text as
before, just relocated) and three label+value+bar rows (shared
`drawStatBar` helper) - player HP (blue, `playerBarColor`, matching
`avatar_player.png`'s hue), target HP (red, `aiBarColor`, matching
`avatar_ai.png`'s hue), and target planet mass (amber, `massBarColor`,
deliberately distinct from either character's color). Each bar's fill
width is its stat's live ratio (`currentHp / maxHp`, or
`mass / initialMass` for the planet) against a dark track drawn the
same way. The debug-only "Missile Y" line (`renderHud`) is untouched -
that one's programmer info, not part of what Boo asked to redesign.
- **`Components.kt`**: `GravitySourceComponent`'s constructor parameter
  `initialMass` became a retained `val` property (was write-once,
  discarded after seeding the mutable `mass` field) specifically so the
  mass bar has an actual "full" value to compute a ratio against,
  instead of an arbitrary hardcoded scale.

### How to test Phase 19c on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. A subtle field of small dim dots should be visible
   across the background - noticeable if you look, but not competing
   with the star/planets/spheres on top of it. If it reads as
   cluttered/distracting rather than subtle, tell me - `STARFIELD_STAR_COUNT`
   and the brightness range are easy to turn down further.
3. Every button (gravity +/-, shot speed +/-, move </>, Pass) should now
   look like an actual grey beveled button - lighter along the top edge,
   darker along the bottom, a visible border - not a flat solid-color
   rectangle like before.
4. Top-left: instead of five separate lines of plain text, there should
   now be one small panel box containing the turn/phase line and three
   labeled bars (Player HP in blue, Target HP in red, Target Planet Mass
   in amber), each bar shrinking as that stat drops. The standalone
   "Missile Y" line above the panel is unchanged.
5. Take a few shots, let the AI go, and damage both the target character
   and the target planet - confirm both the Target HP bar and the Target
   Planet Mass bar visibly shrink as HP/mass drop, and flip to
   "DEFEATED"/"DESTROYED" text (bar disappears/empties) at zero, same
   behavior as the old plain-text version.
6. If the button style, panel look, bar colors, or starfield density
   feel off in any way, tell me what you'd change - same
   regenerate-the-script or swap-in-real-art follow-up path as every
   art phase so far.

**First on-device screenshot found two real bugs, both fixed:**
- **Stats panel rows overlapped into an unreadable jumbled clump.**
  `renderStatsPanel`'s `rowHeight` was set to `HudFont.scaled(40f)` -
  smaller than this font actually renders at this scale (the old
  separate-line HUD used 60px of vertical gap per text-only line, proven
  comfortable; each new row also needs room for a bar underneath the
  text, which 40px can't fit at all). Result: each row's text started
  before the previous row's text/bar had finished, so all four rows
  visually collided into overlapping text with bars slicing through
  letters. **Fix:** `rowHeight` bumped to `HudFont.scaled(70f)`,
  `panelWidth` widened slightly (300 -> 320, the label+value text was
  also crowding the right edge), `panelPadding` 10 -> 12.
- **The panel background was barely visible against the space
  background.** `panel.png`'s original fill (22,27,38) was too close in
  value to the scene's navy clear color (11,18,32) - the box read as
  almost nothing, just a faint outline. **Fix:** regenerated with a
  noticeably lighter fill (32,38,52) and a brighter border (105,114,138)
  so the panel now clearly reads as a distinct HUD element.

**Second on-device screenshot (after the fix above) found a third bug,
also fixed:** the panel was now visible and rows weren't colliding with
each other any more, but each row's bar sliced straight through the
middle of that row's own label/value text - a "strikethrough" look, not
a bar sitting below the text. Root cause: `drawStatBar` positioned the
bar `HudFont.scaled(22f)` below the text's top - a guessed number that
turned out to be well within this font's actual rendered height at
Phase 7's tuned scale (2.4x, see `HudFont`'s class doc comment), not
below it. The first fix's `rowHeight = HudFont.scaled(70f)` was the same
kind of guess and had just happened to still be enough room row-to-row,
masking the fact that the *within-row* text/bar spacing was still wrong.
**Real fix, not another guess:** both `renderStatsPanel`'s `rowHeight`
and `drawStatBar`'s bar position now read `HudFont.font.lineHeight` -
the font's own actual measured line height at whatever scale/density
it's currently rendering at - instead of a hardcoded pixel count, with
three small named gap constants (`STATS_BAR_HEIGHT`,
`STATS_BAR_GAP_BELOW_TEXT`, `STATS_ROW_GAP`, all run through
`HudFont.scaled(...)` like every other fixed UI dimension in this file)
shared between both places so they can't silently drift out of sync
with each other again the way the old 70f/22f pair did.

**Aside confirmed during this same round: the round "notch" shape seen
in the second screenshot (briefly suspected as a bug) was a Samsung
system overlay (most likely an Edge panel/pop-up view handle), not
anything `PlayScreen` draws - confirmed by it being absent from the next
screenshot with no code change in between. Genuinely not an app bug,
noted here only so a future session doesn't rediscover the same dead
end.**

**Third real bug, also from this round, also fixed:** the turn/phase
text ("Turn 2 - Pre-shot: 0 left") ran past the panel's right edge -
`panelWidth` was yet another guessed constant (`HudFont.scaled(320f)`)
that didn't account for how long that text can actually get across
different turn numbers/phases/step counts. **Fix, same pattern as the
two bugs above - measure, don't guess:** `renderStatsPanel` now builds
every row's label/value strings FIRST, measures all of them with
`HudFont.widthOf(...)`, and sizes `panelWidth` to whichever is widest
(with a small minimum floor so the panel never gets oddly narrow) -
only then draws the panel background and content. The panel now can't
run out of room for its own text, no matter how long the turn counter
or a stat's numbers get.

## Phase 19d: AI accuracy pass (aim error + gravity-aware repositioning)

**Status: Built, awaiting on-device test.** Boo played a few turns and
flagged two real gameplay-quality issues, both diagnosed to their actual
root cause (not just symptom-patched) before fixing:

- **"It seems to take the exact same shot every time if I don't move...
  too easy to game."** Root cause: `AiTurnController.searchAim` is a
  fully deterministic search - same origin/target/planet state always
  produces the exact same "best" answer, with zero randomness anywhere
  in the pipeline. Not a bug exactly (the search is *supposed* to find
  the objectively best shot), but it means a stationary player can
  trivially learn and counter one fixed shot.
- **"The AI deciding to move for a better angle seems very primitive."**
  Root cause: `reposition()` scored candidate positions with
  `obstructionSeverity` - a straight-line-only check with no idea a real
  missile curves under gravity - while the actual shot decision
  (`searchAim`) was already the much smarter gravity-aware simulation.
  The two steps were answering different questions ("is there a clear
  straight line" vs. "what's the best curved shot"), which is exactly
  why movement felt disconnected/dumb next to the aiming.

**Fix 1: aim error.** `AiTurnController.applyAimError` (new) perturbs
`searchAim`'s already-best answer with a small random angle/speed jitter
right before firing - `AI_AIM_ERROR_DEGREES` (4°) and
`AI_AIM_ERROR_SPEED_FRACTION` (0.06, illustrative/not tuned, in
`PlayScreen`) control the range. Deliberately applied AFTER the search,
not folded into it - the search still always finds the objectively best
candidate; this only simulates imperfect *execution* of that shot, so
the AI doesn't get dumber, it gets less mechanically perfect. `<= 0f`
for either parameter disables that part of the jitter entirely, useful
for isolating other bugs without randomness in the way. Also a natural
future hook (not built yet): the campaign progression ladder (5/20/30
wins, see "Campaign progression ladder" above) could tighten this error
over time as an actual difficulty curve, not just more planets/
characters.

**Fix 2: gravity-aware repositioning.** `searchAim`'s core sweep-and-
simulate logic was extracted into a new shared method,
`bestAimFor(origin, baseAngleRadians, target, ...)`, returning both the
best velocity AND how close it predicted getting
(`Pair<Vector2, Float>`). `reposition()` now calls `bestAimFor` for
every candidate position it considers (closest-first, same movement
budget as before) and picks whichever position yields the best
predicted shot - not just an unobstructed straight line. Movement and
aiming are now judged by the exact same yardstick. `obstructionSeverity`/
`distanceFromSegment` (the old straight-line-only check) are removed -
no longer used anywhere. Early-exit changed from "first fully clear"
to "first predicted-approach this close to a direct hit"
(`repositionGoodEnoughApproach`, 0.25 world units) - conceptually the
same "stop once it's good enough" shortcut, just against the new,
better metric.

**Performance note (why this is fine despite being ~11x more simulation
work per turn):** `reposition` now runs a full `bestAimFor` sweep (the
same ~93-candidate simulation `searchAim` already does once) for every
candidate position it checks - up to 11 positions with the current
`MOVEMENT_STEPS_PER_PHASE` (5). That's roughly 250k simple vector-math
operations per AI turn, done once during the existing `AI_THINK_DELAY_
SECONDS` pause, not per frame - trivial for a phone CPU, no observable
impact expected. Flagging here in case on-device testing shows
otherwise (e.g. a visible stutter right as the AI's turn starts).

### How to test Phase 19d on-device

1. Sync Gradle, run on-device as usual.
2. Take a shot, then deliberately do nothing different (same avatar
   position) across a few of your turns while the AI keeps firing back -
   its shots should now vary slightly turn to turn (a bit of visible
   angle/speed variation) instead of looking pixel-identical every time.
3. The variation should read as "slightly imperfect," not "wildly
   random" - if it looks too twitchy/inaccurate or barely noticeable
   either way, tell me and `AI_AIM_ERROR_DEGREES`/
   `AI_AIM_ERROR_SPEED_FRACTION` are a quick retune.
4. Set up a scenario where the AI would need to move to get a decent
   shot (e.g., stand somewhere that puts a planet between you) - watch
   whether it now relocates to a position that sets up a genuinely
   better curved shot, not just anywhere with a technically-clear
   straight line.
5. Watch turn-hand-off timing (the "AI's turn..." pause) for any new
   stutter/delay compared to before - see the performance note above;
   this isn't expected to be noticeable, but worth explicitly checking
   since this phase meaningfully increased how much simulation runs per
   AI turn.
6. Logcat (filter `AiTurnController`) now logs both the search's true
   best answer and the actually-fired (post-error) speed in the same
   line, if you want to distinguish "the search chose a bad shot" from
   "the error jitter threw off a good one" while tuning.

## Phase 19e: AI full-planet repositioning + extra movement budget

Boo's on-device follow-up to Phase 19d: after the AI repositioned for a
gravity-assisted shot, he then moved into a spot that called for an easy
direct shot instead - but the AI "stayed in same position and made the
same shot as before." Root cause, confirmed from the screenshot he sent
(the AI's dot was sitting on the far side of its own planet, ~180 degrees
from the near side that would face him): `reposition()` only ever looked
at positions reachable *that turn* (+-75 degrees, the same per-phase
movement budget the player has) and simply gave up without moving at all
whenever nothing reachable beat staying put. A target requiring a big
swing around the planet left the AI stuck in place indefinitely instead
of visibly working its way there over several turns.

**Fix: `reposition()` now searches the entire planet.** All the way
around (same 15-degree resolution as before, 24 positions total) for the
single best predicted shot via `bestAimFor`, regardless of whether it's
reachable this turn - then moves `angleDegrees` as far toward that ideal
angle as the current movement budget allows, the short way around. If the
ideal spot is in reach, this lands exactly on it (same behavior as
before in the easy case). If it isn't, this is real progress rather than
a stall, and the next call re-runs the same full search from the new
position and keeps closing the gap turn by turn - Boo's explicit ask:
"if the ai thinks it needs 2 or 3 turns to get into a firing position,
that is ok. it should start to move in the right direction." The old
`repositionGoodEnoughApproach` early-exit field is gone - no longer
needed once the search always finds the true best angle up front instead
of bailing out of a bounded scan early.

**Confirmed already covered, no change needed:** the "does it try
slingshotting around the planet or the star to hit the player" half of
Boo's ask was already true before this phase - `gravitySources()` always
returns every gravity body in the scene (both planets and the star), and
that full list already feeds `bestAimFor`'s simulation every turn, so the
AI was already trying every gravity-curved path available to it,
regardless of position. Nothing to build there.

**Extra AI movement budget.** Boo, explicit: give the AI 2 more steps
per turn than the player gets - 1 more before the shot, 1 more after.
Player's own two phases (pre-shot/post-shot) are already equal (5 steps
each), so a single `AI_MOVEMENT_STEPS_PER_PHASE = MOVEMENT_STEPS_PER_PHASE
+ 1` constant (6) naturally gives the AI +1 in both, and is now what's
wired into `AiTurnController`'s `stepsPerPhase` instead of the player's
own constant.

**New: the AI now has a post-shot movement phase too.** Previously the
AI's turn ended the instant it fired - no equivalent to the player's own
post-shot repositioning (`AvatarMovementController.Phase.POST_SHOT`).
`fire()` now calls `reposition(targetPosition)` a second time, right
after firing and before ending the turn, using the same enlarged budget.
Explicit scope note: this second move chases the *same* goal as the
pre-shot move (closing more distance toward its own best offensive
angle) - it is not a distinct "dodge the player's likely counter-shot"
heuristic, since nothing scores defensive positioning yet. Net effect:
the AI can now close up to 12 steps' worth of angular distance per full
turn (6 pre-shot + 6 post-shot) versus the player's 10 (5 + 5), reaching
a good firing position over noticeably fewer turns, while still firing a
real shot every single turn along the way.

**Performance note (updated from Phase 19d's).** The full-planet sweep
is 24 simulated candidate positions every reposition() call (up from up
to 11 before, since the old early-exit could stop sooner) - roughly
double the simulation work of Phase 19d's version, and now `reposition()`
runs twice per AI turn (pre-shot and post-shot) instead of once. Still
judged trivial: each candidate's own internal aim sweep is the same fixed
cost as before, this only multiplies a "cheap point-mass simulation, no
real physics bodies" workload that was already deemed negligible, and
it's all hidden behind the existing "AI thinking" pause. Flagging in case
on-device testing says otherwise.

### How to test Phase 19e on-device

1. Sync Gradle, run on-device as usual.
2. Reposition into a spot that clearly calls for the AI to make a large
   move (e.g., put a planet directly between you and it) and confirm the
   AI now visibly starts moving toward a better spot turn after turn,
   instead of staying put indefinitely.
3. Confirm it still fires a real shot every turn while it's still
   mid-journey to its ideal spot, not just once it finally arrives.
4. Once it's close enough to reach its ideal spot in one turn, confirm it
   lands exactly there (not overshooting or oscillating past it).
5. Compare how far the AI seems to reposition per turn versus your own
   move budget - it should now visibly cover more ground than you can in
   the same turn (2 extra steps total, split before/after its shot).
6. Watch the post-fire moment specifically - the AI should now make one
   more visible repositioning move right after its missile launches,
   before handing control back to you.
7. Same as Phase 19d: watch for any new stutter around the AI's turn,
   given the doubled simulation cost noted above.

## Phase 20: random planet placement

Resolves the "Planet placement" bullet in the Campaign progression ladder
above - the star stays fixed at world center every game; the two planets
now get a fresh random position each time instead of the old fixed
`LAUNCH_PLANET_X = 2f` / `TARGET_PLANET_X = 7f` / `PLANETS_Y = 4f`
constants.

Boo's explicit design call, after an initial proposal that split the
world into a left half (player) and right half (AI): he didn't want a
fixed side assignment at all - either planet should be able to land
anywhere, for real variety, so long as they can't spawn too close to the
star or to each other.

**How it works.** `randomPlanetPosition()` draws one random point
anywhere in the play area (inset from the screen edges by
`PLANET_PLACEMENT_MARGIN_X`/`_Y`, so a planet's center never lands
crowded against the edges or the fixed corner UI), re-rolling until it's
at least `MIN_PLANET_STAR_SEPARATION` from the star - since the star's
position is fixed, this alone guarantees no planet can ever spawn
overlapping or awkwardly close to it. `randomizePlanetPositions()` then
draws one such point for each planet independently, and if the second one
happens to land too close to the first (`MIN_PLANET_SEPARATION`), re-rolls
just the second one until it isn't. Both are bounded by
`PLANET_PLACEMENT_MAX_ATTEMPTS` (200) with a sane (if pathological)
fallback if that's ever exhausted, though given how much of the play area
satisfies both rules at once this is expected to resolve in one or two
draws almost always.

Current tuning (first pass, easy to retune - each is a single named
constant in `PlayScreen.kt`):
- `PLANET_PLACEMENT_MARGIN_X = 1.3f`, `PLANET_PLACEMENT_MARGIN_Y = 1.5f` -
  edge clearance.
- `MIN_PLANET_STAR_SEPARATION = 2.5f` - planet-to-star center distance.
- `MIN_PLANET_SEPARATION = 4f` - planet-to-planet center distance (planet
  diameter is 1.6, so this always leaves at least 2.4 units of genuinely
  clear space between them, not just non-overlap).

**Scope note carried over from Phase 19c/19d/19e's pattern:** there's no
"start a new game" trigger yet (that's Phase 22's `GameOverScreen`
wiring), so in practice "each new game" currently means "each time
`PlayScreen` is created" - i.e., each app launch. Once Phase 22 exists,
it'll call this same randomization again for a rematch.

**Untouched by this phase, deliberately:** planet size, planet gravity
(target planet keeps gravity, launch planet stays non-gravity, same
Phase 11 rollout choice as before), and both avatars' fixed starting
angle (still straight up off their own planet's surface, regardless of
where that surface ends up) - a planet landing in an unusual spot doesn't
change how or where its avatar spawns relative to it, just where that
whole pairing sits in the world.

### How to test Phase 20 on-device

1. Sync Gradle, run on-device as usual.
2. Relaunch the app a handful of times and confirm the planets land in
   genuinely different spots each time - not just shuffled left/right,
   but different heights too, sometimes closer together, sometimes
   farther apart.
3. Confirm neither planet ever spawns overlapping the star, touching the
   other planet, or crowded right against a screen edge/corner button.
4. Confirm the star itself never moves - always the same fixed spot.
5. Play a full turn or two and confirm nothing else broke: your own
   avatar still starts correctly on your planet, the AI's still starts
   correctly on its planet, gravity/aiming/HUD all still track the
   (now-different) planet positions correctly.
6. If a layout ever feels too spread out, too cramped, or lets a planet
   spawn somewhere that reads as unfair (e.g., very close to a screen
   edge), tell me roughly what you saw - the three distance/margin
   constants above are quick to retune.

**Phase 20 addendum: star-in-the-flight-path bug, found on the very
first on-device random layout.** Boo's report: "the force the ai is
using is off... it seems to be aiming ok but the missle goes out a
little before being dragged into sun." Screenshot showed the actual
cause - the star had landed almost exactly on the straight line between
the two planets this game. The two placement rules above only check each
planet's own distance from the star; neither says anything about whether
the *path between* the planets stays clear of it. The AI's own gravity-
assist loop around its planet (visible in the trail, working as intended)
still had to cross right past the star to reach the target afterward, and
got captured there - not an AI tuning bug, a layout gap. (Also confirmed
in the same conversation: Gravity x0.7 and Shot Speed x0.4, visible in
the screenshot, are the real tuned defaults from earlier phases, not a
leftover debug-dial mis-set - ruled out as a contributing factor.)

Fix: `planetLayoutIsClear()` adds a third check to
`randomizePlanetPositions()`'s re-roll loop, via a new
`distanceFromSegment(point, a, b)` helper (distance from a point to the
finite line segment, not the infinite line) - the star must stay at least
`MIN_STAR_FLIGHT_PATH_CLEARANCE` (3f) from the actual segment between
`launchPlanetPosition` and `targetPlanetPosition`, not just far from each
endpoint individually. The old fixed layout had a full 5-unit gap between
the star and the direct path (planets both well below it); 3f restores a
similar reliably-clear corridor for the random version without being so
restrictive it fights the "anywhere for variety" goal.

### How to test the star-in-the-flight-path fix

1. Sync Gradle, run on-device as usual.
2. Relaunch several times and check whether the star ever visually sits
   on or very close to the straight line between the two planets - it
   shouldn't anymore.
3. If you can still reproduce a shot getting dragged into the star on a
   path that looks like it should've had a clear lane, send a screenshot
   - `MIN_STAR_FLIGHT_PATH_CLEARANCE` is a single easy number to bump up.

**AI accuracy pass addendum: AI's max shot power was capped below the
player's.** Boo's report with a screenshot: a "straight on" AI shot that
should've been able to power through the star's drag instead got
noticeably dragged off course, and "it should be able to put more power
into the shot like the player can." Root cause, confirmed straight from
the constants: the player's slingshot can reach up to `MAX_MISSILE_SPEED`
(15) at a full pull, but the AI's search was built around its own
separately-tuned `AI_AIM_SPEED` (8) with a 1.3x top multiplier - a
best-case ceiling of 10.4, about 30% below what the player could do with
the same global Shot Speed dial applied to both. The AI never had a
"full power" option to reach for in the first place.

Fix: removed `AI_AIM_SPEED` entirely - the AI's search is now built
directly around `MAX_MISSILE_SPEED` (the `aimSpeed` passed into
`AiTurnController`), the same constant the player's own slingshot is
capped at, so a full-power AI shot and a full-power player shot are now
mathematically identical and can never drift out of sync again the way
two separately-tuned numbers just did. `AI_AIM_SPEED_MULTIPLIERS` changed
from `[0.7, 1, 1.3]` (relative to the old 8) to `[0.4, 0.6, 0.8, 1]`
(relative to the new 15) - top end is exactly 1x (full power, matching
the player's max), with three slower options still available underneath
for whenever a more-curving, less-direct shot actually scores better.

### How to test this fix

1. Sync Gradle, run on-device as usual.
2. Set up a fairly direct shot toward the AI (or wait for one naturally)
   and check whether it now looks like it's throwing real weight behind
   the shot when the situation calls for it, not just a soft toss that
   gravity easily wins against.
3. General play: the AI should still sometimes choose a slower, more
   curved shot when that's the better tactical option - it has more
   speed *available* now, not a mandate to always use max power.

**Player shot accuracy added, matching the AI's.** Boo's testing
feedback: "the player shooter needs some error built in, similar to the
ai shots." `SlingshotInputProcessor` now has its own `applyAimError`,
an exact mirror of `AiTurnController.applyAimError` (small random angle/
speed offset, applied once right before `onFire`, never touching the aim
itself) - wired up via new `PLAYER_AIM_ERROR_DEGREES`/
`PLAYER_AIM_ERROR_SPEED_FRACTION` constants that start equal to the AI's
own (`AI_AIM_ERROR_DEGREES`/`AI_AIM_ERROR_SPEED_FRACTION`) for a fair,
symmetric baseline. Test: fire a few shots along the exact same pull each
time and confirm they land slightly differently, the same "imperfect but
not wild" feel the AI's shots already have.

**Planet-overlap bug report - archived, not reproduced since (Sept 2026
session).** Same testing batch as the player-accuracy fix above, Boo's
report at the time: "a couple of times the planets slightly overlapped.
need some error correction to prevent that." Investigated by re-deriving
the placement math rather than guessing at a fix: `MIN_PLANET_SEPARATION`
(4) vs. the two planets' combined diameter (1.6) leaves roughly 2.4 units
of guaranteed clear space between their centers under the rules above -
true overlap should be essentially impossible given that math, outside
some not-yet-identified edge case (possibly the `PLANET_PLACEMENT_MAX_ATTEMPTS`
fallback path, never confirmed). No fix was ever made - deliberately not
blind-patched without being able to see what was actually touching what.
**Archived (Boo, this session): hasn't recurred since, not chasing it
further for now.** If it resurfaces, a screenshot is what would actually
let this get root-caused rather than guessed at - the same ask as before.

## Weapon accuracy & ammo types - captured design note (Sept 2026 session, not yet built)

Boo's bigger idea, offered alongside the player-accuracy fix above:
accuracy shouldn't be a fixed constant forever - it should start rough
and improve over time as a real progression mechanic, and different
weapons/ammo should carry their own accuracy-vs-impact tradeoff. His
model, in his own words:

- **Lasers:** high accuracy, but line-of-sight only - no arcing/gravity
  curve, so a planet or the star genuinely blocks the shot rather than
  just bending it.
- **Missiles:** medium accuracy - today's existing gravity-curved
  projectile, effectively the baseline case.
- **Bombs:** low accuracy, but a larger impact area - an area-of-effect
  hit instead of today's single-point damage.

Explicitly logged, not built: sequencing decision confirmed with Boo
directly - keep the current build order (Phase 21 planet damage visuals
→ Phase 22 win/loss + new game → Phase 23 progression ladder) and revisit
this afterward, rather than pausing that order to design/build this now.

**Why this is a real phase of its own, not a quick add**, for whichever
future session picks it up:
- A persistent "accuracy improves over time" value needs to live in the
  save file (`SaveManager`/`GameSave`, the same file the win counter from
  the Campaign progression ladder above will use) and needs a curve
  decided (per-win? per-shot-landed? asymptotic toward some floor, never
  reaching zero error?).
- A weapon-select mechanic doesn't exist yet at all - needs UI, and a
  decision on whether the AI also gets to choose/vary weapons or stays
  missile-only.
- Lasers' "line-of-sight only, no gravity curve" is a genuinely different
  flight model from the missile-only physics-projectile system
  everything currently assumes (`TrajectorySimulator`, `AiTurnController`'s
  whole gravity-aware search) - not a parameter tweak on the existing one.
- Bombs' "larger impact area" needs an actual area-of-effect damage model
  - `ProjectileContactListener` currently only ever damages whatever the
  missile directly touches.

**Sub-note: where in the shot is error applied? (Sept 2026, not changing now)**
Both the player's and AI's aim error currently apply at a single point -
right at release/fire (`applyAimError`, called once on the final velocity
just before `onFire`/the search's return, per `SlingshotInputProcessor`
and `AiTurnController` - see their doc comments). So the whole flight
after release is deterministic; only the launch is fuzzed. Boo, on
noticing this: "having the error... at the point of release definitely
adds a bit of complexity and randomness. I dont want to change it. just a
note for future consideration as to at what point or points of the path
errors are introduced." Explicitly not a request to change anything -
just flagging it as a design axis worth revisiting whenever the weapon
system above gets built, since different weapons could plausibly want
error at different points along the shot rather than only at release:
- **Release-only (current).** One random nudge to angle/speed at the
  moment of firing, then a perfectly deterministic gravity-aware flight.
  Simple, and matches "you aimed slightly wrong," but every shot's error
  is fully knowable from the instant it's fired - nothing changes mid-flight.
- **In-flight perturbation.** Small continuous or periodic force
  jitter/wobble applied to the projectile during simulation, not just at
  launch - would make trajectories feel less "locked in" once fired, at
  the cost of `TrajectorySimulator`'s preview line no longer exactly
  matching what actually happens (a real design tradeoff, not just an
  implementation detail).
- **Weapon-dependent error profile.** Ties naturally into the laser/
  missile/bomb idea above - e.g. lasers get release-only (or none, if
  "high accuracy" means near-zero), missiles keep today's release-only
  jitter, bombs could plausibly get in-flight wobble to sell "low
  accuracy, unpredictable" rather than just a wider one-time cone.

## Phase 21: planet damage visuals (craters/scorch overlay)

Resolves the "Damage visual, decided" note above - craters/scorch marks
overlaid as the target planet takes hits, with true shrink-as-mass-drops
deliberately deferred as a later follow-up (Boo's original answer:
"Both - craters now, shrinking later").

**How it works.** `damage_overlay.png` (procedurally generated, same
Python/numpy/Pillow approach as every other texture this project - no
network access for external assets, see the earlier art-generation
phases) is a single 256x256 transparent PNG: a fixed scatter of six
crater blotches (dark scorched fill, warm rim highlight near each
crater's edge) positioned across the disc. It's drawn on top of
`planetTargetTexture` every frame in `renderCelestialSprites`, with its
alpha scaled to `1 - (mass / initialMass)` read straight off the target
planet's `GravitySourceComponent` - 0 when pristine (invisible), 1 once
fully destroyed (fully visible). Since there are only 4 hit-steps to
destruction (`CELESTIAL_MASS_DAMAGE` 0.5 out of `TARGET_PLANET_MASS` 2),
that's a clean 25/50/75/100% progression - each hit measurably darkens
the same fixed crater pattern rather than revealing new geometry per hit.

**Deliberately not per-impact-location.** The game doesn't track where
on the planet a missile actually landed, only the aggregate mass lost -
so this is "the planet looks more scarred overall" rather than "a crater
appears exactly where you hit it." A true per-impact decal system would
need to record hit positions/angles on the sphere, which is a bigger
feature than this phase's scope.

**Originally the target planet only** - see the "Phase 21 addendum"
below for why that's no longer true.

**Untouched by this phase, deliberately:** the planet's actual rendered
size, its Box2D fixture radius, and `PLANET_RADIUS` itself - true
shrinking is the explicitly-deferred follow-up mentioned above, not
something this phase touches.

### How to test Phase 21 on-device

1. Sync Gradle, run on-device as usual.
2. Land a hit on the target planet and confirm a faint crater pattern
   becomes visible - shouldn't be there at all before the first hit.
3. Land a second, third, and fourth hit and confirm the craters get
   progressively more visible/darker each time, most pronounced right as
   the planet is destroyed.
4. Confirm the launch planet (yours) now shows the same overlay behavior
   under hits - see the addendum below; this used to be "expected, no
   overlay" but that's changed.
5. If the craters read as too subtle or too harsh at any damage stage,
   tell me roughly what you saw - `damage_overlay.png`'s crater
   strengths are a quick regenerate, not a code change.

### Phase 21 addendum: launch planet made a symmetric gravity source too

Boo, on-device: "the players planet doesnt seem to take any damage."
Root cause: the launch planet never had a `GravitySourceComponent` at
all - Phase 11 deliberately scoped gravity/damage to just the target
planet first ("to keep the number of new gravity sources Boo is feeling
out at once to just one"), with an explicit old comment noting "every
celestial body is confirmed to eventually exert gravity, this is just an
incremental rollout." Confirmed to close that gap now: "yes. that also
explains some other shooting behavior. knowing the home planet is not
influencing path explains it" - the launch planet wasn't pulling on
anything, so every shot near it (the AI's included) was missing a real
source of gravity the whole time.

**What changed.** The launch planet now gets identical treatment to the
target planet: same mass (`LAUNCH_PLANET_MASS = TARGET_PLANET_MASS`),
wrapped in its own ECS `Entity` with `PhysicsBodyComponent` +
`GravitySourceComponent` (`launchPlanetEntity`, mirroring
`targetPlanetEntity`), added to the engine the same way. No AI or
trajectory code needed to change at all - `GravitySystem.currentSources()`
already queries generically for any entity with a `GravitySourceComponent`,
and `AiTurnController`/`TrajectorySimulator` already loop over whatever
list of sources they're handed. `drawDamageOverlayIfDamaged` (extracted
from the old target-only inline logic) now runs for both planets, and
`renderStatsPanel` gained a "Player Planet Mass" HUD row, paired with
Player HP the same way "Target Planet Mass" is paired with Target HP.

**Why this also explains shooting behavior.** Every trajectory
calculation (player aim preview, AI's `bestAimFor` search) sums gravity
from every current source. With the launch planet contributing nothing,
shots passing near it flew straighter than they should have, and the
AI's own repositioning search was optimizing against an incomplete
picture of the gravity field around its own launch point. No search or
physics logic changed - the sources list was just missing an entry.

### How to test the Phase 21 addendum on-device

1. Sync Gradle, run on-device as usual.
2. Confirm the HUD now shows a "Player Planet Mass" bar alongside your
   HP, matching the target's mass bar in style.
3. Land AI hits on your own planet and confirm both the mass bar drops
   and the crater overlay appears/darkens on your planet, the same way
   it already does for the target.
4. Take a shot that passes close to your own launch planet and confirm
   it now visibly curves/gets pulled near it, the same way shots already
   curve near the target planet.
5. General play-feel check: does gravity now feel more symmetric/fair
   between the two sides? This was the root cause behind some of the odd
   AI shot behavior noted earlier.

## Phase 22: win/loss detection + New Game

First real use of `GameOverScreen`, which until now was only a stand-in
reachable via `PauseScreen`'s "end run" tap zone with no real trigger
behind it, and always showed the same generic "GAME OVER" text.

**Win condition, confirmed scope call:** character HP only
(`HealthComponent.isDefeated`). Destroying a planet (`GravitySourceComponent
.isDestroyed`) does NOT by itself end the game - Boo, explicitly: "character
only." A defeated-planet-but-still-alive character is its own follow-up,
see the "Orbital drift" design note below.

**How it works.** `PlayScreen.render()` checks both `HealthComponent`s
right after `flushRemovals` each frame - the same point damage from that
frame's contacts has already resolved. Whichever character is defeated
first ends the run: `SaveManager.recordRunEnded()` (Phase 6's existing
persist-before-teardown step, same one `PauseScreen`'s manual quit already
used), `dispose()` (this `PlayScreen` instance's own native resources -
Box2D `World`, textures, batches - previously only ever freed by
`PauseScreen`'s "end run" path; skipping this on a real win/loss would
have quietly leaked every time a run actually finished normally), then
`game.setScreen(GameOverScreen(game, won = ...))`.

**`GameOverScreen` is now a real outcome screen**, not a stand-in: takes a
`won: Boolean`, shows "VICTORY" (dark green background) or "DEFEAT" (dark
red, the color it always used), and tapping starts a brand new
`PlayScreen` directly - same "fresh instance every time" pattern
`MenuScreen`'s own tap-to-play already uses - instead of going back to the
menu first. `PauseScreen`'s manual "end run" quit now passes `won = false`
(there's no neutral third state built, so a manual quit reads the same as
a loss).

### How to test Phase 22 on-device

1. Sync Gradle, run on-device as usual.
2. Let the AI defeat your character (4 direct hits at the current
   illustrative HP/damage numbers) and confirm you land on a red
   "DEFEAT" screen, not the old generic "GAME OVER" text.
3. Tap the DEFEAT screen and confirm it drops you straight into a brand
   new run (fresh random planet layout, full HP/mass on both sides) -
   not back to the main menu.
4. Defeat the AI's character and confirm a green "VICTORY" screen
   instead, same tap-for-new-game behavior.
5. Pause mid-run and tap "END RUN" - confirm it still reaches the same
   screen (as a DEFEAT-styled outcome, since a manual quit isn't a real
   win), same as before this phase.
6. Play a couple of full runs back-to-back through the New Game flow and
   confirm nothing looks stale/leftover from the previous run (planet
   positions, HP bars, mass bars should all be fresh).

## Orbital drift for a defeated-planet-but-alive character - captured design note (Sept 2026 session) - now built, see Phase 29 below

Boo's follow-up the moment win/loss scope came up: what happens to a
character whose planet gets destroyed while they still have HP left?
Right now nothing - the avatar/AI position is purely a walked angle
around a fixed `planetCenter` (`AvatarMovementController`/
`AiTurnController` - there's no physics body driving it, no free
movement at all), so a destroyed planet leaves that character with
nothing under their own logic to stand on and no defined behavior.

**Boo's confirmed design**, from the on-device discussion:
- The character becomes a free orbital object, given some initial
  velocity **perpendicular to the destroyed planet's former center** (a
  tangential "flung into orbit" kick, not a random direction) at the
  moment of destruction, then drifts under the gravity of whatever
  bodies remain (the star, and the surviving planet if any) - a real
  physics body from that point on, not the angle-around-a-fixed-center
  model the avatar uses today.
- They keep the ability to aim and fire on their turn from wherever
  they've drifted to ("Still aim/fire" - confirmed over "no control at
  all"). They lose the walk-around-the-planet movement budget entirely
  (nothing to stand on), but aiming/firing itself isn't blocked.
- Hitting the star kills the character outright - confirmed, same
  finality as a direct missile defeat.
- Landing on a planet or moon (the surviving one, or any future
  additional body) deals exactly one point of damage on impact, "which
  may or may not kill them" depending on HP left - not an instant kill
  like the star, just another hit.

**Confirmed sequencing: its own phase**, after Phase 22 lands, not folded
into it - Boo: "Split into its own phase (Recommended)." Reasoning
this earned separate scope, for whichever future session picks it up:
- It's a genuinely different movement model from anything that exists
  today - every other moving thing in this game (avatar, AI, missiles)
  is either an angle-around-a-fixed-center walk or a
  `TrajectorySimulator`-style ballistic sim triggered once per shot; this
  is the first *persistent, continuously-integrated, multi-turn* free
  body driven by `GravitySystem`.
- "Landing" needs its own definition that doesn't exist yet - does the
  character stick to the surface at the point of impact (a new fixed
  angle-around-center position, effectively re-anchoring them the way
  they started), bounce, or something else? Not settled by the
  discussion above, deliberately left for whoever scopes this phase.
- Needs a decision on whether this character keeps orbiting indefinitely
  (multiple turns of drift) or the drift only covers the time between
  turns, with their position "frozen" in place while waiting for their
  next turn to aim/fire - not yet decided.
- Overlaps with the archived planet-overlap bug report (see Phase 20's
  addendum - not reproduced since, but worth a second look if a
  persistent drifting body starts exposing it again) and the
  star-flight-path clearance work from Phase 20 - a drifting character's
  path needs the same kind of collision/clearance thinking those phases
  already dealt with for missiles, just applied to a persistent body
  instead of a one-shot simulated trajectory.

## Phase 23: win-only progression counter (escalation ladder itself, still deferred)

**Deliberately a narrower slice than the full "Campaign progression
ladder" design above.** That design's escalation content (5 wins -> AI
gets a 2nd planet/character, 20 wins -> AI's 3rd + player's 2nd, 30 wins
-> campaign complete + reset option) needs multiple characters per side
to actually exist, and that design's own "Not yet decided / deferred"
bullet flags squad composition, AI behavior with more than one character,
and turn order (interleaved vs. whole-squad-then-whole-squad) as all
still open. Building the ladder now means hitting that wall the moment
anyone reaches 5 wins. Boo agreed to this narrower scope on confirmation:
just the counter itself, visible and persisting correctly, with the
actual escalation logic left for once turn-order/squad design gets its
own pass.

**What's built:**
- `GameSave.winCount: Int` (schema v3 - purely additive, same safe
  migration pattern as v2's `appLaunchCount`; an old save simply doesn't
  have the field and it comes back at its Kotlin default of 0).
- `SaveManager.recordWin()` - deliberately separate from
  `recordRunEnded()`, called ONLY from `PlayScreen`'s win branch (right
  next to the existing `recordRunEnded()` call there), never from the
  loss branch. A loss still counts as a completed run (`runCount` still
  goes up) but never touches `winCount`, and nothing anywhere decrements
  it - matches the "wins only, never reset by a loss... explicitly NOT a
  roguelite streak" line from the original design above.
- `MenuScreen` now shows "Wins: N" (`SaveManager.currentWinCount()`),
  drawn just above the existing "Runs completed: N" line, same centered
  style - the menu is the one persistent-between-runs screen, so it's the
  natural place for a counter meant to survive a loss.

**Still not built** (this is the next thing blocking the rest of the
ladder, whenever it's picked up): the actual escalation content at 5/20/
30 wins, which needs a real design pass on multi-character-per-side squad
composition and turn order first - see the "Not yet decided / deferred"
bullet in the Campaign progression ladder section above, still accurate.

### How to test Phase 23 on-device

1. Sync Gradle, run on-device as usual.
2. From the main menu, confirm you see a new "Wins: N" line above "Runs
   completed: N" (starts at 0 on a fresh save, or wherever it already
   was if you've been testing this build).
3. Win a run (defeat the AI's character) and confirm, back at the menu,
   "Wins:" went up by exactly 1.
4. Lose a run and confirm "Wins:" does NOT change, while "Runs
   completed:" still goes up as it always has.
5. Force-close and reopen the app (or just background/foreground it) and
   confirm the win count survives - same persistence guarantee the run
   count already had.

## Movement freeze during shot flight

Boo, on-device: "once the ai has shot and the projectile is still in
flight, I can start moving my character to get out of the way... for now
though, I do not want either player to be able to move for 3 seconds
(retuned to 5 seconds after a testing pass, same session)
after they enemy has taken a shot."

**Root cause.** The AI->player input handoff happened essentially the
instant the AI fired, not once its shot actually resolved:
`AiTurnController.fire()` does its own post-shot `reposition()`
synchronously (no animation, one function call) and then immediately
calls `onTurnComplete`, which handed `Gdx.input.inputProcessor` straight
back to `fullInputProcessor` - full movement control - while the just-
fired missile was still simulating its real flight through
`PhysicsSystem`/`GravitySystem`. There was never an actual gap between
"missile leaves" and "player can dodge it."

**Fix - a flat, symmetric freeze, not a "wait for the missile to
resolve" mechanic.** `PlayScreen.SHOT_FLIGHT_FREEZE_SECONDS` (5f - retuned up from an initial 3f the same session, after Boo tried it on-device and wanted a longer pause)
starts counting down from `fireMissile()` - the single spawn point
already shared by both the player's and the AI's shots - regardless of
which side fired. While it's counting down, neither turn-handoff
callback performs its handoff immediately:
- **AI -> player** (`AiTurnController`'s `onTurnComplete`): normally
  hands `fullInputProcessor` back instantly; now the callback (`Gdx.input
  .inputProcessor = fullInputProcessor`) is stashed in
  `pendingTurnHandoff` instead, and only actually runs once the freeze
  expires - `render()` checks and fires it every frame.
- **Player -> AI** (`AvatarMovementController`'s `onTurnPassed`):
  `Gdx.input.inputProcessor = restrictedInputProcessor` still happens
  immediately (locking the player OUT sooner is never exploitable), but
  `aiTurnController.startTurn(...)` - which does its OWN synchronous
  pre-shot `reposition()` the instant it's called - is what gets
  deferred the same way. This is the "either player" half of Boo's ask:
  the AI doesn't get to reposition for 5 seconds after the player's own
  shot either, even though a human can't exploit that side today.

**Deliberately untouched: each side's own post-shot movement.** The
freeze only gates the HANDOFF to the other side - not the shooter's own
turn, which is already governed separately by `AvatarMovementController`'s
own phase/budget (or, for the AI, its own `reposition()` call inside
`fire()`, which already runs before `onTurnComplete` is ever reached).
The existing "move again to take cover" mechanic from the original core
gameplay loop design is completely unaffected - a shooter can still
reposition normally right after firing, in the same turn; it's only the
OTHER side's very next action that's held back.

**HUD fix that came with it.** `AiTurnController.isTurnActive` goes
false the instant the AI fires (before `onTurnComplete` even runs), and
`AvatarMovementController`'s phase/steps already reset for the player's
next turn the instant they pass - so without checking
`shotFlightFreezeRemaining` first, the turn-number HUD label would have
claimed it was someone's turn to act during the exact window neither
side actually can. It now shows "Turn N - Shot in flight..." during the
freeze, ahead of the existing "AI's turn..." / "Pre-shot: N left" /
"Post-shot: N left" text.

### How to test on-device

1. Sync Gradle, run on-device as usual.
2. Let the AI take a shot and immediately try tapping the movement
   buttons while the missile is visibly still traveling - confirm they
   do nothing until roughly 5 seconds after the shot, and the HUD reads
   "Shot in flight..." during that window.
3. Fire your own shot, then use your post-shot movement as normal
   ("take cover") - confirm this still works exactly as before, with no
   new delay on your OWN movement in the same turn.
4. After your post-shot movement ends (budget exhausted or Pass tapped),
   confirm there's now a beat before "AI's turn..." appears and the AI
   actually starts moving/aiming - roughly 5 seconds from when your shot
   left, not from when you tapped Pass.
5. General feel check: does 5 seconds feel like the right pause now, too
   short, or too long? It's a flat illustrative number, easy to retune again.

**Captured for later - "an interesting advanced version."** Boo flagged,
without specifying details yet, that there's probably a more interesting
mechanic here than a flat freeze - logged as a marker for future
ideation, not a design. Roughly the space it'd occupy: something that
lets movement during an incoming shot's flight be a real, deliberate
choice again (a genuine dodge) rather than forbidding it outright - e.g.
a real-time reaction window, a movement action that costs something
(steps, a cooldown, an ammo-like resource) if used reactively, or a
partial freeze (some movement allowed, just not enough to fully evade a
well-aimed shot). None of this is decided; the flat 5-second freeze
above is what's actually built, and this paragraph exists so a future
session doesn't have to re-derive that Boo saw more potential here than
the simple version.

## Aiming UX improvements

Two aiming complaints from on-device play, logged together since Boo
raised them in the same message, then both built together in a follow-up
session once the design forks were confirmed.

### No firing below your own horizon (own planet, and the AI's own planet)

Boo: "remove the ability to fire directly in the planet the player is
on. same for ai. lets add some logic where you cannot fire if the angle
is lower than the horizon from the players perspective." First design
pass: **clamp**, not silently cancel - a release (or an AI candidate)
below horizon still fires, just leveled off to skim along it. Boo also
asked for a visual tell: the aim line/trajectory preview should
disappear while aimed illegally, so it *looks* wrong before you even
let go.

**Revised after first on-device test.** Boo tried it and found the
clamp-and-fire behavior inconsistent with the hidden aim line: "if I
move the aim so that its pointed below horizon it does indeed not show
aim lines. however, if I release like I am shooting, it still shoots a
projectile although at the horizon. I would like it so that if the aim
disappears, then even if you make a shooting gesture, it will not
fire." So for the **player**, what you see is now exactly what you get:
an illegal raw release fires nothing at all, full stop, matching the
hidden line. The clamp itself wasn't thrown away - it's still there as
a hard safety net for the one case the raw check can't cover, a release
that WAS legal but gets nudged just below horizon by `applyAimError`'s
random jitter after the fact; that shot still fires, leveled off,
rather than silently eating a shot the player clearly meant to take.
The **AI** side was deliberately left as clamp-and-fire and not changed
to match - it has no equivalent "hidden line" to stay consistent with,
and it has to produce some shot every turn regardless (this scoping
call hasn't been separately confirmed with Boo; flag it if the AI's
below-horizon shots ever look wrong on-device).

**How it works.** Both `SlingshotInputProcessor` (player) and
`AiTurnController` (AI) got their own private `clampAboveHorizon(origin,
velocity)` - same logic, two copies, same reasoning as
`applyAimError` already being duplicated between them. Local horizon =
the tangent line perpendicular to straight-out-from-`planetCenter` at
`origin`; a velocity whose direction has a negative component along that
outward radial gets leveled off to skim exactly along the horizon
instead, same speed, keeping whichever side (left/right) the illegal
direction was leaning toward rather than snapping to one fixed side.
- **Player**: `touchUp` checks the raw pre-jitter velocity with
  `isBelowHorizon` right after computing it and returns early (no
  `onFire` at all) if it's illegal - before `applyAimError` even runs.
  `clampAboveHorizon` is still applied, but only after `applyAimError`,
  purely as the safety net described above for jitter pushing a legal
  aim below horizon. `currentAimBelowHorizon` exposes the same
  `isBelowHorizon` check live, mid-drag, purely so `PlayScreen` can hide
  the aim-line (`renderDebugOverlay`) and the real trajectory preview
  (`renderAimTrajectoryPreview`) while it's true - so the visual tell and
  the actual fire/no-fire decision are driven by the same check.
- **AI**: unchanged - `clampAboveHorizon` applied inside `bestAimFor`
  itself, the function already shared between `searchAim` (the real
  shot) and `reposition` (scoring hypothetical positions), so both get
  the guarantee for free from one choke point. Also re-applied in
  `searchAim` after `applyAimError`, same hard-safety-net reasoning.

### How to test on-device

1. Sync Gradle, run on-device as usual.
2. Try dragging your aim so the shot would point back into your own
   ground - confirm the aim line and the gray trajectory preview dots
   both disappear while aimed that way.
3. Release anyway while aimed illegally - confirm nothing fires at all
   (no missile, no shot) - matching the hidden aim line exactly.
4. Aim legally, hold near the horizon edge, and release a few times to
   see if the jitter ever nudges a legal-looking release below horizon -
   confirm those still fire, leveled off along the horizon, rather than
   silently doing nothing.
5. Watch the AI over a few turns from awkward repositioned angles and
   confirm it never fires straight into its own ground either (it still
   clamps-and-fires rather than skipping the shot).
6. General feel check: does the AI's clamp ever look weirdly
   flat/unnatural or inconsistent now that the player's side works
   differently? Worth a call on whether the AI should match.

### Horizon geometry corrected (flat-plane bug) - Sept 2026 session, after Phase 28

On-device, after Phase 27's red-dot preview had been playing for a
while, Boo: "note how the red dots appear even though the aim is not
through the planet." The bug: `isBelowHorizon`/`clampAboveHorizon` never
actually tested "does this shot hit the planet" - they tested "does this
shot have any downward component relative to the flat plane tangent to
the planet at the firing position." That's only the same thing if the
firing position sits exactly ON the planet's surface. It doesn't -
`LAUNCH_POINT_CLEARANCE` (0.3) holds the avatar/AI above `PLANET_RADIUS`
(0.8) - so the sphere's real curve falls away well below that flat
plane. Worked out geometrically (right triangle: hypotenuse = distance
from the planet's center to the firing position, opposite side = the
planet's radius), a shot at this game's actual proportions can dip about
**47 degrees** below the flat horizon and still cleanly clear the
planet - the old check was flagging all of that as illegal.

**Fix.** Both `SlingshotInputProcessor.isBelowHorizon`/
`clampAboveHorizon` (player) and `AiTurnController.clampAboveHorizon`
(AI) now compute the true tangent-to-sphere grazing angle instead of
assuming zero clearance - a new private `horizonSinThreshold(origin)` in
each (sine of the grazing angle = planetRadius / distance-to-planet-
center), used to build the exact `cos`/`sin` components of the real
grazing direction rather than the old "just zero out the radial
component" approach. `SlingshotInputProcessor` needed a new constructor
parameter, `planetRadius`, to do this (previously had `planetCenter` but
nothing about the planet's size). Verified analytically (a quick script,
not on-device) against real ray-sphere intersection math: the new legal/
illegal boundary never lets an actually-hitting direction through, and
every clamped shot lands exactly on the sphere's tangent line rather
than still clipping it.

Net effect: a noticeably wider legal firing cone before anything turns
red, refuses to fire, or gets leveled off - same underlying rule ("never
fire into your own planet"), just accurately drawn to the sphere's real
silhouette instead of the flat plane at the firing height.

#### How to test this fix on-device

1. Sync Gradle, run on-device as usual.
2. Aim well below the flat horizon line (steeper than before) - confirm
   the preview stays gray (legal) much further down than it used to,
   right up until the shot would actually clip your own planet's curve.
3. Confirm red only appears once the aim would genuinely intersect the
   planet - not just "any downward tilt."
4. Release right at that new boundary a few times - confirm it fires
   (leveled off to the sphere's tangent, not the old flat skim) rather
   than refusing.
5. Watch a few AI turns from different repositioned angles - confirm its
   shots/candidate search aren't visibly more conservative than before
   (the AI's search range effectively widened too, same fix).

### A real way to aim, then decide NOT to fire

Boo: "there needs to be a way for you to aim and then decide to not
fire... now you have to release your finger in exactly the position of
the character. there is no margin for error." Explicitly ruled out a new
button when this got revisited. Confirmed gesture: **pull back past your
character** - the same physical motion as snapping a slingshot back
through its own resting point instead of letting it fly.

**How it works.** `SlingshotInputProcessor` tracks two flags per drag,
both reset in `touchDown`: `pulledPastCommitDistance` latches once the
pull first reaches `PULL_COMMIT_DISTANCE` (2 world units) - a real,
committed aim, not touch-down jitter - and `passedBackThroughCenter`
then latches once, after that, the pull returns within `AIM_START_RADIUS`
(1.5, the same radius that already gates starting to aim at all - reused
rather than adding a second meaning-adjacent constant). Once
`passedBackThroughCenter` is true, `touchUp` always cancels, even if the
finger is back out aiming somewhere else by the time it lifts - the
pass-through-center is what commits to "never mind," not the final
release position, so a legitimate full redirect that never dips back
near the character doesn't accidentally cancel.

### How to test on-device

1. Sync Gradle, run on-device as usual.
2. Pull back to aim a real shot, then drag your finger back through/near
   your character and release from wherever it ends up - confirm this
   cancels (no shot fires) rather than firing whatever the final drag
   position was.
3. Confirm a normal single pull-and-release, with no pass back through
   center, still fires exactly as before.
4. Confirm the existing "release right on the character" cancel (a
   near-zero drag) still works too - this is additive, not a replacement.
5. Feel check: does pulling back past center feel discoverable/natural
   without being told, or does it need a visual hint (e.g. dimming the
   aim line once the cancel is armed)? Not built - flagging as an easy
   follow-up if it feels too hidden.

## Multi-character combat: turn order, camera, and stray-shot lifecycle - captured design (Sept 2026 session; all three steps now built - fixed-squad turn order (Phase 30), the player's turn-order picker (Phase 31), and real AI targeting + obstacle polish (Phase 32) - see below)

Picked back up from the "escalation ladder" backlog item - the ladder's
own design (see "Campaign progression ladder" above and Phase 23 below)
flagged squad composition, turn order, and multi-planet placement as all
still open. This session resolved turn order, and a cluster of camera
and physics questions that turn order turned out to depend on, in one
long discussion. Squad composition/AI-behavior-with-multiple-characters
and planet/character placement are still open - see the end of this
section.

**Turn order: whole-squad-then-whole-squad, with player-chosen order
within it.** Considered three shapes: interleaved individual turns
(each character, either side, in strict rotation), whole-squad-then-
whole-squad (one side acts with every one of its characters before
control passes), and freeform (pick any of your living characters each
"round," one action per side per round). Boo chose whole-squad-then-
whole-squad, and explicitly wants to choose the order his own
characters act in during his squad's turn ("I had not thought about
selecting the order of characters. that does seem to make the game more
fully fleshed out so lets do that") - not a fixed sequence. Whether the
AI picks its own order strategically or just uses a simple fixed rule
(e.g. planet/creation order) is not decided - flagging as an
implementation-time call, not something Boo needs to weigh in on unless
it looks wrong on-device once built.

**Camera: this session's fixed single-screen assumption is going away.**
Checked the current code: `PlayScreen` uses one `FitViewport` locked to
constant `WORLD_WIDTH`/`WORLD_HEIGHT`, camera pinned dead-center, never
moves, no zoom - the whole "everything always fits on one screen"
tech-demo assumption, still true as of this session. Boo: "I don't want
[the play field] to always fit on one screen... pinch to zoom and
ability to scroll around and zoom in and out." Decided:
- **Gesture split:** pinch-zoom and pan are strictly 2-finger. 1-finger
  touch stays exactly as it works today - reserved entirely for
  character movement and slingshot aiming, no conflict/disambiguation
  logic needed between camera control and existing gestures.
- **Camera auto-behavior:** camera is NOT a continuous auto-follow (not
  chasing the active character or the projectile in flight). Instead it
  snaps to frame the active avatar once, at the moment a new avatar's
  turn begins. Between that snap and the next one, the player is free
  to pinch/zoom/pan anywhere they want, including all through their own
  turn's aiming and after they've fired - the camera does not chase the
  shot. The next snap-to-active-avatar only happens when control passes
  to the next character.
- **This is a prerequisite phase, not a side effect of the ladder.**
  Multi-planet placement (still open, below) only becomes meaningfully
  designable once the field can actually be bigger than one screen, so
  camera/pan-zoom needs to land as its own phase before the ladder's
  placement question gets finalized/built.

**Play field size: grows, but capped.** As more celestial objects get
added over the course of the ladder (more planets, and per the "Orbital
drift" note above and future ideas, eventually binary stars/black
holes), the play field's bounds grow to fit them - but not without
limit. Boo: "it has to stop at some point. we should have a certain
number of limit to the size and that will be based on the number of
celestial objects and their sizes." The actual cap formula (how object
count/size maps to a max field size) is not decided yet - just captured
as a real constraint, not an afterthought, for whenever the field-sizing
logic actually gets built.

**Missed shots don't despawn - they stay live, permanently, anywhere.**
Boo confirmed explicitly and enthusiastically: a projectile that exits
the play field is NOT destroyed. It keeps simulating for real (gravity
and all) indefinitely, and if it ever drifts back and hits something -
even the character who fired it - that's a real hit with real damage,
not a visual-only flourish. "It absolutely can still cause damage. even
to oneself. thats the unexpected element I like." This is a deliberate
feature, not a bug to guard against.

**Turn-ending trigger for a missed shot: play-field-boundary-based, not
screen-based, and separate from the projectile's own lifetime.** Since a
missed shot no longer ends anything by being destroyed, something else
has to end the turn. Clarified twice by Boo since the first framing was
imprecise: it is NOT about camera visibility (a projectile can be
off-screen while pinch/zoom has the camera pointed elsewhere, but still
be well inside the play field, and that does not start any timer). The
timer only starts once the projectile actually crosses outside the play
field's own boundary - "a few seconds outside the play field," not "a
few seconds off screen." Once that timer elapses, control passes to the
next avatar. The projectile itself is unaffected by this - it keeps
existing and simulating, per the point above; the timer only gates when
the *turn* ends, not the shot's lifetime.  Exact timer duration ("a few
seconds") not tuned to a specific number yet - same pattern as
`SHOT_FLIGHT_FREEZE_SECONDS` starting at a guess and getting tuned via
on-device feel-testing.

**Stray-projectile cap: 4 per side, 8 total, oldest-first eviction.**
Since strays never despawn on their own and could in principle
accumulate turn after turn over a long game (most concerning if one
ends up in a stable-ish orbit outside the field and never wanders back
in), Boo wants a hard cap to bound the memory/physics cost: "each player
can have 4 stray shots for a total of 8." The two sides' caps are
independent - a side's own oldest still-outside-the-field stray is
quietly retired only when THAT side's own new miss would push it past
its own 4; hitting the cap never affects the other side's strays. Chosen
over "refuse to let a new shot go stray once the cap's hit" because that
would make otherwise-identical shots behave inconsistently depending on
how many strays happen to already be floating around - quietly retiring
the oldest (already invisible, already out of mind) is the less jarring
rule.

**Planet/character placement: fully scattered, with a region-quota rule
to prevent corner-hoarding.** This was the original question that kicked
off the whole camera/play-field discussion above. Considered clustered-
by-side ("home base" read, easy to parse, gravity mostly contained
within a side) vs. fully scattered (no spatial concept of "sides" at
all, ownership and proximity decoupled). Boo chose scattered - "fully
scattered is much more interesting to me" - given how central gravity
is to the whole game, decoupling ownership from position means gravity
and stray-shot paths can meaningfully involve any planet regardless of
who it belongs to, not just your own cluster.

Pure independent-random placement doesn't actually deliver "scattered
but not clumpy," though - the existing minimum-clearance rule (already
used for today's 2-planet case: reject/retry if too close to the star
or another object) only guarantees objects don't overlap or sit right
on top of each other locally. It says nothing about the field as a
whole, so it's entirely possible for most objects to land in one region
by chance while the rest of a large field sits empty. Boo flagged this
directly: "if field size is large, I dont want 6 objects clustered in a
corner. some natural clustering is ok if it feels organic."

**Decided approach - two independent rules, stacked:**
1. **Minimum clearance** (existing rule, unchanged) - keeps placement
   from overlapping/touching, same reject-and-retry pattern already in
   use.
2. **Region quota (new)** - the field is divided into a coarse grid of
   regions, sized relative to the total number of objects being placed
   (so it scales automatically as the field grows with the ladder), and
   each region has a cap on how many objects it's allowed to hold.
   Placement retries into a different region once a region hits its
   cap. This is the layer that actually prevents corner-hoarding - it
   guarantees field-wide spread without making placement feel grid-
   snapped, since exact positions within an allowed region are still
   randomly jittered, same organic-but-not-clumpy result procedural
   generation normally uses for scattering trees/rocks/stars naturally.

Exact numbers (region grid density, max objects per region) not tuned
yet - same "reasonable starting guess, tune via on-device feel-testing"
pattern as every other constant in this project
(`SHOT_FLIGHT_FREEZE_SECONDS`, `MIN_PLANET_SEPARATION`, etc.). Boo
confirmed writing this up as the design rather than debating the exact
knobs now ("yes").

**Still not decided - open threads for whenever they're picked back
up:**
- **Squad composition / AI behavior with multiple characters** - fixed
  2-per-side squads (Phase 30, Step 1), the player's turn-order picker
  (Phase 31, Step 2), and a real AI-targeting heuristic plus obstacle-
  avoidance polish (Phase 32, Step 3) are now all built - all three
  originally-planned steps are done. Squad-size flexibility beyond
  fixed-2, and the character placement/planet-scaling design below, are
  what's left.
- **Build order** for everything in this section - camera/pan-zoom
  landed first as planned (Phase 24), and multi-character combat's three
  steps (Phase 30, 31, 32) have all now landed; planet/character scaling
  is next (see the design note immediately below), then the play-field-
  size cap and the campaign ladder's actual escalation content.
- **HUD/visual polish, project-wide priority.** Boo, after confirming
  Phase 31's turn-order prompt actually works but is easy to miss on
  first glance: "dont change anything there for now. once we get game
  mechanics more polished well come back to the HUD and other graphic
  elements." A general build-order call, not specific to the order-picker
  prompt alone - mechanics come before HUD/graphics passes across the
  board until Boo says otherwise.

## Planet/character scaling: generalizing beyond fixed 2-per-side - captured design (Sept 2026 session, not yet built)

Boo, right after Phase 32 closed out multi-character combat: "design
planet placement/scaling first" - the piece needed before the campaign
progression ladder's actual escalation content (5/20/30 wins, see
"Campaign progression ladder" above) can be built, since that ladder
assumes each side grows from 1 planet to 2 to 3 over time, and today's
code hardcodes exactly one `launchPlanetPosition`/`targetPlanetPosition`
field per side - there's no list, no N-planets concept anywhere yet.

**Confirmed design, four parts:**
1. **Data model.** Each side's planets become a `List<Planet>` (position,
   radius, owning ECS entity for the existing gravity-source/health
   lookups) instead of one fixed `Vector2` field per side.
   `currentCelestialObstacles()` reads a flat list (the star plus every
   planet on both sides) - ownership is just a label used for character
   assignment (see below), not a physics or obstacle distinction, matching
   the already-decided "fully scattered" placement choice (see "Planet/
   character placement" above) - a planet blocks shots and drifts
   characters the same way no matter which side it's tagged as belonging
   to.
2. **Character-to-planet assignment**, computed once at match start (not
   live-rebalanced mid-match - a destroyed planet's characters just keep
   drifting exactly like today, they don't get reassigned to a still-
   living planet). If a side has more characters than planets, those
   characters share a planet (randomly positioned on it, replacing
   today's fixed symmetric-offset stopgap). Once a side has planets >=
   characters, one character per planet instead. This matches Boo's own
   stated long-term preference exactly (from the Phase 30 on-device
   follow-up): "characters randomly on their planet when 2 on 1 planet.
   if there are enough planets for each player, distribute evenly."
3. **Placement algorithm.** Reuses the already-decided scattered +
   region-quota rule (see "Planet/character placement" above), generalized
   from exactly-2-planets to N - minimum-clearance reject/retry, plus the
   region-quota layer that prevents corner-hoarding as the field grows.
4. **Deliberately deferred, not part of this design:** the actual
   play-field-size-cap formula and `WORLD_WIDTH`/`WORLD_HEIGHT` growth.
   Nothing exercises a variable field size until the ladder itself starts
   adding a 3rd+ planet, so a formula designed now would have nothing real
   to validate against. This design only makes the machinery *capable* of
   N planets/characters per side - the campaign ladder's own win-threshold
   logic (still a future phase) is what will actually grow the counts,
   and that's when the field-size question needs a real answer.

**Build broken into steps, same discipline multi-character combat's
Steps 1-3 used:**
- **Step A - built, see Phase 33 below:** generalize the data model
  (`Planet` list, ownership tag, `currentCelestialObstacles()` over the
  flat list) as a pure refactor - zero visible behavior change, still
  exactly 2 planets/2 characters per side sharing via today's fixed
  offset. Confirms the plumbing works before any new behavior rides on
  it.
- **Step B - built, see Phase 33 below:** replace the fixed
  symmetric-offset sharing with random-on-planet positioning, and add the
  one-per-planet distribution path for when planets >= characters (part 2
  above) - this is where "characters randomly on their planet...
  distribute evenly" actually gets built. Turned out `assignCharacterPlanets`
  from Step A already handled the one-per-planet-vs-share distribution
  correctly on its own - Step B's real work was the *positioning* of
  characters sharing a planet, not the distribution itself.
- **Step C - built, see Phase 33 below:** generalize the placement
  algorithm itself (region-quota, part 3 above) from exactly-2-planets to
  N, since Step A/B alone don't yet let more than 2 planets actually exist
  per side. Deliberately scoped to just the position-generation math -
  celestial-body construction/rendering/drift/HUD all still assume
  exactly one planet per side, left for Step D since only the ladder
  actually needs more than one to exist.
- **Step D+ - risk-assessed, confirmed design, not yet built - see "Phase
  33, Step D" below:** the campaign ladder's own escalation content
  (5/20/30 wins) and the field-size-cap formula - deliberately out of
  scope here, picked up once this machinery exists. Split into Step D1
  (tier lookup, dynamic field size, win-count debug control) and Step D2
  (the real N-planet/character generalization) - see that section for the
  full risk assessment and why.

## Phase 33: planet/character scaling, Step A (generalized data model, no behavior change)

The first of the four steps from the design note immediately above.
Confirmed scope going in: a pure refactor, zero visible behavior change -
still exactly 2 planets/2 characters per side, sharing via today's fixed
symmetric-offset placement. The point of this step is the plumbing, not
new behavior.

**What's built**, all in `PlayScreen.kt`:
- **New `Planet` class** (position, radius, owning ECS entity) and two new
  `playerPlanets`/`aiPlanets: List<Planet>` fields, built once in `init{}`
  right after `launchPlanetEntity`/`targetPlanetEntity` exist - each list
  is size 1 today, wrapping the exact same `launchPlanetPosition`/
  `targetPlanetPosition`/`*PlanetEntity` fields that already existed.
  Deliberately not a replacement for those fields - they stay exactly as
  they were, still read directly by rendering, drift/landing, the horizon
  check, and `randomizePlanetPositions` (all untouched this step, per the
  design note's Step A/Step C split). `playerPlanets`/`aiPlanets` are a
  thin, always-in-sync view for the two things that actually needed to
  start reading a list: obstacle-building and character-to-planet
  assignment.
- **`currentCelestialObstacles()` generalized.** Used to read from a fixed
  3-element `celestialObstacles` list (star/launch/target by hardcoded
  index) built once at init. Now builds the result fresh from
  `playerPlanets`/`aiPlanets` each call - same star-plus-every-non-
  destroyed-planet result as before (byte-identical positions/radii/
  filtering for today's exactly-2-planet case), but with no fixed-index
  assumption left anywhere, so it's already correct for however many
  planets either list holds once Step C lands. The old `celestialObstacles`
  field is gone.
- **New `assignCharacterPlanets(planets, characterCount)` seam.** Computed
  once in `init{}` for each side (`aiCharacterPlanets`/
  `playerCharacterPlanets`, each size `CHARACTERS_PER_SIDE`), and threaded
  into the `aiCharacters`/`playerCharacters` construction in place of the
  old direct `targetPlanetPosition`/`launchPlanetPosition` reads. Its Step
  A body is deliberately trivial - `planets[i % planets.size]` - which
  with a size-1 list means every character maps to index 0, i.e. exactly
  "everyone shares the one planet," identical to today's real behavior.
  Step B replaces only this function's body (share-randomly-positioned
  when characters > planets, one-per-planet once planets >= characters)
  with every caller unchanged.

**Not built this step (by design - see Phase 33's own step breakdown
above):** the random-when-sharing/one-per-planet assignment logic itself
(Step B), letting `playerPlanets`/`aiPlanets` actually hold more than one
planet (Step C - `randomizePlanetPositions`/`randomPlanetPosition`/planet-
body creation are all still hardcoded to exactly one draw per side), and
the campaign ladder's own escalation content (Step D+).

#### How to test this phase on-device

This step is a pure refactor with no intended behavior change, so testing
is really regression testing - confirm nothing from earlier phases broke:
1. Build and run a full 2v2 match as normal. Planets should still be
   randomly placed each new game exactly as before (Phase 20), both
   characters per side should still start at their usual spread-out
   positions on their one shared planet (Phase 30).
2. Destroy a planet (either side's) and confirm the "ghost obstacle" fix
   still holds - AI aim search and your own aim preview should both stop
   treating the destroyed planet's old position as solid the instant it's
   gone, same as before this refactor.
3. General playthrough - turn order (Phase 31), AI targeting/obstacle-
   avoidance (Phase 32), drift-when-your-planet-is-destroyed (Phase 29),
   and the horizon/no-fire-below-your-own-planet check should all still
   behave exactly as they did before this session. Any difference here
   would mean this refactor accidentally touched something it shouldn't
   have.

✅ **DONE** - confirmed on-device ("play is as expected"). One real bug
found and fixed along the way, worth flagging for future doc-comment
writing: a KDoc comment in the original Step A commit contained the
literal substring `*/` inside its prose (`launchPlanet*/targetPlanet*`),
which is Kotlin's actual block-comment-close token - it silently
terminated that comment early and everything after it in the file parsed
as broken code (a wall of "Expecting member declaration" errors starting
right where the comment should have still been open). Fixed in a
follow-up commit by rewording the sentence; the lesson - never write a
literal `*/` inside a `/** ... */` comment's text, even as shorthand like
"field*/otherField*" - is now something to actively check for, not just
avoid by luck.

### Phase 33, Step B: random-on-planet positioning when characters share

The second of the four steps from the "Planet/character scaling" design
note above. Confirmed scope: replace the fixed ± symmetric-offset start
angle with genuine randomness when characters share a planet, and make
sure the one-per-planet case (not reachable yet - still exactly one
planet per side until Step C) is handled correctly too.

**A discovery, not just a build:** `assignCharacterPlanets` (Step A's
"placeholder" cycling formula, `planets[it % planets.size]`) turned out
to already BE the correct final assignment rule, not a stand-in for one -
when characters ≤ planets it gives each character its own distinct
planet (one-per-planet) automatically, since every index stays below
`planets.size`; when characters > planets it round-robins them, giving
each planet floor/ceil of an even split. So Step B needed zero changes to
that function - what Boo's "characters randomly on their planet" design
actually needed was the *positioning of characters already assigned to
the same planet*, which the old fixed-offset scheme handled only for
exactly two characters, hard-coded.

**What's built**, all in `PlayScreen.kt`:
- **`assignCharacterStartAngles(characterPlanets)`** (new) - one start
  angle per character, same index order as its input. Groups characters
  by which `Planet` *object* they were assigned to (reference equality,
  not equal-by-value - two entries pointing at the identical `Planet`
  instance from `assignCharacterPlanets`'s cycling means "these share a
  planet"), then draws each group's angles together via
  `randomAnglesWithMinSeparation` so a planet with multiple characters on
  it never places them too close; a solo character (group size 1) just
  gets one uniformly-random angle, nothing to check against.
- **`randomAnglesWithMinSeparation(count)`** (new) - reject-and-retry, the
  same discipline `randomPlanetPosition` already uses for planet spacing:
  draw a random angle, keep it only if it's at least
  `MIN_SHARED_PLANET_ANGLE_SEPARATION_DEGREES` (60°) from every angle
  already kept, up to `PLANET_ANGLE_PLACEMENT_MAX_ATTEMPTS` (50) tries
  total, with an even-spacing fallback (still from a random starting
  angle) if that bound is ever hit - only realistic once
  `MIN_SHARED_PLANET_ANGLE_SEPARATION_DEGREES × count` starts
  approaching 360°, not the case at today's 2-per-side squads.
- **60° chosen to exactly preserve the old scheme's clearance
  guarantee**, not picked fresh: the removed `CHARACTER_START_ANGLE_SPREAD_DEGREES`
  was a ±30° offset, i.e. 60° apart total - the value Phase 30's on-device
  testing already confirmed gives both sides' sprite sizes (`AVATAR_RADIUS`/
  the bigger `TARGET_RADIUS`) real clearance at this orbit radius. Using it
  as a **minimum** rather than an exact value is the only actual change -
  two characters sharing a planet can now land anywhere from 60° to 300°
  apart, never closer, instead of always exactly 60° apart at a fixed base
  direction. `ORDER_PICKER_TAP_RADIUS` (0.45) still depends on this same
  60° figure to keep the two characters' tap zones from overlapping (see
  that constant's own doc comment) - unaffected by this change since the
  number itself didn't move, only how it's enforced.
- **`AVATAR_START_ANGLE_DEGREES`/`AI_START_ANGLE_DEGREES` removed** - they
  encoded "center of the fixed facing arc," which no longer means
  anything once placement is random across the whole planet rather than
  offset from a base direction. Nothing else in the file referenced them.

**Judgment call, not confirmed with Boo specifically:** angles are drawn
from the character's *entire* planet (0° to 360°, no preferred "facing
the other side" direction), not restricted to an arc facing the opposing
side the way the old fixed scheme implicitly did. This matches "randomly
on their planet" literally, but means a character could now spawn facing
away from the enemy planet entirely. Easy to constrain to an arc later if
that reads wrong on-device - flagging it explicitly rather than deciding
silently.

**Not built this step:** the one-per-planet code path is written and
should be correct (each character in its own group of one, per the
`assignCharacterPlanets` discovery above), but isn't actually reachable
yet - `playerPlanets`/`aiPlanets` stay size 1 until Step C, so today every
character is still in a shared-planet group of exactly 2. Worth a
specific on-device check once Step C lands multiple planets per side.

#### How to test this phase on-device

1. Build and run several fresh games in a row (each "New Game" reshuffles
   planet positions, so each also reshuffles character start angles now).
   Confirm each side's two characters land at a genuinely different
   relative angle to each other from game to game - not always the same
   fixed-looking spread as before.
2. Across several games, confirm the two characters on a side never look
   like they're touching or overlapping, and specifically watch the AI
   pair (the bigger `TARGET_RADIUS` sprite) since that's the case Phase
   30 had to bump the old fixed spread for.
3. Confirm the Step 2 turn-order picker (tap-a-character-to-go-first)
   still reliably picks whichever character you actually tap, even at the
   new random angles - this is exactly what `ORDER_PICKER_TAP_RADIUS`'s
   preserved 60°-minimum guarantee is protecting.
4. General regression check: turn order, AI targeting/obstacle-avoidance,
   drift, and the horizon/no-fire check should all still behave normally -
   this step only touches where characters *start*, nothing about turn
   flow or combat logic.

### Phase 33, Step C: generalized the placement algorithm to N planets (region-quota)

The third of the four steps from the "Planet/character scaling" design
note above. Scope, confirmed by continuing straight down the documented
plan: generalize the *placement algorithm itself* (the already-decided
scattered + region-quota rule) from always-exactly-2-planets to a
reusable N-capable function - not to actually put more than 2 planets in
a game yet. That's deliberately still Step D's job (the campaign ladder's
own escalation content), since nothing needs a bigger field or more
planets to exist until the ladder is what's asking for them.

**What's built**, all in `PlayScreen.kt`:
- **`generateScatteredPositions(count)`** (new) - produces `count`
  positions, each clear of the star (`MIN_PLANET_STAR_SEPARATION`) and of
  every other position already placed this call (`MIN_PLANET_SEPARATION`),
  via the same reject-and-retry discipline the old single-planet drawer
  used. Layers the region-quota rule on top: the field divides into a
  square grid (`regionsPerAxis`, sized so total regions ≈ `count` /
  `REGION_QUOTA_MAX_OBJECTS_PER_REGION`, i.e. on average right at
  capacity), and a candidate is rejected if its region has already hit
  `REGION_QUOTA_MAX_OBJECTS_PER_REGION` (2, a starting guess, not tuned
  yet) - the layer that actually stops objects piling into one corner of
  a large field, per Boo's "if field size is large, I dont want 6 objects
  clustered in a corner" concern from the original design discussion.
- **At today's `count == 2`, the region grid works out to a single 1x1
  region** covering the whole field, so the quota layer is a complete
  no-op right now - confirmed by the math (`regionsPerAxis(2)` = ceil(√(2/2))
  = 1), not just asserted. This was the intended outcome, not a bug: the
  point of Step C is reusable machinery, not a visible change to today's
  2-planet game. Nothing should look different on-device after this step.
- **`randomizePlanetPositions()` now calls `generateScatteredPositions(2)`**
  instead of drawing the launch/target pair as two separate single-planet
  calls. The star-*flight-path* check (`planetLayoutIsClear` - the star
  can't sit too close to the direct line between the two planets) stays a
  separate post-check, since "which of N planets counts as the flight
  path" only means something for exactly a launch/target pair - on
  failure the whole pair is redrawn via a fresh `generateScatteredPositions`
  call, same retry budget as before.
- **`randomPlanetPosition()` (the old single-planet drawer) is gone**,
  fully superseded by `generateScatteredPositions`.

**Not built this step:** anything that would actually let more than one
planet exist per side in a real game - the celestial-body construction in
`init{}` (`launchPlanetBody`/`launchPlanetEntity`/`targetPlanetBody`/
`targetPlanetEntity`) is still exactly two hardcoded fields, not a list,
and rendering/drift/HUD all still assume exactly one planet per side.
Generalizing *those* wasn't part of Step C's scope - they're needed only
once Step D's campaign ladder actually asks for a second planet, and are
sized better as part of building that real, playable escalation content
than as speculative plumbing here. The field-size-cap formula (also
deferred, see the design note above) is the other piece Step D still
needs.

#### How to test this phase on-device

This step is a pure refactor with no intended visible change (see "at
today's count == 2" above) - testing is regression-only:
1. Build and run several fresh games in a row. Both planets should still
   land randomly each game, respecting the same clearances as always -
   never overlapping the star, never too close to each other, never with
   the star sitting on the direct line between them (the older star-in-
   flight-path bug this file already fixed once).
2. Nothing about how planets look, where they land, or how the game plays
   should feel any different from before this step - if it does, that's a
   sign this refactor accidentally changed something it shouldn't have.
3. Play a handful of games to reasonable completion (destroy a planet,
   let a shot go stray, etc.) and confirm nothing regressed there either -
   this step didn't touch anything past initial placement, but it's worth
   confirming.

### Phase 33, Step D: campaign ladder + dynamic field size

Boo, right after Step C shipped: confirmed the two remaining open design
questions, then asked to pause before building - picked back up in a
later session to actually build Step D1 (see its own "✅ DONE" entry
below, after the design/risk-assessment writeup). Step D2 is still not
started.

**Confirmed this session (both via direct answers, not judgment calls):**
- **Field-size formula:** scale both `WORLD_WIDTH`/`WORLD_HEIGHT` by
  `sqrt(currentCelestialObjectCount / 3)`, capped at 1.5x, keeping the
  same 9:16 ratio throughout. At today's tier (3 objects: star + 1 planet
  each side) this is exactly 1x - byte-identical to today's fixed 9f/16f.
  5-win tier (4 objects) -> ~1.155x (~15% bigger). 20/30-win tier (6
  objects) -> ~1.414x (~41% bigger) - never actually hits the 1.5x cap at
  today's ladder, that's headroom for a future extension.
- **`STAR_X`/`STAR_Y` both scale with the field.** `STAR_X = WORLD_WIDTH /
  2f` already did (existing formula). `STAR_Y` is today an independent
  fixed `9f` (NOT derived from `WORLD_HEIGHT`, confirmed by direct read -
  it's not `WORLD_HEIGHT / 2`, just its own hand-picked value at the base
  16f field height). Boo, asked directly: scale it proportionally rather
  than leave it literally fixed, so the star keeps the same *relative*
  position (`WORLD_HEIGHT * 9f/16f`) as the field grows, instead of
  visually drifting toward center at higher tiers.
- **Multi-planet size variation: plain radius variation, existing art
  only.** Boo's exact words: "Size variation, eventually I will want to
  reskin these elements but dont want to focus on that now. use the same
  image but do vary the sizes of the planets" - reuse
  `planetLaunchTexture`/`planetTargetTexture` exactly as-is, just draw
  each planet on a side at a different `PLANET_RADIUS`-equivalent size.
  No tinting, no rotation, no new art - texture/art reskinning is
  explicitly a separate, later, deferred idea.
- **Add a permanent win-count debug control**, same standing precedent as
  `GravityDebugController`/`ShotSpeedDebugController` (small on-screen
  buttons, left in permanently - no release build to worry about a debug
  tool leaking into). A `+1` (and a `reset to 0`) button for
  `SaveManager`'s win count, so every ladder tier can be tested on-demand
  instead of needing to actually grind 5/20/30 real wins each time this
  gets touched again.

**Risk-assessment findings from this session (why Step D isn't a quick
follow-on to A/B/C, and can't be zero-visible-change the way those were):**
- **`SaveManager.currentWinCount()` confirmed ready to use as-is** -
  already exists, already the exact API needed (`GameSave.winCount`, v3
  schema, incremented only by `SaveManager.recordWin()`, called only from
  `PlayScreen`'s win branch - see Phase 23 above). Nothing needed there.
- **`WORLD_WIDTH`/`WORLD_HEIGHT` have to stop being `companion object
  const val`s and become instance-level `val`s**, since they now need to
  depend on a runtime-read win count (through the campaign-tier lookup).
  Confirmed only one other companion constant is derived from them
  (`STAR_X`) - `STAR_Y` turned out NOT to be (see above), so the ripple is
  smaller than it could have been. But Kotlin instance properties
  initialize in strict textual declaration order (interleaved with
  `init{}` blocks in that same order), with no compiler available on this
  side to catch a mistake - so the campaign-tier lookup and the new
  `WORLD_WIDTH`/`WORLD_HEIGHT`/`STAR_X`/`STAR_Y` values all need to be
  declared very early in the class body, before `starfieldStars` (an
  existing instance `val` that already reads `WORLD_WIDTH`/`WORLD_HEIGHT`
  today) and before the main `init{}` block's viewport/camera setup
  (`ExtendViewport(WORLD_WIDTH, WORLD_HEIGHT, camera)` /
  `camera.position.set(...)`), both of which currently sit well before any
  of Step D's new code would naturally go.
- **Good news: HUD and character rendering are already mostly
  generalized**, a free side-effect of the multi-character combat work
  (Steps 1-3) - `renderStatsPanel`'s `playerRows`/`aiRows` already loop
  over `playerCharacters`/`aiCharacters` (`mapIndexed`, sized to whatever
  the list holds), and `renderCharacterSprites` already loops over both
  lists too. Only the *planet* side of both still assumes exactly one
  planet per side: `renderCelestialSprites` draws `launchPlanetEntity`/
  `targetPlanetEntity` directly (not a loop), and the stats panel's
  `launchRow`/`targetRow` (planet mass) are still single hardcoded rows,
  not one per planet. Both are straightforward to generalize into loops
  over `playerPlanets`/`aiPlanets` (the `Planet` class already carries
  `entity`/`position`/`radius` - Step A/C plumbing pays off here).
- **Real complication found: `SlingshotInputProcessor` assumes the player
  has exactly one planet, fixed at construction.** Its constructor takes
  `planetCenter: Vector2` and `planetRadius: Float` once, used for the
  horizon/aim-blocking math (`isBelowHorizon`/`clampAboveHorizon`) -
  today that's always `launchPlanetPosition`/`PLANET_RADIUS` because
  there's only ever one player planet. `launchPoint` (the aim origin)
  already IS updated live on every turn hand-off (`launchPoint.set(...)`
  in `advanceAfterPlayerFired`, an aliased `Vector2` `SlingshotInputProcessor`
  shares by reference) - but `planetCenter`/`planetRadius` are not: they
  never change after `init{}`. Once the player can have a 2nd planet (20-
  win tier) with a different character possibly standing on a different,
  differently-sized planet (size variation), the currently-active
  character's real planet center/radius has to be read live, the same way
  `launchPoint` already is - likely `planetCenter` becomes another
  mutated-in-place `Vector2` and `planetRadius` becomes a lambda
  (`() -> Float`) instead of a fixed `Float`. A real interface change to
  that class, not just `PlayScreen`-internal plumbing.
- **New collision category needed for the AI's 3rd character** (20-win
  tier) - today only `CATEGORY_AI_TARGET`/`_2` and `CATEGORY_PLAYER_AVATAR`/
  `_2` exist (two per side). The player side never exceeds 2 planets/
  characters in this ladder, so only the AI needs a 3rd
  (`CATEGORY_AI_TARGET_3` or an array-based lookup replacing the current
  `if (i == 0) X else X_2` pattern, which doesn't extend past 2 as
  written).

**Planned build order** (split into two deliveries rather than one large
one, given the risk above):
- **Step D1 - built, see its own entry immediately below.**
- **Step D2 - built, see its own entry further below.** Per-planet size
  variation ended up deliberately narrowed out of this step's actual
  scope during design (kept every planet on a side geometrically
  identical, `SlingshotInputProcessor`'s `planetRadius` stayed a fixed
  `Float` rather than becoming a lambda) - see that entry's own notes for
  why, and what's left for whenever size variation is picked up.

#### Phase 33, Step D1: campaign tier lookup + dynamic field size + win-count debug control - ✅ DONE, built (Sept 2026 session)

The first of the two Step D deliveries above. Confirmed scope going in:
the campaign-tier lookup, the `WORLD_WIDTH`/`WORLD_HEIGHT`/`STAR_X`/
`STAR_Y` conversion to correctly-ordered instance values, and the new
permanent win-count debug control - deliberately NOT the entity/rendering/
HUD/collision generalization (that's Step D2's job).

**What's built, all in `PlayScreen.kt` unless noted:**
- **`CampaignTier` data class + `campaignTierFor(winCount)`** (both in the
  companion object) - the exact thresholds from "Campaign progression
  ladder" above: 0-4 wins = 1 planet/character each side (today's real
  behavior), 5-19 wins = AI gets a 2nd, 20-29 wins = AI gets a 3rd AND
  player gets a 2nd, 30+ wins = same counts as 20-29 plus
  `isCampaignComplete = true`. Pure function of an `Int`, not tied to any
  instance state - safe to call from a property initializer regardless of
  declaration order.
- **`campaignTier`/`totalCelestialObjectCount`/`fieldScale`/`WORLD_WIDTH`/
  `WORLD_HEIGHT`/`STAR_X`/`STAR_Y`** - a new block of instance `val`s,
  placed immediately after the companion object closes (before every
  other property in the class) for exactly the reason the risk-assessment
  above flagged: `starfieldStars` and `init{}`'s viewport/camera setup
  both already read `WORLD_WIDTH`/`WORLD_HEIGHT`/`STAR_X`, so these have
  to resolve before either runs, and Kotlin initializes properties in
  strict textual order. `WORLD_WIDTH`/`WORLD_HEIGHT`/`STAR_X`/`STAR_Y`
  deliberately kept these exact same names (now instance vals instead of
  companion `const val`s) rather than being renamed, so every other
  existing reference to them in this file needed zero changes - a real
  risk-reduction call given there's no compiler on this side of the
  workflow to catch a missed rename. `STAR_X`/`STAR_Y` both now scale with
  the field per the confirmed design (`STAR_Y = WORLD_HEIGHT * 9f/16f`,
  proportional as Boo asked, not literally fixed).
- **`BASE_WORLD_WIDTH`/`BASE_WORLD_HEIGHT`** (companion object) - the old
  fixed `9f`/`16f` values, now just `fieldScale`'s baseline rather than
  the field size itself.
- **`WinCountDebugController`** (new file) - two on-screen tap zones
  ("+1"/"R" for reset), stacked directly below
  `ShotSpeedDebugController`'s row (same right-edge column, same
  standing-permanent-debug-tool precedent as
  `GravityDebugController`/`ShotSpeedDebugController` - no release build
  to worry about a debug control leaking into). Wired into all three of
  `PlayScreen`'s `InputMultiplexer`s (`fullInputProcessor`/
  `restrictedInputProcessor`/`orderPickerInputProcessor`) plus
  `rebuildFullInputProcessor()`, and drawn every frame via the new
  `renderWinCountDebugControls()`, showing a live "Wins: N" readout.
- **`SaveManager.resetWinCount()`** (new function) - sets `winCount` back
  to 0 and persists immediately, same corruption-safe write every other
  `SaveManager` mutator uses. Backs the debug control's "Reset" button;
  also happens to be the same behavior the ladder design calls for at the
  30-win "reset progress to zero" milestone, just not yet wired to a real
  in-game menu option.

**Not built this step (by design - Step D2's job):** any of
`launchPlanetEntity`/`targetPlanetEntity`/`playerPlanets`/`aiPlanets`
actually growing past size 1 - `campaignTier.playerPlanetCount`/
`aiPlanetCount` are computed and drive the field-size formula, but nothing
yet *reads* them to create additional planets/characters, so a real game
still always has exactly 1 planet/character per side regardless of win
count. **This means tapping the debug `+1` button past 5/20 wins will
visibly resize the play field with no new planets/characters appearing to
fill it - that's expected, not a bug**, and worth knowing before testing
this step so it doesn't read as something broke.

#### How to test Step D1 on-device

1. Build and run a fresh game at whatever win count the save file
   currently has. If that's under 5, everything should look and play
   exactly as it did before this step - same field size, same single
   planet/character per side (byte-identical at the 3-object baseline
   tier, per the confirmed formula).
2. From the menu, start a game and look at the bottom-right corner: below
   the Gravity and Shot Speed tuning rows there should now be a third row
   - two buttons ("+1" and "R") and a "Wins: N" readout matching whatever
   `SaveManager` currently has.
3. Tap "+1" five times (or however many needed to cross 5 total wins),
   then back out to the menu and start a NEW game (the tier is only read
   once, at `PlayScreen` construction - tapping mid-game does nothing
   visible until the next new game, as noted above). The play field
   should now be visibly larger (~15% bigger in both dimensions) than the
   untouched-tier game, with the star sitting proportionally further from
   center vertically too - but still only one planet/character per side,
   per the "not built this step" note above.
4. Tap "+1" until past 20 total wins, start another new game, and confirm
   the field grows again (~41% bigger than the original baseline this
   time).
5. Tap "R" (reset), start a new game, and confirm the field returns to
   exactly its original size - confirms `resetWinCount()` round-trips
   cleanly and nothing about the scaling math is one-directional.
6. Regression check: play a couple of games to reasonable completion
   (destroy a planet, let a shot go stray, try the camera pinch/pan) at
   whichever field size you land on - nothing about turn structure,
   aiming, or combat should feel different, only the field's overall
   scale and the star's position within it.

#### Phase 33, Step D2: real N-planet/character generalization - ✅ DONE, built (Sept 2026 session)

The second of the two Step D deliveries above - the actual "more planets/
characters really appear at higher win counts" work. Confirmed scope going
in per the risk assessment: entity/body construction for real N-sized
`playerPlanets`/`aiPlanets`, every remaining call site that still read a
fixed `launchPlanet*`/`targetPlanet*` singular field, the new AI 3rd
collision category, and the `SlingshotInputProcessor` planetCenter fix -
all shipped together as one delivery, per the risk assessment's own call
that splitting them further would leave aiming subtly wrong at the 20-win
tier in between.

**A pleasant surprise going in:** Steps A/B/C (earlier "Planet/character
scaling" work, well before this campaign-tier design existed) had already
generalized the *hard* parts - obstacle avoidance
(`currentCelestialObstacles`), planet-sharing/round-robin assignment
(`assignCharacterPlanets`), and within-a-shared-planet start-angle
placement (`assignCharacterStartAngles`) all already read
`playerPlanets`/`aiPlanets` as real lists, not a fixed-index pair. Step
D2's actual work turned out to be: build those two lists for real (they'd
only ever held exactly 1 `Planet` each, wrapping the old singular fields),
and fix the handful of remaining spots - rendering, drift, the stats
panel, the debug wireframe filter, camera framing - that still bypassed
those lists and read `launchPlanetEntity`/`targetPlanetPosition`/etc.
directly. Much smaller than the risk assessment feared, precisely because
of that earlier groundwork.

**What's built, all in `PlayScreen.kt` unless noted:**
- **`Planet` gained a `body: Body` field** (alongside its existing
  `entity`/`position`/`radius`) - needed so the debug wireframe filter and
  the drift-landing check can identify/locate any planet generically, now
  that there's no fixed `launchPlanetBody`/`targetPlanetBody` pair to fall
  back on.
- **`playerCharacterCount`/`aiCharacterCount`** (new instance `val`s, right
  after Step D1's `campaignTier` block) replace the old fixed
  `CHARACTERS_PER_SIDE = 2` constant - just `campaignTier.playerPlanetCount`/
  `aiPlanetCount` directly, since every tier on today's ladder gives each
  character its own dedicated home planet (no tier actually shares one
  planet across characters, though `assignCharacterPlanets`' round-robin-
  sharing behavior is still real, working machinery underneath, kept for
  whenever a future tier needs it).
- **Real per-planet construction** - `playerPlanets`/`aiPlanets` are now
  built via `.map` over `playerPlanetPositions`/`aiPlanetPositions` (new
  fields, filled by `randomizePlanetPositions` - see below), each with its
  own Box2D body/Ashley entity/`GravitySourceComponent`, replacing the old
  two-singular-planets-then-wrap-in-a-size-1-list code. Every planet on a
  side gets that side's same fixed mass constant and the same reused
  texture - **no per-planet size or mass variation yet** (the confirmed
  "same image, vary sizes" design is real but deliberately deferred to a
  separate later polish pass, not part of this step's scope - keeping
  every planet on a side geometrically identical for now was a deliberate
  risk-reduction call, since it meant `SlingshotInputProcessor`/
  `AiTurnController`/`AvatarMovementController` could all keep using the
  fixed `PLANET_RADIUS` constant unchanged instead of also becoming
  per-planet this same step).
- **`randomizePlanetPositions()`/`planetLayoutIsClear()` generalized** to
  N positions - `generateScatteredPositions(campaignTier.playerPlanetCount
  + campaignTier.aiPlanetCount)`, split into the player/AI position lists.
  The old planet-to-planet separation re-check is gone (redundant -
  `generateScatteredPositions` already guarantees it for every pair it
  places, regardless of side); the star/flight-path clearance check now
  covers every player-planet/AI-planet pairing (up to 2×3 = 6 at the
  20-win tier), not just one fixed pair.
- **Each character now tracks its own `homePlanet: Planet`**
  (`PlayerCharacterState`/`AiCharacterState` both gained this field, set
  from `assignCharacterPlanets`' output at construction). This is what let
  the drift trigger, `beginPlayerDrift`/`beginAiDrift`, and
  `resolveDriftLanding` all generalize cleanly - see the real behavior
  change below.
- **New AI 3rd collision category** (`CATEGORY_AI_TARGET_3`) plus
  `PLAYER_AVATAR_CATEGORIES`/`AI_TARGET_CATEGORIES` array lookups
  replacing the old `if (i == 0) X else X_2` ternaries, which didn't scale
  past 2. The player side still only ever needs 2 categories on today's
  ladder.
- **`SlingshotInputProcessor`'s `planetCenter` fix** - new
  `activePlayerPlanetCenter: Vector2` field, live-mutated every frame
  (same pattern `launchPoint` already used) to whichever player character
  currently has the turn's own home planet, passed into
  `SlingshotInputProcessor` instead of the old fixed
  `launchPlanetPosition`. `planetRadius` stayed a fixed `Float` rather than
  becoming a lambda as originally guessed in the risk assessment - safe to
  simplify since every planet shares `PLANET_RADIUS` this step (no size
  variation yet, see above); revisit if/when size variation actually ships.
- **Rendering/HUD generalized to loop over `playerPlanets`/`aiPlanets`**:
  `renderCelestialSprites` (each planet drawn/damage-overlaid
  independently instead of exactly 2 named draws) and `renderStatsPanel`'s
  planet mass rows (now `launchRows`/`targetRows`, one per planet,
  numbered "Player Planet 1 Mass"/"Player Planet 2 Mass" etc. - matches
  the "always numbered" convention the character HP rows already used,
  even at today's 1-planet-per-side tiers, a small deliberate label change
  from the old unnumbered "Player Planet Mass" for consistency).
  `debugRenderer`'s wireframe-hiding override now checks
  `playerPlanets`/`aiPlanets` by reference instead of the old fixed
  `launchPlanetBody`/`targetPlanetBody` pair.

**A real behavior change, not just plumbing** - worth flagging clearly
before on-device testing: the orbital-drift trigger (in `render()`) used to
check one SHARED home planet per side, so destroying either side's one
planet knocked every character on that side into drift at once. Now that
each character tracks its own `homePlanet`, at the 20-win tier (2 separate
player planets, 1 character each) **destroying one of the player's planets
only sets THAT planet's character drifting - the other player character,
on their own still-intact planet, stays put and keeps fighting normally.**
This is the whole point of the generalization, not a bug, but it's a
genuinely different feel from every earlier tier (where the two characters
per side always shared one planet and always drifted together) - worth
Boo's explicit attention during on-device testing at that tier specifically.

**A judgment call, not confirmed with Boo, flagged for on-device
feedback:** `beginPlayerOrderPick()` (the "tap a character to go first"
picker, shown whenever 2+ player characters are alive) used to reframe the
camera on "the" shared planet - with 2 SEPARATE player planets at the
20-win tier, there's no longer one single planet to frame on. It now
reframes on the midpoint between every living player planet, at the same
existing `AVATAR_SNAP_ZOOM`. This is a reasonable first cut, not a
confirmed design - `AVATAR_SNAP_ZOOM` may turn out too tight to show both
planets clearly enough to tap between them; a real zoom-to-fit would be
the natural fix if that's what on-device testing at the 20-win tier shows.

**Not built this step (by design - separate later work, not part of this
step's confirmed scope):**
- **Per-planet size/mass variation** - the confirmed "same image, vary
  sizes" design. Every planet on a side is still geometrically/physically
  identical this step (see above) - a deliberate scope-narrowing call made
  during this step's own design pass (not previously confirmed with Boo as
  a separate step), to keep this already-large delivery lower-risk. Needs
  its own pass through `SlingshotInputProcessor`/`AiTurnController`/
  `AvatarMovementController` (all currently still read the fixed
  `PLANET_RADIUS` constant, not `Planet.radius`) plus texture/draw-size
  changes once picked up.
- The two still-open orbital-drift bugs from the earlier drift-speed-fix
  session (overlap bug, freeze-timing bug) - untouched by this step,
  still open, see that section above.

#### How to test Step D2 on-device

1. Baseline check (same as Step D1's own step 1): a fresh game at under 5
   total wins should look and play exactly as before - 1 planet/character
   each side, nothing visibly different from pre-D2.
2. Use the win-count debug control ("+1"/"R", bottom-right, from Step D1)
   to cross 5 wins, then start a NEW game (tier is read once at
   construction, same as Step D1). Confirm the AI now has 2 characters on
   its own single (bigger) planet, while the player still has just 1 -
   matches the "5-19 wins" tier. Check the stats panel shows 2 "Target N
   HP" rows and 1 numbered "Target Planet 1 Mass" row.
3. Cross 20 wins, start another new game. Confirm the AI now has 3
   characters (still all on one AI planet), and the player now has 2
   characters **each on their own separate planet** - this is the tier
   where the behavior actually gets structurally new. Check:
   - The stats panel shows 2 "Player Planet N Mass" rows now, not 1.
   - The player's own turn-order picker (tap either character to go first,
     each round) reframes the camera somewhere that shows both of the
     player's planets - flag if `AVATAR_SNAP_ZOOM` feels too tight to
     comfortably tap between them (see the judgment call above).
   - Destroy ONE of the player's two planets (aim at it directly) and
     confirm only the character standing on that specific planet starts
     drifting - the other player character, on their still-intact planet,
     should keep standing/aiming normally, not drift. This is the main
     new behavior this step adds - see "A real behavior change" above.
   - Confirm a drifting character can still land on ANY surviving planet
     (not just its own former one) if its orbit happens to carry it there,
     and that flying into the star still ends that character the same way
     it always has.
4. Cross 30 wins, confirm the field/planet counts match the 20-29 tier
   exactly (no further growth) and nothing looks broken at
   `isCampaignComplete = true`.
5. Regression check across every tier tested above: normal aiming/firing
   still works for every character (including the horizon-restriction
   "can't fire into your own planet" rule - watch this specifically for
   the player's 2nd character at the 20-win tier, since
   `activePlayerPlanetCenter` is the part of this step most directly
   responsible for keeping that check correct per-character), collision/
   friendly-fire behavior is unchanged, and a full game still ends
   correctly (GameOverScreen, win or loss) at every tier.
6. Tap "R" to reset win count back toward 0 and confirm a fresh game
   returns cleanly to the 1-planet/1-character baseline - same
   round-trip check Step D1's own test script already covered, worth
   reconfirming now that real entities are actually being built/torn down
   per tier instead of just the field size changing.

## Phase 24: pinch-zoom/pan camera + snap-to-active-avatar

Builds the camera/play-field prerequisite identified in the "Multi-
character combat" design note above - implements the parts of that
discussion that were actually buildable now (the camera system itself),
not the parts still waiting on other undecided design (play-field size
cap, planet placement algorithm specifics, squad composition).

**What's built:**
- **`CameraGestureController`** (new) - a 2-finger-only pinch-zoom/pan
  `InputProcessor`. Claims nothing while only one pointer is down (falls
  through untouched to `AvatarMovementController`'s move buttons and
  `SlingshotInputProcessor`'s aim drag, exactly as before); starts
  consuming touch events the instant a second pointer arrives, and every
  drag while two are down solves for the camera position that keeps the
  world point under the gesture's starting midpoint anchored under the
  current midpoint at whatever the current pinch-derived zoom is - one
  calculation handles both "the point between your fingers stays put"
  pinch-zoom and ordinary drag-to-pan together. Registered FIRST in both
  of `PlayScreen`'s InputMultiplexers (`fullInputProcessor` and
  `restrictedInputProcessor` - camera control works regardless of whose
  turn it is), since `SlingshotInputProcessor` doesn't filter events by
  pointer index and would otherwise risk misreading a second finger as
  continuing an in-progress aim drag.
- **Aim gets cancelled, not corrupted, if a second finger arrives mid-
  drag.** `SlingshotInputProcessor.cancelAim()` (new) resets `aiming`/
  `currentAimLine`/the cancel-gesture flags, same as a fresh `touchDown`
  would. `PlayScreen` wires `CameraGestureController`'s
  `onGestureEngaged` callback to call it the instant a pinch/pan begins.
  Once a pinch/pan ends, a still-resting finger does nothing further
  until it's lifted and touched down fresh - not a seamless handoff back,
  a deliberate simplicity/safety tradeoff worth an on-device feel-check.
- **Camera snaps to the active avatar at each turn transition.** Boo: "
  when it is a avatars turn, the camera should go to that specific
  character" - not a continuous follow (doesn't chase the projectile
  after firing, doesn't track movement mid-turn). `PlayScreen.
  snapCameraToActiveAvatar(worldPosition)` sets `camera.position` and a
  fixed `AVATAR_SNAP_ZOOM` framing level, called from inside the
  deferred `giveControlToPlayer`/`startAiTurn` closures specifically
  (not the outer `onTurnPassed`/`onTurnComplete` bodies, which can fire
  before `shotFlightFreezeRemaining`'s freeze actually lets the handoff
  happen - see that field's doc comment), plus once more at the end of
  `init{}` so the very first turn starts framed too instead of on the
  old fixed full-world view. Between snaps, pinch/pan/zoom is completely
  free, including all through the active player's own aiming and after
  they've fired.
- **`resize()` no longer discards a pan on rotation.** `FitViewport`'s
  own `update(..., centerCamera = true)` unconditionally recenters
  `camera.position` to world-center - `PlayScreen.resize()` now stashes
  and restores the camera position around that call, so a device
  rotation or window resize only re-letterboxes, never silently resets
  the view back to center.

**Deliberately not built this phase** (see "Multi-character combat"
design note above for why): the play-field-size cap, the scattered-with-
region-quota planet placement algorithm, squad composition/AI behavior
with multiple characters, the play-field-boundary stray-shot timer, and
the 4-per-side stray-shot cap. This phase is camera plumbing only - the
prerequisite those depend on, not those themselves.

### Phase 24 addendum: first on-device test fixes

Boo tested on a foldable (screenshots) and found three things worth
fixing before this phase is really done:

1. **AVATAR_SNAP_ZOOM was way too tight.** 0.4 filled most of the screen
   with just the active planet - "way too zoomed." Bumped to **1.0**,
   which isn't a fresh guess: it's exactly camera.zoom's own default,
   and matches the framing this game always used before this camera
   system existed at all (both planets and the star visible together).
   With only 2 planets in play right now this mostly looks like the
   classic view again - the snap's actual "frame just this one
   character" effect will read more clearly once multi-planet layouts
   exist.
2. **Black bars on non-9:16 screens.** The foldable's main screen,
   opened flat, is far squarer than the fixed 9:16 `WORLD_WIDTH`:
   `WORLD_HEIGHT` ratio - visible in Boo's screenshots as black bars
   down both sides, circled. That was `FitViewport` letterboxing by
   design (see the removed comment this session deleted for the exact
   old reasoning). Boo: "whether in portrait, landscape or on a
   foldable device, the visible part of the playing field goes to the
   bezel." Swapped `FitViewport` -> **`ExtendViewport`**: `WORLD_WIDTH`/
   `WORLD_HEIGHT` become a guaranteed MINIMUM visible area rather than a
   locked aspect ratio: whichever axis is needed extends to fill the
   real screen exactly, no stretching/distortion, no bars - a squarer or
   wider screen just reveals more backdrop at the edges. Nothing about
   where planets/the star/celestial bodies get placed changes - every
   other `WORLD_WIDTH`/`WORLD_HEIGHT` reference in the file still means
   the same fixed 9x16 game field; only how much of the surrounding
   screen gets filled with rendered content changed.
3. **The starfield shrank with zoom.** Boo: "I zoomed out and the
   objects got smaller. again that's good but notice how the starfield
   also shrinks." Foreground gameplay objects correctly shrinking as you
   zoom out is right; a background backdrop doing the exact same thing
   reads wrong - real distant stars don't visibly change size as you
   zoom a camera. Fixed two separate things:
   - `renderStarfield()` now multiplies each star's radius by
     `camera.zoom` before drawing - cancels out exactly the per-world-
     unit screen-pixel change zooming causes, so a star's on-screen size
     stays roughly constant at any zoom level. Positions still pan with
     the world normally (unchanged), only the size compensation is new.
   - The starfield's spawn area was also just the exact `WORLD_WIDTH` x
     `WORLD_HEIGHT` rectangle, a leftover from when the camera never
     moved - once pinch/pan/zoom (plus `ExtendViewport` revealing more
     area on odd-aspect screens) can show well past that fixed
     rectangle, panning or zooming out far enough would have run off
     the backdrop into plain black space. `STARFIELD_EXTENT_MULTIPLIER`
     (3x both dimensions, centered on the same field center) and
     `STARFIELD_STAR_COUNT` (70 -> 320, a deliberate compromise - full
     9x density for 9x the area would be close to 630 stars/9x the draw
     calls for a background element) both cover this now. Not aware of
     the future play-field-size cap (still undecided, see the "Multi-
     character combat" design note above) - likely wants revisiting
     once that exists rather than being a permanent fixed multiplier.

### How to test Phase 24 on-device

1. Sync Gradle, run on-device as usual.
2. Confirm the game starts framed on your own avatar, at roughly the
   classic full-field zoom level (both planets/the star visible), not
   the earlier too-tight framing.
3. During your own turn, pinch with two fingers to zoom in/out, and drag
   with two fingers to pan around - confirm one finger alone still only
   ever moves/aims exactly as before, never pans the camera.
4. While one finger is actively aiming (pull-back in progress, aim line
   visible), add a second finger and pinch/pan - confirm the aim line
   disappears/cancels cleanly (no stuck aim, no corrupted trajectory)
   and the camera responds to the pinch normally.
5. Take a shot, then immediately pinch/pan away from your character
   before the turn hands off - confirm the camera does NOT fight you or
   snap back mid-turn, and only jumps to the AI's character once the
   handoff actually happens (after the shot-flight freeze expires).
6. Confirm the same snap happens in reverse once the AI's turn ends and
   control returns to you.
7. On the foldable (or any non-9:16 screen/orientation), confirm the
   play field now fills all the way to the bezel - no black bars on any
   edge, in portrait, landscape, or folded-flat.
8. Rotate the device (or resize the window, if testing on an emulator)
   mid-game after having panned away from center - confirm the view
   re-fits the new screen shape but does NOT jump back to world-center.
9. Zoom out as far as MAX_ZOOM allows and pan toward an edge - confirm
   there's still visible starfield, no plain black void, and that
   individual stars look roughly the same size as they did before
   zooming out (not visibly shrunk).
10. General feel check: does AVATAR_SNAP_ZOOM (1.0) frame things well
    now? Does the pinch-zoom range (0.25-2.5) feel right given the
    current fixed field size? Does the starfield's new density (320
    stars over a 3x-wider area) look right, or too sparse/too busy?
    All still starting guesses, easy to retune further.

## Phase 25: turn ends on shot resolution + persistent stray shots + eased camera snap

Picks up two items straight from Phase 24's "deliberately not built"
list, plus a fresh complaint about the snap itself. Boo, testing Phase
24's hard camera snap: "the abruptness. and you cant see your shot an
dfollow it to completion or it flying out of the play field." Rather
than building a narrower fix, this directly builds out the "Multi-
character combat" design note's stray-shot lifecycle (turn-ending
trigger + persistence + damage + the 4-per-side/8-total cap) that Phase
24 had explicitly deferred - Boo confirmed the full scope after being
walked through the tradeoff: "are you sayin that if I have the turn
terminate that I have to sacrifice the shot that can possibly return
later?" / "yes" (build the full thing, no shortcuts).

**What's built:**
- **The turn no longer hands off on a flat timer.** The old
  `SHOT_FLIGHT_FREEZE_SECONDS` (a fixed 5-second freeze after every shot,
  regardless of what happened to it) is gone entirely. Instead,
  `PlayScreen` tracks whichever shot was just fired (`activeShotEntity`/
  `activeShotSide`, plus `shotResolved` - false the instant it's fired)
  and only allows the pending turn-handoff (`pendingTurnHandoff`, same
  deferred-closure mechanism as before) to fire once that specific shot
  is actually resolved: either it hits something (removed by
  `ProjectileContactListener`'s `flushRemovals` - detected via a new
  `engineHasEntity` check, run right after `flushRemovals` each frame so
  a same-frame impact is caught immediately, not one frame late), or it
  has been continuously outside the play field
  (`WORLD_WIDTH` x `WORLD_HEIGHT`, unchanged rectangle - still a
  pragmatic stand-in for the still-undecided future field-size cap) for
  `FIELD_EXIT_TURN_END_SECONDS` (3s). Being off *camera* (pinch/pan/zoom
  looking elsewhere) never starts this timer - only actually leaving the
  fixed play-field rectangle does, exactly as designed.
- **A shot that times out via field-exit is NOT destroyed - it becomes a
  persistent "stray."** `ProjectileComponent` now carries a `side`
  (`Side.PLAYER`/`Side.AI`, new enum in Components.kt) so a stray can
  still be attributed to whoever fired it long after the fact. A stray
  keeps its `GravityAffectedComponent`/`ProjectileComponent` tags and
  keeps existing/simulating/getting pulled by gravity indefinitely -
  `ProjectileContactListener` needs zero changes, since it already
  treats any `ProjectileComponent`-tagged entity generically, so a stray
  drifting back in and hitting something (even its own side) deals real
  damage exactly like a fresh shot, per Boo's explicit "it absolutely
  can still cause damage. even to oneself. thats the unexpected element
  I like."
- **Per-side stray cap: 4 per side, 8 total, oldest-first eviction,
  sides independent.** `playerStrays`/`aiStrays` (two separate lists) -
  `addStray` appends the newly-timed-out shot to its own side's list and,
  if that pushes the side over `STRAY_SHOT_CAP_PER_SIDE` (4), immediately
  despawns (destroys body + removes entity, bypassing
  `ProjectileContactListener`'s deferred-removal queue since this never
  runs from inside a Box2D contact callback) that same side's own oldest
  stray. The two sides never affect each other's cap. Each frame, right
  after `flushRemovals`, both lists get pruned of any entry a later
  impact already destroyed, so a stray that dies naturally doesn't keep
  occupying a cap slot forever.
- **No failsafe/backstop timer for a shot that never resolves either
  way** (e.g. drifts into a stable orbit outside the field and never
  crosses back in or hits anything) - considered and explicitly declined
  by Boo: "i dont care about something getting in a stable orbit."
- **Camera snap is eased, not an instant jump-cut.** Boo: "the
  abruptness." `snapCameraToActiveAvatar` now starts a
  `CAMERA_EASE_DURATION_SECONDS` (0.5s) smoothstep transition from the
  camera's current position/zoom to the target avatar's framing, advanced
  every frame in `render()`, instead of setting `camera.position`/
  `camera.zoom` outright. An `instant` parameter (used only for the very
  first framing in `init{}`, where there's nothing worth easing from yet)
  keeps the old jump-cut behavior available. `CameraGestureController`'s
  `onGestureEngaged` callback cancels an in-progress ease the instant the
  player starts a 2-finger pinch/pan, so a snap never fights the player's
  own camera control.

**Deliberately not built this phase** (still open per the "Multi-
character combat" design note's "Still not decided" list): the
play-field-size cap tied to celestial-object count/size, the scattered-
with-region-quota planet placement algorithm, and squad composition/AI
behavior with multiple characters. Also not part of this phase (Boo's
separate, still-queued items from the same conversation): revisiting
planet/celestial graphics and design language, keeping the aim line
always visible (red when aiming at your own planet) instead of hiding it
below the horizon, and removing post-shot movement entirely in favor of
a pre-shot-only movement budget.

### How to test Phase 25 on-device

1. Sync Gradle, run on-device as usual.
2. Take a shot that clearly hits something (a planet or a character) -
   confirm the turn hands off promptly right on impact, same responsive
   feel as before, not a fixed multi-second wait regardless of outcome.
3. Take a shot and immediately pinch/pan/zoom away from it while it's
   still flying - confirm you can freely look around, and that the turn
   does NOT hand off just because the shot is off-screen; it should only
   hand off once you can confirm (e.g. by panning back to look) that the
   shot has either hit something or genuinely left the fixed play field
   for a few seconds.
4. Fire a wild shot that flies out past the edge of the field and never
   comes back or hits anything - confirm the turn still hands off after
   a few seconds (not never), and that the projectile is NOT visibly
   destroyed - if you pan/zoom out far enough you should still be able
   to find it drifting.
5. Let a stray shot drift back into the play field and hit something
   (including its own side's planet/character, if you can arrange it) -
   confirm it deals real damage exactly like a fresh shot would.
6. Deliberately rack up more than 4 strays on one side (repeatedly miss
   and let shots time out past the field boundary) - confirm the oldest
   one quietly disappears once the 5th would-be stray times out, and
   that the OTHER side's own stray count is unaffected by this.
7. Confirm every turn-handoff camera snap (both directions - player to
   AI and back) now visibly eases into place over about half a second
   instead of jump-cutting; confirm starting a 2-finger pinch/pan during
   that eased transition takes over cleanly without any camera fighting
   or snapping back.
8. General feel check: does 3 seconds (`FIELD_EXIT_TURN_END_SECONDS`)
   feel like the right "few seconds" for the field-exit timer? Does 0.5
   seconds feel right for the camera ease? Both still starting guesses.

## Phase 26: real planet art (Kenney CC0 "Planets" pack)

Picks up item #2 from Boo's post-Phase-24 feedback list - "I want to
revisit the graphics and planets... figure out a good design language or
find a good open source library of celestial objects." Boo, walked
through the options (sprite pack vs. a custom procedural shader vs.
improving the existing procedural art), chose to try Kenney's real pack
first, same plan discussed back at Phase 18 - only this time it actually
worked, since Boo downloaded it himself in his own browser (which can
reach kenney.nl fine) and handed the zip over directly, sidestepping the
egress-allowlist block that stopped this the first time (see Phase 18's
"Where the art came from" note - neither the cloud sandbox nor the
device-bridge shell can reach any external asset host, Kenney or
otherwise, confirmed again this session before asking Boo to grab it
himself).

**What changed:** `planet_launch.png` and `planet_target.png` in
`android/src/main/assets/textures/` were replaced with two sprites from
Kenney's **Planets (1.0)** pack (CC0, kenney.nl) - `planet02.png` (warm
orange/rust/maroon, matching the existing "warm rust/orange rocky world"
launch-planet identity) and `planet00.png` (teal/mint/blue, matching the
existing "cool teal/blue oceanic world" target-planet identity),
downscaled from their native 1280x1280 to the existing 256x256 spec and
saved under the same filenames - a pure file swap, **zero code changes**,
exactly as Phase 18 predicted ("same filenames, same folder... since
loading is a plain `Gdx.files.internal("textures/...")` lookup").
`star.png` is UNCHANGED - Boo only grabbed the "Planets" pack, not
Kenney's "Space Kit"/"Simple Space" (which do have sun/star sprites) - so
the procedural star from Phase 18 is still in place, now alongside real
planet art. A visible mismatch between the two styles (if any, once
seen on-device) is expected until/unless Boo grabs a matching star too.

The Kenney pack itself also ships a `Parts/` folder (separate sphere-
shading, noise-texture, and light-phase layers meant for compositing
custom planet variety, not just the 10 pre-rendered `planet00-09.png`
finished sprites two of which were used here) - worth keeping in mind
for later once more than 2 planets exist at once (per the still-open
multi-planet-placement design), since it could give real variety instead
of reusing/re-tinting the same 10 finished sprites.

### How to test Phase 26 on-device

1. Sync Gradle, run on-device as usual.
2. Menu → Play. The launch planet should now show a warm orange/maroon
   mottled sphere and the target planet a teal/mint/blue mottled sphere -
   both noticeably more detailed than the old procedurally-generated
   placeholder spheres, while keeping the same warm-vs-cool color
   identity so the two planets are still instantly distinguishable at a
   glance.
3. Confirm both sprites are still centered and sized correctly against
   their Box2D fixtures (same alignment check as Phase 18 - the sprites'
   soft outer glow can bleed slightly past the wireframe, that's
   expected and matches how `star.png`'s glow already works; the solid
   sphere body itself should still fill the fixture circle).
4. Confirm the damage-overlay crater/scorch effect (Phase 21) still
   fades in correctly over the new art as each planet takes damage - it
   draws in the same place using the same alpha-blend approach, so this
   should need no changes, but worth a visual check since the new sprite
   colors are quite different from the old ones.
5. Confirm the star (unchanged, still the Phase 18 procedural version)
   doesn't look jarringly out of place next to the new planet art - flag
   it if it does, since a matching Kenney star sprite is an easy
   follow-up if Boo wants to grab the Space Kit/Simple Space pack too.

### Phase 26 addendum: removed the debug wireframes on the star + both planets

Boo, right after seeing the new art on-device: "I want to remove the
green circles around the planets and sun." `Box2DDebugRenderer` colors
every body's wireframe by Box2D body type - static bodies (the star and
both planets, all three permanently motionless gravity sources) draw
green; that's exactly what Boo meant, not the avatar/AI target
(kinematic, blue) or missiles (dynamic, red/pink), which he didn't
mention and this leaves untouched. This is precisely the "future step"
Phase 18's own doc comment flagged at the time ("hiding the wireframes
for just these three bodies... needs a small deliberate change, not a
one-line toggle") - now that all three have had real sprite art since
Phase 18/26 with alignment already confirmed on-device, there's nothing
left for their wireframes to verify.

**What's built:** `Box2DDebugRenderer` has no built-in per-body filter,
but its `renderBody(Body)` method is `protected`, not `private` -
confirmed directly against libGDX's own source
(`extensions/gdx-box2d/gdx-box2d/src/.../Box2DDebugRenderer.java`) rather
than assumed, including which color each body type actually draws
(`SHAPE_STATIC` = green, matching the star/both planets exactly).
`debugRenderer` is now constructed as a small anonymous subclass whose
overridden `renderBody` returns early for exactly `starBody`/
`launchPlanetBody`/`targetPlanetBody` (three new direct `Body` fields,
same pattern as the existing `avatarBody`/`targetCharacterBody` fields -
`createStar()`/`createPlanet(...)`'s return values are now captured into
these instead of being used inline) and otherwise falls through to
`super.renderBody(body)` unchanged - every other body (avatar, AI
target, missiles, strays) still gets its usual debug wireframe exactly
as before.

#### How to test the Phase 26 addendum on-device

1. Sync Gradle, run on-device as usual.
2. Confirm the star and both planets no longer show a green wireframe
   circle - just the sprite art itself.
3. Confirm the avatar, AI target, and any in-flight missile still show
   their usual debug wireframe outline (blue for the characters,
   red/pink for missiles) - unaffected, since only the three green
   (static-body) wireframes were targeted.

## Phase 27: aim trajectory preview stays visible below horizon (red instead of hidden)

Picks up item #3 from Boo's post-Phase-24 feedback list. Boo: "the way
the aiming dots disappear when aiming below the horizon becomes a little
confusing in practice. I am thinging to keep the aim always visible but
maybe its red when aiming at the planet." The original hide-it behavior
(`renderAimTrajectoryPreview` returning early when
`SlingshotInputProcessor.currentAimBelowHorizon`) was itself a deliberate
fix from an earlier on-device round - the very first version clamped an
illegal release and fired it anyway, which read as inconsistent with a
hidden aim line ("if the aim disappears, then even if you make a
shooting gesture, it will not fire" - see `SlingshotInputProcessor`'s
"Horizon restriction" doc paragraph). That firing-side fix (an illegal
raw release now fires nothing at all, full stop) is UNCHANGED by this
phase - only the rendering-side "hide it" half is replaced with "show it
in red." The red dots now serve as the visual explanation for why a
release right now wouldn't fire, which is arguably a more complete
version of the same original intent rather than a reversal of it.

**What changed:** `renderAimTrajectoryPreview()` no longer returns early
on `currentAimBelowHorizon` - it still runs the exact same gravity-
curved trajectory simulation/obstacle-stop logic as always, just reading
that flag once into a local and using it to pick `Color.RED` instead of
the normal `Color.LIGHT_GRAY` for the dots. Since aiming below horizon
means pointing back toward your own launch planet, in practice this
mostly shows a short red stub of dots terminating almost immediately at
your own planet's surface (the same obstacle-stop logic that already
truncates the preview early against any celestial body) - a clear "this
would hit your own planet" visual rather than nothing at all.

**Deliberately left alone:** `renderDebugOverlay()`'s own straight pull-
line has the exact same currentAimBelowHorizon hide-check and was NOT
touched - that overlay is explicitly documented as "a Phase 8 testing
aid, not meant to be the final aiming UI," and Boo's feedback was
specifically about "the aiming dots" (the Phase 16 gravity-curved
preview), not the plain debug line. The two can now behave slightly
differently below horizon (dots show in red, the debug line still
vanishes) - low-impact since the debug line isn't the real aiming UI,
but flagged here in case Boo wants that made consistent too later.

### How to test Phase 27 on-device

1. Sync Gradle, run on-device as usual.
2. During your own turn, pull back to aim in a normal, legal direction -
   confirm the dotted trajectory preview still shows in its usual light
   gray, unchanged.
3. Pull back so the aim points below your own local horizon (back toward
   your own planet) - confirm the dots now show in red and stay visible,
   instead of disappearing.
4. Release while the dots are red - confirm nothing fires (unchanged
   behavior; the red dots are the reason why, not a promise it will
   fire).
5. While aiming below horizon (red dots showing), move the aim back
   above horizon without releasing - confirm the dots switch back to
   light gray smoothly, still following the live drag.
6. General feel check: does red communicate "this won't fire" clearly at
   a glance? Worth flagging if a different color or a distinct visual
   treatment (e.g. dashed vs. dotted) would read better in practice.

### Phase 27 addendum: red preview draws through the planet, capped to a fixed 24 dots

First on-device test of Phase 27 found the red preview effectively
invisible in practice - Boo: "I want the red patch to extend like the
other aim line. even if its throgh the planet. I cannot see it as it
is." The cause: aiming below horizon points straight back at the
shooter's own planet, and the existing obstacle-stop logic (the same
logic that correctly truncates the normal, legal preview right where it
would hit something) was cutting the red preview off after just a step
or two of simulation, since it's pointed directly at an obstacle from
the very first instant.

**First fix (superseded immediately below):** skip the obstacle-stop
check entirely while `belowHorizon`, so the red preview draws its full
`effectiveMaxSeconds` length regardless of what's in its path. Boo,
right after seeing that on-device: "lets not have it be full length.
lets jst have it extend beyong the planet but 24 dots. just enough so
the player nows its an invalid shot."

**Second fix (also insufficient on its own):** additionally capped at a
fixed `AIM_PREVIEW_INVALID_DOT_COUNT` (24) dots regardless of
`effectiveMaxSeconds`. On-device screenshot showed this still wasn't
enough by itself: with obstacle-stopping skipped, the star's own
gravity could fling the simulated point far across the map - looping
down near the star and back - well before 24 dots' worth of simulated
steps ran out, since a dot count alone doesn't bound how far apart those
steps can land in world-space.

**Final behavior:** a second, distance-based cap -
`AIM_PREVIEW_INVALID_MAX_DISTANCE` (3f world units, Boo: "3 world units
is good" - roughly twice the planet's diameter) - stops the simulation
the instant the point is that far from `launchPoint`, checked every step
BEFORE the dot-count check so whichever limit is hit first wins. Between
the two caps, the red preview is now reliably a short stub that clears
the shooter's own planet and stops, regardless of how the gravity
simulation happens to curve it - never a long wandering loop toward
something else in the scene. The legal (gray) preview is untouched by
any of this - still obstacle-stopped, still runs the full
`effectiveMaxSeconds` when nothing blocks it.

#### How to test the Phase 27 addendum on-device

1. Sync Gradle, run on-device as usual.
2. Aim below your own horizon - confirm the red dots now form a short,
   contained stub that clears your own planet and stops (roughly 3
   world-units' worth, or 24 dots, whichever comes first) - NOT a long
   loop wandering toward the star or elsewhere in the scene.
3. Try this near different parts of the scene/at different pull
   strengths - confirm the red stub stays similarly short and contained
   every time, not just in the one layout already tested.
4. Aim in a normal, legal direction - confirm the gray preview's length/
   obstacle-stop behavior is unchanged from before this addendum.

## Phase 28: post-shot movement removed entirely - firing ends the turn

The last of the four items from Boo's original punch list (camera
abruptness, planet graphics, aim-line visibility, and this one). Boo,
explicit: "remove post-shot movement entirely - only pre-shot movement
(5 paces), then firing ends the turn." Since Phase 9, every turn had
been split into two movement budgets - move up to `stepsPerPhase` steps,
fire, then move again (take cover) before the turn actually passed,
either automatically once that second budget hit zero or early via a
dedicated Pass button. That second half is gone: a turn is now move
(up to `stepsPerPhase` steps) then fire, full stop.

**Symmetric for the AI too.** `AiTurnController.fire()` used to make its
own second `reposition()` call after firing, mirroring the player's
post-shot phase - asked and confirmed with Boo ("Yes, remove it too")
before touching it, so both sides now play by the exact same
move-then-shoot-then-done rule.

**What changed:**

- `AvatarMovementController`: removed the `Phase` enum (`PRE_SHOT`/
  `POST_SHOT`), the `phase` field, `passButtonRect`, and the
  `touchDown`/`move()` logic that triggered a Pass. There's now a single
  `stepsRemaining` budget. `onFired()` - still called by `PlayScreen`
  the instant a missile actually launches - now does what the old
  `passTurn()` used to: resets the budget, advances `turnNumber`, and
  fires `onTurnPassed()` immediately, ending the turn right there.
- `AiTurnController.fire()`: removed the second `reposition(targetPosition)`
  call after `onFire(...)` - the AI's turn now ends immediately after it
  fires, same as the player's.
- `PlayScreen`: `renderMovementControls()` no longer draws a Pass button
  (nothing left for it to do early). `renderStatsPanel()`'s turn label
  lost the "Pre-shot"/"Post-shot" distinction - just `"Turn N: X left"`
  now. The `SlingshotInputProcessor.onFire` wiring and
  `renderAimTrajectoryPreview()` no longer gate on `canFire` (that
  property's whole reason to exist - blocking a shot during post-shot
  repositioning - no longer applies), since `AvatarMovementController`
  switches away the input processor synchronously the instant a shot
  fires, before another touch event could ever reach the slingshot.
- The existing shot-resolution/turn-handoff machinery from Phase 25
  (`shotResolved`, `pendingTurnHandoff`, stray shots, eased camera snap)
  is untouched - `onFired()`/`fire()` ending the turn immediately still
  goes through the same deferred-handoff path if the just-fired shot
  hasn't resolved yet.

#### How to test Phase 28 on-device

1. Sync Gradle, run on-device as usual.
2. Take your pre-shot movement steps (up to 5), then fire - confirm the
   turn ends right away (no post-shot movement window, no Pass button
   ever appears) and control hands to the AI once the shot resolves,
   same as before.
3. Confirm the turn/steps HUD readout now just reads "Turn N: X left"
   with no Pre-shot/Post-shot label.
4. Watch a few AI turns - confirm the AI also stops moving the instant
   it fires, with no extra repositioning hop afterward.
5. Play several turns each way - confirm nothing regressed from Phase
   25's stray-shot/camera-ease behavior (shots still resolve, camera
   still eases to whoever's turn is next).

## Ghost-obstacle bug fixed (destroyed planets no longer block aim/AI search forever)

A long-known, small gap - first flagged all the way back at Phase 14's
original obstruction check ("it also doesn't yet account for the target
planet being partially destroyed... a known minor gap") and never fixed
since, because a whole planet getting destroyed mid-game was rare enough
not to matter. Boo asked about it directly while reviewing the orbital-
drift plan below, since drift would put a character floating right where
this bug lives.

**The bug.** `celestialObstacles` (`PlayScreen`) is a fixed list of
`Obstacle(center, radius)` values built once at game start from wherever
the star/planets landed - plain numbers, no live link back to the actual
Ashley entity or Box2D body. Two things read it: the AI's own aim search
(`AiTurnController.searchAim`/`reposition`, via `bestAimFor`/
`simulateClosestApproach`) and the player's own gray trajectory preview
(`renderAimTrajectoryPreview`) - both treat every entry as solid, un-
passable ground. When a planet is actually destroyed, its real body/
entity gets torn down (`ProjectileContactListener.flushRemovals`), but
nothing ever touched the fixed list to match - so a destroyed planet kept
blocking shots and aim previews forever, at a spot where a real fired
missile would now fly straight through with nothing there to stop it.

**Fix.** New `PlayScreen.currentCelestialObstacles()` rebuilds the list
live every time it's needed, dropping a planet's entry the instant
`GravitySourceComponent.isDestroyed` is true for it (the star is never
filtered - it's `isDamageable = false`, can't be destroyed). Both
consumers now call this instead of reading the fixed list directly:
`AiTurnController`'s constructor took an `obstacleSource: () -> List<Obstacle>`
supplier instead of a fixed `List<Obstacle>` (same pattern already used
for `gravitySources`/`gravityMultiplier` - fetched once per `searchAim`/
`reposition` call, threaded through `bestAimFor`/`simulateClosestApproach`
as a parameter, not re-fetched per candidate or per simulated step), and
`renderAimTrajectoryPreview` calls the new function directly.

#### How to test this fix on-device

1. Sync Gradle, run on-device as usual.
2. Destroy a planet (repeatedly hit it until `GravitySourceComponent.isDestroyed`)
   and confirm your own gray aim preview no longer stops/hides as though
   something solid is still there.
3. Confirm the AI's shots/repositioning also stop avoiding that now-empty
   space - watch a few of its turns after the destruction and check its
   aim search doesn't look artificially constrained near the old planet's
   position.
4. Fire an actual shot straight through where the destroyed planet used
   to be - confirm it flies through cleanly, matching what the preview
   now shows.

## Stable-orbit shot could freeze the game forever - fixed

Boo, on-device: "I made a shot and it perfectly went into an orbit not
hitting anything. the game dosnt recognize this and things the turn is
still going on. the shot, if it goes into a stable orbit inside the
field of view, should time out at some point." This reverses an
explicit call from Phase 25 ("i dont care about something getting in a
stable orbit" - no failsafe timer, at the time) now that the actual
stuck-forever case showed up in real play.

**The bug.** `shotResolved` (see that field's doc comment) only ever
had two ways to become true again: the shot hits something, or it's
been continuously outside the play field for `FIELD_EXIT_TURN_END_SECONDS`
(3s). A shot that curves into a genuinely stable orbit - never touching
anything, never crossing the field boundary - satisfies neither
condition, so `shotResolved` just stays false forever and the turn
never hands off. Nothing was broken about the physics; the turn-
resolution logic simply had no path out of this specific case.

**Fix.** New `MAX_SHOT_FLIGHT_SECONDS` (15f, generous on purpose - see
its own doc comment on why: `GravitySystem` describes orbital periods
as "multi-second," so this needs real headroom before it risks cutting
off a shot that's still legitimately working toward a hit or a field
exit) is a hard ceiling on how long ANY shot gets to stay unresolved,
tracked by a new `activeShotElapsedSeconds` that counts up regardless
of whether the shot is inside or outside the field. The per-frame check
in `render()` now resolves the turn the instant EITHER timer trips -
field-exit or max-flight-time, whichever comes first - and treats both
the same way: the shot becomes a stray (`addStray`, same per-side cap
and "it can still cause damage later" behavior Phase 25 already built)
rather than being destroyed outright, so a timed-out orbiter doesn't
just vanish - it keeps existing and can still hit something down the
line, same as any other stray.

#### How to test this fix on-device

1. Sync Gradle, run on-device as usual.
2. Try to reproduce a stable orbit (a light tap near the star at a
   grazing angle is usually what does it) - confirm that after about 15
   seconds, even with the shot never hitting anything and never leaving
   the field, the turn hands off on its own instead of staying frozen.
3. Confirm the now-timed-out shot doesn't just disappear - it should
   keep existing/orbiting and still be able to deal damage later, same
   as a shot that went stray by leaving the field.
4. General play: confirm normal shots that resolve quickly (hit
   something, or exit the field within a couple seconds) are completely
   unaffected - this only ever kicks in after 15 real seconds of an
   unresolved shot.

## Destroyed planet's sprite kept showing forever - fixed

Boo, on-device, right after confirming the ghost-obstacle fix worked
("projectiles now go through that space unaffected"): "however, the
planet image is still there." Exact same root cause as the ghost-
obstacle bug, just on the visual side instead of the aim/AI-search
side: `renderCelestialSprites()` drew each planet's texture
unconditionally every frame from its fixed `launchPlanetPosition`/
`targetPlanetPosition`, with no check against whether that planet had
actually been destroyed. `drawDamageOverlayIfDamaged` already faded a
damage overlay toward full opacity as mass approached zero, but that
overlay draws ON TOP of the still-fully-drawn base sprite underneath -
at 100% damage the base planet was still there, just with a maxed-out
overlay over it, not actually hidden.

**Fix.** Both planet draws (and their matching `drawDamageOverlayIfDamaged`
call - nothing left to overlay once the base sprite is gone) are now
gated on `!GravitySourceComponent.isDestroyed` for that planet. A
destroyed planet's sprite and damage overlay simply stop drawing the
instant it's destroyed, matching what already happens physically (real
body/entity gone) and matching the aim-preview/AI-search fix from
earlier this session.

#### How to test this fix on-device

1. Sync Gradle, run on-device as usual.
2. Destroy either planet and confirm its sprite (and any damage overlay)
   disappears completely the instant it's destroyed - not just faded/
   scorched-looking, genuinely gone.
3. Confirm the star and the surviving planet are unaffected - still
   drawing normally.
4. Confirm the HUD's "DESTROYED" label (`renderStatsPanel`) and this
   sprite disappearing agree with each other - they should always change
   at the same moment, since both read the same `isDestroyed` flag.

**Was not built yet as of this fix - now built, see Phase 29 immediately
below.** At the time this fix landed, the character who was standing on
a now-destroyed planet (the AI's target, in Boo's on-device report)
still didn't start drifting - it kept walking the same fixed
angle-around-a-now-empty-point model it always had, since orbital drift
had only been designed and confirmed (two open questions resolved:
freeze during its own turn, re-anchor on landing) earlier in this same
session, not yet implemented - these three fixes (ghost obstacle, stuck
orbit, destroyed-planet sprite) all came up as side issues in between
agreeing on that design and actually starting to build it.

## Phase 29: orbital drift built

Boo confirmed "yes" to building this right after the three side-bug
fixes above landed - see the captured design note earlier in this file
for the original discussion and the two confirmed open questions
(freeze during its own turn; re-anchor as a walking avatar on landing).
This phase is that design, actually implemented.

**What's built**, matching the confirmed design exactly:
- **Trigger.** Checked once per frame in `PlayScreen.render()`, right
  after `flushRemovals`/the stray-pruning step and *before* the
  shot-resolution block (this ordering matters - see the in-code
  comment at that call site: a missile that destroys a planet and
  resolves the active shot in the very same frame is the common case,
  not a rare one, and the turn-handoff closures that block can
  synchronously fire need to already know a side is drifting so they
  freeze it correctly). The instant a side's home planet
  (`launchPlanetEntity`/`targetPlanetEntity`) is found destroyed while
  that side's character still has HP, `beginPlayerDrift()`/
  `beginAiDrift()` fires exactly once (latched by a new
  `playerDriftResolved`/`aiDriftResolved` flag - see the known
  limitation below).
- **Becoming a free body.** The character's existing Box2D body
  (`avatarBody`/`targetCharacterBody` - previously always Kinematic)
  flips to Dynamic, gets a real fixture density (1f, set once here -
  it never needed one before since Kinematic bodies ignore mass) and a
  `resetMassData()` call, gets tagged `GravityAffectedComponent` (Ashley's
  family queries pick this up automatically - `GravitySystem` starts
  pulling on it next frame with zero other plumbing needed), and gets an
  initial tangential "flung into orbit" kick: `driftKickVelocity()`
  computes a true circular-orbit speed around the star at the drift's
  starting radius (`v = sqrt(G_effective * STAR_MASS / r)`), scaled by
  a new tunable `ORBITAL_DRIFT_SPEED_FRACTION` (1f - illustrative, not
  tuned), in the counterclockwise tangent direction (arbitrary but
  fixed, same for both sides).
- **Position takes over from the old model.** `AvatarMovementController`/
  `AiTurnController` both gained a `driftPositionOverride` field and a
  `beginDrift(positionSupplier)` method - once set, `position` reads
  straight from the live Box2D body instead of the old
  angle-around-`planetCenter` formula. `PlayScreen.render()`'s per-frame
  `avatarBody.setTransform(...)`/`targetCharacterBody.setTransform(...)`
  calls are skipped for whichever side is drifting (that call would
  otherwise fight the physics-driven position every frame).
- **No movement budget.** "Lose the walk-around movement budget entirely"
  - `AvatarMovementController.touchDown()` stops responding to the move
  buttons the instant it's drifting, and `PlayScreen.renderMovementControls()`
  hides them outright rather than leaving dead buttons on screen.
  `AiTurnController.startTurn()` skips its own `reposition()` call the
  same way.
- **Freeze during its own turn, thaw on firing.** The moment a drifting
  side's turn actually starts (`giveControlToPlayer`/`startAiTurn` in
  `PlayScreen`'s `init{}`), `freezePlayerDrift()`/`freezeAiDrift()` saves
  the body's current velocity, zeroes it, and flips the body back to
  Kinematic - confirmed against Box2D's own source that `Body::SetType`
  does *not* reset velocity on a Kinematic<->Dynamic switch by itself,
  and a Kinematic body still integrates its own `linearVelocity` every
  physics tick even with no forces applied, so this zeroing is what
  actually holds it still (not just the type flip). The character can
  aim/fire from that stable spot exactly like a normal turn.
  `thawPlayerDrift()`/`thawAiDrift()` (called from the `onFire` callbacks,
  the instant a shot actually launches) flips back to Dynamic and
  restores the saved velocity, so the same drift resumes uninterrupted.
- **No horizon restriction while drifting.** There's no home planet left
  to protect. `SlingshotInputProcessor` gained a
  `horizonRestrictionEnabled` flag (off while the player drifts, back on
  after re-anchoring); `AiTurnController.clampAboveHorizon` is a no-op
  whenever `driftPositionOverride` is set. `AiTurnController.searchAim`
  also re-centers its angle sweep on the straight line to the target
  while drifting instead of the now-meaningless "outward from my own
  planet" direction the grounded case uses.
- **Hitting the star / landing.** Checked every frame while a side is
  actually drifting (`checkPlayerDriftLanding()`/`checkAiDriftLanding()`,
  right after the trigger check above) via a plain distance-between-
  centers test - **not** a second Box2D `ContactListener`:
  `World.setContactListener` only ever accepts one at a time, already
  claimed by `ProjectileContactListener` for missile impacts, and a
  drifting character touching a celestial body doesn't share that
  event's semantics anyway, so polling positions (the same style the
  shot-resolution/field-exit checks already use) was simpler and lower-
  risk than a second listener/dispatcher. Flying into the star deals
  enough damage to guarantee defeat (the existing win/loss check catches
  it the same frame). Landing on a surviving planet/moon deals exactly
  `ORBITAL_DRIFT_LANDING_DAMAGE` (1) and re-anchors the character via
  `AvatarMovementController.reanchor()`/`AiTurnController.reanchor()` -
  wherever it actually touched down (via `atan2`, not a re-roll or fixed
  angle) becomes its new walking position on whichever planet it hit -
  necessarily the opponent's planet in this game's current 2-planet
  layout.

**Known limitation, documented not accidental:** the trigger only ever
fires once per side, latched by `playerDriftResolved`/`aiDriftResolved`.
If the planet a side re-anchors onto is *later* also destroyed (with
that character still alive on it), they will **not** drift a second
time - they'll just keep standing on the now-destroyed planet the same
way any character stood on solid ground before this phase existed. This
matches the confirmed design conversation, which only ever discussed a
single hand-off (the current 2-planet layout guarantees at most one
"still has a home, then doesn't" transition per side), and generalizing
it to "whichever planet you're currently on gets destroyed, drift again"
would need real design/scoping of its own - flagging it here rather than
quietly leaving it unhandled.

Other tunables introduced this phase, both illustrative/not tuned yet:
`ORBITAL_DRIFT_SPEED_FRACTION` (now 0.2f - see the fix below) and
`ORBITAL_DRIFT_LANDING_DAMAGE` (1), both in `PlayScreen`'s companion object.

### Kick speed and direction fixed after first on-device test

Boo's on-device feedback right after the phase above first landed: "the
angular velocity of the ai when planet is gone is not realistic...the ai
speeds off in an orbit of the sun...it should be at a much slower speed"
- and, separately, the direction itself was wrong: it always used the
same fixed counterclockwise-tangent-around-the-star direction regardless
of anything about the actual destruction, which isn't how a shockwave
from a specific impact point would actually fling someone standing
elsewhere on the surface.

**Speed.** `ORBITAL_DRIFT_SPEED_FRACTION` dropped from `1f` (a full
circular-orbit speed) to `0.2f` (a fifth of that) - still an illustrative
starting guess, easy to tune further.

**Direction.** Boo gave three worked clock-position examples to pin down
the intended physics (character always at 12:00 on the planet): a shot
hitting at 3:00 flings the character left; a shot at 11:00 flings it
right (toward 3:00); a shot at 6:00 - directly opposite the character -
flings it straight up. The pattern: a shockwave travels from the impact
point around the surface to the character, continuing tangentially in
whichever rotational direction (clockwise/counterclockwise) got there
the *shorter* way; when the impact is exactly opposite (both ways
equally short), there's no rotational sense to continue, so it's flung
straight out radially instead. All three of Boo's examples check out
exactly against this rule.

**Implementation** needed a new piece of state that didn't exist before:
*where* a planet's finishing blow actually landed.
- `GravitySourceComponent` (`Components.kt`) gained `lastImpactPosition`
  (null until first hit, overwritten on every hit - so once a source is
  `isDestroyed`, this is exactly the finishing blow's impact point).
  `applyDamage` gained an optional `impactPosition` parameter (defaults
  to `null`, so every other, unrelated caller is unaffected) that records
  it.
- `ProjectileContactListener.handleProjectileHit` now passes the
  missile's own Box2D body position (still valid at that point in
  `beginContact` - the body isn't actually destroyed until `flushRemovals`
  runs later that frame) through to `applyDamageIfApplicable`, which
  forwards it into `GravitySourceComponent.applyDamage` for a celestial
  hit.
- `PlayScreen.driftKickVelocity` now takes the character's position, the
  planet's center, and that recorded impact position (nullable - falls
  back to pure radial-outward in the unlikely case none was ever
  recorded), computes the angular difference between the character's
  angle and the impact's angle around the planet's center, and picks the
  counterclockwise tangent, the clockwise tangent, or (within a small
  band around exactly +-180 degrees, using `sin` of that angular
  difference) pure radial-outward, exactly matching the rule above.

#### How to test this fix on-device

1. Sync Gradle, run on-device as usual.
2. Repeat Phase 29's own on-device test (steps 2 and 7 below/above) and
   confirm the drift speed now looks like a slow knocked-loose drift, not
   a fast orbital launch.
3. If possible, try to line up (or just observe over a few different
   games) a destroying hit landing at roughly the "3 o'clock" and
   "9 o'clock" position relative to wherever the character is currently
   standing on that planet, and confirm the character launches off to
   the correct side (away from the shorter arc to the impact) rather
   than always the same direction regardless of where the hit landed.
   Exact clock-position aiming isn't practical to force on-device, so
   this is more "does it look directionally sensible" than a precise
   check - flag it if it ever looks backwards or random.

#### How to test this phase on-device

1. Sync Gradle, run on-device as usual.
2. Focus fire on the AI's planet (not the AI character itself) until it's
   destroyed while the AI still has HP. The instant it's destroyed, the
   AI's character should visibly separate from where the dead planet was
   and start drifting under gravity (curving, not standing still) -
   watch it over a few seconds even outside anyone's turn.
3. When it becomes the AI's turn: camera should snap to it as usual, and
   it should hold perfectly still (frozen) the whole time it's
   "thinking" and firing - not continue drifting mid-turn. Immediately
   after it fires, it should resume visibly drifting again.
4. Confirm the AI's shot during this turn is never blocked/red for
   "firing into your own planet" - there's no planet left to protect.
5. Confirm the move buttons are gone from the corner while the AI would
   normally show them for movement (this only visually applies to the
   player's own turn, but worth double-checking the player-side drift
   scenario in step 7 below shows the buttons vanish).
6. Let it drift into the star (may take a few tries/turns depending on
   the kick direction/speed) - should be an instant Game Over (AI
   defeated).
7. Reproduce the same sequence for the player's own planet (focus the
   AI's fire on your own planet until it's destroyed while you still
   have HP) - confirm the same drifting/freeze-thaw/no-horizon-
   restriction behavior, plus that landing on the AI's surviving planet
   deals a small hit and puts you back into normal walk-around-a-point
   control (move buttons reappear, horizon restriction is back) exactly
   where you touched down.
8. General regression check: a normal game where neither planet is
   destroyed should play exactly as before - this phase should be
   completely invisible until a planet actually dies with its character
   still alive.

### Phase 29 follow-up: orbital-drift game-balance fix (Sept 2026 session), plus two still-open bugs

Found while testing Step D1 (unrelated to it - this is pre-existing
orbital-drift behavior, just noticed during that testing pass). Boo:
destroying the enemy's planet was turning into a near-guaranteed kill,
because a drifting character's kick speed (`ORBITAL_DRIFT_SPEED_FRACTION`,
originally a fixed `0.2`) was only a fifth of true circular-orbit speed -
real orbital mechanics, not a bug in the code: a tangential kick that
slow puts the star well inside the resulting decaying ellipse's close
approach, so the character dives into the star almost every time. Boo,
exact words: "that makes the winning strategy to always blow up the
planet and not aim at characters" - this made planet destruction strictly
better than aiming at characters directly, undermining the core combat
loop.

**Fix - ✅ DONE, built and confirmed on-device:**
- **`OrbitalDriftTuning`** (new file) - the drift-physics equivalent of
  `ShotSpeedTuning`: a live-tunable `speedFraction` value, read fresh at
  the moment of the kick (`driftKickVelocity`) instead of a fixed
  constant.
- **`OrbitalDriftDebugController`** (new file) - a fourth live-tuning
  debug row (-/+ buttons, "Drift Speed xN.N" readout), stacked below the
  Wins row, same standing-permanent-debug-tool precedent as
  `GravityDebugController`/`ShotSpeedDebugController`/
  `WinCountDebugController`. Range 0.1-2.0 (deliberately goes past 1.0 -
  true circular-orbit speed - so an escape-trajectory kick could be felt
  out too, not just the stable-orbit target value).
- **Confirmed on-device: `1.0` (true circular-orbit speed) feels right.**
  Boo tuned it live with the slider and landed on 1.0 - the working
  theory going in - now baked in as `OrbitalDriftTuning
  .DEFAULT_SPEED_FRACTION`'s actual default (still a live-tunable `var`,
  same as the Gravity multiplier's own tune-then-bake-in history). The
  slider stays wired in, not removed now that a good default was found.

**Two bugs found during this same testing pass - NOT fixed yet, still
open:**
1. **A drifting character can visually overlap another planet's sprite**
   instead of cleanly landing on it - Boo's screenshot showed the AI's
   drifting character sitting inside the edge of the player's (still-
   intact) planet. `resolveDriftLanding` already checks landing distance
   against BOTH planets (not just the drifting character's own former
   one), so distance-based landing detection isn't obviously wrong on its
   own - the likely culprit is bug 2 below (a character getting frozen by
   `freezeDrift` mid-flight, wherever it happens to be, rather than only
   once it's actually landed) rather than a separate landing-detection
   gap, but this hasn't been confirmed by tracing an actual repro yet.
2. **Confirmed root cause: a drifting character can sit completely still
   until it fires**, instead of visibly drifting right away. A planet
   being destroyed and the shot that destroyed it resolving are usually
   the same frame - and that frame's turn-handoff (very often passing
   control right back to the character whose planet just died) calls
   `activatePlayerCharacter`/`activateAiCharacter`, which freezes ANY
   character with `drifting == true` unconditionally (see `freezeDrift`'s
   doc comment: "freeze the instant it becomes a drifting character's
   turn to act"). Since the drift-start code and the turn-handoff can
   both run in that same frame, the kick gets applied and then
   immediately zeroed/frozen before a single frame ever renders it moving
   - the character just sits there (visually motionless, but technically
   "drifting" per its own `drifting` flag) until `thawDrift` resumes it
   the instant they fire. Not yet fixed - needs a real decision on what
   SHOULD happen here (should the very first activation after a fresh
   kick let the kick actually play out visually for a moment before
   freezing? does bug 1's overlap happen precisely because a character
   freezes mid-flight, overlapping whatever planet it happened to be
   passing over at that exact frame?) - flagged for a future session,
   not carried further this session per Boo's own pacing.

## Phase 30: multi-character combat, Step 1 (fixed 2-per-side squads, shared planet, whole-squad-then-whole-squad)

Boo, after Phase 29 landed: "anything open?" -> chose "multi character"
next over the other open backlog items. Before building, four design
questions genuinely needed Boo's own call (not implementation-time
judgment calls) - asked directly and confirmed:
- **Scope:** fixed squad size first (2 per side), not a player-facing
  squad-builder yet.
- **Placement:** characters can share a planet (not "clustered nearby but
  separate ground" or forced onto different planets).
- **AI targeting with multiple player characters alive:** left to
  implementation judgment ("not important yet - pick something
  reasonable") - see the placeholder heuristic below, flagged for a real
  pass in Step 3.
- **Win condition:** a side loses only once *all* its characters are
  defeated (last-man-standing), not when the first one falls.

Given the size of the change (nearly every method in `PlayScreen.kt`
assumed exactly one character per side), this was broken into three
steps, confirmed with Boo before starting: **Step 1** (this phase) -
replace the singular player/AI characters with fixed 2-per-side squads,
sharing each side's existing one planet, whole-squad-then-whole-squad
turn order in a fixed index sequence (no player-facing order picker
yet). **Step 2** (built, see Phase 31 below) - a tap-to-choose UI for
the player's own turn order within their squad. **Step 3** (built, see
Phase 32 below) - a real AI targeting heuristic (lowest-current-HP) plus
an obstacle-avoidance pass for AI shots sharing a planet with a
teammate/enemy.

**What's built.** `AvatarMovementController`/`AiTurnController` needed
*zero* changes - both already took `planetCenter`/`startAngleDegrees` as
independent constructor parameters and already exposed everything
(`position`, `beginDrift`, `reanchor`, `onFired`/`startTurn`,
`isTurnActive`, `stepsRemaining`) generically enough to just construct
two of each. All of Step 1's work is in `PlayScreen.kt`:

- **Per-character state.** New `PlayerCharacterState`/`AiCharacterState`
  classes (nested in `PlayScreen`) each hold one character's `controller`,
  Box2D `body`, ECS `entity`, and its own `drifting`/`driftResolved`/
  `driftFrozenVelocity` (previously per-*side* fields, now per-character -
  see the "orbital drift generalizes per-character" note below).
  `playerCharacters`/`aiCharacters: List<...>` (fixed size
  `CHARACTERS_PER_SIDE = 2`) replace the old singular
  `avatarMovementController`/`avatarBody`/`avatarEntity` and
  `aiTurnController`/`targetCharacterBody`/`targetCharacterEntity` fields
  entirely.
- **Shared planet, offset start angles.** Both characters on a side are
  built with `Vector2(launchPlanetPosition)`/`Vector2(targetPlanetPosition)`
  as their `planetCenter` (two independent `Vector2` copies of the same
  value - each can `reanchor()` independently later without affecting the
  other), at `AVATAR_START_ANGLE_DEGREES`/`AI_START_ANGLE_DEGREES` ±
  `CHARACTER_START_ANGLE_SPREAD_DEGREES` (30°, bumped from an initial 20° -
  see "On-device follow-up" below) so they don't spawn overlapping.
- **Distinct collision categories per character.** New
  `CATEGORY_PLAYER_AVATAR_2`/`CATEGORY_AI_TARGET_2` bits (alongside the
  existing `CATEGORY_PLAYER_AVATAR`/`CATEGORY_AI_TARGET`) so each
  character's own missile still only excludes colliding with *its own*
  firer at spawn - a missile can still hit a teammate. Friendly fire on a
  teammate was never explicitly ruled out, and the original single-
  category design was only ever about the spawn-overlap glitch, so this
  preserves that behavior rather than accidentally introducing a
  friendly-fire-immunity side effect.
- **Turn order: whole-squad-then-whole-squad, fixed index sequence.**
  New `advanceAfterPlayerFired`/`advanceAfterAiFired` (called from each
  character's own `onTurnPassed`/`onTurnComplete`) check whether a later,
  still-living character on the *same* side hasn't acted yet this round;
  if so, control passes to it (`activatePlayerCharacter`/
  `activateAiCharacter` - same freeze-if-drifting/camera-snap/input-
  routing work the old single-character `giveControlToPlayer`/
  `startAiTurn` did, just parameterized per character now). Otherwise the
  whole side's turn is over and it hands off to the other side's first
  living character (`handOffToAi`/`handOffToPlayer` - the latter also
  increments the new `roundNumber`, which replaces
  `AvatarMovementController.turnNumber` as the HUD's turn counter now
  that there are two independent per-character counters instead of one).
  Input is restricted the instant *any* character fires (regardless of
  who's next) to preserve the existing "only one shot in flight at a
  time" invariant, and re-enabled the instant any player character
  becomes active - `rebuildFullInputProcessor()` swaps which
  `AvatarMovementController` instance is actually wired into
  `fullInputProcessor` (only the active one should receive movement-
  button taps; `SlingshotInputProcessor` stays a single shared instance
  since `launchPoint` already gets `.set()` from whichever character is
  active every frame, and both characters share one `planetCenter`
  anyway).
- **AI targeting placeholder (Step 1 only).** `activateAiCharacter`
  always aims at the first living player character, by index - a
  deliberate placeholder per Boo's "pick something reasonable," not the
  real heuristic. Replaced by the real lowest-HP heuristic in Phase 32
  (Step 3).
- **Orbital drift generalizes per-character, not per-side.** Boo,
  explicit: "characters can share a planet" - so when a shared planet is
  destroyed, *every* living character standing on it starts drifting in
  the same frame (a loop over each side's list in `render()`'s trigger
  check), not just one. `beginPlayerDrift`/`beginAiDrift`,
  `freezeDrift`/`thawDrift` (now single functions shared by both sides,
  taking whichever body applies), and `checkPlayerDriftLanding`/
  `checkAiDriftLanding` (built on a shared `resolveDriftLanding` helper)
  are all now parameterized by character instead of assuming one. Each
  character's own `driftResolved` flag means the "no second drift" known
  limitation from Phase 29 is now scoped per-character rather than
  per-side - unchanged in spirit, just per-character now.
- **Win/loss check.** A side only loses once `playerCharacters.all { ...
  isDefeated }` (or `aiCharacters.all { ... }`) - not the first character
  to fall. This is the one genuinely new correctness wrinkle multi-
  character combat introduces: since a single fallen character no longer
  ends the run immediately, a defeated-but-not-yet-destroyed-this-frame
  character's body can still be present mid-`render()` while its
  surviving teammate keeps fighting - every per-character loop (position
  sync, drift trigger/landing, sprite rendering) checks
  `HealthComponent.isDefeated` *before* touching that character's Box2D
  body, since `world.destroyBody` frees native memory and a defeated
  character's body is destroyed by `ProjectileContactListener.flushRemovals`
  the same frame it's confirmed dead. `HealthComponent` itself stays
  safely readable after removal regardless (same pattern already relied
  on for planet mass).
- **HUD.** `renderStatsPanel` now shows one HP row per character (via a
  small local `StatRow` list) instead of one per side - "Player 1 HP",
  "Player 2 HP", "Target 1 HP", "Target 2 HP", plus both planets' mass
  rows and a `Round N - P{1|2}: M left` / `AI's turn...` / `Shot in
  flight...` turn label.

**Not built this step (deliberately deferred, all now built later):**
- A player-facing turn-order-picker UI - order was a fixed index sequence
  (character 0, then 1) in this step. Built in Phase 31 (Step 2).
- A real AI-targeting heuristic - see the placeholder above. Built in
  Phase 32 (Step 3).
- Polish specific to two characters sharing one planet - an AI's aim
  search treating a teammate as an obstacle. Built in Phase 32 (Step 3);
  see that phase's "Not built" notes for what's still only a soft,
  scored preference rather than a hard guarantee. (The one crowding issue
  that did turn up on first on-device look - the AI pair's starting
  spread being too tight - got a stopgap fix already; see "On-device
  follow-up" below.)
- Characters were not added to `currentCelestialObstacles()` (that
  function stays star/planets-only) - instead Phase 32 added a separate
  `characterObstaclesExcluding()` used only by each AI character's own
  `obstacleSource`, so an AI's aim search now avoids routing through a
  teammate/enemy character too.

#### How to test this phase on-device

1. Sync Gradle, run on-device as usual. **This changes almost every
   system in `PlayScreen.kt`, so build first and report any compile
   errors before testing behavior** - this was hand-edited without a
   Kotlin/Gradle toolchain available to verify it compiles.
2. Confirm each side now shows two characters standing near each other
   on their one shared planet, at slightly different angles, not
   overlapping.
3. Take your first character's turn (move + fire) and confirm control
   passes to your *second* character immediately after your shot
   resolves - not to the AI. Camera should snap to character 2, its own
   movement budget should be fresh.
4. Fire your second character's shot and confirm control now passes to
   the AI (its first character acts, then its second, then back to your
   first character - watch `roundNumber` increment in the HUD once a
   full round completes).
5. Defeat one of your characters (or one of the AI's) while its teammate
   is still alive - confirm the game does NOT end, the defeated
   character's sprite disappears, and its teammate keeps taking turns
   normally. Confirm the HUD shows that character's row as "DEFEATED".
6. Only once *both* of a side's characters are defeated should Game
   Over trigger.
7. Destroy a side's shared planet while both its characters are still
   alive - confirm *both* characters visibly start drifting in the same
   frame, not just one.
8. General regression check: everything from Phase 29's own on-device
   test script (freeze/thaw, no-horizon-restriction while drifting,
   landing/re-anchoring, hitting the star) should still hold for
   whichever character is currently drifting.

#### On-device follow-up (same session, first real look at Step 1 running)

Boo's first on-device screenshot of Step 1 showed the two AI (red)
characters looking crowded/near-touching on their shared planet, while
the two player (blue) characters at the same angular spread looked
comfortably spaced. Root cause: `TARGET_RADIUS` (0.3, the AI/target
sprite's drawn radius) is 50% bigger than `AVATAR_RADIUS` (0.2, the
player sprite's) - a difference that's existed since long before Step 1,
just never visible until two characters had to share the gap between
them. At the original 20° spread and this scene's orbit radius (`PLANET_
RADIUS` 0.8 + `LAUNCH_POINT_CLEARANCE` 0.3 = 1.1), the chord distance
between the two characters left the bigger AI sprites almost no
clearance while the smaller player sprites had plenty. Purely cosmetic -
since neither character is in orbital-drift/physics mode at spawn, their
positions come straight from the angle formula, so they don't actually
collide or push apart, they just visually crowd.

**Fix applied:** `CHARACTER_START_ANGLE_SPREAD_DEGREES` bumped 20° -> 30°
(both sides, still one shared constant) - confirmed as the quick stopgap
Boo wanted over the other two options offered (build real random
placement now, or leave the crowding as-is). Gives both sides comfortable
clearance at this orbit radius; not yet re-verified on-device as of this
write-up.

**Long-term design captured, not built:** Boo, explicit, once he'd seen
the fixed-spread layout: characters sharing one planet should eventually
be placed *randomly* on it rather than at a fixed symmetric offset: "long
term I want characters randomly on their planet when 2 on 1 planet. if
there are enough planets for each player, distribute evenly. eventuall I
see more celestial bodies than players but that is for later." So the
eventual model is: (1) random placement for characters sharing a single
planet, (2) even distribution across planets once a side has enough
celestial bodies that each character *could* get its own, and (3) a
future scene with more celestial bodies in play than there are
characters to place on them - explicitly flagged by Boo as further out
than the other two. None of this is built - `CHARACTER_START_ANGLE_
SPREAD_DEGREES` stays a fixed shared constant for now. Added to the
"Still not decided" list below as its own thread.

## Phase 31: multi-character combat, Step 2 (player's own tap-to-choose turn-order picker)

Boo: "do the next step in phase 30" - picking up the 3-step multi-
character combat plan from Phase 30. Two design questions asked directly
(not left to implementation judgment) before building:
- **How to pick:** tap the character's sprite directly on the shared
  planet (not dedicated HUD buttons/portraits).
- **How often:** every round, as long as both of the player's characters
  are still alive - not a sticky default you only override sometimes.

**What's built.** All in `PlayScreen.kt`:
- **`awaitingPlayerOrderPick`** (new field) - true only in the window
  between the AI's round ending and the player picking who goes first.
  Set in `beginPlayerOrderPick` (new - called from `handOffToPlayer` when
  both characters are still alive; with only one survivor, `handOffToPlayer`
  still activates it directly, same as before this step - nothing to
  choose between). Always cleared by `activatePlayerCharacter`, regardless
  of whether it was reached via a tap or the no-choice-needed path.
- **`PlayerOrderPickerInputProcessor`** (new inner class) - the only
  listener wired into a new `orderPickerInputProcessor` `InputMultiplexer`
  (built once in `show()`, same pattern as `fullInputProcessor`/
  `restrictedInputProcessor`) while the picker is active. A touch within
  `ORDER_PICKER_TAP_RADIUS` of either still-living player character's own
  position activates it via the same `activatePlayerCharacter` every other
  path already uses; anything else is ignored. `ORDER_PICKER_TAP_RADIUS`
  (0.45) is generous for a fingertip but deliberately kept under half the
  chord distance between the two characters at
  `CHARACTER_START_ANGLE_SPREAD_DEGREES`'s current 30 degrees, so their tap
  zones can't overlap.
- **Camera reframes to the shared planet itself** (not either character
  specifically - neither's picked yet) at the same `AVATAR_SNAP_ZOOM` every
  other turn-transition uses, which already shows the whole planet clearly
  - both characters end up visible and tappable without any new camera
  logic.
- **HUD** shows "Round N - Tap a character to act first" in place of the
  usual "Round N - P1: 5 left" line while the picker is up (`activePlayerIndex`
  is stale during this window - still whoever last acted - so the normal
  label would be actively misleading, not just uninformative). The move
  buttons are also hidden during the picker, same reasoning.
- **Whole-squad-then-whole-squad sequencing, generalized for an arbitrary
  starting pick.** This was the one real correctness wrinkle: the existing
  `advanceAfterPlayerFired` found "who goes next" via
  `firstLivingPlayerIndexFrom(finishedIndex + 1)`, which only worked
  because index 0 always went first and index 1 always went second. With
  the player now able to tap index 1 to go *first*, "the next index up"
  stops meaning "the teammate who hasn't gone yet" - there's nothing after
  index 1 to find that way, which would have skipped index 0's turn
  entirely for that round. Fixed with a new `PlayerCharacterState
  .actedThisRound` flag: reset false for every character at the start of
  each player round (`handOffToPlayer`), set true the instant a character
  fires (`advanceAfterPlayerFired`), and consulted instead of index order
  to find who goes next. Generalizes correctly to any pick order; the AI
  side is untouched - it has no order picker, so its original fixed-index-
  order assumption still holds there.

**Not built / deliberate scope calls:**
- **Round 1 has no picker.** The very first round of a match still starts
  on character 0 directly (set in `init{}`, before `show()`'s
  `orderPickerInputProcessor` even exists) rather than prompting a pick
  before anything has happened yet. Every round from the AI's first
  hand-off onward does prompt. Flagged as a judgment call, not confirmed
  with Boo - easy to change if round 1 should prompt too.
- **The AI side has no equivalent picker** - it still always starts from
  index 0 each round (its own targeting, unrelated to turn order, was
  still the placeholder at the time this phase was written - see Phase
  32 for the real heuristic).
- Squad-size flexibility beyond fixed-2 remains open; the AI's real
  targeting heuristic (also flagged here as Step 3) was built in Phase 32.

#### How to test this phase on-device

1. Build and run - this touches turn-handoff logic (`handOffToPlayer`,
   `advanceAfterPlayerFired`) more than rendering, so watch for anything
   that looks like a stuck turn or a skipped character first.
2. Let the AI's round finish (both its characters, if both are alive).
   Confirm the HUD switches to "Tap a character to act first" and the
   move buttons disappear, instead of one of your characters just
   auto-activating.
3. Tap your *second* character (not the one who went first last time).
   Confirm it becomes active (camera snaps to it, its own movement budget
   is fresh) and that after it fires, control passes to your *first*
   character next - not straight to the AI. This is the specific case the
   `actedThisRound` fix targets; if this regresses, the old index-order bug
   is back.
4. Tap your *first* character instead on a different round and confirm
   the mirror case still works too (first, then second, then AI).
5. Defeat one of your characters, leaving the other alive. Confirm the
   next round skips the picker entirely and auto-activates the survivor -
   no tap prompt with only one option.
6. Try tapping empty space, a planet, or the AI's side during the picker
   window - confirm nothing happens (no crash, no character activates)
   and the prompt just stays up until a valid tap lands.
7. Confirm pinch/pan and the Back button (pause) still work while the
   picker is up.

## Phase 32: multi-character combat, Step 3 (real AI targeting + planet-sharing obstacle polish)

Boo: "no. proceed with step 3" - the third and final step of the plan
from Phase 30, closing out multi-character combat's core mechanics.
Both pieces were already flagged as Step 3's job in Phase 30/31's own
"not built yet" notes, and both had already-proposed approaches
(lowest-HP targeting; AI obstacle-avoidance around teammates) that Boo
had effectively pre-approved by leaving them as the documented plan
rather than open questions - so no new `AskUserQuestion` was raised
before building this step, unlike Steps 1 and 2.

**What's built.** Both in `PlayScreen.kt`:
- **Real AI targeting.** New `lowestHpLivingPlayerIndex()` replaces the
  Step 1 placeholder ("always the first living index") in
  `activateAiCharacter` - the AI now aims each of its characters at
  whichever living player character currently has the lowest HP, ties
  going to the lower index. Matches the heuristic already named as the
  Step 3 plan in Phase 30's writeup.
- **AI obstacle-avoidance around characters.** New
  `characterObstaclesExcluding(selfEntity)` builds an
  `AiTurnController.Obstacle` for every other living character on either
  side (`AVATAR_RADIUS`-sized for player characters, `TARGET_RADIUS`-sized
  for AI characters), excluding whichever character is doing the aiming.
  Each `AiTurnController`'s `obstacleSource` now returns
  `currentCelestialObstacles() + characterObstaclesExcluding(...)` instead
  of just the star/planets - so an AI character's aim search now avoids
  routing a shot through a planet-mate or the enemy character it isn't
  currently targeting, the same way it already avoided planets. This is
  AI-planning-only: the player's own aim preview
  (`renderAimTrajectoryPreview`) is untouched, and real shots still
  resolve through Box2D/`ProjectileContactListener` regardless of what the
  AI's own simulation predicted.
- **Tunneling-gap padding.** Worked through a real correctness question
  before building the obstacle list: `AiTurnController`'s aim search only
  samples position every `AI_TRAJECTORY_SIM_STEP_SECONDS` (1/60s), which
  at `MAX_MISSILE_SPEED` (15 units/sec) is up to ~0.25 units between
  samples - bigger than a character's own radius (0.2-0.3). A shot that
  grazes a character between two samples would never register as
  "collided with an obstacle" in the simulation, even at full overlap.
  New `AI_CHARACTER_OBSTACLE_PADDING` constant (half that worst-case step
  distance, ~0.125, derived from the existing constants rather than a
  hardcoded number) is added to each character-obstacle's radius to close
  that gap. Only applied to character obstacles - the star/planets are
  already far bigger than one step's worth of travel, so this was never a
  real risk for them.

**Not built / deliberate scope calls:**
- This is a planning-only change - it doesn't touch actual collision
  resolution, friendly fire, or anything Box2D-side. An AI shot can still
  physically hit a teammate if the aim search's own scoring ends up
  preferring that trajectory anyway (avoiding an obstacle only *penalizes*
  a path that clips one during the simulated search - it doesn't forbid
  it outright the way solid ground does for a planet).
- No tie-break beyond index order for equal-HP targets, and no
  target-switching mid-fight if HP changes after a character's turn has
  already started (`startTurn` is called once per activation, same as
  before).
- Squad-size flexibility beyond fixed-2, random per-planet placement, and
  the rest of the long-term celestial-body/placement backlog (see "Still
  not decided") remain untouched - this step was scoped to exactly the
  two Step 3 items Phase 30 named.

#### How to test this phase on-device

1. Build and run a 2v2 match. Bring one of your characters to noticeably
   lower HP than the other (let the AI hit one character several times
   while leaving the other untouched, or use the debug tools if that's
   faster) and confirm the AI's *next* shot targets your lower-HP
   character, not whichever one happens to be first.
2. Heal/switch which of your characters has lower HP (by having the
   healthier one take damage instead) and confirm the AI's targeting
   follows - it should keep re-picking whichever is lowest each time it
   aims, not stick to one character all match.
3. With both of your characters roughly in line with one of the AI's
   shots at an enemy character sharing your planet, watch a few AI turns
   and see whether its trajectory noticeably curves or angles around your
   other character rather than aiming straight through it - this is
   inherently a soft, scored preference (see "Not built" above), so don't
   expect a hard guarantee, just a visible bias away from a straight
   line through a teammate when a similarly-good angled shot exists.
4. General regression check: nothing about turn order, the Step 2 order
   picker, or HUD should have changed this step - confirm those all still
   behave exactly as Phase 31 described.

## Phase 34: turn-handoff pacing (tap-to-continue-or-auto-advance) + camera snap keeps player's zoom - ✅ DONE, built (Sept 2026 session)

Boo's own feedback, unprompted: the automatic turn-handoff (camera snap +
control switch) fired the instant a shot resolved, which read as "clumsy"
especially as the field got bigger via Step D2's campaign tiers - it could
yank the camera away while Boo was still watching a shot fly or looking
around, and the forced zoom change on top of that felt jarring in its own
right.

**Design discussion, not guessed at:** talked through three candidate
fixes for the timing (a fixed pause, requiring a tap to continue, or only
waiting while the camera was actively mid-gesture). Boo picked "tap to
continue" outright at first, then caught himself before it was built -
"the tapping to continue after each and every shot will get tedious" -
since it would mean a tap after literally every shot, both sides, every
character. Landed on a hybrid instead: **an automatic pause that continues
on its own if nothing is tapped, but a tap skips the wait immediately** -
never a mandatory tap, but never cut off early either. Boo picked ~2.5-3s
for the pause length (2.5s shipped, not yet tuned live on-device). For the
zoom question, Boo picked "keep your current zoom, just pan" outright -
stop forcing a fixed zoom level on every snap.

**What's built, all in `PlayScreen.kt`:**
- **`awaitingTurnContinue`/`turnContinuePauseElapsed`** (new fields) -
  `resolveActiveShot()` no longer invokes `pendingTurnHandoff` directly;
  it just starts this wait (if a real handoff is actually pending).
  `continueToNextTurn()` (new function) is the only thing that actually
  invokes `pendingTurnHandoff` now, called either by a tap or by render()'s
  own per-frame check once `turnContinuePauseElapsed` reaches
  `TURN_CONTINUE_PAUSE_SECONDS` (2.5s).
- **`ContinueTapInputProcessor`** (new inner class, same pattern as
  `BackKeyHandler`) - lives in `restrictedInputProcessor` (already the
  active multiplexer for this whole "shot in flight, then waiting on
  handoff" window, for both sides), registered LAST so an existing debug
  button still gets first dibs on a tap that lands on it instead of also
  continuing the turn. A tap anywhere else while waiting calls
  `continueToNextTurn()` immediately; a no-op otherwise.
- **`renderContinuePrompt()`** (new function) - draws a plain "Tap to
  continue" text label, centered, about a third of the way up the screen
  (clear of the movement buttons, debug-tool column, and stats panel),
  only while `awaitingTurnContinue` is true.
- **This gates EVERY automatic handoff, not just after Boo's own shots** -
  between each of the AI's characters when it has more than one, and
  between the AI's turn ending and the player's beginning, same as after
  the player's own shot. Deliberate, matches what was actually discussed.
- **`snapCameraToActiveAvatar`'s eased path no longer forces
  `AVATAR_SNAP_ZOOM`** - `cameraEaseToZoom` is now set to whatever
  `camera.zoom` already is at the moment the snap starts, so the eased pan
  keeps the player's own zoom level instead of overriding it. The `instant`
  path (used exactly once, for the very first camera framing in `init{}`)
  still forces `AVATAR_SNAP_ZOOM` - there's no "current" zoom worth
  preserving before the very first frame.

**Explicitly NOT touched, by Boo's own scoping in the design discussion:**
- The win/loss screen (`GameOverScreen`) still appears immediately the
  instant the last character on a side is defeated - not gated behind a
  tap or the pause.

### Revision: 5s live countdown, not a static prompt (Sept 2026 session, same session)

On-device feedback after the initial build: "the tap to continue is ok but
it is too quick. what I am thinking is that instead of that, it comes up
with a message towards the bottom that... is counting down from 5 sec
something like 'Next turn in x sec'." Adopted essentially as proposed - it
directly fixes the actual complaint (the static prompt didn't say how much
time was left, which read as "too quick" once the pause ran out) - and
kept the existing tap-to-skip behavior alongside it, since nothing asked
for that to go and it's a strict improvement with no added cost.

- **`TURN_CONTINUE_PAUSE_SECONDS` bumped from 2.5f to 5f.** Still a plain
  constant, not a live-tunable slider - a natural candidate for its own
  debug slider later if 5s turns out wrong in practice too.
- **`renderContinuePrompt()` rewritten** - instead of static "Tap to
  continue" text, now shows a live `"Next turn in ${n}s"` label, `n`
  computed as `ceil(TURN_CONTINUE_PAUSE_SECONDS - turnContinuePauseElapsed)`
  (coerced to never go below 0) so it counts down cleanly through whole
  seconds (5, 4, 3, 2, 1) instead of jumping or showing a fraction. A tap
  still skips the wait immediately regardless of what the countdown
  currently reads.

## Bug fixes found via Phase 34 on-device testing (Sept 2026 session)

Two bugs Boo found while testing Phase 34 (both from the same on-device
session, reported with screenshots). Neither is caused by Phase 34 itself
- both are older orbital-drift/drift-landing issues that Step D2's
N-planet-per-side generalization made reachable in practice, surfaced now
because that on-device session happened to hit them.

### Bug 1: player sprite stranded in space after drifting onto (then losing) a second planet

Boo, on-device: "the blue circle is the player sprite staying on planet
after planet has go[ne] away. in this scenario, the sprite[']s original
planet was destroyed and it ended up drifting to an enemy pl[a]net along
side an enemy sprite. eventually that planet got destroyed and the enemy
went right into the sun but the player sprite remained."

**Root cause:** `PlayerCharacterState`/`AiCharacterState` each held an
immutable (`val`) `homePlanet: Planet`, set once at construction. The
drift-trigger check in `render()` only ever watches whether *that specific
planet* is destroyed. Before Step D2 (one planet per side) this was fine -
there was nothing else a character could ever be standing on. Once Step D2
let a drifting character land on a *different, still-intact* planet
(including an enemy's), `homePlanet` never updated to reflect the new
ground, and `driftResolved` had already latched permanently `true` from
the first landing (by original design, to stop a landed character from
re-triggering off its own now-moot original planet). So when that second
planet was later destroyed too, nothing was watching it, and the character
was left stranded - visually still standing on empty space. Confirmed this
exact gap was already called out in this class's own doc comment from the
Step D2 era as "a real limitation... not an oversight" - Step D2 knowingly
left it, and this round is what actually closes it.

**Fix, all in `PlayScreen.kt`:**
- `homePlanet` renamed to `groundPlanet` throughout the file (plain
  rename, `sed`, 15 occurrences - the old name undersold what it now
  does), and changed from `val` to `var` on both `PlayerCharacterState`
  and `AiCharacterState`.
- `resolveDriftLanding`'s return type changed from
  `Pair<DriftLandingOutcome, Vector2?>` to
  `Pair<DriftLandingOutcome, Planet?>` - callers need the whole landed-on
  `Planet` object now, not just its position, so they can re-ground the
  character on it.
- `checkPlayerDriftLanding`/`checkAiDriftLanding` now set
  `pc.groundPlanet`/`ac.groundPlanet` to whatever `Planet` was actually
  landed on, and clear `driftResolved = false` again on every successful
  (non-fatal) landing - so if *that* planet gets destroyed too, the
  character can drift a second (or third) time instead of being
  permanently un-watched after the first landing.

### Bug 2: defeated-by-the-sun sprite's wireframe outline keeps orbiting forever

Boo, on-device: "when an enemy falls into the sun, the red part of the
sprite disappears but the green cir[c]le outline remains and constantly
rolls around the sun."

**Root cause, two parts:**
1. `resolveDriftLanding`'s HIT_STAR branch only ever dealt lethal damage
   (`health.applyDamage(health.maxHp)`) - it never touched the Box2D body
   itself. The body stayed `DynamicBody` and kept being simulated (still
   affected by gravity), even though the character was defeated. The
   sprite draw is correctly gated on `isDefeated` so it vanished, but the
   physics body kept orbiting the star underneath.
2. `debugRenderer`'s `Box2DDebugRenderer.renderBody` override (the
   wireframe/debug-outline pass) already had a special case to hide the
   star's and every planet's body, but never had one for a defeated
   character's body - so the still-simulating, sprite-less body kept
   drawing its green wireframe dot, which is exactly the "outline...
   constantly rolls around the sun" Boo saw.

**Fix, both in `PlayScreen.kt`:**
- `resolveDriftLanding`'s HIT_STAR branch now freezes the body the same
  way the LANDED outcome already did: `body.type = KinematicBody`,
  `body.linearVelocity = Vector2.Zero`, and removes
  `GravityAffectedComponent` - so it actually stops moving, not just stops
  taking damage.
- `debugRenderer`'s `renderBody` override extended to also skip any
  player/AI character body whose entity is `isDefeated`, so no leftover
  wireframe dot draws at all. This incidentally also fixes the same
  latent issue for a character defeated by lethal *landing* damage
  (undiscovered before now, same symptom, same fix covers it).

#### How to test this round on-device

1. Fire a shot and watch the post-shot prompt: it should read
   "Next turn in 5s" and count down live (5, 4, 3, 2, 1) rather than
   showing static "Tap to continue" text.
2. Do nothing and let it run out - the handoff should fire right as the
   countdown reaches 0, roughly 5 seconds after the shot resolved (up from
   2.5s before this revision).
3. Tap anywhere (not a debug button) mid-countdown - the handoff should
   still fire immediately, same as before, regardless of what number the
   countdown currently shows.
4. Bug 1 repro: get a character drifting off its own destroyed planet so
   it lands on a *different* (enemy or own-side) still-intact planet, then
   destroy that second planet too - the character should drift again
   (visually), not remain frozen standing on empty space.
5. Bug 2 repro: get a character (either side) to drift into the sun and
   get defeated that way - confirm nothing at all is left drawing at the
   sun afterward: no sprite (already worked) AND no wireframe outline
   orbiting it (the actual fix this round).
6. Regression check: a character defeated by ordinary combat damage (not
   drift-related) should look exactly as before - sprite and any debug
   wireframe both disappear immediately, no lingering outline.
7. Regression check: a character that drifts and lands successfully
   (not into the sun) should behave exactly as before - reanchors, can
   fire from its new spot, no wireframe or stranding issues.

## Gravitons economy - design discussion + Step 1 (Sept 2026 session)

Grew directly out of on-device feedback on the campaign ladder: "its too
hard when its 2 v 1" (the 5-win tier, where the AI gets a 2nd character/
planet before the player does - see "Campaign progression ladder" above,
"a deliberate asymmetric difficulty step"). Talked through fix directions
(soften the AI, buff the player, remove the asymmetric step, or don't
guess and discuss tradeoffs) - Boo widened the conversation instead:
wants a persistent in-game economy so losses still feel like forward
progress, explicitly citing the "typical video game" pattern of earning
currency (more on a win, some on a loss) to spend on upgrades, and wants
neither side to ever feel too overmatched.

**Design discussion, grounded in actual research, not just guessed at:**
- **Flow theory (Csikszentmihalyi):** enjoyment peaks when challenge
  tracks player skill - too easy is boring, too hard is anxiety-inducing.
  This is the formal version of "never overmatched either direction," and
  it's why Boo's own pick of **tuned static balance over live rubber-
  banding** is the right call - flow theory is about a rising curve staying
  matched to the player, not a system that visibly props up whoever's
  currently losing (which reads as patronizing when players notice it).
- **Loss aversion (Kahneman & Tversky):** losses hurt disproportionately
  more than equivalent gains feel good - a loss that still hands you
  something forward-moving measurably softens that sting. Directly backs
  Boo's "earn a little even on a loss" instinct.
- **Self-determination theory (Deci & Ryan):** the *feeling* of competence/
  progress matters as much as the reward itself - implies the post-game
  screen should visibly show what was earned, not just silently bank it.
  Not yet built (see "Not built this step" below).
- **Explicit caution (Boo asked about casino research specifically):**
  slot machines/gacha lean on variable-ratio reinforcement (Skinner) -
  unpredictable reward timing/size is what makes them compulsive, and
  that's also what makes them manipulative rather than just fun. Decided
  to go the opposite way on purpose - transparent, predictable economy
  math (you always know what a win/loss earns and what an upgrade costs),
  rewarding skill/persistence rather than manufactured uncertainty. This
  project isn't monetized, so there's no reason to reach for that lever.

**Decisions locked in (via AskUserQuestion + follow-up discussion):**
- **Currency persists across games** (the meta-progression layer from the
  earlier "Game design exploration" three-layer note), not a per-run-only
  resource - matches "grind to get better, come back stronger." An in-run-
  only currency layer (the *other* third of that original three-layer
  note) is explicitly deferred, not decided against - "both eventually."
- **Balance approach: tuned static, not live adaptive** - confirmed above.
- **First thing it buys: Max HP** (simplest, proves the earn/spend/persist
  loop end to end before anything more complex). Health regen (a brand
  new mechanic, doesn't exist at all yet) and new weapons (the much bigger,
  already-captured lasers/missiles/bombs design note) are later, not this
  step.
- **Planet size variation bundled into this same round** (Boo's call,
  explicit) - not part of Step 1 below; its own step once Step 1/2 land.
- **Name: "Gravitons"** - ties the currency directly to the game's actual
  core mechanic (gravity) rather than a generic scrap/currency name. Boo's
  own words: "lets go with gravitons for the time being. I may change in
  the future" - deliberately not treated as permanent; renaming later is
  just a label/string change, nothing structural depends on the name.

**Planned build order** (same discipline as every other multi-part
delivery - each step on-device-testable before the next lands):
- **Step 1 - built, see its own entry immediately below.** Confirmed
  working on-device (Boo: "gravitons work as expected").
- **Step 2 - built, see its own entry further below.**
- **Step 3 (not yet built):** planet size variation - generalizing
  `PLANET_RADIUS` from a fixed constant to a per-planet randomized value,
  touching `SlingshotInputProcessor`/`AiTurnController`/
  `AvatarMovementController` (all three currently assume the fixed
  constant) plus render/texture scaling. Independent of the currency work,
  just bundled into this same round per Boo's call above.

### Gravitons economy, Step 1: plumbing + earning - ✅ DONE, built (Sept 2026 session)

Deliberately narrow: prove Gravitons actually accrue and persist, visible
on the menu screen, before building anything that spends them.

**What's built:**
- **`GameSave.gravitons: Int`** (schema v4, purely additive - same safe
  migration pattern as v2's `appLaunchCount`/v3's `winCount`).
- **`SaveManager.awardGravitons(won: Boolean)`** - adds `GRAVITONS_PER_WIN`
  (10) on a win or `GRAVITONS_PER_LOSS` (3) on a loss, then persists.
  Deliberately separate from `recordWin()` (win-only) - this one is called
  from BOTH of `PlayScreen`'s win and loss branches, since the whole point
  is that a loss still moves you forward, just by less. Both amounts are
  first-guess numbers, not derived from anything - same "ship a guess,
  tune it once it's actually been played" treatment as
  `TURN_CONTINUE_PAUSE_SECONDS` or `ShotSpeedTuning`'s default.
- **`SaveManager.currentGravitons()`** - mirrors `currentWinCount()`.
- **`MenuScreen` now shows "Gravitons: N"**, drawn directly under "Wins"
  (both being the persistent-between-runs numbers), with "Runs completed"
  shifted down to make room. Purely a visibility check for this step -
  proves the balance is actually accruing, same role Phase 6's `runCount`
  display originally played for `SaveManager` itself.

**Explicitly NOT built this step:**
- **Nothing spends Gravitons yet** - Step 2's job.
- **No visible "+N Gravitons" feedback on the actual game-over screen** -
  the self-determination-theory point above (visible progress matters as
  much as the reward) is noted but not acted on this step; right now the
  only way to see the balance change is to go back to the menu and notice
  the number went up. Worth revisiting once Step 2 gives the number
  something to actually mean.
- Planet size variation (Step 3, separate).

#### How to test Step 1 on-device

1. Note the current "Gravitons: N" value on the menu screen before
   playing (0 on a fresh save/install).
2. Play a game to a loss. Return to the menu and confirm the number went
   up by exactly 3.
3. Play a game to a win. Return to the menu and confirm the number went
   up by exactly 10 this time (more than a loss, per the design).
4. Force-close and reopen the app (or otherwise cold-start it) and
   confirm the Gravitons number survived - same persistence check every
   other `SaveManager` field gets.
5. Regression check: Wins/Runs completed counters still behave exactly as
   before - this step only adds a new number, doesn't touch how those two
   are earned or displayed otherwise.

### Gravitons economy, Step 2: the first spend (Max HP upgrade) - ✅ DONE, built (Sept 2026 session)

Confirmed via two follow-up `AskUserQuestion` rounds before building: the
buy UI gets its own new `UpgradesScreen` (not a section bolted onto
`MenuScreen`), and Max HP purchases are capped at a fixed ceiling rather
than stacking forever (Boo, explicit - ties back to the "never overmatched"
goal, since an uncapped climb would eventually make the player trivially
durable against AI stats the campaign ladder never grows past a given
tier).

**Numbers, grounded in what's actually in the game, not picked blind:**
both sides sit at 100 Max HP (`AVATAR_MAX_HP`/`TARGET_MAX_HP`) and a direct
missile hit does 25 damage (`MISSILE_DAMAGE` - 4 hits to defeat). Landed
on **+10 Max HP per purchase, capped at 5 purchases** (100 -> 150 HP, a
50% buff at full investment), with an escalating cost curve - **15 / 30 /
50 / 75 / 105 Gravitons** for levels 1 through 5 (275 total to max out).
The escalating cost is deliberate: the cheap early levels help fastest
over the "2v1 feels too hard" hump that started this whole economy, while
the expensive later ones are a longer grind that roughly tracks reaching
the campaign ladder's higher tiers - tying the two curves together instead
of letting either race ahead of the other. Same "ship a guess, tune it
once it's actually been played" treatment as every other feel constant in
this project - not derived from anything beyond those two real in-game
numbers.

**What's built:**
- **`GameSave.hpUpgradeLevel: Int`** (schema v5, purely additive - same
  migration pattern as every schema bump before it).
- **`SaveManager.purchaseHpUpgrade()`** - the actual spend: checks against
  `nextHpUpgradeCost()` (null once capped), deducts Gravitons, increments
  the level, persists. Returns `Boolean` (success/failure) but the only
  caller ignores it - a failed attempt (can't afford it, or already
  maxed) changes nothing, and the very next frame's redraw already shows
  whatever actually happened either way.
- **`SaveManager.hpUpgradeBonus()`** - `hpUpgradeLevel * HP_PER_UPGRADE_LEVEL`
  (10). Read by `PlayScreen`'s `playerCharacters` construction, added on
  top of the existing `AVATAR_MAX_HP` constant - **player-side only**, AI
  characters stay at the plain `TARGET_MAX_HP`, untouched. `AVATAR_MAX_HP`
  itself lost its `private` modifier so `UpgradesScreen` can display the
  same base number `SaveManager` builds on top of, without a second,
  drift-prone copy of "100" living in two files.
- **New `UpgradesScreen`** - a dedicated screen, same "solid color, tap
  zone" pattern every other screen in this project uses (`PauseScreen`'s
  left/right split, generalized here to two vertical bands instead, since
  this screen also needs room above them for the stat readout: title,
  current Gravitons balance, current Max HP + level, then a BUY band
  (dims to gray and reads "MAXED OUT" once capped) and a BACK band below
  it. Reached only from `MenuScreen` - see next bullet - never mid-run.
- **`MenuScreen`'s one exception to "tap anywhere plays"** - a small
  top-right corner zone (last 35% width, top 15% height) now opens
  `UpgradesScreen` instead, with an "Upgrades >" label drawn there so it's
  discoverable. Deliberately corner-anchored and small so it can't be
  brushed by accident on the way to starting a game.

**Explicitly NOT built this step:**
- Nothing else is purchasable yet - health regen and new weapons are
  later, separate work (see the design-discussion section above).
- No "insufficient funds" feedback beyond the button simply not doing
  anything when unaffordable - no error message, no shake/flash. Worth
  revisiting if that reads as broken/unresponsive on-device rather than
  "nothing happened because you can't afford it yet."
- Planet size variation (Step 3, still separate, still not started).

#### How to test Step 2 on-device

1. From the menu, tap the "Upgrades >" label in the top-right corner -
   confirm it opens a new dark-purple screen (not a game), showing your
   current Gravitons balance and "Max HP: 100 (Lv 0/5)".
2. With fewer than 15 Gravitons banked, tap the BUY band - confirm
   nothing happens (balance/level both unchanged) - this is the
   "unaffordable" case, silent by design (see above).
3. Grind (or otherwise reach) at least 15 Gravitons, tap BUY again -
   confirm the balance drops by 15, the level reads "Lv 1/5", and the Max
   HP line now reads 110.
4. Play a game as the player side and confirm you can actually take more
   hits than before at Lv 1+ - e.g. at Lv 1 (110 HP) you should survive a
   5th direct hit that would have been fatal at the old 100 HP/4-hit math.
   AI characters should be completely unaffected - still defeated in
   exactly 4 hits as always.
5. Buy through all 5 levels (or manually verify the cost sequence 15/30/
   50/75/105) and confirm the screen reads "MAX HP - MAXED OUT" (band
   dims to gray) once Lv 5/5 is reached, and tapping that band does
   nothing further - balance stays put, no 6th level appears.
6. Tap BACK and confirm it returns cleanly to the menu, with the same
   Gravitons/Wins/Runs numbers as before (nothing lost in the round trip).
7. Force-close and reopen the app - confirm both the Gravitons balance
   and the HP upgrade level survived, and a fresh game correctly starts
   your character(s) at whatever HP the current level grants.
8. Regression check: tapping anywhere else on the menu (i.e. NOT that
   corner) still starts a fresh game immediately, exactly as before.

## Post-foundation hardening (not numbered phases — ongoing, as-needed)

- **16 KB native alignment** — resolved, see "Resolved risks" above.
- **Automated unit tests for `core`.** Since `core` is pure Kotlin/JVM (no
  Android dependency), it can run real JUnit tests with no device/emulator —
  a gap that stood out once Phase 7 closed: every phase so far was verified
  by hand on-device, with zero automated regression coverage. Added:
  - `PhysicsSystemTest` — steps a **real** Box2D `World` (not a mock, via a
    `natives-desktop` test dependency) through `PhysicsSystem.update()` and
    checks the resulting body velocity, since a free-falling body's velocity
    change per full step is exactly `gravity.y * TIME_STEP`. Covers: one
    exact-timestep update produces one step; many small deltas summing to
    1.0s produce the same outcome as few large deltas summing to the same
    1.0s (frame-rate independence — the actual point of a fixed-timestep
    accumulator); a huge stalled frame (5s) is clamped to `MAX_FRAME_TIME`
    instead of running 5 seconds of physics at once.
  - `SaveManagerTest` — exercises the real corruption-safe read/write
    algorithm (round-trip, corrupt-primary-falls-back-to-backup,
    corrupt-with-no-backup-falls-back-to-defaults, mismatched schema version
    is rejected) against scratch files in a fresh temp directory per test.
    **Required a small refactor**: `SaveManager`'s `persist`/`load` logic
    was pulled out into `internal fun persistTo(...)`/`loadFrom(...)` that
    take their file targets as parameters, instead of only being reachable
    through the singleton's lazily-cached `current` field (which loads once
    per JVM and can't safely be reset between tests). The public API
    (`currentRunCount()`, `recordRunEnded()`) is unchanged.
  - `GdxTestBootstrap` (test-only) — starts a `HeadlessApplication` with
    `updatesPerSecond = -1` (no render-loop thread) so `Gdx.app`/`Gdx.files`
    exist during tests, and calls `Box2D.init()`. Idempotent, called from
    every test class's `@Before`.
  - Added to `core/build.gradle.kts` as `testImplementation` only (JUnit
    4.13.2, `gdx-backend-headless`, and box2d's `natives-desktop` classifier
    jar) — none of this reaches the Android APK.
  - ✅ **DONE** — confirmed passing on Boo's PC (`:core:test`, all 8 tests,
    `BUILD SUCCESSFUL`). One real bug found and fixed along the way — see
    "Real bugs hit and fixed" above (missing `gdx-platform:natives-desktop`
    test dependency). Note for next time this is run: `SerializationException`
    stack traces printed to the console during the run are expected, not
    failures — three tests deliberately feed in corrupted JSON to prove
    `SaveManager` catches and recovers from it; each is immediately followed
    by a log line confirming the fallback (backup or defaults) worked.
    Claude's cloud sandbox still can't run these itself (Maven Central is
    blocked by network policy there, confirmed via the proxy status
    endpoint) - they need to keep being run from Android Studio/Gradle on
    Boo's PC. See "How to run the unit tests" below.
- **Save-schema migration path.** Previously, any `GameSave.schemaVersion`
  mismatch — even a harmless one — made `SaveManager` discard the whole save
  and fall back to defaults. Now `readValid()` treats the two directions
  differently: a save *older* than `CURRENT_SCHEMA_VERSION` is migrated
  forward (LibGDX's reflection-based `Json` reader already leaves a field
  that didn't exist yet at that older version at its normal Kotlin default,
  which covers any purely additive change with zero extra code — the
  version check just needed to stop treating "older" as "invalid"), while a
  save *newer* than this build understands is still rejected, same as
  before (no safe way to guess what a field added later means). Demonstrated
  with a real version bump rather than untested scaffolding: `GameSave` is
  now schema v2, adding `appLaunchCount` (see its "Schema history" doc
  comment) - a genuine new stat, incremented once per cold start via
  `SaveManager.recordAppLaunched()` (called from `PhysicsDuelGame.create()`,
  right after the existing Phase 6 cold-start log), not just a throwaway
  test fixture. A future rename or type change of an existing field would
  need real per-case handling added to `readValid()`'s migration branch -
  there's none of that yet because no schema change has needed it; the
  comment there says exactly where it'd go.
  - New test: `SaveManagerTest.olderSchemaVersion_migratesForwardInsteadOfBeingDiscarded`
    (hand-writes a v1-shaped JSON file — missing `appLaunchCount` entirely —
    and confirms it loads with `runCount` preserved, `appLaunchCount`
    defaulted to 0, and `schemaVersion` upgraded in memory to current).
    `wrongSchemaVersion_isTreatedAsInvalid` was renamed to
    `newerSchemaVersion_isRejected` to reflect that only the "newer" 
    direction is still rejected; its behavior is unchanged.
  - ✅ **DONE** — confirmed passing on Boo's PC (`:core:test`, all 9 tests).
- **HUD scaling audit.** `HudFont`'s font size was a raw `BitmapFont.setScale`
  pixel multiplier (`1.4`, tuned by eye on Boo's one test device) with no
  regard for screen density - the same "1.4x" would render at a visibly
  different physical size on a phone with a different pixel density, even
  on a similarly-sized screen. Fixed by multiplying by
  `Gdx.graphics.density` (on Android this literally *is* `DisplayMetrics
  .density`, the same value dp/sp units are defined against), the standard
  way to get a consistent logical size across devices instead of a fixed
  pixel count. `PlayScreen`'s HUD label margin (`16f` px) was the only other
  raw-pixel constant in the UI - now goes through the same scaling via a new
  `HudFont.scaled()` helper. Everything else (Menu/Pause/GameOver's text
  positions) was already expressed as a fraction of screen width/height, so
  it was already resolution-relative and didn't need this.
  - `HudFont.REFERENCE_DENSITY` calibrates this - it needs to be the density
    Boo's actual test device reports, so that device keeps rendering exactly
    as before. Confirmed via a (now-removed) diagnostic Logcat line: Boo's
    device (a Samsung Galaxy Fold, `SM-F971U1`) reports **density=2.625,
    1248x1972px**. `REFERENCE_DENSITY` is set to that exact value - not a
    placeholder anymore.
  - Tested on-device across both of the fold's screens (cover screen closed,
    inner screen open) - looked the same in both states, a good sign the
    scaling itself is doing the right thing rather than nothing at all
    (this device's two physical panels are genuinely different displays,
    not just a resize).
  - Text size follow-up (done): Boo asked to enlarge it. `HudFont
    .REFERENCE_SCALE` bumped from `1.4` to `2.4` (~70% larger) - a separate,
    deliberate size decision from the scaling-consistency fix above, not a
    side effect of it. ✅ Confirmed on-device, looks good - no clipping
    issues raised on either the fold's cover or inner screen.
  - No new automated test - screen size/density scaling isn't something a
    JVM unit test can meaningfully check (there's no real display), so this
    one stays a visual, on-device check like Phase 7's HUD work was.
  - ✅ **DONE** — confirmed on-device (both fold states), calibrated to
    Boo's real device density.

## How to run the unit tests

1. Sync Gradle first (it needs to pull in the new test-only dependencies).
2. In Android Studio's project panel, expand `core > src > test > kotlin >
   com.devavona.physicsduel`. Right-click that `physicsduel` test package
   (or either test file directly) and choose **Run 'Tests in ...'**.
3. A "Run" panel opens at the bottom showing each test method with a
   green check or red X. All should be green. If anything's red, send me
   the failure text (click the failing test to see it) and I'll fix it.
4. This never touches your phone — it runs entirely on your PC's JVM, no
   device/emulator needed.

## How to test Phase 7 on-device

1. Sync Gradle, run on-device as usual.
2. **Menu**: should now show "PHYSICS DUEL" title, "Tap to Play", and
   "Runs completed: N" (N should match whatever Phase 6 last saved/logged).
   Tapping to start a run should play a short tap sound.
3. **Pause** (Back from Play): the same green/red halves as before, now
   labeled "RESUME" and "END RUN". Tapping either should play the tap sound.
4. **Game Over** (via Pause's "END RUN"): should show "GAME OVER" / "Tap to
   continue", with a tap sound on the transition in and on tapping to
   continue.
5. **In Play**: top-left corner should show a small live "Y: <number>" label
   that updates every frame as the circle falls/settles/gets dragged - watch
   it track the circle's motion in real time.
6. General check: rotate/resize (if you do) shouldn't misplace any of the
   text - it should stay correctly positioned relative to the screen edges.

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

- **Resolved (Sept 2026 session):** `device_bash` "being down" every prior
  session (Phases 1 through 6) was not a bridge bug — the session simply had
  no folder connected, so there was nothing under `$HOME/mnt/` for it to
  operate on. Confirmed by requesting access to `C:\Apps\dev\physicsduel`
  and re-testing: `device_bash` worked immediately (listed the repo
  contents). Going forward, Claude can read/edit files and run git directly
  on Boo's PC via `device_bash` once the folder is connected for the
  session, instead of routing through stage/commit — still following the
  standing rule: init/commit only, never push — Boo always runs the actual
  push.
- **`device_bash` down again (different cause, Phase 30/31 session):**
  every attempt this session failed with "Workspace unavailable... a
  Windows update released September 8 prevents Claude's workspace from
  reaching your files" - a platform-side issue, not the no-folder-connected
  cause above (a folder was connected throughout). Fell back to the
  `device_list_dir` (get a fresh mtime) → write to `/mnt/user-data/outputs/`
  → `device_commit_files` (with that mtime as `expectedMtimeMs`) pattern for
  every file write this whole session. Revisit whether `device_bash` is
  back before assuming this fallback is still needed.
- **File-revert bug found this same session, cause unconfirmed but
  probably related to the point above.** Multiple times, a file Claude
  wrote via `device_commit_files` (both `PlayScreen.kt` and
  `PROJECT_STATE.md`) silently reverted back to its previous (already-
  committed) content on Boo's PC before he got to `git add`/`git commit` -
  once within the normal time it took him to read Claude's message and
  paste commands, i.e. not obviously tied to any specific action on his
  end. Ruled out: OneDrive (not enabled) and Dropbox (has its own separate
  folder, not this one). Tried Android Studio's Invalidate Caches/Restart -
  didn't fix it. Leading theory: `device_commit_files` writes bytes
  successfully but doesn't trigger Windows' normal file-change
  notifications on this PC (plausibly the same Sept 8 Windows update issue
  above) - so Android Studio (or any app with its own file cache) never
  invalidates its stale in-memory copy, and later flushes that stale copy
  back over Claude's write on its own schedule (a save-all-before-
  sync/run, or possibly just periodic autosave - not fully isolated).
  Not confirmed as the root cause, just the best-fitting theory so far.
  **Practical mitigation, not a fix:** since a `git commit` captures
  whatever's on disk at the instant it runs, a later revert of the
  working-tree file doesn't undo an already-completed commit. So: always
  give Boo one single copy-pasteable block that adds, commits, AND pushes
  immediately after Claude writes a file - never split "check it first"
  and "commit it later" into two separate exchanges, since the gap between
  them (even just the time to read a message) has been enough for a revert
  to happen. If `git status`/`git log` ever shows a commit didn't actually
  happen (working tree already clean, nothing staged), that means the
  revert won the race that time - just re-write the file and retry the
  same single-block pattern.
- Boo prefers step-by-step pacing with no assumed familiarity with dev tool
  UIs (Android Studio menus, git terminal) — see Claude's memory for the
  full standing preference and the "SBS" shorthand.
- **Git identity for this repo, set locally (not `--global`) in the Linux
  VM behind the device bridge (Sept 2026 session):** `user.name devavona`,
  `user.email dev@delavona.com` (Boo's own domain — distinct from
  "devavona," which is just his dev-handle naming convention, not a
  domain). Needed because this VM had never run a git command before and
  had no identity configured at all - unrelated to whatever's set up in
  Boo's actual Windows git/Git Bash. First commit made with it:
  `9bacaa4` ("Add generic difficulty scoring engine..."). Still
  uncommitted/unpushed beyond that at time of writing - `gradlew.bat`'s
  line-ending change (see above) deliberately left unstaged. Standing rule
  unchanged: Claude may init/commit, Boo always runs the actual `git
  push`.
- This file is kept in sync in two places: here in the repo (so a fresh clone
  tells the whole story on its own) and in the "Physics Dual" Claude Project's
  docs (so a brand-new chat can pick up context without touching Boo's PC at
  all). Keep both updated together at each phase checkpoint.
