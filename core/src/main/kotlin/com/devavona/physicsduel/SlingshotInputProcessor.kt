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
 *
 * **Horizon restriction (Sept 2026 session).** Boo: "I don't want the
 * player to ever be able to fire into their own planet." A raw release
 * whose direction points below this position's own local horizon (the
 * tangent line perpendicular to straight-out-from-[planetCenter] here) -
 * the exact same check [currentAimBelowHorizon] uses to hide the aim
 * line/trajectory preview mid-drag - simply doesn't fire at all, same as
 * the other cancel cases in [touchUp]. **Revised after first on-device
 * test**: the original version clamped an illegal release to skim the
 * horizon and still fired it, which Boo found inconsistent with the
 * hidden aim line - "if the aim disappears, then even if you make a
 * shooting gesture, it will not fire." [clampAboveHorizon] is still
 * applied, but now only as a hard safety net AFTER [applyAimError], for
 * the rare case where a raw aim that WAS legal (line showing) gets
 * nudged just below horizon by the error jitter - the player's own
 * intent (a legal release) is honored with a leveled-off shot rather
 * than silently eating a shot they clearly meant to take; an illegal raw
 * aim, matching what they saw on screen, never fires anything at all.
 * This class draws nothing itself - [currentAimBelowHorizon] is purely
 * for [PlayScreen] to read.
 *
 * **Cancel gesture (Sept 2026 session).** Boo: "there needs to be a way
 * to aim and then decide not to fire," but didn't want a dedicated
 * button for it. [touchUp] now also cancels (same as the existing
 * "released right on the character" case) if, at any point during THIS
 * drag, the pull first went out far enough to be a real aim
 * ([PULL_COMMIT_DISTANCE]) and then came back within [AIM_START_RADIUS]
 * of [launchPoint] again - "pull back past your character," the same
 * physical motion as snapping a slingshot back through its own resting
 * point instead of letting it fly. Once that's happened during a drag,
 * releasing ALWAYS cancels, even if the finger is back out aiming
 * somewhere by the time it lifts - the pass-through-center is what
 * commits to "never mind," not the final release position.
 */
class SlingshotInputProcessor(
    private val launchPoint: Vector2,
    // Sept 2026 session - see the class doc comment's "Horizon
    // restriction" and touchUp's clampAboveHorizon for why this is
    // needed: fixed for the whole game (this shooter's own planet never
    // moves), so it's safe to read fresh from touchDown/touchDragged/
    // touchUp without PlayScreen needing to push updates.
    private val planetCenter: Vector2,
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
        // hijack every touch anywhere on screen. Reused by the cancel
        // gesture below as "close enough to the character to count as
        // passing back through it," same radius, same meaning either way.
        private const val AIM_START_RADIUS = 1.5f

        // Sept 2026 session - see the class doc comment's "Cancel gesture"
        // paragraph. How far a pull has to go before it's a real, committed
        // aim direction - below this, a drag that dips back within
        // AIM_START_RADIUS is just touch-down jitter, not a deliberate
        // "pull back past your character."
        private const val PULL_COMMIT_DISTANCE = 2f
    }

    private val touchPoint = Vector2()
    private var aiming = false

    // Sept 2026 session - see the class doc comment's "Cancel gesture"
    // paragraph. pulledPastCommitDistance latches once this drag's pull
    // first reaches PULL_COMMIT_DISTANCE; passedBackThroughCenter then
    // latches once, after that, the pull returns within AIM_START_RADIUS -
    // both reset at the start of every new drag in touchDown.
    private var pulledPastCommitDistance = false
    private var passedBackThroughCenter = false

    /** The live pull vector (dragCurrent - launchPoint) while aiming, or null. Read-only for [PlayScreen]'s debug line. */
    var currentAimLine: Vector2? = null
        private set

    /**
     * True while aiming if the shot THIS pull would currently fire points
     * below this position's own local horizon - see the class doc
     * comment's "Horizon restriction" paragraph. [PlayScreen] uses this to
     * hide the aim-line/trajectory-preview as a visual tell; the actual
     * shot is still safely clamped by [clampAboveHorizon] regardless of
     * whether anything reads this.
     */
    val currentAimBelowHorizon: Boolean
        get() {
            val pull = currentAimLine ?: return false
            if (pull.isZero(0.01f)) return false
            return isBelowHorizon(launchPoint, Vector2(pull).scl(-1f))
        }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat()))
        if (touchPoint.dst(launchPoint) > AIM_START_RADIUS) return false
        aiming = true
        pulledPastCommitDistance = false
        passedBackThroughCenter = false
        currentAimLine = Vector2(touchPoint).sub(launchPoint)
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!aiming) return false
        viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat()))
        currentAimLine = Vector2(touchPoint).sub(launchPoint)
        trackCancelGesture(currentAimLine!!)
        return true
    }

    /** See the class doc comment's "Cancel gesture" paragraph. */
    private fun trackCancelGesture(pull: Vector2) {
        val distance = pull.len()
        if (distance >= PULL_COMMIT_DISTANCE) pulledPastCommitDistance = true
        if (pulledPastCommitDistance && distance <= AIM_START_RADIUS) passedBackThroughCenter = true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (!aiming) return false
        aiming = false
        viewport.unproject(touchPoint.set(screenX.toFloat(), screenY.toFloat()))
        val pull = Vector2(touchPoint).sub(launchPoint)
        currentAimLine = null

        if (pull.isZero(0.01f)) return true // treat a near-zero drag as "cancelled," not a limp shot
        if (passedBackThroughCenter) return true // pulled back past the character during this drag - see trackCancelGesture

        val speed = minOf(pull.len() * powerScale, maxSpeed) * speedMultiplier()
        val velocity = pull.nor().scl(-speed) // opposite the drag direction
        // Sept 2026 session - matches the hidden aim line exactly: if this
        // raw release is illegal, nothing fires at all, full stop - see
        // the class doc comment's "Horizon restriction" paragraph for why
        // this replaced the original clamp-and-fire-anyway behavior.
        if (isBelowHorizon(launchPoint, velocity)) return true
        onFire(clampAboveHorizon(launchPoint, applyAimError(velocity)))
        return true
    }

    /** Mirrors [AiTurnController.applyAimError] - see this class's doc comment's "Player shot accuracy" paragraph. */
    private fun applyAimError(velocity: Vector2): Vector2 {
        if (aimErrorDegrees <= 0f && aimErrorSpeedFraction <= 0f) return velocity
        val angleErrorRadians = MathUtils.random(-aimErrorDegrees, aimErrorDegrees) * MathUtils.degreesToRadians
        val speedErrorFactor = 1f + MathUtils.random(-aimErrorSpeedFraction, aimErrorSpeedFraction)
        return Vector2(velocity).rotateRad(angleErrorRadians).scl(speedErrorFactor)
    }

    /** True if [velocity], fired from [origin], points below [origin]'s own local horizon - see [clampAboveHorizon]. */
    private fun isBelowHorizon(origin: Vector2, velocity: Vector2): Boolean {
        val radialOutward = Vector2(origin).sub(planetCenter).nor()
        return velocity.dot(radialOutward) < 0f
    }

    /**
     * Mirrors [AiTurnController]'s own private copy of this same logic
     * exactly (that class needs its own since it clamps many hypothetical
     * candidates, not one live drag) - see this class's doc comment's
     * "Horizon restriction" paragraph. Leaves [velocity] untouched if it's
     * already legal; otherwise levels it off to skim exactly along
     * [origin]'s local horizon, same speed, keeping whichever side
     * (left/right along the horizon) the illegal direction was leaning
     * toward rather than snapping to one fixed side.
     */
    private fun clampAboveHorizon(origin: Vector2, velocity: Vector2): Vector2 {
        val radialOutward = Vector2(origin).sub(planetCenter).nor()
        if (velocity.dot(radialOutward) >= 0f) return velocity
        val speed = velocity.len()
        val tangentA = Vector2(-radialOutward.y, radialOutward.x)
        val tangentB = Vector2(radialOutward.y, -radialOutward.x)
        val tangent = if (velocity.dot(tangentA) >= velocity.dot(tangentB)) tangentA else tangentB
        return tangent.nor().scl(speed)
    }
}
