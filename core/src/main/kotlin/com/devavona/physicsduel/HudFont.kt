package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
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
 *
 * **Density scaling (hardening pass, post-Phase-7):** [BitmapFont.setScale]
 * is a raw pixel multiplier - on its own it has no idea what physical size
 * a pixel actually is on a given screen. Phase 7 originally hardcoded
 * [REFERENCE_SCALE] (1.4) purely by eye, at whatever pixel density Boo's
 * one test device happens to have. That's correct on that device, but the
 * exact same "1.4x the raw bitmap" would render at a visibly different
 * PHYSICAL text size on a phone with a different pixel density - too small
 * on a higher-density screen, too large on a lower-density one - even on a
 * similarly-sized screen. Multiplying by [com.badlogic.gdx.Graphics.getDensity]
 * fixes that: on the Android backend, that value IS Android's own
 * `DisplayMetrics.density` (the same number `dp`/`sp` units are defined
 * against - 1.0 at the historical 160dpi baseline, ~2.0-3.5 on most modern
 * phones), so dividing [REFERENCE_SCALE] by [REFERENCE_DENSITY] recovers a
 * density-independent size, and multiplying by the *current* device's real
 * density converts it back to that device's correct pixel scale - the same
 * idea as Android's own dp/sp units: a consistent logical size everywhere,
 * not a fixed pixel count everywhere.
 *
 * [REFERENCE_DENSITY] is Boo's actual test device's confirmed density
 * (2.625, a Samsung Galaxy Fold - reported via a diagnostic Logcat line
 * that has since been removed, its job done), so [REFERENCE_SCALE] is
 * exactly what renders on that device - change it and every device scales
 * proportionally from the new value, [REFERENCE_DENSITY] doesn't need to
 * change too.
 *
 * [REFERENCE_SCALE] was bumped from its original `1.4` to `2.4` (~70%
 * larger) after Boo confirmed the density-scaling change worked but the
 * text itself was uncomfortably small - a separate, deliberate size
 * decision from the scaling-consistency fix above, not a side effect of it.
 */
object HudFont {
    private const val REFERENCE_SCALE = 2.4f
    private const val REFERENCE_DENSITY = 2.625f // Boo's test device, confirmed via Logcat

    private val densityFactor: Float get() = Gdx.graphics.density / REFERENCE_DENSITY

    val font = BitmapFont().apply { data.setScale(REFERENCE_SCALE * densityFactor) }
    private val layout = GlyphLayout()

    /** Width in pixels [text] would render at, for simple centering math. */
    fun widthOf(text: String): Float {
        layout.setText(font, text)
        return layout.width
    }

    /**
     * Converts a pixel value tuned by eye on the reference device (see the
     * class doc comment) into the equivalent size on the *current* device,
     * the same way [font]'s own size is scaled. Use this for any fixed-pixel
     * UI offset (a screen-edge margin, for example) instead of a bare
     * literal, so it stays a consistent logical size across devices instead
     * of a fixed number of raw pixels.
     */
    fun scaled(referenceDevicePixels: Float): Float = referenceDevicePixels * densityFactor

    fun dispose() {
        font.dispose()
    }
}
