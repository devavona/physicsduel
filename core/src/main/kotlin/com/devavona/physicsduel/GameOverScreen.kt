package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch

/**
 * Game-over screen: solid color, VICTORY/DEFEAT + prompt text, tap
 * anywhere to start a fresh run.
 *
 * Phase 22 - now a real outcome screen, not just a stand-in. Reached two
 * ways: [PlayScreen] itself, the moment either character's
 * [HealthComponent.isDefeated] goes true (a real win/loss, [won] reflects
 * which side won); and [PauseScreen]'s "end run" tap zone, a manual quit
 * that isn't really a win or loss - passes `won = false`, same visual
 * treatment as an actual defeat since there's no neutral third state built.
 */
class GameOverScreen(private val game: PhysicsDuelGame, private val won: Boolean) : InputAdapter(), Screen {

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
        // Dark red for a loss (was the only color this screen ever used,
        // back when it was a stand-in), a dark green for a win - same
        // "solid color background" treatment either way, just tinted.
        if (won) {
            Gdx.gl.glClearColor(0.05f, 0.20f, 0.08f, 1f) // dark green
        } else {
            Gdx.gl.glClearColor(0.25f, 0.05f, 0.05f, 1f) // dark red
        }
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        batch.projectionMatrix = camera.combined

        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val font = HudFont.font

        batch.begin()
        val title = if (won) "VICTORY" else "DEFEAT"
        font.draw(batch, title, (width - HudFont.widthOf(title)) / 2f, height * 0.56f)
        val prompt = "Tap for New Game"
        font.draw(batch, prompt, (width - HudFont.widthOf(prompt)) / 2f, height * 0.44f)
        batch.end()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        AudioManager.playTap()
        // Phase 22 - straight into a fresh run (same "brand new instance"
        // pattern MenuScreen's own tap-to-play uses), not back to the menu
        // first - "New Game" means a new game.
        game.setScreen(PlayScreen(game))
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
