package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch

/**
 * Menu screen: solid color, title + prompt text, tap anywhere to start a
 * fresh run.
 *
 * **Gravitons economy Step 2 (Sept 2026 session):** the one exception to
 * "tap anywhere" - a top-right corner zone (see [UPGRADES_ZONE_MIN_X_FRACTION]/
 * [UPGRADES_ZONE_MIN_Y_FRACTION]) opens [UpgradesScreen] instead. Kept
 * deliberately small and corner-anchored so it can't be brushed by accident
 * on the way to a "tap to play" everywhere else.
 */
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

        // Gravitons economy Step 2 - see this class's own doc comment and
        // touchDown's for the tap-zone this label sits inside. Top-right
        // corner, clear of the centered title/prompt/stats column below.
        val upgradesLabel = "Upgrades >"
        font.draw(batch, upgradesLabel, width - HudFont.widthOf(upgradesLabel) - HudFont.scaled(24f), height * 0.94f)

        val prompt = "Tap to Play"
        font.draw(batch, prompt, (width - HudFont.widthOf(prompt)) / 2f, height * 0.5f)

        // Phase 23 - the Campaign progression ladder's persistent win-only
        // counter (see PROJECT_STATE.md's "Campaign progression ladder"
        // entry). Drawn above the run count below, same centered style -
        // this is the one persistent-between-runs screen, so it's the
        // natural place for a counter that's meant to survive a loss.
        val wins = "Wins: ${SaveManager.currentWinCount()}"
        font.draw(batch, wins, (width - HudFont.widthOf(wins)) / 2f, height * 0.20f)

        // Gravitons economy, Step 1 (Sept 2026 session) - the new persistent
        // currency, drawn right under Wins since both live on this one
        // persistent-between-runs screen. Nothing spends it yet (Step 2) -
        // this is purely "prove the balance is actually accruing and visible"
        // for on-device testing, same role Phase 6's runCount display played
        // for SaveManager originally.
        val gravitons = "Gravitons: ${SaveManager.currentGravitons()}"
        font.draw(batch, gravitons, (width - HudFont.widthOf(gravitons)) / 2f, height * 0.15f)

        // Phase 6 tie-in: proves persisted state (SaveManager) reaches the
        // screen, not just Logcat - the visible number should match whatever
        // was last logged as "Loaded save: runCount=N" at cold start.
        val runs = "Runs completed: ${SaveManager.currentRunCount()}"
        font.draw(batch, runs, (width - HudFont.widthOf(runs)) / 2f, height * 0.10f)
        batch.end()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        AudioManager.playTap()
        // Gravitons economy Step 2 - LibGDX touch coordinates are Y-DOWN (0
        // at the top), the opposite of this screen's render()/camera Y-UP
        // convention (0 at the bottom) - flip once here rather than
        // re-deriving the zone fractions in Y-down terms.
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val worldY = height - screenY
        if (screenX >= width * UPGRADES_ZONE_MIN_X_FRACTION && worldY >= height * UPGRADES_ZONE_MIN_Y_FRACTION) {
            game.setScreen(UpgradesScreen(game))
            return true
        }
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

    companion object {
        // The top-right corner tap zone that opens UpgradesScreen - see this
        // class's own doc comment. Last 35% of width, top 15% of height.
        private const val UPGRADES_ZONE_MIN_X_FRACTION = 0.65f
        private const val UPGRADES_ZONE_MIN_Y_FRACTION = 0.85f
    }
}
