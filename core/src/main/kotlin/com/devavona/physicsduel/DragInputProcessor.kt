package com.devavona.physicsduel

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Family
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.physics.box2d.joints.MouseJoint
import com.badlogic.gdx.physics.box2d.joints.MouseJointDef
import com.badlogic.gdx.utils.viewport.Viewport

/**
 * Lets a finger drag any entity tagged [DraggableComponent] around, using
 * Box2D's own MouseJoint rather than hand-setting body position/velocity.
 * MouseJoint is the standard Box2D-idiomatic way to do this: it applies a
 * proper spring-like force toward the touch point, so dragging still
 * respects mass, can't shove a body through a wall, and hands off realistic
 * momentum on release.
 *
 * Which bodies are draggable now comes from the ECS (anything with a
 * [DraggableComponent] + [PhysicsBodyComponent]) rather than scanning every
 * Box2D body and checking its type - this is the payoff of the Phase 4 ECS
 * refactor: a future object becomes draggable just by tagging it, no changes
 * needed here.
 *
 * [anchorBody] is a static/kinematic body required by Box2D as the joint's
 * other end - it's never itself moved, just needed to construct the joint.
 * Reusing an existing static body (e.g. the floor) for this is standard
 * practice, no dedicated dummy body needed.
 */
class DragInputProcessor(
    private val engine: Engine,
    private val world: World,
    private val viewport: Viewport,
    private val anchorBody: Body
) : InputAdapter() {

    private val physicsBodyMapper = ComponentMapper.getFor(PhysicsBodyComponent::class.java)
    private val draggableFamily = Family.all(DraggableComponent::class.java, PhysicsBodyComponent::class.java).get()

    private var mouseJoint: MouseJoint? = null
    private val touchPoint = Vector2()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat()))

        val hitBody = findDraggableBodyAt(touchPoint) ?: return false

        val jointDef = MouseJointDef().apply {
            bodyA = anchorBody
            bodyB = hitBody
            target.set(touchPoint)
            maxForce = 1000f * hitBody.mass
            dampingRatio = 0.9f
            frequencyHz = 5f
        }
        mouseJoint = world.createJoint(jointDef) as MouseJoint
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val joint = mouseJoint ?: return false
        viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat()))
        joint.setTarget(touchPoint)
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val joint = mouseJoint ?: return false
        world.destroyJoint(joint)
        mouseJoint = null
        return true
    }

    private fun findDraggableBodyAt(point: Vector2): Body? {
        val draggables = engine.getEntitiesFor(draggableFamily)
        for (entity in draggables) {
            val body = physicsBodyMapper.get(entity).body
            for (fixture in body.fixtureList) {
                if (fixture.testPoint(point)) {
                    return body
                }
            }
        }
        return null
    }
}
