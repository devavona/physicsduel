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
- Whether a damaged celestial body visibly shrinks as its mass drops, or
  keeps its visual size while only an internal mass value changes.
- Trebuchet's actual weapon behavior - named but not yet designed.

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
