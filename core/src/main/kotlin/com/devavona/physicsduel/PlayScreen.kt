package com.devavona.physicsduel

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Box2D
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import kotlin.random.Random

/**
 * The physics playground itself (originally Phases 2-4's falling-circle
 * demo), now wrapped as a [Screen] instead of being the app's top-level
 * class. Pressing Back pauses rather than quitting - see [PauseScreen].
 *
 * **Phase 8 milestone: pull-and-release aiming + a gravity-curved
 * projectile.** Replaces the orbital gravity-well milestone's demo (a
 * single body orbiting a star, draggable via [DragInputProcessor]) with the
 * first real *combat* interaction: a fixed launch point on one planet,
 * pull-back-and-release aiming ([SlingshotInputProcessor]), and a fired
 * missile that curves under the star's gravity exactly like the orbiting
 * body did, via the same [GravitySystem]. See PROJECT_STATE.md's "Phase 8"
 * entry for the full scope and its deliberate simplifications: only the
 * star exerts gravity this phase (the two planets don't pull yet, even
 * though every celestial body is confirmed to eventually pull), and a hit
 * is only *detected* (logged, missile removed) - no health, damage, or
 * cratering yet.
 *
 * **Phase 9 milestone: a real avatar + movement-budget/turn structure.**
 * Phase 8's fixed launch point is now [AvatarMovementController]'s live
 * position instead - the avatar walks along the launch planet's surface,
 * spending steps from a budget split into a pre-shot half (line up an
 * angle) and a post-shot half (take cover), with firing gated on being in
 * the pre-shot half. No AI opponent, health, or second character yet -
 * passing the turn just starts a fresh one, so this phase is purely about
 * proving the movement/budget/turn-boundary mechanic feels right on-device
 * before anything else builds on top of it. See PROJECT_STATE.md's
 * "Phase 9" entry for the full scope.
 *
 * **Phase 10 milestone: health/damage.** Adds the target planet's
 * stand-in defender - a small static [HealthComponent]-tagged body, not a
 * full character yet (no movement, no turn structure, no AI of its own) -
 * and wires [ProjectileContactListener] to actually apply damage on a hit
 * instead of only detecting one. See PROJECT_STATE.md's "Phase 10" entry.
 *
 * **Phase 11 milestone: mutable celestial-body mass.** The target planet
 * (only the target one, for now - see PROJECT_STATE.md's "Phase 11" entry)
 * is now tagged [GravitySourceComponent] with a mutable mass a hit can chip
 * away at, so it actually pulls things and that pull measurably weakens as
 * it takes damage, down to being destroyed entirely. The launch planet and
 * the star are unchanged (the star opts out of being damaged at all - see
 * [createStar]).
 *
 * **Phase 12 milestone: a minimal AI opponent.** The turn no longer just
 * loops back to the player - [AiTurnController] (new) takes over for one
 * turn in between, firing a single simple shot at wherever the player's
 * avatar was standing when the hand-off happened, before control returns.
 * Player input (movement + aiming) is disabled for that window by swapping
 * [Gdx.input]'s processor rather than teaching every input class about a
 * "whose turn is it" flag - see [restrictedInputProcessor]. See
 * PROJECT_STATE.md's "Phase 12" entry for the full scope.
 *
 * **Phase 13 milestone: the player's avatar can take damage.** Closes the
 * gap Phase 12 deliberately left open - [avatarBody] gives the avatar a
 * real (kinematic, not physics-simulated - see that field's doc comment)
 * Box2D body, and [avatarEntity] tags it [HealthComponent] the same way
 * Phase 10 did for the target character, so [ProjectileContactListener]'s
 * already-generic damage dispatch (no changes needed there) now applies to
 * it too. Also fixes a fresh self-collision problem this exposed - a
 * missile spawning exactly at its own firer's position would otherwise hit
 * (and physically bounce off) that same firer the instant it's created -
 * see [fireMissile]'s `excludeCategory` parameter.
 *
 * **Phase 14 milestone: the AI checks its shot before taking it.**
 * [AiTurnController] no longer just fires blind from a fixed spot -
 * [aiTurnController] now searches nearby angles around the target planet
 * (the same step-budget the player gets) for one with a clear line to the
 * player, and only falls back to firing blind if nothing in range is
 * fully clear. [targetCharacterBody] changes from Static to Kinematic (see
 * [avatarBody]'s doc comment for why that body type) so it can actually
 * move there. See PROJECT_STATE.md's "Phase 14" entry for the full scope.
 *
 * **Phase 15 milestone: the AI's aim is gravity-aware.** [aiTurnController]
 * no longer always fires a straight line at the target - it now sweeps a
 * range of aim angles/speeds and actually simulates each candidate's
 * gravity-curved flight (using the exact math [GravitySystem.applyForces]
 * itself uses, via [GravitySystem.currentSources]/[GravitySystem.G]/
 * [GravitySystem.MIN_DISTANCE]) before picking whichever gets closest to
 * the target. See PROJECT_STATE.md's "Phase 15" entry for the full scope.
 *
 * [DragInputProcessor] is no longer wired up here - nothing in this
 * milestone's scene is tagged [DraggableComponent] anymore, since the demo
 * body it used to drag is gone. The class itself is untouched and stays in
 * the codebase for when character movement needs exactly this drag
 * interaction again.
 *
 * Deliberately NOT disposed on [hide] - hide() is called every time we
 * navigate away, including a temporary pause, and disposing there would
 * destroy the Box2D world we want to resume into. Whoever transitions away
 * from this screen *permanently* (currently: [PauseScreen]'s "end run" tap
 * zone) is responsible for calling [dispose] explicitly first. This is the
 * one place in the whole state machine a native-memory leak could sneak in,
 * so it's worth remembering if this class changes.
 */
class PlayScreen(private val game: PhysicsDuelGame) : Screen {

    companion object {
        private const val WORLD_WIDTH = 9f
        private const val WORLD_HEIGHT = 16f

        // Scene tuning - hand-picked "game feel" numbers, not realistic ones,
        // same spirit as the orbital milestone's STAR_MASS/ORBIT_RADIUS (see
        // GravitySystem's class doc comment). All meant to be re-tuned after
        // real-device testing, not treated as final.
        private const val STAR_RADIUS = 0.5f
        private const val STAR_MASS = 9f
        private const val PLANET_RADIUS = 0.8f
        private const val MISSILE_RADIUS = 0.15f
        private const val LAUNCH_MARKER_RADIUS = 0.2f

        // Phase 10's stand-in target - illustrative numbers, not tuned.
        // 100 HP / 25 damage per hit (see ProjectileContactListener
        // .MISSILE_DAMAGE) means 4 direct hits to defeat it.
        private const val TARGET_RADIUS = 0.3f
        private const val TARGET_MAX_HP = 100

        // Phase 11 - illustrative, not tuned. Deliberately much smaller
        // than STAR_MASS (9f): an ordinary planet should pull noticeably
        // weaker than the star, not compete with it. Paired with
        // ProjectileContactListener.CELESTIAL_MASS_DAMAGE (0.5f) for the
        // same illustrative "4 hits to destroy" as the character target.
        private const val TARGET_PLANET_MASS = 2f

        // Phase 12 - illustrative, not tuned. Purely pacing (long enough
        // that the turn hand-off is visible, not so long it feels sluggish).
        private const val AI_THINK_DELAY_SECONDS = 1f

        // Phase 15 - gravity-aware aim search tuning, illustrative/not
        // tuned. angleSearchDegrees/angleStepDegrees sweep +-120 degrees
        // around the AI's own outward-facing direction (away from its own
        // planet - see AiTurnController.searchAim's doc comment for why
        // it's centered there and not on the straight line to the target)
        // in 8-degree steps (31 angles); speedMultipliers additionally
        // tries each of those at 4 speeds, as fractions of MAX_MISSILE_SPEED
        // (see the AiTurnController constructor call below) - ~124
        // candidate shots total, each simulated forward for up to
        // AI_TRAJECTORY_SIM_MAX_SECONDS at AI_TRAJECTORY_SIM_STEP_SECONDS
        // per step (matching PhysicsSystem.TIME_STEP so the simulated
        // path tracks the real one closely). All comfortably cheap - this
        // runs once per AI turn, well within AI_THINK_DELAY_SECONDS.
        // 120 degrees is a deliberately generous (not exactly computed)
        // margin under the true "still clear of my own planet" limit for
        // this scene's PLANET_RADIUS/LAUNCH_POINT_CLEARANCE (geometrically
        // about +-133 degrees from outward) - covers everything actually
        // launchable without wasting candidates on directions guaranteed
        // to immediately clip the AI's own planet.
        private const val AI_AIM_ANGLE_SEARCH_DEGREES = 120f
        private const val AI_AIM_ANGLE_STEP_DEGREES = 8f
        // On-device bug fix: top end is 1f (full MAX_MISSILE_SPEED), not
        // some multiplier past it - the AI's best-case shot should match a
        // player's max-pull shot, not exceed it. The lower three still give
        // it real slower/more-curving options.
        private val AI_AIM_SPEED_MULTIPLIERS = listOf(0.4f, 0.6f, 0.8f, 1f)
        private const val AI_TRAJECTORY_SIM_MAX_SECONDS = 4f
        private const val AI_TRAJECTORY_SIM_STEP_SECONDS = 1f / 60f

        // AI accuracy pass - illustrative, not tuned. Applied to the
        // search's already-best answer, right before firing (see
        // AiTurnController.applyAimError) - not part of the search
        // itself, so this doesn't affect how SMART the AI's shot choice
        // is, only how perfectly it executes it. +-4 degrees / +-6% speed
        // is deliberately small: enough that two identical setups won't
        // fire pixel-identical shots (Boo, explicit: "too easy to game"),
        // small enough to still read as a deliberate, competent shot.
        private const val AI_AIM_ERROR_DEGREES = 4f
        private const val AI_AIM_ERROR_SPEED_FRACTION = 0.06f

        // Player shot accuracy (Sept 2026 session) - Boo, explicit: the
        // player's own shots should carry the same kind of small
        // imprecision the AI's already do, not a pixel-perfect release.
        // Starts equal to the AI's own tuning for a fair baseline - see
        // SlingshotInputProcessor's "Player shot accuracy" doc paragraph.
        // "Accuracy improves over time" / per-weapon accuracy profiles are
        // a deliberately separate later idea, logged in PROJECT_STATE.md's
        // "Weapon accuracy & ammo types" design note, not this constant.
        private const val PLAYER_AIM_ERROR_DEGREES = AI_AIM_ERROR_DEGREES
        private const val PLAYER_AIM_ERROR_SPEED_FRACTION = AI_AIM_ERROR_SPEED_FRACTION

        // Visual polish, not a new mechanic - see TrailComponent's doc
        // comment. 90 points at one recorded per render frame is ~1.5
        // seconds of trail at 60fps - long enough to show a full arc for
        // most shots without needing to also track elapsed time.
        private const val TRAIL_MAX_POINTS = 90

        // Phase 16 - aim trajectory preview, drawn while the player is
        // pulling back to shoot (see renderAimTrajectoryPreview). Reuses
        // TrajectorySimulator, the same predictor AiTurnController's aim
        // search uses. simStepSeconds matches PhysicsSystem's own tick;
        // dotIntervalSteps only draws every 4th simulated point (a dotted
        // line, not a solid one - visually distinct from the solid orange
        // flight trail an actual in-flight missile leaves, so "this is a
        // projection" never looks like "this already happened").
        private const val AIM_PREVIEW_MAX_SECONDS = 3f
        private const val AIM_PREVIEW_STEP_SECONDS = 1f / 60f
        private const val AIM_PREVIEW_DOT_INTERVAL_STEPS = 4
        private const val AIM_PREVIEW_DOT_RADIUS = 0.05f

        // Phase 13 - illustrative, not tuned. AVATAR_RADIUS reuses
        // LAUNCH_MARKER_RADIUS's value on purpose, so the avatar's actual
        // hitbox matches the size of the cyan marker circle Boo already
        // sees on screen, rather than an invisible mismatch between what's
        // drawn and what's hittable. AVATAR_MAX_HP matches TARGET_MAX_HP -
        // no reason yet for the two sides to be asymmetric.
        private const val AVATAR_RADIUS = LAUNCH_MARKER_RADIUS
        private const val AVATAR_MAX_HP = 100

        // Box2D collision-filter categories - only exist to solve one
        // specific problem (see fireMissile's `excludeCategory` parameter):
        // a missile spawns exactly at its firer's own position, so without
        // this, it would immediately collide with (and physically bounce
        // off) whoever just fired it. Not used for anything else - every
        // other fixture in the scene keeps Box2D's default filter (collides
        // with everything).
        private const val CATEGORY_PLAYER_AVATAR: Short = 0x0002
        private const val CATEGORY_AI_TARGET: Short = 0x0004

        // Phase 20: the star stays fixed dead center of the world every
        // game (Boo, explicit: "the star stays centered") - only the two
        // planets' positions are randomized now, see [randomizePlanetPositions].
        private const val STAR_X = WORLD_WIDTH / 2f
        private const val STAR_Y = 9f

        // Phase 20 planet placement constraints. Boo, explicit: planets can
        // land anywhere for variety (not pinned to "player's always left of
        // the star, AI's always right"), so long as they can't spawn too
        // close to the star or to each other. PLANET_PLACEMENT_MARGIN_X/Y
        // keep a planet's center away from the screen edges (and the fixed
        // corner UI); MIN_PLANET_STAR_SEPARATION keeps a planet clear of the
        // star; MIN_PLANET_SEPARATION (checked pairwise, with a re-roll if
        // violated - see [randomizePlanetPositions]) keeps real empty space
        // between the two planets, not just non-overlap (2 * PLANET_RADIUS =
        // 1.6, so 4f leaves at least 2.4 units of clear space even in the
        // closest allowed roll).
        private const val PLANET_PLACEMENT_MARGIN_X = 1.3f
        private const val PLANET_PLACEMENT_MARGIN_Y = 1.5f
        private const val MIN_PLANET_STAR_SEPARATION = 2.5f
        private const val MIN_PLANET_SEPARATION = 4f
        private const val PLANET_PLACEMENT_MAX_ATTEMPTS = 200

        // On-device bug, first random layout to actually hit it: the two
        // checks above only look at each planet's OWN distance from the
        // star, never whether the straight line *between* the planets
        // passes close to it - so a roll could (and did) put the star
        // almost exactly on the direct path between them. The AI's shot
        // then had no way to reach the target without grazing the star,
        // got dragged in mid-flight, and looked to Boo like "the AI's
        // force is off" when the real problem was the layout leaving no
        // safe shot available at all. The old fixed layout never had this
        // problem - planets were always well below the star, so a direct
        // shot passed 5 world-units clear of it (see the removed
        // LAUNCH_PLANET_X/TARGET_PLANET_X/PLANETS_Y comment). This
        // constant restores a similar (if less extreme, since full 2D
        // variety is the whole point) minimum clearance for the random
        // version - see [randomizePlanetPositions]/[distanceFromSegment].
        private const val MIN_STAR_FLIGHT_PATH_CLEARANCE = 3f

        // How far above the launch planet's surface the fixed launch point
        // sits - needs at least MISSILE_RADIUS of clearance so a freshly
        // spawned missile doesn't immediately overlap the planet's own
        // fixture and register a same-instant "impact".
        private const val LAUNCH_POINT_CLEARANCE = 0.3f

        // Phase 9 movement budget - illustrative numbers from the design
        // conversation (Boo's "say 5 steps" example), not tuned yet.
        // stepAngleDegrees is how far one "step" moves the avatar around
        // the planet's surface; 5 steps * 15 degrees = 75 degrees of arc
        // per movement phase, enough to visibly change the shot angle
        // without letting one phase's budget circle the whole planet.
        private const val MOVEMENT_STEPS_PER_PHASE = 5
        private const val MOVEMENT_STEP_ANGLE_DEGREES = 15f

        // Boo, explicit: give the AI 2 more steps per turn than the player
        // gets, split 1-and-1 across its pre-shot and post-shot movement
        // (see AiTurnController.fire()'s post-shot reposition() call) -
        // since the player's own two phases are already equal (5 and 5),
        // a single +1'd constant naturally gives the AI +1 in both.
        private const val AI_MOVEMENT_STEPS_PER_PHASE = MOVEMENT_STEPS_PER_PHASE + 1

        // Standard math convention (0 degrees = +X/east, 90 = +Y/north) -
        // 90 starts the avatar at the top of the launch planet, roughly
        // facing the target planet to its right.
        private const val AVATAR_START_ANGLE_DEGREES = 90f

        // Phase 14 - the AI's own starting angle around the target planet.
        // Numerically identical to AVATAR_START_ANGLE_DEGREES (both mean
        // "top of the planet, facing the other side") - kept as a separate
        // constant since the two sides are independent and coincidence
        // isn't the same as a shared meaning.
        private const val AI_START_ANGLE_DEGREES = 90f

        // Converts a pull-back drag distance (world units) into launch
        // speed, clamped to MAX_MISSILE_SPEED so a wild drag can't fire an
        // unreasonably fast shot.
        private const val PULL_POWER_SCALE = 4f
        private const val MAX_MISSILE_SPEED = 15f

        // Phase 19c - UI visual pass. Star count deliberately modest -
        // "doesn't overwhelm what we have so far" (Boo, explicit).
        private const val STARFIELD_STAR_COUNT = 70

        // Shared between renderStatsPanel (row height) and drawStatBar
        // (bar position) so they can't drift out of sync with each other
        // the way the old hardcoded 70f/22f pair silently did.
        private const val STATS_BAR_HEIGHT = 10f
        private const val STATS_BAR_GAP_BELOW_TEXT = 8f
        private const val STATS_ROW_GAP = 16f
    }

    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private lateinit var world: World
    private lateinit var debugRenderer: Box2DDebugRenderer
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var engine: Engine
    private lateinit var slingshotInputProcessor: SlingshotInputProcessor
    private lateinit var avatarMovementController: AvatarMovementController
    private lateinit var aiTurnController: AiTurnController
    // Phase 16 - shared between aiTurnController's obstacle list and the
    // player's own aim preview (see renderAimTrajectoryPreview), so both
    // read the same geometry instead of two copies that could drift.
    private lateinit var celestialObstacles: List<AiTurnController.Obstacle>
    private lateinit var trajectorySimulator: TrajectorySimulator

    // Phase 13 - the avatar's own physics body. Kinematic, not dynamic:
    // the avatar moves under AvatarMovementController's direct control
    // (button taps stepping it around the planet), never under physics
    // forces, so Kinematic is the Box2D body type actually meant for
    // "moves via direct position control but still participates in
    // collision detection" - unlike a static body (not meant to move at
    // all, even though Box2D technically allows repositioning one) or a
    // dynamic body (would let forces/collisions push it around, which
    // nothing here should ever do). Position is kept in sync every frame
    // in render(), the same place launchPoint already is.
    private lateinit var avatarBody: Body
    private lateinit var avatarEntity: Entity
    private lateinit var targetCharacterEntity: Entity
    // Phase 14 - Kinematic now, not Static (see avatarBody's doc comment
    // for why Kinematic is the right body type for "moves under direct
    // control, still collidable"), since aiTurnController can now
    // reposition it. Position kept in sync every frame in render(), the
    // same place avatarBody's is.
    private lateinit var targetCharacterBody: Body

    // Built once in show(), swapped between on a turn hand-off (see
    // avatarMovementController's onTurnPassed / aiTurnController's
    // onTurnComplete below) rather than teaching every input class about a
    // "whose turn is it" flag - restrictedInputProcessor omits
    // avatarMovementController and slingshotInputProcessor entirely, so a
    // touch during the AI's turn simply falls through to nothing.
    private lateinit var fullInputProcessor: InputMultiplexer
    private lateinit var restrictedInputProcessor: InputMultiplexer
    private lateinit var projectileContactListener: ProjectileContactListener
    private lateinit var launchPoint: Vector2
    private lateinit var gravitySystem: GravitySystem

    // Phase 20: randomized once per [init] by [randomizePlanetPositions] -
    // replaces the old fixed LAUNCH_PLANET_X/TARGET_PLANET_X/PLANETS_Y
    // constants. Everything that used to reference those now reads these
    // instead (star creation, planet bodies, AI/avatar planetCenter,
    // rendering).
    private lateinit var launchPlanetPosition: Vector2
    private lateinit var targetPlanetPosition: Vector2
    private lateinit var gravityDebugController: GravityDebugController
    // Phase 17 - shotSpeedTuning is the live-adjustable value itself
    // (read by SlingshotInputProcessor, aiTurnController, and the aim
    // preview below); shotSpeedDebugController is the on-screen +/- tool
    // that adjusts it, same split GravitySystem/GravityDebugController use.
    private lateinit var shotSpeedTuning: ShotSpeedTuning
    private lateinit var shotSpeedDebugController: ShotSpeedDebugController

    // Phase 7 HUD: a screen-pixel (not world-unit) camera + batch, separate
    // from [camera]/[viewport] above which stay in Box2D world units for the
    // debug renderer. Queries the ECS each frame rather than holding a direct
    // Body reference. Originally tracked the orbital milestone's orbiting
    // body; now naturally tracks whichever [GravityAffectedComponent] body
    // currently exists instead - Phase 8's in-flight missile, when there is
    // one - with zero changes needed to this rendering code, exactly the
    // "any future HUD reuses this pattern" payoff Phase 7 was built to prove.
    private val hudCamera = OrthographicCamera()
    private val hudBatch = SpriteBatch()
    // Phase 18 - baseline sprite art. worldBatch draws in the same world
    // units/camera as shapeRenderer/debugRenderer (not hudCamera - these
    // are real scene objects, not screen-space HUD). Textures are
    // deliberately generic-placeholder art (procedurally generated, not
    // hand-drawn) - see PROJECT_STATE.md's Phase 18 entry for the plan to
    // swap in real/purchased art later without touching this code, since
    // loading is just a file-name lookup.
    private val worldBatch = SpriteBatch()
    private val starTexture = Texture(Gdx.files.internal("textures/star.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val planetLaunchTexture = Texture(Gdx.files.internal("textures/planet_launch.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val planetTargetTexture = Texture(Gdx.files.internal("textures/planet_target.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    // Phase 21 - a single crater/scorch overlay for the target planet,
    // drawn on top of planetTargetTexture with alpha scaled to how
    // damaged it currently is (see renderCelestialSprites) - a fixed
    // scatter of blotches that fades in as mass is lost, not a per-hit
    // decal at the actual impact point (the game doesn't track individual
    // impact positions, only aggregate mass lost). True shrink-as-mass-
    // drops is a deliberate later follow-up, not this phase - see
    // PROJECT_STATE.md's Phase 21 entry.
    private val damageOverlayTexture = Texture(Gdx.files.internal("textures/damage_overlay.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    // Phase 19 - shaded sphere art for the player avatar (blue) and AI
    // target (red), drawn at their live positions each frame (unlike the
    // Phase 18 star/planets, these move) - see renderCharacterSprites.
    private val avatarPlayerTexture = Texture(Gdx.files.internal("textures/avatar_player.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val avatarAiTexture = Texture(Gdx.files.internal("textures/avatar_ai.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    // Phase 19b - missile sprite art, a soft violet/amethyst chosen to
    // contrast the blue player/red AI spheres and the orange/teal planets
    // without clashing (Boo, explicit: "contrasting color that is not
    // harsh"). Reuses trailFamily below to find every in-flight missile -
    // every missile entity is already TrailComponent-tagged (Phase 15/16),
    // so no new Family/mapper is needed just for this.
    private val missileTexture = Texture(Gdx.files.internal("textures/missile.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    // Phase 19c - UI visual pass: real "game button" art (neutral grey,
    // beveled), a small graphical stats panel, and a muted starfield
    // backdrop. See PROJECT_STATE.md's Phase 19c entry for the full
    // design-language decisions - why NinePatch (clean stretch to any
    // button/panel size without distorting the rounded corners) and why
    // these specific colors.
    private val buttonTexture = Texture(Gdx.files.internal("textures/button.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val buttonPatch = NinePatch(buttonTexture, 16, 16, 16, 16)
    private val panelTexture = Texture(Gdx.files.internal("textures/panel.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    private val panelPatch = NinePatch(panelTexture, 16, 16, 16, 16)
    private val barPillTexture = Texture(Gdx.files.internal("textures/bar_pill.png")).apply {
        setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }
    // top/bottom margin 0 is safe here - drawStatBar always draws this at
    // its native pixel height (see barHeight there), so no vertical
    // stretch actually occurs regardless of what the patch allows.
    private val barPillPatch = NinePatch(barPillTexture, 8, 8, 0, 0)
    private val playerBarColor = Color(0.30f, 0.55f, 0.92f, 1f) // matches avatar_player.png's base hue
    private val aiBarColor = Color(0.85f, 0.25f, 0.25f, 1f) // matches avatar_ai.png's base hue
    private val massBarColor = Color(0.80f, 0.58f, 0.30f, 1f) // distinct from either character color
    private val barTrackColor = Color(0.16f, 0.17f, 0.22f, 1f)

    /** One decorative background star - see [starfieldStars]. */
    private class StarfieldStar(val x: Float, val y: Float, val radius: Float, val color: Color)

    // Phase 19c - generated once (not per-game), fixed for this screen's
    // lifetime. Deliberately muted: brightness is capped well below full
    // white and radii stay small, so this reads as a backdrop rather than
    // competing with the sprites/HUD drawn on top of it.
    private val starfieldStars: List<StarfieldStar> = buildList {
        repeat(STARFIELD_STAR_COUNT) {
            val brightness = 0.30f + Random.nextFloat() * 0.40f
            add(
                StarfieldStar(
                    x = Random.nextFloat() * WORLD_WIDTH,
                    y = Random.nextFloat() * WORLD_HEIGHT,
                    radius = 0.012f + Random.nextFloat() * 0.028f,
                    color = Color(brightness, brightness, (brightness * 1.08f).coerceAtMost(1f), 1f)
                )
            )
        }
    }
    private val physicsBodyMapper = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
    private val healthMapper = ComponentMapper.getFor(HealthComponent::class.java)
    private val gravitySourceMapper = ComponentMapper.getFor(GravitySourceComponent::class.java)
    private val trailMapper = ComponentMapper.getFor(TrailComponent::class.java)
    private val gravityAffectedFamily = Family.all(GravityAffectedComponent::class.java, PhysicsBodyComponent::class.java).get()
    private val trailFamily = Family.all(TrailComponent::class.java, PhysicsBodyComponent::class.java).get()
    // A direct reference, not a family query, because the star also carries
    // GravitySourceComponent now - a family query alone couldn't tell the
    // HUD which one to read. Kept even after the entity is removed from the
    // engine (on destruction) so renderStatsPanel can still read its
    // final mass/isDestroyed state - see that method.
    private lateinit var targetPlanetEntity: Entity

    init {
        Box2D.init()
        randomizePlanetPositions()

        camera = OrthographicCamera()
        viewport = FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera)
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f)

        // Zero, not -9.8: GravitySystem is the only source of gravity - see
        // its class doc comment.
        world = World(Vector2(0f, 0f), true)
        debugRenderer = Box2DDebugRenderer()
        shapeRenderer = ShapeRenderer()

        engine = Engine()
        // GravitySystem is a plain class, not an Ashley system - see its own
        // class doc comment and PhysicsSystem's beforeStep doc comment for
        // why (apsidal precession bug, fixed by applying gravity exactly
        // once per physics tick instead of once per render frame).
        gravitySystem = GravitySystem(engine)
        engine.addSystem(PhysicsSystem(world, beforeStep = gravitySystem::applyForces))

        projectileContactListener = ProjectileContactListener(engine)
        world.setContactListener(projectileContactListener)

        val star = createStar()
        engine.addEntity(
            Entity().apply {
                add(PhysicsBodyComponent(star))
                add(GravitySourceComponent(initialMass = STAR_MASS, isDamageable = false))
            }
        )

        // Launch planet deliberately unchanged since Phase 8 - still a
        // plain non-gravity static Box2D body, not added to the ECS at all
        // (nothing about it needs an Ashley query). Only the target planet
        // below gets Phase 11's mutable-mass/gravity treatment this phase,
        // to keep the number of new gravity sources Boo is feeling out at
        // once to just one - see PROJECT_STATE.md's "Phase 11" entry. Every
        // celestial body is confirmed to eventually exert gravity, this is
        // just an incremental rollout, not a final design line.
        createPlanet(launchPlanetPosition.x, launchPlanetPosition.y)
        targetPlanetEntity = Entity().apply {
            add(PhysicsBodyComponent(createPlanet(targetPlanetPosition.x, targetPlanetPosition.y)))
            add(GravitySourceComponent(initialMass = TARGET_PLANET_MASS))
        }
        engine.addEntity(targetPlanetEntity)

        // Phase 14 - built here (before the target character body) since
        // createTarget() below now seeds the body's position from
        // aiTurnController.position instead of a separately-hardcoded
        // formula. Shared with PlayScreen's own Phase 16 aim preview (see
        // celestialObstacles) - fixed geometry (center + radius), not live
        // Box2D references - see AiTurnController.Obstacle's doc comment;
        // a destroyed target planet (Phase 11) still counts as an obstacle
        // here, a known minor gap, not addressed this phase.
        celestialObstacles = listOf(
            AiTurnController.Obstacle(Vector2(STAR_X, STAR_Y), STAR_RADIUS),
            AiTurnController.Obstacle(Vector2(launchPlanetPosition), PLANET_RADIUS),
            AiTurnController.Obstacle(Vector2(targetPlanetPosition), PLANET_RADIUS)
        )
        trajectorySimulator = TrajectorySimulator(GravitySystem.G, GravitySystem.MIN_DISTANCE)

        aiTurnController = AiTurnController(
            planetCenter = Vector2(targetPlanetPosition),
            planetRadius = PLANET_RADIUS,
            heightAboveSurface = LAUNCH_POINT_CLEARANCE,
            stepsPerPhase = AI_MOVEMENT_STEPS_PER_PHASE,
            stepAngleDegrees = MOVEMENT_STEP_ANGLE_DEGREES,
            startAngleDegrees = AI_START_ANGLE_DEGREES,
            aimSpeed = MAX_MISSILE_SPEED,
            thinkDelaySeconds = AI_THINK_DELAY_SECONDS,
            obstacles = celestialObstacles,
            aimSearch = AiTurnController.AimSearchConfig(
                angleSearchDegrees = AI_AIM_ANGLE_SEARCH_DEGREES,
                angleStepDegrees = AI_AIM_ANGLE_STEP_DEGREES,
                speedMultipliers = AI_AIM_SPEED_MULTIPLIERS,
                simMaxSeconds = AI_TRAJECTORY_SIM_MAX_SECONDS,
                simStepSeconds = AI_TRAJECTORY_SIM_STEP_SECONDS
            ),
            gravitationalConstant = GravitySystem.G,
            gravityMinDistance = GravitySystem.MIN_DISTANCE,
            gravityMultiplier = { gravitySystem.gravityMultiplier },
            gravitySources = { gravitySystem.currentSources() },
            shotSpeedMultiplier = { shotSpeedTuning.multiplier },
            aimErrorDegrees = AI_AIM_ERROR_DEGREES,
            aimErrorSpeedFraction = AI_AIM_ERROR_SPEED_FRACTION,
            onFire = { origin, velocity -> fireMissile(origin, velocity, excludeCategory = CATEGORY_AI_TARGET) },
            onTurnComplete = { Gdx.input.inputProcessor = fullInputProcessor }
        )

        targetCharacterBody = createTarget(aiTurnController.position)
        targetCharacterEntity = Entity().apply {
            add(PhysicsBodyComponent(targetCharacterBody))
            add(HealthComponent(TARGET_MAX_HP))
        }
        engine.addEntity(targetCharacterEntity)

        avatarMovementController = AvatarMovementController(
            planetCenter = Vector2(launchPlanetPosition),
            planetRadius = PLANET_RADIUS,
            heightAboveSurface = LAUNCH_POINT_CLEARANCE,
            stepsPerPhase = MOVEMENT_STEPS_PER_PHASE,
            stepAngleDegrees = MOVEMENT_STEP_ANGLE_DEGREES,
            startAngleDegrees = AVATAR_START_ANGLE_DEGREES,
            onTurnPassed = {
                aiTurnController.startTurn(avatarMovementController.position)
                Gdx.input.inputProcessor = restrictedInputProcessor
            }
        )
        launchPoint = Vector2(avatarMovementController.position)

        avatarBody = createAvatarBody(avatarMovementController.position)
        avatarEntity = Entity().apply {
            add(PhysicsBodyComponent(avatarBody))
            add(HealthComponent(AVATAR_MAX_HP))
        }
        engine.addEntity(avatarEntity)

        shotSpeedTuning = ShotSpeedTuning()

        slingshotInputProcessor = SlingshotInputProcessor(
            launchPoint = launchPoint,
            viewport = viewport,
            powerScale = PULL_POWER_SCALE,
            maxSpeed = MAX_MISSILE_SPEED,
            speedMultiplier = { shotSpeedTuning.multiplier },
            aimErrorDegrees = PLAYER_AIM_ERROR_DEGREES,
            aimErrorSpeedFraction = PLAYER_AIM_ERROR_SPEED_FRACTION,
            onFire = { velocity ->
                if (avatarMovementController.canFire) {
                    fireMissile(launchPoint, velocity, excludeCategory = CATEGORY_PLAYER_AVATAR)
                    avatarMovementController.onFired()
                }
            }
        )
        gravityDebugController = GravityDebugController(gravitySystem)
        shotSpeedDebugController = ShotSpeedDebugController(shotSpeedTuning)
    }

    override fun show() {
        fullInputProcessor = InputMultiplexer().apply {
            addProcessor(BackKeyHandler())
            addProcessor(gravityDebugController)
            addProcessor(shotSpeedDebugController)
            addProcessor(avatarMovementController)
            addProcessor(slingshotInputProcessor)
        }
        // Deliberately still includes BackKeyHandler (pausing should always
        // work) and gravityDebugController/shotSpeedDebugController (both
        // standing debug tools, not something turn structure should ever
        // lock out) - only the player's own movement/aiming input is left
        // out during the AI's turn.
        restrictedInputProcessor = InputMultiplexer().apply {
            addProcessor(BackKeyHandler())
            addProcessor(gravityDebugController)
            addProcessor(shotSpeedDebugController)
        }
        Gdx.input.inputProcessor = fullInputProcessor
        Gdx.input.setCatchKey(Input.Keys.BACK, true) // otherwise Android treats Back as "quit app"
        resizeHudCamera()
    }

    private fun resizeHudCamera() {
        hudCamera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    }

    private inner class BackKeyHandler : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            if (keycode == Input.Keys.BACK) {
                game.setScreen(PauseScreen(game, playScreen = this@PlayScreen))
                return true
            }
            return false
        }
    }

    /**
     * The gravity well: a static body - static so it never itself gets
     * pulled around, exactly like a real star is many orders of magnitude
     * heavier than anything nearby. [GravitySourceComponent] (not this
     * body's Box2D mass, which is zero for any static body) is what
     * [GravitySystem] actually reads.
     */
    private fun createStar(): Body {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.StaticBody
            position.set(STAR_X, STAR_Y)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = STAR_RADIUS }
        body.createFixture(shape, 0f) // density is meaningless on a static body - mass comes from GravitySourceComponent instead
        shape.dispose() // shapes are native-backed; always dispose after the fixture is built
        return body
    }

    /**
     * Phase 20: picks fresh [launchPlanetPosition]/[targetPlanetPosition]
     * for this game. Boo, explicit: planets can land anywhere for variety
     * - not pinned to opposite sides of the star - so each is drawn
     * independently from anywhere in the margin-inset play area (see
     * [randomPlanetPosition], which already keeps a single planet clear of
     * the star). The only extra rule enforced here is pairwise: if the
     * second draw happens to land too close to the first
     * ([MIN_PLANET_SEPARATION]), it's simply re-rolled (itself still
     * subject to the same star-clearance rule) until it isn't, up to
     * [PLANET_PLACEMENT_MAX_ATTEMPTS] tries - given how much of the play
     * area satisfies both rules at once, this is expected to succeed on
     * the first or second attempt almost always.
     */
    private fun randomizePlanetPositions() {
        launchPlanetPosition = randomPlanetPosition()
        targetPlanetPosition = randomPlanetPosition()
        var attempts = 0
        while (!planetLayoutIsClear() && attempts < PLANET_PLACEMENT_MAX_ATTEMPTS) {
            targetPlanetPosition = randomPlanetPosition()
            attempts++
        }
    }

    /**
     * True once the current [launchPlanetPosition]/[targetPlanetPosition]
     * pair satisfies both layout rules: the planets aren't too close to
     * each other, and - the bug this method was added to fix - the star
     * isn't sitting too close to the direct path between them (checked
     * against the actual line *segment*, via [distanceFromSegment], not
     * the infinite line - the star being far off to the side of where the
     * segment happens to extend to doesn't count as "in the way").
     */
    private fun planetLayoutIsClear(): Boolean {
        if (targetPlanetPosition.dst(launchPlanetPosition) < MIN_PLANET_SEPARATION) return false
        val starDistanceFromPath = distanceFromSegment(
            Vector2(STAR_X, STAR_Y), launchPlanetPosition, targetPlanetPosition
        )
        return starDistanceFromPath >= MIN_STAR_FLIGHT_PATH_CLEARANCE
    }

    /** Shortest distance from [point] to the finite line segment [a]-[b] (not the infinite line each defines). */
    private fun distanceFromSegment(point: Vector2, a: Vector2, b: Vector2): Float {
        val segmentX = b.x - a.x
        val segmentY = b.y - a.y
        val lengthSquared = segmentX * segmentX + segmentY * segmentY
        if (lengthSquared <= 0.0001f) return point.dst(a)
        val t = (((point.x - a.x) * segmentX + (point.y - a.y) * segmentY) / lengthSquared).coerceIn(0f, 1f)
        return point.dst(a.x + t * segmentX, a.y + t * segmentY)
    }

    /**
     * One random point for a single planet: anywhere in the play area
     * inset by [PLANET_PLACEMENT_MARGIN_X]/[PLANET_PLACEMENT_MARGIN_Y] from
     * the screen edges, re-rolled until it's at least
     * [MIN_PLANET_STAR_SEPARATION] from the star - the star's fixed
     * position means this alone is enough to guarantee no planet ever
     * spawns overlapping or awkwardly close to it, independent of the
     * pairwise planet-to-planet check [randomizePlanetPositions] does on
     * top of this.
     */
    private fun randomPlanetPosition(): Vector2 {
        repeat(PLANET_PLACEMENT_MAX_ATTEMPTS) {
            val candidate = Vector2(
                MathUtils.random(PLANET_PLACEMENT_MARGIN_X, WORLD_WIDTH - PLANET_PLACEMENT_MARGIN_X),
                MathUtils.random(PLANET_PLACEMENT_MARGIN_Y, WORLD_HEIGHT - PLANET_PLACEMENT_MARGIN_Y)
            )
            if (candidate.dst(STAR_X, STAR_Y) >= MIN_PLANET_STAR_SEPARATION) return candidate
        }
        // Pathological fallback - shouldn't be reachable given the margins/
        // separations above leave most of the play area valid, but returns
        // something sane rather than crashing if it ever is.
        return Vector2(PLANET_PLACEMENT_MARGIN_X, PLANET_PLACEMENT_MARGIN_Y)
    }

    /**
     * One of the two planets - purely a static surface to aim from/hit,
     * no gravity pull yet (see the class doc comment's Phase 8 scope note).
     */
    private fun createPlanet(x: Float, y: Float): Body {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.StaticBody
            position.set(x, y)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = PLANET_RADIUS }
        body.createFixture(shape, 0f)
        shape.dispose()
        return body
    }

    /**
     * The AI's own body - tagged [HealthComponent] by the caller so
     * [ProjectileContactListener] can damage it on a direct hit, same
     * mechanic Phase 10 introduced this stand-in target for. [Kinematic],
     * not Static, since Phase 14 lets [aiTurnController] reposition it -
     * see [avatarBody]'s doc comment for why Kinematic is the right body
     * type for "moves under direct control, still collidable". [render]
     * keeps its position synced to [aiTurnController]'s logical position
     * every frame after [initialPosition] seeds it.
     */
    private fun createTarget(initialPosition: Vector2): Body {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.KinematicBody
            position.set(initialPosition)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = TARGET_RADIUS }
        // Phase 13: tagged CATEGORY_AI_TARGET so the AI's own missile (which
        // spawns from aiTurnController.position, wherever that currently is)
        // can exclude colliding with it - see fireMissile's excludeCategory
        // parameter.
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            filter.categoryBits = CATEGORY_AI_TARGET
        }
        body.createFixture(fixtureDef)
        shape.dispose()
        return body
    }

    /**
     * Phase 13's avatar body - see [avatarBody]'s field doc comment for why
     * Kinematic. [initialPosition] seeds where it starts; [render] keeps it
     * in sync with [AvatarMovementController]'s logical position every
     * frame after that. Tagged [CATEGORY_PLAYER_AVATAR] so the player's own
     * missile (which spawns exactly here - see [launchPoint]) can exclude
     * colliding with it - see [fireMissile]'s `excludeCategory` parameter.
     */
    private fun createAvatarBody(initialPosition: Vector2): Body {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.KinematicBody
            position.set(initialPosition)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = AVATAR_RADIUS }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            filter.categoryBits = CATEGORY_PLAYER_AVATAR
        }
        body.createFixture(fixtureDef)
        shape.dispose()
        return body
    }

    /**
     * Fires one missile from [origin] with the given velocity (already
     * computed by whichever caller is firing - [SlingshotInputProcessor]
     * for the player, [AiTurnController] for the AI - this function just
     * spawns the body/entity, it doesn't know about pull vectors, power
     * scaling, or whose turn it is). [origin] used to be hardcoded to
     * [launchPoint] - a real Phase 12 bug, since that meant the AI's shots
     * spawned from the player's own position instead of the AI's.
     * Tagged [GravityAffectedComponent] so [GravitySystem] curves its
     * flight, and [ProjectileComponent] so [ProjectileContactListener]
     * knows to remove it on impact instead of leaving it as a permanent
     * scene body.
     *
     * [excludeCategory] (Phase 13) solves a problem [origin] otherwise
     * causes on its own: a missile spawns exactly at its firer's position,
     * so without this, it would immediately - physically, not just in game
     * logic - collide with (and bounce off) whoever just fired it, the
     * instant it's created. Passing the firer's own collision category
     * here (see the CATEGORY_ constants) excludes just that one pairing;
     * everything else the missile can hit still collides normally. `0`
     * (the default) excludes nothing, for anything fired from a spot with
     * no fixture of its own to worry about colliding with.
     */
    private fun fireMissile(origin: Vector2, velocity: Vector2, excludeCategory: Short = 0) {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(origin)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = MISSILE_RADIUS }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            density = 1f
            friction = 0.4f
            restitution = 0.2f
            if (excludeCategory != 0.toShort()) {
                filter.maskBits = (0xFFFF.toInt() and excludeCategory.toInt().inv()).toShort()
            }
        }
        body.createFixture(fixtureDef)
        shape.dispose()
        body.linearVelocity = velocity

        engine.addEntity(
            Entity().apply {
                add(PhysicsBodyComponent(body))
                add(GravityAffectedComponent())
                add(ProjectileComponent())
                add(TrailComponent(TRAIL_MAX_POINTS))
            }
        )
    }

    override fun render(delta: Float) {
        // Phase 9: the avatar can move between frames (movement-button taps
        // handled by avatarMovementController), so launchPoint - a shared
        // Vector2 instance SlingshotInputProcessor and the debug overlay
        // both already hold a reference to - is refreshed here every frame
        // rather than being fixed once at construction like Phase 8's was.
        launchPoint.set(avatarMovementController.position)
        avatarBody.setTransform(avatarMovementController.position, 0f)
        // Phase 14 - aiTurnController.position can change the instant
        // startTurn() runs (see its reposition()), so this needs to be
        // synced every frame same as avatarBody just above, not only once.
        targetCharacterBody.setTransform(aiTurnController.position, 0f)
        aiTurnController.update(delta)

        engine.update(delta) // drives PhysicsSystem, which owns the fixed-timestep accumulator
        // Only safe to call after world.step() has fully returned for this
        // frame (see ProjectileContactListener's class doc comment) -
        // engine.update above is exactly that point, since PhysicsSystem's
        // step loop is synchronous.
        projectileContactListener.flushRemovals(world)

        for (entity in engine.getEntitiesFor(trailFamily)) {
            trailMapper.get(entity).recordPosition(physicsBodyMapper.get(entity).body.position)
        }

        Gdx.gl.glClearColor(0.043f, 0.071f, 0.126f, 1f) // deep space navy
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // World-space rendering happens inside whatever rectangle [viewport]
        // (a FitViewport locked to WORLD_WIDTH:WORLD_HEIGHT) currently
        // occupies on screen - on a device whose physical aspect ratio is
        // far from that ratio (a foldable's main screen opened flat, far
        // squarer than 9:16, is the case that exposed this), FitViewport
        // letterboxes: it shrinks/centers its GL viewport rather than using
        // the full screen. Explicitly re-applying it here guarantees that
        // rectangle is what's active for world content specifically, no
        // matter what the HUD rendering below last left the GL viewport set
        // to.
        viewport.apply()
        camera.update()
        renderStarfield()
        renderCelestialSprites()
        renderCharacterSprites()
        renderMissileSprites()
        debugRenderer.render(world, camera.combined)
        renderDebugOverlay()
        renderAimTrajectoryPreview()
        renderProjectileTrails()

        // Bug found on-device (Fold 8, unfolded/landscape-wide screen):
        // every HUD element (buttons, text) is drawn via [hudCamera], whose
        // own projection matrix correctly spans the full screen in pixel
        // units - but OpenGL's viewport rectangle (which is what actually
        // maps that projection's NDC output to real screen pixels) was
        // still left set to [viewport]'s letterboxed, narrower-than-full-
        // screen rectangle from the world rendering just above. The result:
        // every HUD element rendered compressed into that narrower strip,
        // visually offset from where touch input (reported in true,
        // un-letterboxed full-screen coordinates by Android) expects it -
        // exactly the "buttons don't register where they're drawn"
        // behavior reported. Resetting the GL viewport to the full screen
        // here, right before any HUD drawing, fixes it.
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        renderHud()
        renderGravityDebugControls()
        renderShotSpeedDebugControls()
        renderMovementControls()
        renderStatsPanel()
    }

    /**
     * Phase 19c - a muted decorative starfield behind everything else.
     * Drawn in world space (same camera as every other world-space
     * element) before any sprite, so it always sits at the very back.
     * See [starfieldStars] for why it's generated once and kept
     * deliberately dim/small rather than regenerated or made brighter.
     */
    private fun renderStarfield() {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (star in starfieldStars) {
            shapeRenderer.color = star.color
            shapeRenderer.circle(star.x, star.y, star.radius, 8)
        }
        shapeRenderer.end()
    }

    /**
     * Phase 18 - baseline sprite art for the star and the two planets,
     * drawn in world space before [debugRenderer] so its wireframe
     * outlines still overlay each sprite - lets on-device testing
     * directly confirm each sprite lines up with its real Box2D fixture
     * (same center, same diameter) rather than trusting it by eye alone.
     * Deliberately doesn't touch the avatar, AI target, or missiles yet -
     * those stay debug markers/wireframes for this pass, see
     * PROJECT_STATE.md's Phase 18 entry for the narrow scope and why.
     */
    private fun renderCelestialSprites() {
        worldBatch.projectionMatrix = camera.combined
        worldBatch.begin()
        val starDiameter = STAR_RADIUS * 2f
        worldBatch.draw(starTexture, STAR_X - STAR_RADIUS, STAR_Y - STAR_RADIUS, starDiameter, starDiameter)
        val planetDiameter = PLANET_RADIUS * 2f
        worldBatch.draw(planetLaunchTexture, launchPlanetPosition.x - PLANET_RADIUS, launchPlanetPosition.y - PLANET_RADIUS, planetDiameter, planetDiameter)
        worldBatch.draw(planetTargetTexture, targetPlanetPosition.x - PLANET_RADIUS, targetPlanetPosition.y - PLANET_RADIUS, planetDiameter, planetDiameter)

        // Phase 21 - fade the damage overlay in as the target planet loses
        // mass (0 = pristine/invisible, 1 = fully destroyed/fully visible).
        // Reads straight from the same GravitySourceComponent the stats
        // panel already uses, via the same "entity may have been removed
        // from the engine but its component data is still readable" trick
        // renderStatsPanel relies on - see targetPlanetEntity's own doc
        // comment for why that's safe.
        val targetSource = gravitySourceMapper.get(targetPlanetEntity)
        if (targetSource != null) {
            val damageRatio = (1f - targetSource.mass / targetSource.initialMass).coerceIn(0f, 1f)
            if (damageRatio > 0f) {
                worldBatch.setColor(1f, 1f, 1f, damageRatio)
                worldBatch.draw(damageOverlayTexture, targetPlanetPosition.x - PLANET_RADIUS, targetPlanetPosition.y - PLANET_RADIUS, planetDiameter, planetDiameter)
                worldBatch.setColor(1f, 1f, 1f, 1f)
            }
        }
        worldBatch.end()
    }

    /**
     * Phase 19 - shaded sphere art for the player avatar and AI target,
     * drawn at their live logical positions each frame (avatarMovementController
     * .position / aiTurnController.position - the same source of truth
     * [render] already uses to sync avatarBody/targetCharacterBody's real
     * Box2D transforms). Sized to each one's own real fixture diameter
     * (AVATAR_RADIUS vs TARGET_RADIUS - deliberately different sizes,
     * unchanged from Phase 10/13), same wireframe-stays-on-top approach as
     * Phase 18 for this pass.
     */
    private fun renderCharacterSprites() {
        worldBatch.projectionMatrix = camera.combined
        worldBatch.begin()
        val avatarDiameter = AVATAR_RADIUS * 2f
        val avatarPos = avatarMovementController.position
        worldBatch.draw(avatarPlayerTexture, avatarPos.x - AVATAR_RADIUS, avatarPos.y - AVATAR_RADIUS, avatarDiameter, avatarDiameter)
        val aiDiameter = TARGET_RADIUS * 2f
        val aiPos = aiTurnController.position
        worldBatch.draw(avatarAiTexture, aiPos.x - TARGET_RADIUS, aiPos.y - TARGET_RADIUS, aiDiameter, aiDiameter)
        worldBatch.end()
    }

    /**
     * Phase 19b - missile sprite art. Iterates [trailFamily] (every
     * in-flight missile is already TrailComponent-tagged, see
     * fireMissile) rather than a dedicated projectile family/mapper -
     * one less thing to keep in sync. Drawn every frame at each missile's
     * real, current Box2D position (physicsBodyMapper.get(entity).body
     * .position) - not a cached/interpolated value - same live-position
     * approach as renderCharacterSprites.
     */
    private fun renderMissileSprites() {
        worldBatch.projectionMatrix = camera.combined
        worldBatch.begin()
        val missileDiameter = MISSILE_RADIUS * 2f
        for (entity in engine.getEntitiesFor(trailFamily)) {
            val position = physicsBodyMapper.get(entity).body.position
            worldBatch.draw(missileTexture, position.x - MISSILE_RADIUS, position.y - MISSILE_RADIUS, missileDiameter, missileDiameter)
        }
        worldBatch.end()
    }

    /**
     * A launch-point marker (always visible - there's no real avatar entity
     * yet for the debug renderer to draw, so without this there'd be no
     * visual cue at all for where to touch to start aiming) plus the live
     * pull line while aiming. [Box2DDebugRenderer] only knows how to draw
     * physics bodies/joints, not arbitrary shapes, so this uses
     * [ShapeRenderer] directly. Purely a Phase 8 testing aid, not meant to
     * be the final aiming UI.
     */
    private fun renderDebugOverlay() {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.CYAN
        shapeRenderer.circle(launchPoint.x, launchPoint.y, LAUNCH_MARKER_RADIUS, 16)
        slingshotInputProcessor.currentAimLine?.let { pull ->
            shapeRenderer.color = Color.YELLOW
            val dragPoint = Vector2(launchPoint).add(pull)
            shapeRenderer.line(launchPoint, dragPoint)
        }
        shapeRenderer.end()
    }

    /**
     * Phase 16 - while the player is actively pulling back to aim (see
     * [SlingshotInputProcessor.currentAimLine]), predicts and draws the
     * shot's actual gravity-curved path *before* release, using the exact
     * same [TrajectorySimulator] [aiTurnController]'s own aim search uses
     * and the exact velocity formula [SlingshotInputProcessor.touchUp]
     * would fire (duplicated here deliberately - `onFire` only runs once
     * released, so there's no live velocity to read mid-drag). Drawn as
     * dots, not a solid line, so it never looks like [renderProjectileTrails]'s
     * "this already happened" trail - this is only a projection, and stops
     * early (per [celestialObstacles]) if the predicted path would hit a
     * planet or the star before the preview window runs out.
     */
    private fun renderAimTrajectoryPreview() {
        val pull = slingshotInputProcessor.currentAimLine ?: return
        if (!avatarMovementController.canFire) return
        if (pull.isZero(0.01f)) return

        val speedTuning = shotSpeedTuning.multiplier
        val speed = minOf(pull.len() * PULL_POWER_SCALE, MAX_MISSILE_SPEED) * speedTuning
        val velocity = Vector2(pull).nor().scl(-speed) // opposite the drag direction - see SlingshotInputProcessor.touchUp

        val sources = gravitySystem.currentSources()
        val multiplier = gravitySystem.gravityMultiplier
        val position = Vector2(launchPoint)
        val currentVelocity = Vector2(velocity)
        // Inverse of speedTuning - see AiTurnController.searchAim's
        // effectiveSimMaxSeconds for the same reasoning, applied here to
        // the preview instead of the AI's aim search.
        val effectiveMaxSeconds = AIM_PREVIEW_MAX_SECONDS / speedTuning

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color.LIGHT_GRAY

        var elapsed = 0f
        var stepIndex = 0
        while (elapsed < effectiveMaxSeconds) {
            trajectorySimulator.step(position, currentVelocity, AIM_PREVIEW_STEP_SECONDS, sources, multiplier)
            elapsed += AIM_PREVIEW_STEP_SECONDS
            stepIndex++

            var blocked = false
            for (obstacle in celestialObstacles) {
                if (position.dst(obstacle.center) <= obstacle.radius) {
                    blocked = true
                    break
                }
            }
            if (blocked) break

            if (stepIndex % AIM_PREVIEW_DOT_INTERVAL_STEPS == 0) {
                shapeRenderer.circle(position.x, position.y, AIM_PREVIEW_DOT_RADIUS, 8)
            }
        }
        shapeRenderer.end()
    }

    /**
     * Draws each live projectile's recorded [TrailComponent] history as a
     * solid line - purely visual (see that component's doc comment). Added
     * directly in response to Boo's Phase 15 feedback: even with the AI's
     * new gravity-aware aim search actually choosing curved shots, the
     * curve itself was too subtle to see at a glance without a drawn
     * trail to compare against a straight line.
     */
    private fun renderProjectileTrails() {
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.ORANGE
        for (entity in engine.getEntitiesFor(trailFamily)) {
            val points = trailMapper.get(entity).points
            for (i in 0 until points.size - 1) {
                shapeRenderer.line(points[i], points[i + 1])
            }
        }
        shapeRenderer.end()
    }

    /**
     * Live-updating overlay showing the Y position of whichever
     * [GravityAffectedComponent] body currently exists - see this class's
     * field doc comment for why that's now Phase 8's in-flight missile
     * (when there is one) instead of the retired orbiting demo body, with
     * no code changes needed here.
     */
    private fun renderHud() {
        val trackedBody = engine.getEntitiesFor(gravityAffectedFamily).firstOrNull()?.let {
            physicsBodyMapper.get(it).body
        }
        hudCamera.update()
        hudBatch.projectionMatrix = hudCamera.combined
        hudBatch.begin()
        val text = trackedBody?.let { "Missile Y: %.2f".format(it.position.y) } ?: ""
        val margin = HudFont.scaled(16f) // density-scaled, not a fixed pixel count - see HudFont
        HudFont.font.draw(hudBatch, text, margin, Gdx.graphics.height - margin)
        hudBatch.end()
    }

    /**
     * Draws [GravityDebugController]'s two tap zones and the current
     * multiplier value, top-right corner - screen-space, same as [renderHud].
     * Debug-only tuning UI (see that class's doc comment), not final art.
     */
    private fun renderGravityDebugControls() {
        val minusRect = gravityDebugController.minusButtonRect
        val plusRect = gravityDebugController.plusButtonRect

        hudBatch.projectionMatrix = hudCamera.combined
        hudBatch.begin()
        buttonPatch.draw(hudBatch, minusRect.x, minusRect.y, minusRect.width, minusRect.height)
        buttonPatch.draw(hudBatch, plusRect.x, plusRect.y, plusRect.width, plusRect.height)
        val minusLabel = "-"
        HudFont.font.draw(
            hudBatch, minusLabel,
            minusRect.x + (minusRect.width - HudFont.widthOf(minusLabel)) / 2f,
            minusRect.y + minusRect.height * 0.65f
        )
        val plusLabel = "+"
        HudFont.font.draw(
            hudBatch, plusLabel,
            plusRect.x + (plusRect.width - HudFont.widthOf(plusLabel)) / 2f,
            plusRect.y + plusRect.height * 0.65f
        )
        val multiplierLabel = "Gravity x%.1f".format(gravitySystem.gravityMultiplier)
        HudFont.font.draw(
            hudBatch, multiplierLabel,
            plusRect.x + plusRect.width - HudFont.widthOf(multiplierLabel),
            gravityDebugController.labelBaselineY
        )
        hudBatch.end()
    }

    /**
     * Phase 17 - draws [ShotSpeedDebugController]'s two tap zones and the
     * current multiplier value, directly below [renderGravityDebugControls]'s
     * row (same right-edge alignment). Debug-only tuning UI, identical
     * structure to that method - see [ShotSpeedDebugController]'s doc
     * comment for why this exists.
     */
    private fun renderShotSpeedDebugControls() {
        val minusRect = shotSpeedDebugController.minusButtonRect
        val plusRect = shotSpeedDebugController.plusButtonRect

        hudBatch.projectionMatrix = hudCamera.combined
        hudBatch.begin()
        buttonPatch.draw(hudBatch, minusRect.x, minusRect.y, minusRect.width, minusRect.height)
        buttonPatch.draw(hudBatch, plusRect.x, plusRect.y, plusRect.width, plusRect.height)
        val minusLabel = "-"
        HudFont.font.draw(
            hudBatch, minusLabel,
            minusRect.x + (minusRect.width - HudFont.widthOf(minusLabel)) / 2f,
            minusRect.y + minusRect.height * 0.65f
        )
        val plusLabel = "+"
        HudFont.font.draw(
            hudBatch, plusLabel,
            plusRect.x + (plusRect.width - HudFont.widthOf(plusLabel)) / 2f,
            plusRect.y + plusRect.height * 0.65f
        )
        val multiplierLabel = "Shot Speed x%.1f".format(shotSpeedTuning.multiplier)
        HudFont.font.draw(
            hudBatch, multiplierLabel,
            plusRect.x + plusRect.width - HudFont.widthOf(multiplierLabel),
            shotSpeedDebugController.labelBaselineY
        )
        hudBatch.end()
    }

    /**
     * Draws [AvatarMovementController]'s move buttons (always) and its pass
     * button (only during [AvatarMovementController.Phase.POST_SHOT], since
     * tapping it does nothing outside that phase - see that class's
     * touchDown), bottom corners, plus a turn/phase/steps-remaining readout
     * above [renderHud]'s "Missile Y" line. Debug-grade UI, same spirit as
     * [renderGravityDebugControls] - not final art.
     */
    private fun renderMovementControls() {
        val leftRect = avatarMovementController.leftButtonRect
        val rightRect = avatarMovementController.rightButtonRect
        val showPassButton = avatarMovementController.phase == AvatarMovementController.Phase.POST_SHOT

        hudBatch.projectionMatrix = hudCamera.combined
        hudBatch.begin()
        buttonPatch.draw(hudBatch, leftRect.x, leftRect.y, leftRect.width, leftRect.height)
        buttonPatch.draw(hudBatch, rightRect.x, rightRect.y, rightRect.width, rightRect.height)
        if (showPassButton) {
            val passRect = avatarMovementController.passButtonRect
            buttonPatch.draw(hudBatch, passRect.x, passRect.y, passRect.width, passRect.height)
        }
        drawCenteredLabel("<", leftRect)
        drawCenteredLabel(">", rightRect)
        if (showPassButton) {
            drawCenteredLabel("Pass", avatarMovementController.passButtonRect)
        }
        hudBatch.end()
    }

    /** Centers [label] inside [rect] - shared by every button label this screen draws. */
    private fun drawCenteredLabel(label: String, rect: Rectangle) {
        HudFont.font.draw(
            hudBatch, label,
            rect.x + (rect.width - HudFont.widthOf(label)) / 2f,
            rect.y + rect.height * 0.65f
        )
    }

    /**
     * Phase 19c - a single small graphical HUD panel (turn/phase, player
     * HP, target HP, target planet mass), replacing the three separate
     * plain-text lines those used to be (renderTargetHud/
     * renderTargetPlanetHud/renderPlayerHud) plus renderMovementControls'
     * old turn-line text. Boo, explicit: "make it a small HUD that
     * graphically matches the rest of the design language so far" -
     * reuses [panelPatch] (same rounded-rect/bordered look as the new
     * button art) as a backdrop, and [barPillPatch] tinted per-stat
     * ([playerBarColor]/[aiBarColor] matching the Phase 19 avatar sprite
     * colors, [massBarColor] distinct from either character) for the
     * three numeric bars. Positioned just below [renderHud]'s "Missile Y"
     * debug line, which is left alone - that one's pure debug info, not
     * part of what Boo asked to redesign here.
     */
    private fun renderStatsPanel() {
        // Content is measured BEFORE anything is drawn, and the panel is
        // sized to whatever that content actually needs - a third bug
        // from the same on-device round as the two above: panelWidth used
        // to be another guessed constant (320f) that didn't account for
        // how long the turn/phase text can actually get ("Turn 12 -
        // Post-shot: 5 left" is much wider than "Turn 1 - Pre-shot: 0
        // left"), so text routinely ran past the panel's right edge
        // instead of wrapping or the panel just being wide enough. Same
        // underlying lesson as the row-height/bar-position fixes above:
        // measure the real content instead of hardcoding a pixel guess.
        val margin = HudFont.scaled(16f)
        val panelPadding = HudFont.scaled(12f)
        val labelValueGap = HudFont.scaled(16f)
        val minContentWidth = HudFont.scaled(220f)

        val turnLabel = if (aiTurnController.isTurnActive) {
            "Turn ${avatarMovementController.turnNumber} - AI's turn..."
        } else {
            val phaseLabel = if (avatarMovementController.phase == AvatarMovementController.Phase.PRE_SHOT) "Pre-shot" else "Post-shot"
            "Turn %d - %s: %d left".format(
                avatarMovementController.turnNumber, phaseLabel, avatarMovementController.stepsRemaining
            )
        }

        val playerHealth = healthMapper.get(avatarEntity)
        val playerLabel = if (playerHealth.isDefeated) "Player: DEFEATED" else "Player HP"
        val playerValue = "%d/%d".format(playerHealth.currentHp, playerHealth.maxHp)

        val targetHealth = healthMapper.get(targetCharacterEntity)
        val targetLabel = if (targetHealth.isDefeated) "Target: DEFEATED" else "Target HP"
        val targetValue = "%d/%d".format(targetHealth.currentHp, targetHealth.maxHp)

        // Ratio against GravitySourceComponent.initialMass (Phase 19c also
        // adds that property) rather than an arbitrary scale - see
        // Components.kt.
        val targetSource = gravitySourceMapper.get(targetPlanetEntity)
        val massLabel = if (targetSource.isDestroyed) "Target Planet: DESTROYED" else "Target Planet Mass"
        val massValue = "%.1f".format(targetSource.mass)

        val contentWidth = maxOf(
            minContentWidth,
            HudFont.widthOf(turnLabel),
            HudFont.widthOf(playerLabel) + labelValueGap + HudFont.widthOf(playerValue),
            HudFont.widthOf(targetLabel) + labelValueGap + HudFont.widthOf(targetValue),
            HudFont.widthOf(massLabel) + labelValueGap + HudFont.widthOf(massValue)
        )
        val panelWidth = contentWidth + panelPadding * 2f

        // Derived from the font's own real metrics instead of a guessed
        // pixel count - see the doc comment on the companion object's
        // STATS_* constants and PROJECT_STATE.md's Phase 19c entry.
        val rowHeight = HudFont.font.lineHeight + HudFont.scaled(STATS_BAR_GAP_BELOW_TEXT) + HudFont.scaled(STATS_BAR_HEIGHT) + HudFont.scaled(STATS_ROW_GAP)
        val panelHeight = panelPadding * 2f + rowHeight * 4
        val panelTop = Gdx.graphics.height - HudFont.scaled(56f)
        val panelX = margin
        val panelY = panelTop - panelHeight

        hudBatch.projectionMatrix = hudCamera.combined
        hudBatch.begin()
        panelPatch.draw(hudBatch, panelX, panelY, panelWidth, panelHeight)

        val contentX = panelX + panelPadding
        var rowTop = panelY + panelHeight - panelPadding

        HudFont.font.draw(hudBatch, turnLabel, contentX, rowTop)
        rowTop -= rowHeight

        drawStatBar(contentX, rowTop, contentWidth, playerLabel, playerValue, playerHealth.currentHp.toFloat() / playerHealth.maxHp.toFloat(), playerBarColor)
        rowTop -= rowHeight

        drawStatBar(contentX, rowTop, contentWidth, targetLabel, targetValue, targetHealth.currentHp.toFloat() / targetHealth.maxHp.toFloat(), aiBarColor)
        rowTop -= rowHeight

        drawStatBar(contentX, rowTop, contentWidth, massLabel, massValue, targetSource.mass / targetSource.initialMass, massBarColor)

        hudBatch.end()
    }

    /**
     * One stat row for [renderStatsPanel]: a label/value text line with a
     * small bar graphic underneath showing [ratio] (clamped to 0..1 here,
     * even though HP/mass never actually go negative) filled in
     * [fillColor] over a dark track. [barPillPatch] is shared across every
     * row and both track/fill - NinePatch bakes its tint into its own
     * vertex colors at [NinePatch.setColor] time (not the batch's current
     * color), so it has to be set immediately before each draw call, not
     * once up front.
     */
    private fun drawStatBar(x: Float, rowTop: Float, width: Float, label: String, valueText: String, ratio: Float, fillColor: Color) {
        HudFont.font.draw(hudBatch, label, x, rowTop)
        val valueWidth = HudFont.widthOf(valueText)
        HudFont.font.draw(hudBatch, valueText, x + width - valueWidth, rowTop)

        // rowTop is the TOP of the text (BitmapFont.draw's y convention),
        // so the text's own rendered height (font.lineHeight) has to be
        // cleared before the bar starts, or the bar draws through the
        // middle of the glyphs instead of below them - exactly what
        // happened with the old hardcoded 22f offset.
        val barHeight = HudFont.scaled(STATS_BAR_HEIGHT)
        val barY = rowTop - HudFont.font.lineHeight - HudFont.scaled(STATS_BAR_GAP_BELOW_TEXT)
        val clampedRatio = ratio.coerceIn(0f, 1f)

        barPillPatch.setColor(barTrackColor)
        barPillPatch.draw(hudBatch, x, barY, width, barHeight)

        if (clampedRatio > 0.02f) {
            barPillPatch.setColor(fillColor)
            barPillPatch.draw(hudBatch, x, barY, width * clampedRatio, barHeight)
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        resizeHudCamera()
    }

    override fun pause() {}
    override fun resume() {}

    override fun hide() {
        // Called on every navigation away from this screen, including a
        // temporary pause - see the class doc comment. Do NOT dispose here.
        Gdx.input.setCatchKey(Input.Keys.BACK, false)
    }

    override fun dispose() {
        // Box2D World and the debug renderer both hold native memory - must be
        // disposed explicitly or it leaks. Called explicitly by whoever ends
        // this run permanently (currently: PauseScreen's "end run" tap zone),
        // never automatically by the Game/Screen lifecycle.
        world.dispose()
        debugRenderer.dispose()
        shapeRenderer.dispose()
        hudBatch.dispose()
        worldBatch.dispose()
        starTexture.dispose()
        planetLaunchTexture.dispose()
        planetTargetTexture.dispose()
        damageOverlayTexture.dispose()
        avatarPlayerTexture.dispose()
        avatarAiTexture.dispose()
        missileTexture.dispose()
        buttonTexture.dispose()
        panelTexture.dispose()
        barPillTexture.dispose()
    }
}
