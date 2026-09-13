package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2

/**
 * Phase 9's movement-budget/turn controller: the avatar walks along a fixed
 * planet's surface (an angle around the planet's center, at a constant
 * height above it) by spending steps from a per-turn budget - see
 * PROJECT_STATE.md's "Core gameplay loop" entry for the agreed design.
 *
 * **Post-shot movement removed (Sept 2026 session).** This used to split
 * each turn's budget in two - move, then fire, then a second post-shot
 * budget to reposition/take cover before a passTurn() step (triggered by
 * that second budget hitting zero, or an early tap on a dedicated Pass
 * button) actually ended the turn. Boo wanted that gone entirely: "remove post-shot
 * movement entirely - only pre-shot movement (5 paces), then firing ends
 * the turn." There's now exactly one budget, spent before firing; [onFired]
 * itself ends the turn immediately, no separate pass step. [AiTurnController]
 * got the symmetric change - its own post-shot [AiTurnController.fire]
 * repositioning call was removed too, so both sides now play by the same
 * move-then-shoot-then-done rule.
 *
 * The one always-visible tap zone pair (bottom-left corner) steps the
 * avatar around [planetCenter] by [stepAngleDegrees] per tap. [PlayScreen]
 * is expected to call [onFired] exactly once, right after a missile
 * actually launches - this class knows nothing about aiming or firing
 * itself, same separation-of-concerns as [GravityDebugController] only
 * owning gravity tuning.
 *
 * **Orbital drift (Sept 2026 session).** When the avatar's home planet is
 * destroyed while it still has HP, [PlayScreen] hands this class's
 * position over to a real, freely-moving Box2D body instead - see
 * [beginDrift]. [position] then reads straight from
 * [driftPositionOverride] instead of the angle-around-[planetCenter]
 * formula, and [touchDown] stops responding to the move buttons entirely
 * ("lose the walk-around movement budget entirely" - Boo, explicit) since
 * there's no fixed surface left to walk. [reanchor] is the way back once
 * the drifting body actually lands on solid ground again.
 */
class AvatarMovementController(
    private val planetCenter: Vector2,
    private val planetRadius: Float,
    private val heightAboveSurface: Float,
    private val stepsPerPhase: Int,
    private val stepAngleDegrees: Float,
    startAngleDegrees: Float,
    // Phase 12: lets PlayScreen hand off to the AI's turn the moment this
    // one ends - since Sept 2026, that's the instant [onFired] is called,
    // not a separate passTurn() trigger. Defaults to a no-op so every
    // earlier test/usage of this class still compiles unchanged.
    private val onTurnPassed: () -> Unit = {}
) : InputAdapter() {

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
    var stepsRemaining: Int = stepsPerPhase
        private set
    var turnNumber: Int = 1
        private set

    // Orbital drift (Sept 2026 session) - see the class doc comment and
    // [beginDrift]/[reanchor]. Null the entire game unless this side's
    // home planet has been destroyed; while non-null, [position] reads
    // straight from it instead of the angle-around-planetCenter formula.
    private var driftPositionOverride: (() -> Vector2)? = null

    /** The avatar's current world position: [heightAboveSurface] above [planetCenter]'s surface, at [angleDegrees] - or, while drifting, wherever [driftPositionOverride] says the real physics body actually is. */
    val position: Vector2
        get() {
            driftPositionOverride?.let { return it() }
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

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        // Orbital drift - no movement budget while drifting, see the class
        // doc comment. Swallow nothing (return false) so a touch here still
        // falls through to whatever else might want it.
        if (driftPositionOverride != null) return false

        val renderX = screenX.toFloat()
        val renderY = Gdx.graphics.height - screenY.toFloat() // touch input is top-left-origin; button rects are render-space (bottom-left-origin), same flip GravityDebugController does

        return when {
            leftButtonRect.contains(renderX, renderY) -> { move(+1); true }
            rightButtonRect.contains(renderX, renderY) -> { move(-1); true }
            else -> false
        }
    }

    private fun move(direction: Int) {
        if (stepsRemaining <= 0) return
        angleDegrees += direction * stepAngleDegrees
        stepsRemaining--
    }

    /**
     * Called by [PlayScreen] right after a missile actually launches - ends
     * the turn immediately (Sept 2026 session: post-shot movement removed,
     * see class doc comment). Resets the budget and advances [turnNumber]
     * for whoever's turn is next, then fires [onTurnPassed] the same way
     * the old passTurn() used to.
     */
    fun onFired() {
        stepsRemaining = stepsPerPhase
        turnNumber++
        onTurnPassed()
    }

    /**
     * Orbital drift begins: [PlayScreen] calls this the instant it flips
     * this side's Box2D body from Kinematic to Dynamic and gives it a
     * tangential kick, handing [position] over to [positionSupplier] (a
     * closure reading that body's live, physics-driven position) instead
     * of the angle-around-[planetCenter] formula from here on.
     */
    fun beginDrift(positionSupplier: () -> Vector2) {
        driftPositionOverride = positionSupplier
    }

    /**
     * Orbital drift ends: called the instant the drifting body actually
     * lands on solid ground (a surviving planet/moon - see [PlayScreen]'s
     * orbital-drift landing check). Clears [driftPositionOverride] and
     * re-anchors the normal walk-around-a-fixed-point model at
     * [newPlanetCenter]/[newAngleDegrees] - wherever the landing spot
     * naturally puts it - with a full fresh movement budget, same as any
     * other turn boundary.
     */
    fun reanchor(newPlanetCenter: Vector2, newAngleDegrees: Float) {
        driftPositionOverride = null
        planetCenter.set(newPlanetCenter)
        angleDegrees = newAngleDegrees
        stepsRemaining = stepsPerPhase
    }
}
