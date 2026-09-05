package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle

/**
 * Debug-only tool (Phase 8 tuning aid): two on-screen tap zones that adjust
 * [GravitySystem.gravityMultiplier] live, up or down, by [STEP] per tap -
 * added because "how strong should gravity feel" turned out to be a
 * subjective feel question, not something to guess at numerically from a
 * text description back and forth. Changing the multiplier takes effect
 * immediately, including on an already-in-flight missile, since
 * [GravitySystem.applyForces] reads it fresh every physics tick.
 *
 * Deliberately NOT gated behind any build flag - this project has no
 * release/Play Store build to worry about leaking a debug control into
 * (see PROJECT_STATE.md's "Debug-only signing" decision) - so it's fine to
 * leave wired in permanently as a standing tuning tool for future phases,
 * not just Phase 8.
 *
 * Touch input's `screenY` has its origin at the top-left (Y grows
 * downward) - the opposite of the bottom-left-origin, Y-up ortho camera
 * [PlayScreen] renders its HUD with. [toRenderSpace] is the one place that
 * flip happens, so the zones this class hit-tests against and the
 * rectangles [PlayScreen] draws can never drift out of sync with each other.
 */
class GravityDebugController(private val gravitySystem: GravitySystem) : InputAdapter() {

    companion object {
        private const val STEP = 0.1f
        private const val MIN_MULTIPLIER = 0.1f
        private const val MAX_MULTIPLIER = 3f

        // Bumped from an original 100f after Boo found the buttons too
        // small to comfortably tap on-device.
        private const val BUTTON_SIZE_REFERENCE_PX = 160f
        private const val MARGIN_REFERENCE_PX = 16f

        // Vertical space reserved above the button row for PlayScreen's
        // "Gravity xN.N" label (see [labelBaselineY]) - the original version
        // tried to draw that label *above* the button row by adding to the
        // row's already-near-the-top Y position, which pushed it past the
        // top edge of the screen entirely. Reserving this space up front and
        // hanging the button row below it, instead of trying to place the
        // label above an already-placed row, keeps both always on-screen.
        private const val LABEL_RESERVE_REFERENCE_PX = 70f
    }

    private val buttonSize get() = HudFont.scaled(BUTTON_SIZE_REFERENCE_PX)
    private val margin get() = HudFont.scaled(MARGIN_REFERENCE_PX)
    private val labelReserve get() = HudFont.scaled(LABEL_RESERVE_REFERENCE_PX)

    /** Top edge (render-space Y) of the button row - below [margin] + [labelReserve]'s worth of space held for the label above it. */
    private val buttonRowTop: Float get() = Gdx.graphics.height - margin - labelReserve

    /** Where [PlayScreen] should draw the "Gravity xN.N" label's top edge - within the space [labelReserve] holds open above the button row. */
    val labelBaselineY: Float get() = Gdx.graphics.height - margin

    /** Render-space (bottom-left origin) rectangle for the "+" button - top-right corner. For [PlayScreen] to draw and this class to hit-test against. */
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
        gravitySystem.gravityMultiplier = MathUtils.clamp(gravitySystem.gravityMultiplier + delta, MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    private fun toRenderSpace(screenY: Int): Float = Gdx.graphics.height - screenY.toFloat()
}
