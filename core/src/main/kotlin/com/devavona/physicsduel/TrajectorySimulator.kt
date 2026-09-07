package com.devavona.physicsduel

import com.badlogic.gdx.math.Vector2

/**
 * A small, Box2D-free reimplementation of the exact gravity math
 * [GravitySystem.applyForces] itself uses, for predicting how a projectile
 * will fly without needing a real Box2D body or world to simulate it -
 * mass-independent for the body being pulled on purpose (Newton's law gives
 * acceleration = G*sourceMass/r^2, the projectile's own mass cancels out,
 * exactly like real gravity - a feather and a bowling ball fall at the same
 * rate), so a plain Vector2-based integrator is all this needs.
 *
 * Introduced in Phase 15 as a private part of [AiTurnController]'s aim
 * search; pulled out into its own shared class in Phase 16 once
 * [PlayScreen]'s aim trajectory preview needed the exact same stepping
 * logic - one simulator, two consumers, instead of two copies that could
 * quietly drift apart.
 */
class TrajectorySimulator(
    private val gravitationalConstant: Float,
    private val gravityMinDistance: Float
) {
    /**
     * Advances [position]/[velocity] by one [dt]-second step - semi-
     * implicit Euler (velocity updates first, then position uses the
     * *updated* velocity), same integration order [PhysicsSystem]'s real
     * fixed-timestep loop effectively produces. Mutates both vectors in
     * place; call repeatedly (with the same or a shrinking [sources]
     * snapshot) to walk out a full predicted flight.
     */
    fun step(position: Vector2, velocity: Vector2, dt: Float, sources: List<Pair<Vector2, Float>>, gravityMultiplier: Float) {
        val acceleration = Vector2()
        for ((sourcePosition, sourceMass) in sources) {
            val direction = Vector2(sourcePosition).sub(position)
            val distance = maxOf(direction.len(), gravityMinDistance)
            direction.nor()
            acceleration.add(direction.scl(gravitationalConstant * sourceMass / (distance * distance) * gravityMultiplier))
        }
        velocity.add(acceleration.scl(dt))
        position.add(Vector2(velocity).scl(dt))
    }
}
