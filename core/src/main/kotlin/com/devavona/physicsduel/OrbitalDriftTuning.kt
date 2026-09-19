package com.devavona.physicsduel

/**
 * Live-tunable fraction of the true circular-orbit speed applied to a
 * character's initial "flung into orbit" kick when their home planet is
 * destroyed - see [PlayScreen.driftKickVelocity]. The drift-physics
 * equivalent of [ShotSpeedTuning] - read fresh at the moment of the kick,
 * so adjusting this live (see [OrbitalDriftDebugController]) takes effect
 * the next time a planet is destroyed, no rebuild needed.
 *
 * Sept 2026 session - added directly in response to Boo's own feedback:
 * at the original fixed 0.2 (a fifth of true circular-orbit speed), a
 * drifting character's orbit decayed fast enough that it dove into the
 * star almost every time - real orbital mechanics, not a bug (a tangential
 * kick well under circular-orbit speed leaves the star inside the
 * resulting ellipse's close approach) - which made destroying the enemy's
 * planet a near-guaranteed kill and always the stronger strategy over
 * aiming at characters directly. [DEFAULT_SPEED_FRACTION] is now `1.0` -
 * true, non-decaying circular-orbit speed - Boo's own confirmed-good value
 * from on-device testing with [OrbitalDriftDebugController]'s live slider
 * ("setting speed to 1 seems decent"), the same live-tune-then-bake-in-
 * the-default workflow [ShotSpeedTuning]'s own default came from. The
 * slider stays wired in afterward, same standing precedent as every other
 * debug tool here - not removed just because a good default was found.
 */
class OrbitalDriftTuning {
    companion object {
        const val DEFAULT_SPEED_FRACTION = 1.0f
    }

    var speedFraction: Float = DEFAULT_SPEED_FRACTION
}
