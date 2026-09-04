package com.devavona.physicsduel

import com.badlogic.gdx.Game

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
        setScreen(MenuScreen(this))
    }
}
