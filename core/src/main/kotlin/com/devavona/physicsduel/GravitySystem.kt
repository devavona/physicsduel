package com.devavona.physicsduel

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Vector2

/**
 * Custom point-source ("Newtonian") gravity, layered on top of Box2D - Box2D
 * itself only offers one *uniform* gravity vector for the whole [com.badlogic.gdx.physics.box2d.World]
 * (what every earlier phase used for "things fall down"), it has no built-in
 * concept of one body pulling another toward itself. This is the core new
 * mechanic for the orbital gravity-well game direction: every
 * [GravityAffectedComponent] body gets pulled toward every
 * [GravitySourceComponent] body each frame, with the pull growing stronger
 * at closer range (inverse-square, same shape as real gravity) - strong
 * enough up close to whip a grazing body around, weak enough at a distance
 * to let it coast, which is what makes an orbit look like an orbit instead
 * of a body drifting in a straight line toward a magnet.
 *
 * [G] is a tuning constant, not the real physical gravitational constant -
 * the real value (~6.674e-11) is meaningless at Box2D's small hand-picked
 * world-unit scale (see [PlayScreen]'s WORLD_WIDTH/HEIGHT), exactly like
 * Phase 2's `-9.8` world gravity was already "Earth-shaped" rather than a
 * literal 9.8 meters. [GravitySourceComponent.mass] is tuned alongside it,
 * together picked so a body released at [PlayScreen]'s demo distance settles
 * into a visibly curving, roughly multi-second orbital period rather than
 * diving straight in (too strong) or barely deflecting (too weak).
 *
 * **Not an Ashley [com.badlogic.ashley.core.EntitySystem] anymore** - the
 * first version of this class was one, updated once per rendered frame like
 * any other system. On-device testing of that version showed the orbit
 * slowly rotating in place over time (apsidal precession) instead of
 * staying a clean repeating loop. Root cause: this class's per-frame force
 * application didn't line up with [PhysicsSystem]'s fixed-timestep physics
 * ticks (see that class's `beforeStep` doc comment for the full
 * explanation) - the fix moves force application to exactly once per
 * physics tick instead of once per render frame, which meant it could no
 * longer be a normal per-frame Ashley system. Now it's a plain class,
 * constructed directly with the [Engine] (so it can still query the same
 * live [Family] results any Ashley system would), exposing [applyForces]
 * for [PlayScreen] to wire into [PhysicsSystem]'s `beforeStep` callback.
 */
class GravitySystem(engine: Engine) {

    companion object {
        /**
         * Tuned constant, not the real physical gravitational constant - see
         * the class doc comment. Not `private`: [PlayScreen] reads it too,
         * to derive the demo orbiting body's initial tangential velocity
         * from the same constant this system actually simulates with,
         * rather than duplicating the number in two places and risking them
         * drifting apart.
         */
        const val G = 10f

        /**
         * Below this distance the inverse-square force would blow up toward
         * infinity (and did, in early testing, fling the orbiting body off
         * to unbounded velocity the instant it grazed the star). Clamping
         * the distance used in the force calculation - not the body's actual
         * position - caps the maximum pull without teleporting or otherwise
         * interfering with the body itself.
         */
        private const val MIN_DISTANCE = 0.5f
    }

    /**
     * Debug/tuning multiplier applied on top of every gravity calculation -
     * added so gravity's overall "feel" can be adjusted live, on-device,
     * without recompiling (see [GravityDebugController]). Default of 0.7
     * is Boo's own confirmed-good value from on-device testing of Phase 8's
     * scene (the original 1.0 - i.e. the raw tuned G/mass math - felt "more
     * dramatic than feels good"). Not a `const` - unlike [G], this is meant
     * to change at runtime, and still fully adjustable live via
     * [GravityDebugController] from this new baseline.
     */
    var gravityMultiplier: Float = 0.7f

    private val physicsBodyMapper = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
    private val gravitySourceMapper = ComponentMapper.getFor(GravitySourceComponent::class.java)

    private val sourceFamily = Family.all(GravitySourceComponent::class.java, PhysicsBodyComponent::class.java).get()
    private val affectedFamily = Family.all(GravityAffectedComponent::class.java, PhysicsBodyComponent::class.java).get()

    // Ashley keeps these ImmutableArrays live from the moment they're first
    // requested - as entities/components are added or removed elsewhere
    // (e.g. a future "spawn another planet" feature), these stay current
    // with no extra work, exactly as they would inside a real system.
    private val sourceEntities: ImmutableArray<Entity> = engine.getEntitiesFor(sourceFamily)
    private val affectedEntities: ImmutableArray<Entity> = engine.getEntitiesFor(affectedFamily)

    // Scratch vectors reused every physics tick/pair instead of allocating a
    // new Vector2 per body per source per tick.
    private val direction = Vector2()
    private val force = Vector2()

    /**
     * Applies one physics-tick's worth of gravitational force to every
     * affected body, from every source. Must be called exactly once per
     * [PhysicsSystem] fixed-timestep tick (via its `beforeStep` callback -
     * see [PlayScreen]'s wiring) - see the class doc comment for why that
     * timing matters.
     */
    fun applyForces() {
        for (affected in affectedEntities) {
            val affectedBody = physicsBodyMapper.get(affected).body

            for (source in sourceEntities) {
                val sourceBody = physicsBodyMapper.get(source).body
                val sourceMass = gravitySourceMapper.get(source).mass

                direction.set(sourceBody.position).sub(affectedBody.position)
                val distance = maxOf(direction.len(), MIN_DISTANCE)
                direction.nor() // now a pure unit vector pointing from the affected body toward the source

                // Newton's law of gravitation: F = G * m1 * m2 / r^2. Using
                // the affected body's real Box2D mass (not a tuned value,
                // unlike the source's) is what makes heavier bodies feel
                // "heavier" - same acceleration as a lighter body at the
                // same distance, but more force/momentum behind it.
                val forceMagnitude = G * sourceMass * affectedBody.mass / (distance * distance) * gravityMultiplier
                force.set(direction).scl(forceMagnitude)

                affectedBody.applyForceToCenter(force, true)
            }
        }
    }
}
