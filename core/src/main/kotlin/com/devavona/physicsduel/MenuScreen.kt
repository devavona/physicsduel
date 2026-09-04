package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20

/** Stub menu screen: solid color, tap anywhere to start a fresh run. */
class MenuScreen(private val game: PhysicsDuelGame) : InputAdapter(), Screen {

    override fun show() {
        Gdx.input.inputProcessor = this
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f) // near-black, distinct from play's navy
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        game.setScreen(PlayScreen(game)) // fresh instance every time - a brand new run
        return true
    }

    override fun resize(width: Int, height: Int) {}
    override fun pause() {}
    override fun resume() {}
    override fun hide() {}
    override fun dispose() {}
}
