package com.devavona.physicsduel

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx

/**
 * Entry point shared by every platform backend (currently just Android).
 *
 * Phase 5 of the foundation build: this class no longer holds any game logic
 * itself - it's just LibGDX's [Game] class, which owns whichever [com.badlogic.gdx.Screen]
 * is currently active and delegates render/resize/pause/resume/dispose to it.
 * All the physics/ECS/input work from Phases 2-4 now lives in [PlayScreen].
 * See [MenuScreen], [PlayScreen], [PauseScreen], [GameOverScreen] for the
 * actual screen flow.
 */
class PhysicsDuelGame : Game() {

    override fun create() {
        // Phase 6: touching SaveManager here forces the load (and its log
        // line) to happen at cold start, proving a save survives a real
        // process kill - not just a screen change within one run of the app.
        Gdx.app.log("PhysicsDuelGame", "Cold start - previous runCount=${SaveManager.currentRunCount()}")

        // Phase 7: audio needs LibGDX's audio device, which doesn't exist
        // until create() runs - can't load this at object-init time.
        AudioManager.init()

        setScreen(MenuScreen(this))
    }

    override fun dispose() {
        super.dispose() // disposes whichever Screen is currently active
        // HudFont and AudioManager are shared singletons (see their doc
        // comments) - not owned by any one screen, so nothing else disposes
        // them. This is the one true "app is shutting down" hook.
        HudFont.dispose()
        AudioManager.dispose()
    }
}
