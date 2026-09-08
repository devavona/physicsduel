package com.devavona.physicsduel

import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.MathUtils
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
 * [powerScale], clamped to [maxSpeed], then scaled once more by
 * [speedMultiplier] (Phase 17 - [ShotSpeedTuning]'s live, on-device-tunable
 * dial on top of the fixed pull-power math, read fresh at the moment of
 * release so an adjustment mid-drag takes effect on the very shot about to
 * fire) so a wild drag can't fire an unreasonably fast shot. [currentAimLine]
 * exposes the live pull vector (null when not aiming) purely for
 * [PlayScreen] to draw a debug aiming line - this class has no rendering
 * code of its own.
 *
 * **Player shot accuracy (Sept 2026 session).** Boo, after the AI's own
 * aim-error jitter ([AiTurnController.applyAimError]) had already made
 * ITS shots imperfect: the player's own release should carry the same
 * kind of small, deliberate imprecision, not a pixel-perfect execution of
 * whatever the pull vector says. [applyAimError] mirrors
 * [AiTurnController.applyAimError] exactly - same small random angle/
 * speed offset, applied once, right before firing, never touching the
 * aim itself. [aimErrorDegrees]/[aimErrorSpeedFraction] start equal to
 * the AI's own tuning values for a fair, symmetric baseline; Boo's bigger
 * idea - accuracy improving over time, and different weapon types
 * carrying their own accuracy profile (see PROJECT_STATE.md's "Weapon
 * accuracy & ammo types" design note) - is a deliberate later phase, not
 * this one. This is just "the player's shots are imperfect too, by the
 * same fixed amount the AI's are."
 */
class SlingshotInputProcessor(
    private val launchPoint: Vector2,
    private val viewport: Viewport,
    private val powerScale: Float,
    private val maxSpeed: Float,
    private val speedMultiplier: () -> Float,
    // See the class doc comment's "Player shot accuracy" paragraph. A
    // `<= 0f` value for either disables that part of the jitter entirely,
    // same escape hatch AiTurnController.applyAimError has.
    private val aimErrorDegrees: Float,
    private val aimErrorSpeedFraction: Float,
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

        val speed = minOf(pull.len() * powerScale, maxSpeed) * speedMultiplier()
        val velocity = pull.nor().scl(-speed) // opposite the drag direction
        onFire(applyAimError(velocity))
        return true
    }

    /** Mirrors [AiTurnController.applyAimError] - see this class's doc comment's "Player shot accuracy" paragraph. */
    private fun applyAimError(velocity: Vector2): Vector2 {
        if (aimErrorDegrees <= 0f && aimErrorSpeedFraction <= 0f) return velocity
        val angleErrorRadians = MathUtils.random(-aimErrorDegrees, aimErrorDegrees) * MathUtils.degreesToRadians
        val speedErrorFactor = 1f + MathUtils.random(-aimErrorSpeedFraction, aimErrorSpeedFraction)
        return Vector2(velocity).rotateRad(angleErrorRadians).scl(speedErrorFactor)
    }
}
