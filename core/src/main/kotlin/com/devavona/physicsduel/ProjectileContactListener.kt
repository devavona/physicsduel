package com.devavona.physicsduel

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.Contact
import com.badlogic.gdx.physics.box2d.ContactImpulse
import com.badlogic.gdx.physics.box2d.ContactListener
import com.badlogic.gdx.physics.box2d.Manifold
import com.badlogic.gdx.physics.box2d.World

/**
 * Watches for any [ProjectileComponent]-tagged body touching anything else.
 * Phase 8 only detected/logged a hit; Phase 10 added character damage
 * ([HealthComponent]); **Phase 11** adds the other half of the damage model
 * from PROJECT_STATE.md's "Core gameplay loop" entry - a hit on a celestial
 * body instead damages its [GravitySourceComponent.mass] rather than a
 * character's health. Whichever component the thing hit actually carries
 * (a target character has [HealthComponent], a gravity-emitting planet has
 * [GravitySourceComponent], never both right now) determines which kind of
 * damage applies; a hit on something with neither (a non-gravity-emitting
 * planet, for bodies that haven't been given a [GravitySourceComponent] yet)
 * still just removes the projectile, same as Phase 8.
 *
 * Bodies are NOT destroyed directly inside [beginContact]: Box2D's world is
 * "locked" for the duration of a contact callback, and creating or
 * destroying bodies/joints during that window is a contract violation (Box2D
 * would throw). Applying damage itself is safe to do immediately - it's just
 * a plain Kotlin field write via [HealthComponent.applyDamage] or
 * [GravitySourceComponent.applyDamage] - but actually removing an
 * entity/body, whether a spent projectile, a defeated character, or a
 * destroyed celestial body, has to wait: [beginContact] only records which
 * entities to remove; [flushRemovals] does the actual [World.destroyBody] +
 * [Engine.removeEntity] work, and must only be called once per frame, after
 * [PhysicsSystem]'s `world.step()` has fully returned - see
 * [PlayScreen.render] for where that happens.
 */
class ProjectileContactListener(private val engine: Engine) : ContactListener {

    companion object {
        // Illustrative, not tuned - matches PlayScreen's equally-illustrative
        // TARGET_MAX_HP (4 direct hits to defeat a fresh target). Every
        // missile does the same damage for now since there's only one
        // weapon type built (see PROJECT_STATE.md's character-roster notes
        // for the eventual per-weapon damage differences).
        private const val MISSILE_DAMAGE = 25

        // Illustrative, not tuned - deliberately a much smaller number than
        // MISSILE_DAMAGE since celestial mass lives on its own unit scale
        // (STAR_MASS=9f, TARGET_PLANET_MASS=2f - see PlayScreen), not the
        // character-HP scale. Matches TARGET_PLANET_MASS / 4 so a modest
        // planet takes the same illustrative "4 hits to destroy" as a
        // character takes to defeat.
        private const val CELESTIAL_MASS_DAMAGE = 0.5f
    }

    private val physicsBodyMapper = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
    private val healthMapper = ComponentMapper.getFor(HealthComponent::class.java)
    private val gravitySourceMapper = ComponentMapper.getFor(GravitySourceComponent::class.java)
    private val projectileFamily = Family.all(ProjectileComponent::class.java, PhysicsBodyComponent::class.java).get()

    // Broad on purpose - every entity that can be hit (character, star,
    // gravity-emitting planet) has a PhysicsBodyComponent, so one lookup
    // covers "what did the missile hit," and applyDamageIfApplicable below
    // decides what to do based on whichever *other* component it actually
    // carries.
    private val bodyEntityFamily = Family.all(PhysicsBodyComponent::class.java).get()

    // Kept live by Ashley the same way GravitySystem keeps its own family
    // results live - see that class's field doc comment.
    private val projectileEntities: ImmutableArray<Entity> = engine.getEntitiesFor(projectileFamily)
    private val bodyEntities: ImmutableArray<Entity> = engine.getEntitiesFor(bodyEntityFamily)

    private val pendingRemoval = mutableListOf<Entity>()

    override fun beginContact(contact: Contact) {
        // Box2D reports one beginContact per contact, not one per fixture -
        // these two calls check both possible arrangements of "which side is
        // the projectile," not the same contact twice.
        handleProjectileHit(projectileBody = contact.fixtureA.body, otherBody = contact.fixtureB.body)
        handleProjectileHit(projectileBody = contact.fixtureB.body, otherBody = contact.fixtureA.body)
    }

    private fun handleProjectileHit(projectileBody: Body, otherBody: Body) {
        val projectile = findProjectileEntity(projectileBody) ?: return
        Gdx.app.log("ProjectileContactListener", "Missile impact")
        queueForRemoval(projectile)

        val target = findBodyEntity(otherBody) ?: return
        applyDamageIfApplicable(target)
    }

    private fun applyDamageIfApplicable(target: Entity) {
        healthMapper.get(target)?.let { health ->
            health.applyDamage(MISSILE_DAMAGE)
            Gdx.app.log("ProjectileContactListener", "Hit - ${health.currentHp}/${health.maxHp} HP remaining")
            if (health.isDefeated) {
                Gdx.app.log("ProjectileContactListener", "Target defeated")
                queueForRemoval(target)
            }
        }

        gravitySourceMapper.get(target)?.let { source ->
            if (!source.isDamageable) return@let
            source.applyDamage(CELESTIAL_MASS_DAMAGE)
            Gdx.app.log("ProjectileContactListener", "Celestial body hit - mass now ${source.mass}")
            if (source.isDestroyed) {
                Gdx.app.log("ProjectileContactListener", "Celestial body destroyed - gravity well removed")
                queueForRemoval(target)
            }
        }
    }

    private fun queueForRemoval(entity: Entity) {
        if (entity in pendingRemoval) return
        pendingRemoval.add(entity)
    }

    private fun findProjectileEntity(body: Body): Entity? =
        projectileEntities.firstOrNull { physicsBodyMapper.get(it).body === body }

    private fun findBodyEntity(body: Body): Entity? =
        bodyEntities.firstOrNull { physicsBodyMapper.get(it).body === body }

    /** Must be called once per frame, only after `world.step()` has returned - see the class doc comment. */
    fun flushRemovals(world: World) {
        if (pendingRemoval.isEmpty()) return
        for (entity in pendingRemoval) {
            world.destroyBody(physicsBodyMapper.get(entity).body)
            engine.removeEntity(entity)
        }
        pendingRemoval.clear()
    }

    override fun endContact(contact: Contact) {}
    override fun preSolve(contact: Contact, oldManifold: Manifold) {}
    override fun postSolve(contact: Contact, impulse: ContactImpulse) {}
}
