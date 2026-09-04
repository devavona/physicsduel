package com.devavona.physicsduel

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout

/**
 * One shared bitmap font for every screen's on-screen text.
 *
 * Deliberately the plainest possible option for this foundation phase -
 * LibGDX's built-in default font, no external font asset, no atlas. Good
 * enough to prove the HUD/overlay plumbing (draw text on top of a scene,
 * from any screen, positioned and centered correctly at any screen size)
 * works; swapping in a real font later is a drop-in replacement for this
 * one object - nothing that uses [HudFont] needs to change.
 *
 * A single shared instance, not one per screen: [BitmapFont] is a
 * GPU-backed resource, and several screens (e.g. [PlayScreen], [MenuScreen])
 * are recreated fresh on every run - creating a new font each time would
 * leak a texture per run. Disposed exactly once, at real app shutdown, by
 * [PhysicsDuelGame.dispose].
 */
object HudFont {
    val font = BitmapFont().apply { data.setScale(1.4f) }
    private val layout = GlyphLayout()

    /** Width in pixels [text] would render at, for simple centering math. */
    fun widthOf(text: String): Float {
        layout.setText(font, text)
        return layout.width
    }

    fun dispose() {
        font.dispose()
    }
}
