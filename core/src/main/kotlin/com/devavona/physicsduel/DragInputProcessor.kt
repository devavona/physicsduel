package com.devavona.physicsduel

import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.World
import com.badlogic.gdx.physics.box2d.joints.MouseJoint
import com.badlogic.gdx.physics.box2d.joints.MouseJointDef
import com.badlogic.gdx.utils.viewport.Viewport
import com.badlogic.gdx.utils.Array as GdxArray

/**
 * Lets a finger drag any dynamic body in [world] around, using Box2D's own
 * MouseJoint rather than hand-setting body position/velocity. MouseJoint is
 * the standard Box2D-idiomatic way to do this: it applies a proper spring-like
 * force toward the touch point, so dragging still respects mass, can't shove a
 * body through a wall, and hands off realistic momentum on release.
 *
 * [anchorBody] is a static/kinematic body required by Box2D as the joint's
 * other end - it's never itself moved, just needed to construct the joint.
 * Reusing an existing static body (e.g. the ground) for this is standard
 * practice, no dedicated dummy body needed.
 *
 * Deliberately generic (queries the world for whatever dynamic body is under
 * the touch, not "the circle" specifically) so later phases/mechanics can
 * reuse this as-is instead of writing their own drag handling.
 */
class DragInputProcessor(
    private val world: World,
    private val viewport: Viewport,
    private val anchorBody: Body
) : InputAdapter() {

    private var mouseJoint: MouseJoint? = null
    private val touchPoint = Vector2()
    private val bodies = GdxArray<Body>()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat()))

        val hitBody = findDynamicBodyAt(touchPoint) ?: return false

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

    private fun findDynamicBodyAt(point: Vector2): Body? {
        world.getBodies(bodies)
        for (body in bodies) {
            if (body.type != BodyDef.BodyType.DynamicBody) continue
            for (fixture in body.fixtureList) {
                if (fixture.testPoint(point)) {
                    return body
                }
            }
        }
        return null
    }
}
