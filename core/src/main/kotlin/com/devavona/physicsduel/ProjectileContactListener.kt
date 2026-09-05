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
 * Phase 8's minimal "did the missile hit something" detection: watches for
 * any [ProjectileComponent]-tagged body touching anything else, logs it,
 * and queues it for removal. There's no damage/health/cratering system yet
 * (see PROJECT_STATE.md's Phase 8 scope) - a hit is only *detected* for now.
 *
 * Bodies are NOT destroyed directly inside [beginContact]: Box2D's world is
 * "locked" for the duration of a contact callback, and creating or
 * destroying bodies/joints during that window is a contract violation (Box2D
 * would throw). Instead, [beginContact] only records which entity to remove;
 * [flushRemovals] does the actual [World.destroyBody] + [Engine.removeEntity]
 * work, and must only be called once per frame, after [PhysicsSystem]'s
 * `world.step()` has fully returned - see [PlayScreen.render] for where
 * that happens.
 */
class ProjectileContactListener(private val engine: Engine) : ContactListener {

    private val physicsBodyMapper = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
    private val projectileFamily = Family.all(ProjectileComponent::class.java, PhysicsBodyComponent::class.java).get()

    // Kept live by Ashley the same way GravitySystem keeps its own family
    // results live - see that class's field doc comment.
    private val projectileEntities: ImmutableArray<Entity> = engine.getEntitiesFor(projectileFamily)

    private val pendingRemoval = mutableListOf<Entity>()

    override fun beginContact(contact: Contact) {
        findProjectileEntity(contact.fixtureA.body)?.let(::queueForRemoval)
        findProjectileEntity(contact.fixtureB.body)?.let(::queueForRemoval)
    }

    private fun queueForRemoval(entity: Entity) {
        if (entity in pendingRemoval) return
        Gdx.app.log("ProjectileContactListener", "Missile impact - no damage/health modeled yet (Phase 8)")
        pendingRemoval.add(entity)
    }

    private fun findProjectileEntity(body: Body): Entity? =
        projectileEntities.firstOrNull { physicsBodyMapper.get(it).body === body }

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
