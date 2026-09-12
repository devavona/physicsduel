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

## Orbital drift for a defeated-planet-but-alive character - captured design note, own future phase (Sept 2026 session, not yet built)

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

## Multi-character combat: turn order, camera, and stray-shot lifecycle - captured design (Sept 2026 session, not yet built)

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
- **Squad composition / AI behavior with multiple characters** - same
  open item as flagged in the original "Campaign progression ladder"
  design above, still open.
- **Build order** for everything in this section - camera/pan-zoom
  almost certainly needs to land first (see above), but the exact
  phase sequencing hasn't been explicitly confirmed with Boo the way
  the Phase 19-23 order was.

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

**Final behavior:** the obstacle-stop skip stays (the red preview still
draws straight through any celestial body rather than stopping at the
first one), but it's now additionally capped at a fixed
`AIM_PREVIEW_INVALID_DOT_COUNT` (24) dots regardless of
`effectiveMaxSeconds` - a short, fixed-length red stub that clears the
shooter's own planet and reads as "pointing here, and it's invalid,"
not a full trajectory projection (which wouldn't mean anything anyway
once it's simulating gravity through solid ground). The legal (gray)
preview is untouched by any of this - still obstacle-stopped, still
runs the full `effectiveMaxSeconds` when nothing blocks it.

#### How to test the Phase 27 addendum on-device

1. Sync Gradle, run on-device as usual.
2. Aim below your own horizon - confirm the red dots now extend a short,
   clearly-visible stub (about 24 dots, passing through your own planet
   if that's the pointed direction) rather than stopping almost
   immediately, and rather than running the full length of a normal
   preview.
3. Aim in a normal, legal direction - confirm the gray preview's length/
   obstacle-stop behavior is unchanged from before this addendum.

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
