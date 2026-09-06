package com.devavona.physicsduel

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.physics.box2d.Body

/**
 * Links an entity to its Box2D physics body. Box2D already owns position,
 * velocity, etc. internally, so this is deliberately just a reference - no
 * duplicated transform state to keep in sync.
 */
class PhysicsBodyComponent(val body: Body) : Component

/** Marker/tag component: entities with this can be picked up by [DragInputProcessor]. */
class DraggableComponent : Component

/**
 * Tags an entity as a source of custom radial gravity (see [GravitySystem]) -
 * e.g. a central "star" that other bodies orbit. [mass] is a standalone
 * tuning value, not read from the Box2D body: a *static* body (which the
 * gravity source is expected to be, so it doesn't itself get pulled around)
 * always reports zero mass in Box2D, since static bodies are immovable by
 * definition - there's no physical mass for [GravitySystem] to read off it.
 *
 * **Phase 11: mass is mutable.** [applyDamage] is what
 * [ProjectileContactListener] calls when a missile strikes a celestial body
 * instead of a character - see PROJECT_STATE.md's "Core gameplay loop"
 * entry: chipping away a body's mass measurably weakens its gravity well in
 * real time, since [GravitySystem.applyForces] reads [mass] fresh every
 * physics tick, and reducing it to zero ([isDestroyed]) removes the well
 * (and, per that entry, the body itself) entirely. [isDamageable] lets a
 * source opt out of this - the star does, for now (see
 * [PlayScreen.createStar]'s call site) - rather than making every gravity
 * source destructible by default; every design conversation about this
 * mechanic so far has been about planets/moons specifically, not the star.
 */
class GravitySourceComponent(initialMass: Float, val isDamageable: Boolean = true) : Component {
    var mass: Float = initialMass
        private set

    fun applyDamage(amount: Float) {
        mass = (mass - amount).coerceAtLeast(0f)
    }

    val isDestroyed: Boolean get() = mass <= 0f
}

/**
 * Marker/tag component: entities with this get pulled toward every
 * [GravitySourceComponent] entity each frame by [GravitySystem]. Unlike the
 * source's mass, the affected body's own mass comes straight from its Box2D
 * body (see [GravitySystem]) - gravitational acceleration doesn't depend on
 * the falling body's own mass, only Newton's second law (force = mass x
 * acceleration) does, so [GravitySystem] needs the mass only to convert the
 * acceleration it computes back into a force to hand Box2D.
 */
class GravityAffectedComponent : Component

/**
 * Marker/tag component: entities with this are a fired projectile (Phase 8's
 * missile) rather than a permanent scene body (a planet, a star). Lets
 * [ProjectileContactListener] tell "something that should be removed on
 * impact" apart from anything else a body might collide with, without
 * needing every non-projectile body to also carry some "permanent" marker.
 */
class ProjectileComponent : Component

/**
 * Gives an entity hit points a hit can reduce - the "health lives on
 * characters, not celestial bodies" half of the agreed damage model (see
 * PROJECT_STATE.md's "Core gameplay loop" entry; celestial bodies taking
 * damage to their *mass* instead is separate, still-unbuilt future work).
 * [applyDamage] is what [ProjectileContactListener] calls on a direct hit -
 * a safe plain Kotlin field write, unlike actually removing the entity/body
 * once [isDefeated], which still has to go through that class's deferred-
 * removal queue since Box2D's world is locked during a contact callback.
 */
class HealthComponent(val maxHp: Int, currentHp: Int = maxHp) : Component {
    var currentHp: Int = currentHp
        private set

    fun applyDamage(amount: Int) {
        currentHp = (currentHp - amount).coerceAtLeast(0)
    }

    val isDefeated: Boolean get() = currentHp <= 0
}
