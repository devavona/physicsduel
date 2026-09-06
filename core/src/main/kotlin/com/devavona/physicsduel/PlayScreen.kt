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
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
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

        // Launch/target planets sit at the same height, star above and
        // between them - a straight-line shot passes well below the star, so
        // using its pull to curve a shot up and over is a real aiming
        // choice, not the only way to reach the target.
        private const val LAUNCH_PLANET_X = 2f
        private const val TARGET_PLANET_X = 7f
        private const val PLANETS_Y = 4f
        private const val STAR_X = (LAUNCH_PLANET_X + TARGET_PLANET_X) / 2f
        private const val STAR_Y = 9f

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

        // Standard math convention (0 degrees = +X/east, 90 = +Y/north) -
        // 90 starts the avatar at the top of the launch planet, roughly
        // facing the target planet to its right.
        private const val AVATAR_START_ANGLE_DEGREES = 90f

        // Converts a pull-back drag distance (world units) into launch
        // speed, clamped to MAX_MISSILE_SPEED so a wild drag can't fire an
        // unreasonably fast shot.
        private const val PULL_POWER_SCALE = 4f
        private const val MAX_MISSILE_SPEED = 15f
    }

    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private lateinit var world: World
    private lateinit var debugRenderer: Box2DDebugRenderer
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var engine: Engine
    private lateinit var slingshotInputProcessor: SlingshotInputProcessor
    private lateinit var avatarMovementController: AvatarMovementController
    private lateinit var projectileContactListener: ProjectileContactListener
    private lateinit var launchPoint: Vector2
    private lateinit var gravitySystem: GravitySystem
    private lateinit var gravityDebugController: GravityDebugController

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
    private val physicsBodyMapper = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
    private val healthMapper = ComponentMapper.getFor(HealthComponent::class.java)
    private val gravityAffectedFamily = Family.all(GravityAffectedComponent::class.java, PhysicsBodyComponent::class.java).get()
    private val healthFamily = Family.all(HealthComponent::class.java, PhysicsBodyComponent::class.java).get()

    init {
        Box2D.init()

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
                add(GravitySourceComponent(mass = STAR_MASS))
            }
        )

        // Deliberately NOT tagged GravitySourceComponent - Phase 8's scope
        // keeps the star as the only thing that pulls, even though every
        // celestial body is confirmed to eventually exert gravity (see
        // PROJECT_STATE.md). Plain static bodies for now, not added to the
        // ECS at all - nothing about them needs an Ashley query yet.
        createPlanet(LAUNCH_PLANET_X, PLANETS_Y)
        createPlanet(TARGET_PLANET_X, PLANETS_Y)

        engine.addEntity(
            Entity().apply {
                add(PhysicsBodyComponent(createTarget()))
                add(HealthComponent(TARGET_MAX_HP))
            }
        )

        avatarMovementController = AvatarMovementController(
            planetCenter = Vector2(LAUNCH_PLANET_X, PLANETS_Y),
            planetRadius = PLANET_RADIUS,
            heightAboveSurface = LAUNCH_POINT_CLEARANCE,
            stepsPerPhase = MOVEMENT_STEPS_PER_PHASE,
            stepAngleDegrees = MOVEMENT_STEP_ANGLE_DEGREES,
            startAngleDegrees = AVATAR_START_ANGLE_DEGREES
        )
        launchPoint = Vector2(avatarMovementController.position)

        slingshotInputProcessor = SlingshotInputProcessor(
            launchPoint = launchPoint,
            viewport = viewport,
            powerScale = PULL_POWER_SCALE,
            maxSpeed = MAX_MISSILE_SPEED,
            onFire = { velocity ->
                if (avatarMovementController.canFire) {
                    fireMissile(velocity)
                    avatarMovementController.onFired()
                }
            }
        )
        gravityDebugController = GravityDebugController(gravitySystem)
    }

    override fun show() {
        val multiplexer = InputMultiplexer()
        multiplexer.addProcessor(BackKeyHandler())
        multiplexer.addProcessor(gravityDebugController)
        multiplexer.addProcessor(avatarMovementController)
        multiplexer.addProcessor(slingshotInputProcessor)
        Gdx.input.inputProcessor = multiplexer
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
     * Phase 10's stand-in for an opposing character: a small static body
     * sitting above the target planet's surface (same clearance as the
     * avatar's launch point), tagged [HealthComponent] by the caller so
     * [ProjectileContactListener] can damage it on a direct hit.
     * Deliberately not a full character yet - no movement, no turn
     * structure, no AI - this phase is purely about proving the
     * hit-damage-defeat mechanic itself before a real character carries it.
     */
    private fun createTarget(): Body {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.StaticBody
            position.set(TARGET_PLANET_X, PLANETS_Y + PLANET_RADIUS + LAUNCH_POINT_CLEARANCE)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = TARGET_RADIUS }
        body.createFixture(shape, 0f)
        shape.dispose()
        return body
    }

    /**
     * Fires one missile from [launchPoint] with the given velocity (already
     * computed by [SlingshotInputProcessor] - this function just spawns the
     * body/entity, it doesn't know about pull vectors or power scaling).
     * Tagged [GravityAffectedComponent] so [GravitySystem] curves its
     * flight, and [ProjectileComponent] so [ProjectileContactListener]
     * knows to remove it on impact instead of leaving it as a permanent
     * scene body.
     */
    private fun fireMissile(velocity: Vector2) {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(launchPoint)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = MISSILE_RADIUS }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            density = 1f
            friction = 0.4f
            restitution = 0.2f
        }
        body.createFixture(fixtureDef)
        shape.dispose()
        body.linearVelocity = velocity

        engine.addEntity(
            Entity().apply {
                add(PhysicsBodyComponent(body))
                add(GravityAffectedComponent())
                add(ProjectileComponent())
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

        engine.update(delta) // drives PhysicsSystem, which owns the fixed-timestep accumulator
        // Only safe to call after world.step() has fully returned for this
        // frame (see ProjectileContactListener's class doc comment) -
        // engine.update above is exactly that point, since PhysicsSystem's
        // step loop is synchronous.
        projectileContactListener.flushRemovals(world)

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
        debugRenderer.render(world, camera.combined)
        renderDebugOverlay()

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
        renderMovementControls()
        renderTargetHud()
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

        shapeRenderer.projectionMatrix = hudCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.25f, 0.25f, 0.32f, 1f)
        shapeRenderer.rect(minusRect.x, minusRect.y, minusRect.width, minusRect.height)
        shapeRenderer.rect(plusRect.x, plusRect.y, plusRect.width, plusRect.height)
        shapeRenderer.end()

        hudBatch.projectionMatrix = hudCamera.combined
        hudBatch.begin()
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

        shapeRenderer.projectionMatrix = hudCamera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.25f, 0.25f, 0.32f, 1f)
        shapeRenderer.rect(leftRect.x, leftRect.y, leftRect.width, leftRect.height)
        shapeRenderer.rect(rightRect.x, rightRect.y, rightRect.width, rightRect.height)
        if (showPassButton) {
            val passRect = avatarMovementController.passButtonRect
            shapeRenderer.rect(passRect.x, passRect.y, passRect.width, passRect.height)
        }
        shapeRenderer.end()

        hudBatch.projectionMatrix = hudCamera.combined
        hudBatch.begin()
        drawCenteredLabel("<", leftRect)
        drawCenteredLabel(">", rightRect)
        if (showPassButton) {
            drawCenteredLabel("Pass", avatarMovementController.passButtonRect)
        }

        val phaseLabel = if (avatarMovementController.phase == AvatarMovementController.Phase.PRE_SHOT) "Pre-shot" else "Post-shot"
        val turnLabel = "Turn %d - %s: %d left".format(
            avatarMovementController.turnNumber, phaseLabel, avatarMovementController.stepsRemaining
        )
        val margin = HudFont.scaled(16f)
        val secondLineY = Gdx.graphics.height - margin - HudFont.scaled(60f) // below renderHud's "Missile Y" line
        HudFont.font.draw(hudBatch, turnLabel, margin, secondLineY)
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
     * Third HUD line, top-left (below "Missile Y" and the turn/phase
     * readout): the Phase 10 target's remaining HP, or "DEFEATED" once
     * [ProjectileContactListener] has actually removed it from the engine
     * entirely (see [HealthComponent]) - the first on-device look at the
     * damage model working end to end.
     */
    private fun renderTargetHud() {
        val targetHealth = engine.getEntitiesFor(healthFamily).firstOrNull()?.let { healthMapper.get(it) }
        hudBatch.projectionMatrix = hudCamera.combined
        hudBatch.begin()
        val text = targetHealth?.let { "Target HP: %d/%d".format(it.currentHp, it.maxHp) } ?: "Target: DEFEATED"
        val margin = HudFont.scaled(16f)
        val thirdLineY = Gdx.graphics.height - margin - HudFont.scaled(120f) // below renderHud's and renderMovementControls' lines
        HudFont.font.draw(hudBatch, text, margin, thirdLineY)
        hudBatch.end()
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
    }
}
