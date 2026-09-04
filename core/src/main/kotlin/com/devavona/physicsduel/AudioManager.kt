package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound

/**
 * Generic audio hook for short UI/sound-effect cues, loaded once and reused.
 *
 * Phase 7 only wires up a single cue (a synthesized tap/click at
 * `audio/tap.wav`, played on every screen-transition tap) to prove the
 * plumbing on-device - a future phase adds more [Sound]s the same way,
 * keyed by name, rather than each feature loading its own audio ad hoc.
 * Deliberately not touching [com.badlogic.gdx.audio.Music] yet (streamed,
 * for background music) - that's a real future addition, not needed to
 * prove this pattern works.
 *
 * [init] must be called once, after LibGDX's audio device exists
 * (i.e. from [PhysicsDuelGame.create], not from a static initializer) -
 * loading is wrapped in a try/catch so a missing/corrupt asset degrades to
 * "no sound" instead of crashing the app, matching [SaveManager]'s
 * fail-soft philosophy for anything touching disk/IO.
 */
object AudioManager {

    private const val TAG = "AudioManager"
    private var tapSound: Sound? = null

    fun init() {
        tapSound = try {
            Gdx.audio.newSound(Gdx.files.internal("audio/tap.wav"))
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Failed to load tap.wav - continuing without audio", e)
            null
        }
    }

    fun playTap() {
        tapSound?.play(0.6f)
    }

    fun dispose() {
        tapSound?.dispose()
        tapSound = null
    }
}
