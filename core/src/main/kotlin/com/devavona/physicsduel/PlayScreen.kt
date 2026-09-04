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
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
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
 * **Post-foundation milestone: orbital gravity-well mechanic.** The original
 * demo (a circle falling under uniform gravity, bouncing inside four walls)
 * has been replaced with the first real gameplay direction: a central
 * "star" body that pulls a smaller body into a curving orbit via
 * [GravitySystem]'s custom point-source gravity, instead of Box2D's uniform
 * world gravity. There are deliberately no boundary walls anymore - an
 * orbit needs open space to curve through, not a box to bounce inside - and
 * [world]'s own gravity vector is zeroed out, since [GravitySystem] is now
 * entirely responsible for every pull a body feels.
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

        // Gravity-well demo tuning. See GravitySystem's class doc comment
        // for why these are hand-picked "game feel" numbers, not realistic
        // ones - STAR_MASS in particular only means anything relative to
        // GravitySystem.G, they're tuned as a pair.
        private const val STAR_RADIUS = 0.6f
        private const val STAR_MASS = 9f
        private const val ORBITING_BODY_RADIUS = 0.3f
        private const val ORBIT_RADIUS = 3f // starting distance from the star, in world units

        /**
         * Launching at exactly the closed-form circular-orbit speed (factor
         * 1.0) produces a perfect circle - constant distance, constant
         * speed, forever, by definition of what a circular orbit *is*. That
         * turned out to be the correct explanation for why the orbit looked
         * "static" once the precession bug was fixed (see PROJECT_STATE.md)
         * - it wasn't stuck, it was doing exactly what a circular orbit does.
         * Launching slower than that speed instead - still purely
         * tangential, just less of it - makes the starting point the
         * *farthest* point of the orbit (apoapsis) rather than the only
         * distance it ever reaches, so gravity pulls it in closer than
         * [ORBIT_RADIUS] before it swings back out: a real ellipse, with a
         * visibly closer/faster point and a visibly farther/slower point,
         * matching Kepler's second law - still a closed, stable, repeating
         * orbit, not a decaying one (see the "Deliberately not modeled yet"
         * note in PROJECT_STATE.md for why this and orbital decay are
         * different things). 0.85 was chosen to keep the close approach
         * comfortably clear of the star's own radius - much lower and the
         * ellipse gets thin enough that the near pass grazes the star.
         */
        private const val ORBIT_SPEED_FACTOR = 0.85f
    }

    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private lateinit var world: World
    private lateinit var debugRenderer: Box2DDebugRenderer
    private lateinit var engine: Engine
    private lateinit var dragInputProcessor: DragInputProcessor

    // Phase 7 HUD: a screen-pixel (not world-unit) camera + batch, separate
    // from [camera]/[viewport] above which stay in Box2D world units for the
    // debug renderer. Queries the ECS each frame rather than holding a direct
    // Body reference, so this keeps working even as more bodies are added -
    // it specifically tracks "the entity being pulled by gravity" (the
    // orbiting body), not the star, which never moves.
    private val hudCamera = OrthographicCamera()
    private val hudBatch = SpriteBatch()
    private val physicsBodyMapper = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
    private val orbitingBodyFamily = Family.all(GravityAffectedComponent::class.java, PhysicsBodyComponent::class.java).get()

    init {
        Box2D.init()

        camera = OrthographicCamera()
        viewport = FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera)
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f)

        // Zero, not -9.8: GravitySystem is now the only source of gravity -
        // see the class doc comment.
        world = World(Vector2(0f, 0f), true)
        debugRenderer = Box2DDebugRenderer()

        engine = Engine()
        // GravitySystem is a plain class, not an Ashley system - it's
        // driven from inside PhysicsSystem's fixed-timestep loop via
        // beforeStep, exactly once per physics tick, rather than once per
        // render frame. See PhysicsSystem's beforeStep doc comment and
        // GravitySystem's own class doc comment for why that timing matters
        // (on-device testing of an earlier per-frame version showed the
        // orbit slowly rotating in place - apsidal precession - caused by
        // gravity's force application not lining up with the physics ticks
        // that actually consume it).
        val gravitySystem = GravitySystem(engine)
        engine.addSystem(PhysicsSystem(world, beforeStep = gravitySystem::applyForces))

        val star = createStar()
        val orbitingBody = createOrbitingBody()
        engine.addEntity(
            Entity().apply {
                add(PhysicsBodyComponent(star))
                add(GravitySourceComponent(mass = STAR_MASS))
            }
        )
        engine.addEntity(
            Entity().apply {
                add(PhysicsBodyComponent(orbitingBody))
                add(DraggableComponent())
                add(GravityAffectedComponent())
            }
        )

        // The star is static, so it's never itself moved by anything - the
        // same "reuse an existing static body" trick the removed floor
        // previously provided (see DragInputProcessor's anchorBody doc
        // comment), just repurposed now that there are no walls to reuse it
        // from.
        dragInputProcessor = DragInputProcessor(engine, world, viewport, anchorBody = star)
    }

    override fun show() {
        val multiplexer = InputMultiplexer()
        multiplexer.addProcessor(BackKeyHandler())
        multiplexer.addProcessor(dragInputProcessor)
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
     * The gravity well itself: a static body at the center of the world -
     * static so it never itself gets pulled around, exactly like a real
     * star is many orders of magnitude heavier than anything orbiting it.
     * [GravitySourceComponent] (not this body's Box2D mass, which is zero
     * for any static body) is what [GravitySystem] actually reads.
     */
    private fun createStar(): Body {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.StaticBody
            position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = STAR_RADIUS }
        body.createFixture(shape, 0f) // density is meaningless on a static body - mass comes from GravitySourceComponent instead
        shape.dispose() // shapes are native-backed; always dispose after the fixture is built
        return body
    }

    /**
     * The body [GravitySystem] pulls into orbit around the star. Placed
     * [ORBIT_RADIUS] to the right of the star and launched with a tangential
     * (perpendicular-to-the-star) velocity, not a straight fall toward it -
     * exactly like a real orbit, "falling and missing": released with zero
     * velocity it would simply fall straight in, so the sideways velocity is
     * what turns that fall into a curve. `v = sqrt(G * starMass / r)` is the
     * closed-form speed for a perfectly *circular* orbit at this distance,
     * derived from setting gravitational force equal to the centripetal
     * force a circular orbit requires - scaled down by [ORBIT_SPEED_FACTOR]
     * (see its own doc comment) so the result is a stable *ellipse* instead,
     * with a real near/far point and a visible speed difference between
     * them, rather than a perfect unchanging circle.
     */
    private fun createOrbitingBody(): Body {
        val startX = WORLD_WIDTH / 2f + ORBIT_RADIUS
        val startY = WORLD_HEIGHT / 2f
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(startX, startY)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = ORBITING_BODY_RADIUS }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            density = 1f
            friction = 0.4f
            restitution = 0.3f
        }
        body.createFixture(fixtureDef)
        shape.dispose()

        val circularOrbitSpeed = kotlin.math.sqrt(GravitySystem.G * STAR_MASS / ORBIT_RADIUS)
        val orbitalSpeed = circularOrbitSpeed * ORBIT_SPEED_FACTOR
        body.setLinearVelocity(0f, orbitalSpeed) // perpendicular to the star->body radius (which is purely horizontal here)

        return body
    }

    override fun render(delta: Float) {
        engine.update(delta) // drives PhysicsSystem, which owns the fixed-timestep accumulator

        Gdx.gl.glClearColor(0.043f, 0.071f, 0.126f, 1f) // deep space navy
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        debugRenderer.render(world, camera.combined)

        renderHud()
    }

    /**
     * A minimal live-updating overlay: the orbiting body's world-space Y
     * position, redrawn every frame from current simulation state. The
     * actual value shown is a placeholder (no score/health exists yet) -
     * the point of Phase 7 is proving 2D screen-space text can be drawn on
     * top of the world-space debug view every frame without interfering
     * with it, which is the pattern any future HUD (score, health, timer)
     * will reuse.
     */
    private fun renderHud() {
        val trackedBody = engine.getEntitiesFor(orbitingBodyFamily).firstOrNull()?.let {
            physicsBodyMapper.get(it).body
        }
        hudCamera.update()
        hudBatch.projectionMatrix = hudCamera.combined
        hudBatch.begin()
        val text = trackedBody?.let { "Y: %.2f".format(it.position.y) } ?: ""
        val margin = HudFont.scaled(16f) // density-scaled, not a fixed pixel count - see HudFont
        HudFont.font.draw(hudBatch, text, margin, Gdx.graphics.height - margin)
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
        hudBatch.dispose()
    }
}
