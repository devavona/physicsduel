package com.devavona.physicsduel

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.physics.box2d.World

/**
 * Steps the Box2D world on a fixed timestep, decoupled from render framerate
 * (see PROJECT_STATE.md's "physics stability" note). Not an IteratingSystem -
 * there's nothing per-entity to do here, Box2D already owns and steps every
 * body registered with [world] internally regardless of which entities (if
 * any) reference them.
 */
class PhysicsSystem(private val world: World) : EntitySystem() {

    companion object {
        private const val TIME_STEP = 1f / 60f
        private const val MAX_FRAME_TIME = 0.25f // clamps a stalled frame so the accumulator can't spiral
        private const val VELOCITY_ITERATIONS = 6
        private const val POSITION_ITERATIONS = 2
    }

    private var accumulator = 0f

    override fun update(deltaTime: Float) {
        val frameTime = minOf(deltaTime, MAX_FRAME_TIME)
        accumulator += frameTime
        while (accumulator >= TIME_STEP) {
            world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS)
            accumulator -= TIME_STEP
        }
    }
}
