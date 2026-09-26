package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Rectangle

/**
 * Debug-only tool (Step D1 tuning aid, same spirit as [GravityDebugController]/
 * [ShotSpeedDebugController]): two on-screen tap zones that adjust
 * [SaveManager]'s persistent win count directly - "+1 Win" and "Reset" -
 * so the Campaign progression ladder's 5/20/30-win tiers (see
 * PROJECT_STATE.md's "Campaign progression ladder" and "Phase 33, Step D"
 * entries) can be tested on-demand instead of needing to actually grind
 * real wins every time this area of the game gets touched again. Stacked
 * directly below [ShotSpeedDebugController]'s own button row (see
 * [rowTopReference]), same right-edge alignment, so all three live-tuning
 * tools sit together in the same corner. Not gated behind any build flag,
 * for the same reason [GravityDebugController] isn't - see that class's
 * doc comment.
 *
 * Unlike the two tuning controllers above, this doesn't just nudge an
 * in-memory multiplier - [SaveManager.recordWin]/[SaveManager.resetWinCount]
 * both persist immediately (the same corruption-safe write [SaveManager]
 * already uses for every other save). The new win count only actually
 * takes effect on the NEXT new game - [PlayScreen]'s own `campaignTier`
 * reads [SaveManager.currentWinCount] exactly once, in its property
 * initializer, when that `PlayScreen` instance is first constructed - not
 * live mid-match. Tapping either button mid-game will NOT change the
 * current match's field size or planet/character counts; back out to the
 * menu and start a new game to see the new tier take effect.
 *
 * "Reset" also happens to be the eventual "reset progress to zero" option
 * the ladder design calls for at 30 wins ("Campaign progression ladder"
 * above) - today it's only reachable through this debug control rather
 * than a real in-game menu option, which is fine for testing but isn't
 * meant to be the permanent way a player resets their campaign.
 */
class WinCountDebugController : InputAdapter() {

    companion object {
        private const val BUTTON_SIZE_REFERENCE_PX = 160f
        private const val MARGIN_REFERENCE_PX = 16f
        private const val LABEL_RESERVE_REFERENCE_PX = 70f
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
     * [ShotSpeedDebugController]'s row - same self-contained-reference-
     * constants approach that class uses relative to
     * [GravityDebugController] (one more label-reserve/button/gap step
     * further down, since this is the third stacked row, not the second).
     */
    private val rowTopReference: Float
        get() = Gdx.graphics.height - margin - hamburgerReserve - labelReserve - buttonSize - rowGap - labelReserve - buttonSize - rowGap

    /** Where [PlayScreen] should draw the "Wins: N" label's top edge. */
    val labelBaselineY: Float get() = rowTopReference

    private val buttonRowTop: Float get() = rowTopReference - labelReserve

    /** Render-space rectangle for the "+1 Win" button - right-aligned, same column as the other debug rows. */
    val plusButtonRect: Rectangle
        get() {
            val size = buttonSize
            return Rectangle(Gdx.graphics.width - margin - size, buttonRowTop - size, size, size)
        }

    /** Render-space rectangle for the "Reset" button, immediately to the left of [plusButtonRect]. */
    val resetButtonRect: Rectangle
        get() {
            val size = buttonSize
            val plusX = Gdx.graphics.width - margin - size
            return Rectangle(plusX - margin - size, buttonRowTop - size, size, size)
        }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val renderX = screenX.toFloat()
        val renderY = toRenderSpace(screenY)

        return when {
            resetButtonRect.contains(renderX, renderY) -> {
                SaveManager.resetWinCount()
                true
            }
            plusButtonRect.contains(renderX, renderY) -> {
                SaveManager.recordWin()
                true
            }
            else -> false
        }
    }

    private fun toRenderSpace(screenY: Int): Float = Gdx.graphics.height - screenY.toFloat()
}
