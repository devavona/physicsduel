package com.devavona.physicsduel

import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport

/**
 * Phase 8's aiming/fire input: pull back from a fixed [launchPoint] and
 * release to fire, Angry-Birds-style - a different interaction than
 * [DragInputProcessor]'s continuous "drag a body to follow my finger" (that
 * one moves a body directly via a MouseJoint; this one never touches a body
 * until release, then hands [onFire] a single launch velocity).
 *
 * The fired velocity points opposite the drag - pull down-and-left, the shot
 * goes up-and-right - and its magnitude is the pull distance scaled by
 * [powerScale], clamped to [maxSpeed] so a wild drag can't fire an
 * unreasonably fast shot. [currentAimLine] exposes the live pull vector
 * (null when not aiming) purely for [PlayScreen] to draw a debug aiming
 * line - this class has no rendering code of its own.
 */
class SlingshotInputProcessor(
    private val launchPoint: Vector2,
    private val viewport: Viewport,
    private val powerScale: Float,
    private val maxSpeed: Float,
    private val onFire: (velocity: Vector2) -> Unit
) : InputAdapter() {

    companion object {
        // How close a touch-down must land to launchPoint to begin aiming, in
        // world units - otherwise Phase 8's single fixed launch point would
        // hijack every touch anywhere on screen.
        private const val AIM_START_RADIUS = 1.5f
    }

    private val touchPoint = Vector2()
    private var aiming = false

    /** The live pull vector (dragCurrent - launchPoint) while aiming, or null. Read-only for [PlayScreen]'s debug line. */
    var currentAimLine: Vector2? = null
        private set

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat()))
        if (touchPoint.dst(launchPoint) > AIM_START_RADIUS) return false
        aiming = true
        currentAimLine = Vector2(touchPoint).sub(launchPoint)
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!aiming) return false
        viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat()))
        currentAimLine = Vector2(touchPoint).sub(launchPoint)
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (!aiming) return false
        aiming = false
        viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat()))
        val pull = Vector2(touchPoint).sub(launchPoint)
        currentAimLine = null

        if (pull.isZero(0.01f)) return true // treat a near-zero drag as "cancelled," not a limp shot

        val speed = minOf(pull.len() * powerScale, maxSpeed)
        val velocity = pull.nor().scl(-speed) // opposite the drag direction
        onFire(velocity)
        return true
    }
}
