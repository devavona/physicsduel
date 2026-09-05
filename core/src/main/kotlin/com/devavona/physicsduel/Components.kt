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
 */
class GravitySourceComponent(val mass: Float) : Component

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
