package com.devavona.physicsduel

/**
 * Live-tunable multiplier applied to every shot's launch speed - player and
 * AI alike - the shot-speed equivalent of [GravitySystem.gravityMultiplier].
 * Doesn't compute anything itself; whoever's about to compute a shot's
 * velocity ([SlingshotInputProcessor], [AiTurnController], [PlayScreen]'s
 * aim trajectory preview) reads [multiplier] fresh at that moment, the same
 * way [GravitySystem.applyForces] reads its own multiplier fresh every
 * physics tick - so adjusting this live (see [ShotSpeedDebugController])
 * takes effect on the very next shot fired, no rebuild needed.
 *
 * **Phase 17: added directly in response to Boo's own feedback** that shot
 * speed felt "very mathmatical and precise... too fast" for the kind of
 * game this is meant to be - gravity's pull builds up over *time in
 * flight*, so a shot that crosses the whole scene in a fraction of a second
 * barely gives gravity (or the player) time to do anything visible, however
 * accurate the underlying physics is. [DEFAULT_MULTIPLIER] (0.4) is Boo's
 * own confirmed-good value from on-device testing - the first-guess 0.5
 * default from that discussion, dialed down slightly further with
 * [ShotSpeedDebugController]'s live +/- buttons until it felt right.
 */
class ShotSpeedTuning {
    companion object {
        const val DEFAULT_MULTIPLIER = 0.4f
    }

    var multiplier: Float = DEFAULT_MULTIPLIER
}
