package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20

/**
 * Stub game-over screen: solid color, tap anywhere back to the menu.
 *
 * Currently only reachable via [PauseScreen]'s "end run" tap zone, as a
 * stand-in - there's no real win/lose condition yet since that's gameplay,
 * not foundation. A real trigger (and the state that produced it) will
 * replace that path once actual game rules exist.
 */
class GameOverScreen(private val game: PhysicsDuelGame) : InputAdapter(), Screen {

    override fun show() {
        Gdx.input.inputProcessor = this
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.25f, 0.05f, 0.05f, 1f) // dark red
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        game.setScreen(MenuScreen(game))
        return true
    }

    override fun resize(width: Int, height: Int) {}
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    override fun dispose() {}
}
