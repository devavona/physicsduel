package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle

/**
 * Debug-only tool (Phase 17 tuning aid, same spirit as
 * [GravityDebugController]): two on-screen tap zones that adjust
 * [ShotSpeedTuning.multiplier] live, up or down, by [STEP] per tap - added
 * because "how fast should a shot feel" turned out to be exactly the same
 * kind of subjective feel question [GravityDebugController] already solved
 * for gravity's strength, not something to guess at numerically from a text
 * description back and forth. Stacked directly below
 * [GravityDebugController]'s own button row (see [rowTopReference]), same
 * right-edge alignment, so both live-tuning tools sit together in the same
 * corner. Not gated behind any build flag, for the same reason
 * [GravityDebugController] isn't - see that class's doc comment.
 */
class ShotSpeedDebugController(private val shotSpeedTuning: ShotSpeedTuning) : InputAdapter() {

    companion object {
        private const val STEP = 0.1f
        private const val MIN_MULTIPLIER = 0.1f
        private const val MAX_MULTIPLIER = 2f

        private const val BUTTON_SIZE_REFERENCE_PX = 160f
        private const val MARGIN_REFERENCE_PX = 16f
        private const val LABEL_RESERVE_REFERENCE_PX = 70f

        // Vertical gap between this row and GravityDebugController's row
        // directly above it.
        private const val ROW_GAP_REFERENCE_PX = 16f
    }

    private val buttonSize get() = HudFont.scaled(BUTTON_SIZE_REFERENCE_PX)
    private val margin get() = HudFont.scaled(MARGIN_REFERENCE_PX)
    private val labelReserve get() = HudFont.scaled(LABEL_RESERVE_REFERENCE_PX)
    private val rowGap get() = HudFont.scaled(ROW_GAP_REFERENCE_PX)

    /**
     * Where this row's "top" should be to sit directly below
     * [GravityDebugController]'s row - computed from the same reference
     * constants that row uses (`Gdx.graphics.height - margin` is its own
     * starting point; subtracting its label reserve and button height gets
     * to its bottom edge, then [rowGap] leaves a gap before this row
     * starts). Self-contained on purpose - doesn't read
     * GravityDebugController's actual rects at runtime, just relies on
     * both classes using the same reference sizes, since both are built
     * once by the same [PlayScreen].
     */
    private val rowTopReference: Float
        get() = Gdx.graphics.height - margin - labelReserve - buttonSize - rowGap

    /** Where [PlayScreen] should draw the "Shot Speed xN.N" label's top edge. */
    val labelBaselineY: Float get() = rowTopReference

    private val buttonRowTop: Float get() = rowTopReference - labelReserve

    /** Render-space rectangle for the "+" button - right-aligned, same column as GravityDebugController's. */
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
        shotSpeedTuning.multiplier = MathUtils.clamp(shotSpeedTuning.multiplier + delta, MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    private fun toRenderSpace(screenY: Int): Float = Gdx.graphics.height - screenY.toFloat()
}
