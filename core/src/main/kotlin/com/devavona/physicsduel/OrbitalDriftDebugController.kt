package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle

/**
 * Debug-only tool (same spirit as [GravityDebugController]/
 * [ShotSpeedDebugController]/[WinCountDebugController]): two on-screen tap
 * zones that adjust [OrbitalDriftTuning.speedFraction] live, up or down, by
 * [STEP] per tap - added so "what orbital-drift kick speed stops a
 * destroyed-planet character from diving into the star" could be dialed in
 * live on-device instead of guessed at blind (see [OrbitalDriftTuning]'s own
 * doc comment for the game-balance problem this is meant to fix). Stacked
 * directly below [WinCountDebugController]'s row (same right-edge
 * alignment), so all four live-tuning tools sit together in the same
 * corner. Not gated behind any build flag, for the same reason
 * [GravityDebugController] isn't - see that class's doc comment.
 *
 * [MAX_SPEED_FRACTION] deliberately goes past 1.0 (true circular-orbit
 * speed) - useful for feeling out what an escape-trajectory kick looks
 * like too, not just the stable-orbit target value.
 */
class OrbitalDriftDebugController(private val orbitalDriftTuning: OrbitalDriftTuning) : InputAdapter() {

    companion object {
        private const val STEP = 0.1f
        private const val MIN_SPEED_FRACTION = 0.1f
        private const val MAX_SPEED_FRACTION = 2f

        private const val BUTTON_SIZE_REFERENCE_PX = 160f
        private const val MARGIN_REFERENCE_PX = 16f
        private const val LABEL_RESERVE_REFERENCE_PX = 70f

        // Vertical gap between this row and WinCountDebugController's row
        // directly above it.
        private const val ROW_GAP_REFERENCE_PX = 16f

        // Sept 2026 session - see GravityDebugController's own constant of
        // the same name.
        private const val HAMBURGER_RESERVE_REFERENCE_PX = 176f
    }

    private val buttonSize get() = HudFont.scaled(BUTTON_SIZE_REFERENCE_PX)
    private val margin get() = HudFont.scaled(MARGIN_REFERENCE_PX)
    private val labelReserve get() = HudFont.scaled(LABEL_RESERVE_REFERENCE_PX)
    private val rowGap get() = HudFont.scaled(ROW_GAP_REFERENCE_PX)
    private val hamburgerReserve get() = HudFont.scaled(HAMBURGER_RESERVE_REFERENCE_PX)

    /**
     * Where this row's "top" should be to sit directly below
     * [WinCountDebugController]'s row - same self-contained-reference-
     * constants approach that class uses relative to
     * [ShotSpeedDebugController] (one more label-reserve/button/gap step
     * further down still, since this is the fourth stacked row).
     */
    private val rowTopReference: Float
        get() = Gdx.graphics.height - margin - hamburgerReserve - labelReserve - buttonSize - rowGap - labelReserve - buttonSize - rowGap - labelReserve - buttonSize - rowGap

    /** Where [PlayScreen] should draw the "Drift Speed xN.N" label's top edge. */
    val labelBaselineY: Float get() = rowTopReference

    private val buttonRowTop: Float get() = rowTopReference - labelReserve

    /** Render-space rectangle for the "+" button - right-aligned, same column as the other debug rows. */
    val plusButtonRect: Rectangle
        get() {
            val size = buttonSize
            return Rectangle(Gdx.graphics.width - margin - size, buttonRowTop - size, size, size)
        }

    /** Render-space rectangle for the "-" button, immediately to the left of [plusButtonRect]. */
    val minusButtonRect: Rectangle
        get() {
            val size = buttonSize
            val plusX = Gdx.graphics.width - margin - size
            return Rectangle(plusX - margin - size, buttonRowTop - size, size, size)
        }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val renderX = screenX.toFloat()
        val renderY = toRenderSpace(screenY)

        return when {
            minusButtonRect.contains(renderX, renderY) -> {
                adjust(-STEP)
                true
            }
            plusButtonRect.contains(renderX, renderY) -> {
                adjust(STEP)
                true
            }
            else -> false
        }
    }

    private fun adjust(delta: Float) {
        orbitalDriftTuning.speedFraction = MathUtils.clamp(orbitalDriftTuning.speedFraction + delta, MIN_SPEED_FRACTION, MAX_SPEED_FRACTION)
    }

    private fun toRenderSpace(screenY: Int): Float = Gdx.graphics.height - screenY.toFloat()
}
