package com.devavona.physicsduel

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
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
 * Entry point shared by every platform backend (currently just Android).
 *
 * Phase 2 of the foundation build: a real Box2D world with a fixed timestep,
 * one static ground body and one dynamic circle that falls and settles on it.
 * Rendered with LibGDX's built-in debug renderer (wireframe outlines) rather
 * than sprites - the goal here is proving physics stability and the render/
 * camera pipeline, not visuals. No input, ECS, or scene management yet -
 * those are later phases layered on top without touching this class's shape.
 */
class PhysicsDuelGame : ApplicationAdapter() {

    companion object {
        // Box2D works best with human-scale (meter) units, not pixels - hence
        // a small logical world size rather than screen-pixel dimensions.
        private const val WORLD_WIDTH = 9f
        private const val WORLD_HEIGHT = 16f

        // Fixed physics timestep, decoupled from render framerate. This is
        // the single biggest lever against jitter/instability/tunneling -
        // see PROJECT_STATE.md's "physics stability" note.
        private const val TIME_STEP = 1f / 60f
        private const val MAX_FRAME_TIME = 0.25f // clamps a stalled frame so the accumulator can't spiral
        private const val VELOCITY_ITERATIONS = 6
        private const val POSITION_ITERATIONS = 2
    }

    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport
    private lateinit var world: World
    private lateinit var debugRenderer: Box2DDebugRenderer

    private var accumulator = 0f

    override fun create() {
        Box2D.init()

        camera = OrthographicCamera()
        viewport = FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera)
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0f)

        world = World(Vector2(0f, -9.8f), true)
        debugRenderer = Box2DDebugRenderer()

        createGround()
        createFallingCircle()
    }

    private fun createGround() {
        val groundDef = BodyDef().apply {
            type = BodyDef.BodyType.StaticBody
            position.set(WORLD_WIDTH / 2f, 1f)
        }
        val groundBody = world.createBody(groundDef)
        val groundShape = PolygonShape().apply {
            setAsBox(WORLD_WIDTH / 2f, 0.25f)
        }
        groundBody.createFixture(groundShape, 0f)
        groundShape.dispose() // shapes are native-backed; always dispose after the fixture is built
    }

    private fun createFallingCircle() {
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
    }

    override fun render() {
        stepPhysics()

        Gdx.gl.glClearColor(0.043f, 0.071f, 0.126f, 1f) // deep space navy
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        debugRenderer.render(world, camera.combined)
    }

    private fun stepPhysics() {
        // Standard fixed-timestep accumulator: the world only ever advances in
        // exact 1/60s increments, however uneven the actual frame times are.
        val frameTime = minOf(Gdx.graphics.deltaTime, MAX_FRAME_TIME)
        accumulator += frameTime
        while (accumulator >= TIME_STEP) {
            world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS)
            accumulator -= TIME_STEP
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun dispose() {
        // Box2D World and the debug renderer both hold native memory - must be
        // disposed explicitly or it leaks. See PROJECT_STATE.md's "lifecycle
        // resilience" note.
        world.dispose()
        debugRenderer.dispose()
    }
}
