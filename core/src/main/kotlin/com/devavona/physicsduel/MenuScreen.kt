package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch

/** Menu screen: solid color, title + prompt text, tap anywhere to start a fresh run. */
class MenuScreen(private val game: PhysicsDuelGame) : InputAdapter(), Screen {

    private val camera = OrthographicCamera()
    private val batch = SpriteBatch()

    override fun show() {
        Gdx.input.inputProcessor = this
        resizeCamera()
    }

    private fun resizeCamera() {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f) // near-black, distinct from play's navy
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        batch.projectionMatrix = camera.combined

        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val font = HudFont.font

        batch.begin()
        val title = "PHYSICS DUEL"
        font.draw(batch, title, (width - HudFont.widthOf(title)) / 2f, height * 0.62f)

        val prompt = "Tap to Play"
        font.draw(batch, prompt, (width - HudFont.widthOf(prompt)) / 2f, height * 0.5f)

        // Phase 6 tie-in: proves persisted state (SaveManager) reaches the
        // screen, not just Logcat - the visible number should match whatever
        // was last logged as "Loaded save: runCount=N" at cold start.
        val runs = "Runs completed: ${SaveManager.currentRunCount()}"
        font.draw(batch, runs, (width - HudFont.widthOf(runs)) / 2f, height * 0.14f)
        batch.end()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        AudioManager.playTap()
        game.setScreen(PlayScreen(game)) // fresh instance every time - a brand new run
        return true
    }

    override fun resize(width: Int, height: Int) {
        resizeCamera()
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        batch.dispose()
    }
}
