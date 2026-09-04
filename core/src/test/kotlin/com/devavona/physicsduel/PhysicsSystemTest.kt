package com.devavona.physicsduel

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Exercises [PhysicsSystem]'s fixed-timestep accumulator against a real
 * Box2D [World] (desktop natives, loaded via [GdxTestBootstrap]) - not a
 * mock or a reimplementation of the math.
 *
 * The trick used throughout: a free-falling body with no other forces on it
 * has its vertical velocity changed by exactly `gravity.y * TIME_STEP` on
 * every full Box2D step (`velocity += gravity * dt`, applied once per
 * `world.step()` call). That gives an observable, black-box way to verify
 * how many actual simulation steps a given sequence of `update()` calls
 * produced, without needing to expose PhysicsSystem's private timestep
 * constants - the tests only assert on physics *outcomes*, matching how
 * this class would actually be judged wrong in play (a body moving too
 * fast, too slow, or jumping on a stalled frame), not on its internals.
 */
class PhysicsSystemTest {

    companion object {
        private const val GRAVITY_Y = -10f
        private const val TIME_STEP = 1f / 60f // must match PhysicsSystem's own constant
        private const val MAX_FRAME_TIME = 0.25f // must match PhysicsSystem's own constant
        private const val EPSILON = 0.05f
    }

    private lateinit var world: World

    @Before
    fun setUp() {
        GdxTestBootstrap.ensureRunning()
        world = World(Vector2(0f, GRAVITY_Y), true)
    }

    @After
    fun tearDown() {
        world.dispose()
    }

    private fun World.freeFallingBody() =
        createBody(BodyDef().apply { type = BodyDef.BodyType.DynamicBody })

    @Test
    fun oneExactTimestep_producesOneSimulationStep() {
        val body = world.freeFallingBody()
        val system = PhysicsSystem(world)

        system.update(TIME_STEP)

        assertEquals(GRAVITY_Y * TIME_STEP, body.linearVelocity.y, EPSILON)
    }

    @Test
    fun manySmallDeltas_accumulateToTheSameResultAsFewLargeOnes() {
        // Two independent worlds, so stepping one can't leak into the other's
        // body - this is purely a "does the same total real time produce the
        // same physics outcome regardless of how it's chopped into frames"
        // check, which is the entire point of a fixed-timestep accumulator.
        val worldA = World(Vector2(0f, GRAVITY_Y), true)
        val worldB = World(Vector2(0f, GRAVITY_Y), true)
        try {
            val bodyA = worldA.freeFallingBody()
            val systemA = PhysicsSystem(worldA)
            repeat(60) { systemA.update(TIME_STEP) } // 60 frames @ 1/60s = 1.0s of sim time

            val bodyB = worldB.freeFallingBody()
            val systemB = PhysicsSystem(worldB)
            repeat(6) { systemB.update(1f / 6f) } // 6 frames @ 1/6s = 1.0s of sim time

            assertEquals(bodyA.linearVelocity.y, bodyB.linearVelocity.y, EPSILON)
        } finally {
            worldA.dispose()
            worldB.dispose()
        }
    }

    @Test
    fun hugeStalledFrame_isClampedInsteadOfCatchingUpAllAtOnce() {
        val body = world.freeFallingBody()
        val system = PhysicsSystem(world)

        // Simulates a huge stall (e.g. the app was backgrounded and just
        // resumed with a multi-second gap) - MAX_FRAME_TIME should clamp
        // this to at most 0.25s of simulated time, not run 5 real seconds
        // of physics in one frame (the "spiral of death" this pattern
        // exists to prevent).
        system.update(5f)

        assertEquals(GRAVITY_Y * MAX_FRAME_TIME, body.linearVelocity.y, EPSILON)
    }
}
