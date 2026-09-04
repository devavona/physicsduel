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
import com.badlogic.gdx.physics.box2d.PolygonShape
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport

/**
 * The physics playground itself (Phases 2-4's content), now wrapped as a
 * [Screen] instead of being the app's top-level class. Pressing Back pauses
 * rather than quitting - see [PauseScreen].
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
    // Body reference, so this keeps working unchanged if/when more physics
    // bodies exist - it always finds "the entities with a physics body",
    // not "the one demo circle" specifically.
    private val hudCamera = OrthographicCamera()
    private val hudBatch = SpriteBatch()
    private val physicsBodyMapper = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
    private val physicsBodyFamily = Family.all(PhysicsBodyComponent::class.java).get()

    init {
        Box2D.init()

        camera = OrthographicCamera()
        viewport = FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera)
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f)

        world = World(Vector2(0f, -9.8f), true)
        debugRenderer = Box2DDebugRenderer()

        engine = Engine()
        engine.addSystem(PhysicsSystem(world))

        val floor = createBoundaries()
        val circleBody = createFallingCircle()
        engine.addEntity(
            Entity().apply {
                add(PhysicsBodyComponent(circleBody))
                add(DraggableComponent())
            }
        )

        dragInputProcessor = DragInputProcessor(engine, world, viewport, anchorBody = floor)
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
     * A thin static wall around all four edges of the visible world, so a
     * dragged (or bouncing) body stays inside the play area instead of
     * flying off into unbounded space. Returns the floor body specifically,
     * since it's reused as the MouseJoint's anchor body (see [DragInputProcessor]).
     *
     * Deliberately NOT wrapped in ECS entities: static level geometry has no
     * per-frame behavior, so there's nothing for a system to do with it -
     * not everything needs to be an entity.
     */
    private fun createBoundaries(): Body {
        val floor = createWall(centerX = WORLD_WIDTH / 2f, centerY = 0f, halfWidth = WORLD_WIDTH / 2f, halfHeight = 0.25f)
        createWall(centerX = WORLD_WIDTH / 2f, centerY = WORLD_HEIGHT, halfWidth = WORLD_WIDTH / 2f, halfHeight = 0.25f) // ceiling
        createWall(centerX = 0f, centerY = WORLD_HEIGHT / 2f, halfWidth = 0.25f, halfHeight = WORLD_HEIGHT / 2f) // left wall
        createWall(centerX = WORLD_WIDTH, centerY = WORLD_HEIGHT / 2f, halfWidth = 0.25f, halfHeight = WORLD_HEIGHT / 2f) // right wall
        return floor
    }

    private fun createWall(centerX: Float, centerY: Float, halfWidth: Float, halfHeight: Float): Body {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.StaticBody
            position.set(centerX, centerY)
        }
        val body = world.createBody(bodyDef)
        val shape = PolygonShape().apply { setAsBox(halfWidth, halfHeight) }
        body.createFixture(shape, 0f)
        shape.dispose() // shapes are native-backed; always dispose after the fixture is built
        return body
    }

    private fun createFallingCircle(): Body {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT - 2f)
        }
        val body = world.createBody(bodyDef)
        val shape = CircleShape().apply { radius = 0.5f }
        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            density = 1f
            friction = 0.4f
            restitution = 0.3f // small bounce on landing, to prove collision response is actually happening
        }
        body.createFixture(fixtureDef)
        shape.dispose()
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
     * A minimal live-updating overlay: the tracked body's world-space Y
     * position, redrawn every frame from current simulation state. The
     * actual value shown is a placeholder (no score/health exists yet) -
     * the point of Phase 7 is proving 2D screen-space text can be drawn on
     * top of the world-space debug view every frame without interfering
     * with it, which is the pattern any future HUD (score, health, timer)
     * will reuse.
     */
    private fun renderHud() {
        val trackedBody = engine.getEntitiesFor(physicsBodyFamily).firstOrNull()?.let {
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
