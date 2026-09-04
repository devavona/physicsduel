package com.devavona.physicsduel

import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.physics.box2d.World

/**
 * Steps the Box2D world on a fixed timestep, decoupled from render framerate
 * (see PROJECT_STATE.md's "physics stability" note). Not an IteratingSystem -
 * there's nothing per-entity to do here, Box2D already owns and steps every
 * body registered with [world] internally regardless of which entities (if
 * any) reference them.
 *
 * [priority] lets this run at a specific point relative to other Ashley
 * systems, if that's ever needed. Defaults to 0 (Ashley's own default) so
 * existing callers that don't care about ordering don't need to change.
 *
 * **[beforeStep] - why a callback, not just "run some other system first"
 * (post-mortem, first gravity-well milestone):** the original design had a
 * separate [GravitySystem] as its own Ashley system, ordered via [priority]
 * to run before this one each frame. On-device testing caught a real bug in
 * that design: Ashley (and this class's own `update()`) runs once per
 * *rendered frame*, but the fixed-timestep loop below only actually calls
 * `world.step()` a variable number of times per frame (zero, one, or more,
 * depending on how the real frame time lines up with [TIME_STEP]). Box2D
 * queues up any force applied via `Body.applyForceToCenter()` and only
 * consumes/clears it on the *next* `world.step()` call - so a gravity force
 * computed once per render frame doesn't line up with "once per physics
 * tick": a frame that triggers two steps only delivers that force to the
 * first one, while several fast render frames between steps stack multiple
 * force applications onto a single step. The gravitational "kick" the body
 * actually receives per tick ends up depending on frame-rate timing instead
 * of being a fixed, consistent quantity - exactly the kind of small,
 * inconsistent perturbation that shows up over hundreds of orbits as a slow
 * apsidal precession (the whole orbit slowly rotating in place), which is
 * exactly what on-device testing showed.
 *
 * The fix: [beforeStep], when provided, is invoked from *inside* this loop,
 * immediately before each individual `world.step()` call - guaranteeing
 * whatever force it applies (gravity, or any future per-step force) is
 * computed and delivered exactly once per physics tick, in perfect lockstep
 * with the simulation, regardless of render frame rate. [GravitySystem] is
 * no longer an Ashley system at all as a result - see its own doc comment.
 */
class PhysicsSystem(
    private val world: World,
    priority: Int = 0,
    private val beforeStep: (() -> Unit)? = null
) : EntitySystem(priority) {

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
            beforeStep?.invoke()
            world.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS)
            accumulator -= TIME_STEP
        }
    }
}
