package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.math.Rectangle

/**
 * Sept 2026 session - Boo, on-device: the four stacked debug-tuning rows
 * ([GravityDebugController]/[ShotSpeedDebugController]/
 * [WinCountDebugController]/[OrbitalDriftDebugController]) permanently ate
 * a large chunk of the top-right corner's screen real estate. This wraps
 * all four behind a single hamburger icon instead: closed (the default -
 * freeing up the corner is the whole point) draws nothing but the icon
 * itself and none of the four wrapped controllers accept taps at all; open
 * draws the icon plus the same four rows exactly as they always worked -
 * same buttons, same live tuning, nothing about their own behavior changed.
 *
 * The only thing [PlayScreen] adds to its input multiplexers for any of
 * the four debug tools now (see [PlayScreen.show]) - it forwards taps to
 * them itself, only while open, using the same "first one to claim it
 * wins" dispatch [com.badlogic.gdx.InputMultiplexer] itself uses. The four
 * wrapped controllers are otherwise untouched, except each one's own
 * row-position math gained one extra reserved-space term
 * ([GravityDebugController]'s own `HAMBURGER_RESERVE_REFERENCE_PX`, and
 * the matching constant in the other three) so the topmost row starts
 * below this icon instead of overlapping it - same "self-contained,
 * duplicated reference constants" pattern those four classes already used
 * relative to each other before this.
 */
class DebugMenuController(private val wrapped: List<InputAdapter>) : InputAdapter() {

    companion object {
        private const val ICON_SIZE_REFERENCE_PX = 160f
        private const val MARGIN_REFERENCE_PX = 16f
    }

    private val iconSize get() = HudFont.scaled(ICON_SIZE_REFERENCE_PX)
    private val margin get() = HudFont.scaled(MARGIN_REFERENCE_PX)

    /**
     * True once opened - [PlayScreen] reads this to decide whether to
     * draw/allow taps on the four wrapped rows. Starts closed.
     */
    var isOpen: Boolean = false
        private set

    /** Render-space (bottom-left origin) rectangle for the hamburger icon itself - top-right corner, above where the four wrapped rows start. */
    val iconRect: Rectangle
        get() {
            val size = iconSize
            return Rectangle(Gdx.graphics.width - margin - size, Gdx.graphics.height - margin - size, size, size)
        }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val renderX = screenX.toFloat()
        val renderY = Gdx.graphics.height - screenY.toFloat()

        if (iconRect.contains(renderX, renderY)) {
            isOpen = !isOpen
            return true
        }
        if (!isOpen) return false
        return wrapped.any { it.touchDown(screenX, screenY, pointer, button) }
    }
}
