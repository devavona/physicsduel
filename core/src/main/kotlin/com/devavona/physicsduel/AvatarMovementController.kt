package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2

/**
 * Phase 9's movement-budget/turn controller: the avatar walks along a fixed
 * planet's surface (an angle around the planet's center, at a constant
 * height above it) by spending steps from a budget that resets in two
 * halves per turn - see PROJECT_STATE.md's "Core gameplay loop" entry for
 * the agreed design (move to line up an angle, take one shot, move again to
 * take cover, then the turn passes). There's no AI opponent yet, so
 * [passTurn] - triggered automatically once the post-shot budget hits zero,
 * or early via a tap on [passButtonRect] - just starts a fresh turn instead
 * of handing off to anyone. This phase is about proving the movement/
 * budget/turn-boundary mechanic feels right in isolation, the same scoping
 * spirit as Phase 8's aiming-only slice.
 *
 * Two always-visible tap zones (bottom-left corner) step the avatar around
 * [planetCenter] by [stepAngleDegrees] per tap; a third zone, bottom-right,
 * only matters during [Phase.POST_SHOT] and lets the player end their
 * post-shot repositioning early instead of using every remaining step.
 * [PlayScreen] is expected to call [onFired] exactly once, right after a
 * missile actually launches - this class knows nothing about aiming or
 * firing itself, same separation-of-concerns as [GravityDebugController]
 * only owning gravity tuning.
 */
class AvatarMovementController(
    private val planetCenter: Vector2,
    private val planetRadius: Float,
    private val heightAboveSurface: Float,
    private val stepsPerPhase: Int,
    private val stepAngleDegrees: Float,
    startAngleDegrees: Float
) : InputAdapter() {

    enum class Phase { PRE_SHOT, POST_SHOT }

    companion object {
        private const val BUTTON_SIZE_REFERENCE_PX = 160f

        // Bumped from an original 16f: at that distance the left ("<") move
        // button sat inside Android's left-edge back-gesture zone, which
        // swallows touches there before the app ever sees them - confirmed
        // on-device (the left button did nothing, the right one - already
        // ~190px further from the edge - worked fine). This clears that
        // zone with margin to spare (140px is well past the typical ~24dp
        // edge width on Boo's reference device density).
        private const val MARGIN_REFERENCE_PX = 140f
        private const val BUTTON_GAP_REFERENCE_PX = 16f
    }

    private val buttonSize get() = HudFont.scaled(BUTTON_SIZE_REFERENCE_PX)
    private val margin get() = HudFont.scaled(MARGIN_REFERENCE_PX)
    private val gap get() = HudFont.scaled(BUTTON_GAP_REFERENCE_PX)

    var angleDegrees: Float = startAngleDegrees
        private set
    var phase: Phase = Phase.PRE_SHOT
        private set
    var stepsRemaining: Int = stepsPerPhase
        private set
    var turnNumber: Int = 1
        private set

    /** True only during [Phase.PRE_SHOT] - [PlayScreen] gates firing on this so a shot can't sneak in mid post-shot repositioning. */
    val canFire: Boolean get() = phase == Phase.PRE_SHOT

    /** The avatar's current world position: [heightAboveSurface] above [planetCenter]'s surface, at [angleDegrees]. */
    val position: Vector2
        get() {
            val rad = angleDegrees * MathUtils.degreesToRadians
            val r = planetRadius + heightAboveSurface
            return Vector2(
                planetCenter.x + r * MathUtils.cos(rad),
                planetCenter.y + r * MathUtils.sin(rad)
            )
        }

    /** Bottom-left corner: "<" move button (increases [angleDegrees]). */
    val leftButtonRect: Rectangle
        get() {
            val size = buttonSize
            return Rectangle(margin, margin, size, size)
        }

    /** Immediately right of [leftButtonRect]: ">" move button (decreases [angleDegrees]). */
    val rightButtonRect: Rectangle
        get() {
            val size = buttonSize
            return Rectangle(margin + size + gap, margin, size, size)
        }

    /** Bottom-right corner - only meaningful, and only drawn by [PlayScreen], during [Phase.POST_SHOT]: ends the turn early. */
    val passButtonRect: Rectangle
        get() {
            val size = buttonSize
            return Rectangle(Gdx.graphics.width - margin - size, margin, size, size)
        }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val renderX = screenX.toFloat()
        val renderY = Gdx.graphics.height - screenY.toFloat() // touch input is top-left-origin; button rects are render-space (bottom-left-origin), same flip GravityDebugController does

        return when {
            leftButtonRect.contains(renderX, renderY) -> { move(+1); true }
            rightButtonRect.contains(renderX, renderY) -> { move(-1); true }
            phase == Phase.POST_SHOT && passButtonRect.contains(renderX, renderY) -> { passTurn(); true }
            else -> false
        }
    }

    private fun move(direction: Int) {
        if (stepsRemaining <= 0) return
        angleDegrees += direction * stepAngleDegrees
        stepsRemaining--
        if (phase == Phase.POST_SHOT && stepsRemaining == 0) passTurn()
    }

    /** Called by [PlayScreen] right after a missile actually launches - moves from the pre-shot budget into the post-shot one. */
    fun onFired() {
        if (phase != Phase.PRE_SHOT) return
        phase = Phase.POST_SHOT
        stepsRemaining = stepsPerPhase
    }

    private fun passTurn() {
        phase = Phase.PRE_SHOT
        stepsRemaining = stepsPerPhase
        turnNumber++
    }
}
