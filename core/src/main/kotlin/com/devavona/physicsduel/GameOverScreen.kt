package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch

/**
 * Game-over screen: solid color, "GAME OVER" + prompt text, tap anywhere
 * back to the menu.
 *
 * Currently only reachable via [PauseScreen]'s "end run" tap zone, as a
 * stand-in - there's no real win/lose condition yet since that's gameplay,
 * not foundation. A real trigger (and the state that produced it) will
 * replace that path once actual game rules exist.
 */
class GameOverScreen(private val game: PhysicsDuelGame) : InputAdapter(), Screen {

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
        Gdx.gl.glClearColor(0.25f, 0.05f, 0.05f, 1f) // dark red
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        batch.projectionMatrix = camera.combined

        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val font = HudFont.font

        batch.begin()
        val title = "GAME OVER"
        font.draw(batch, title, (width - HudFont.widthOf(title)) / 2f, height * 0.56f)
        val prompt = "Tap to continue"
        font.draw(batch, prompt, (width - HudFont.widthOf(prompt)) / 2f, height * 0.44f)
        batch.end()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        AudioManager.playTap()
        game.setScreen(MenuScreen(game))
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
