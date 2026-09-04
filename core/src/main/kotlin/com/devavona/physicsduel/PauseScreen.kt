package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

/**
 * Pause screen: left half of the screen resumes [playScreen] exactly as it
 * was (same instance - the physics world isn't touched), right half ends
 * the run. Phase 5 shipped this as two unlabeled colored zones; Phase 7
 * adds the "RESUME" / "END RUN" text on top so it reads as UI rather than
 * an unexplained color swatch.
 */
class PauseScreen(
    private val game: PhysicsDuelGame,
    private val playScreen: PlayScreen
) : InputAdapter(), Screen {

    private val shapeRenderer = ShapeRenderer()
    private val batch = SpriteBatch()
    private val camera = OrthographicCamera()

    override fun show() {
        Gdx.input.inputProcessor = this
        resizeCamera()
    }

    private fun resizeCamera() {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        val halfWidth = Gdx.graphics.width / 2f
        val height = Gdx.graphics.height.toFloat()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0.15f, 0.35f, 0.2f, 1f) // resume - green-ish, left half
        shapeRenderer.rect(0f, 0f, halfWidth, height)
        shapeRenderer.color = Color(0.4f, 0.12f, 0.12f, 1f) // end run - red-ish, right half
        shapeRenderer.rect(halfWidth, 0f, halfWidth, height)
        shapeRenderer.end()

        val font = HudFont.font
        batch.begin()
        val resumeLabel = "RESUME"
        font.draw(batch, resumeLabel, (halfWidth - HudFont.widthOf(resumeLabel)) / 2f, height / 2f)
        val endLabel = "END RUN"
        font.draw(batch, endLabel, halfWidth + (halfWidth - HudFont.widthOf(endLabel)) / 2f, height / 2f)
        batch.end()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        AudioManager.playTap()
        if (screenX < Gdx.graphics.width / 2) {
            game.setScreen(playScreen) // resume - same instance, world state preserved
        } else {
            SaveManager.recordRunEnded() // Phase 6: persist before tearing anything down
            playScreen.dispose() // permanently ending this run - see PlayScreen's doc comment on why this matters
            game.setScreen(GameOverScreen(game))
        }
        return true
    }

    override fun resize(width: Int, height: Int) {
        resizeCamera()
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {}

    override fun dispose() {
        shapeRenderer.dispose()
        batch.dispose()
    }
}
