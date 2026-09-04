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
