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
 * Gravitons economy, Step 2 (Sept 2026 session) - the first real "spend"
 * screen. See PROJECT_STATE.md's "Gravitons economy" entry for the full
 * design discussion (why a dedicated screen, why capped, why this cost
 * curve). Reached from a corner tap zone on [MenuScreen] - see that
 * screen's own doc comment - not from anywhere mid-run; spending currency
 * during an active game isn't part of the design.
 *
 * Only one upgrade exists yet (Max HP, via [SaveManager.purchaseHpUpgrade]).
 * Deliberately not built as a generic "list of purchasable things" system -
 * that would be speculative generality for a single item. Whoever adds the
 * next upgrade (weapons, health regen - see PROJECT_STATE.md's roadmap)
 * should generalize this screen's layout at that point, not before.
 *
 * Same "solid color, stacked tap zones" pattern [PauseScreen] uses for its
 * left/right split - vertical bands here instead of a left/right split,
 * since this screen also needs room above them for the stat readout text.
 */
class UpgradesScreen(private val game: PhysicsDuelGame) : InputAdapter(), Screen {

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
        Gdx.gl.glClearColor(0.08f, 0.05f, 0.14f, 1f) // dark purple - distinct from every other screen's color
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val maxed = SaveManager.currentHpUpgradeLevel() >= SaveManager.MAX_HP_UPGRADE_LEVEL

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        // Dimmed gray once maxed (nothing left to buy) instead of the normal
        // buy-zone color, so "there's nothing more to do here" reads visually,
        // not just through the label text.
        shapeRenderer.color = if (maxed) Color(0.18f, 0.18f, 0.18f, 1f) else Color(0.15f, 0.3f, 0.35f, 1f)
        shapeRenderer.rect(0f, height * BUY_BAND_BOTTOM_FRACTION, width, height * (BUY_BAND_TOP_FRACTION - BUY_BAND_BOTTOM_FRACTION))
        shapeRenderer.color = Color(0.3f, 0.12f, 0.12f, 1f)
        shapeRenderer.rect(0f, height * BACK_BAND_BOTTOM_FRACTION, width, height * (BACK_BAND_TOP_FRACTION - BACK_BAND_BOTTOM_FRACTION))
        shapeRenderer.end()

        val font = HudFont.font
        batch.begin()
        val title = "UPGRADES"
        font.draw(batch, title, (width - HudFont.widthOf(title)) / 2f, height * 0.90f)

        val gravitons = "Gravitons: ${SaveManager.currentGravitons()}"
        font.draw(batch, gravitons, (width - HudFont.widthOf(gravitons)) / 2f, height * 0.80f)

        val level = SaveManager.currentHpUpgradeLevel()
        val currentMaxHp = PlayScreen.AVATAR_MAX_HP + SaveManager.hpUpgradeBonus()
        val hpLine = "Max HP: $currentMaxHp (Lv $level/${SaveManager.MAX_HP_UPGRADE_LEVEL})"
        font.draw(batch, hpLine, (width - HudFont.widthOf(hpLine)) / 2f, height * 0.72f)

        val buyLabel = if (maxed) {
            "MAX HP - MAXED OUT"
        } else {
            "Buy +${SaveManager.HP_PER_UPGRADE_LEVEL} Max HP  -  Cost: ${SaveManager.nextHpUpgradeCost()}"
        }
        font.draw(
            batch, buyLabel,
            (width - HudFont.widthOf(buyLabel)) / 2f,
            height * (BUY_BAND_TOP_FRACTION + BUY_BAND_BOTTOM_FRACTION) / 2f
        )

        val backLabel = "BACK"
        font.draw(
            batch, backLabel,
            (width - HudFont.widthOf(backLabel)) / 2f,
            height * (BACK_BAND_TOP_FRACTION + BACK_BAND_BOTTOM_FRACTION) / 2f
        )
        batch.end()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        AudioManager.playTap()
        // LibGDX touch coordinates are Y-DOWN (0 at the top of the screen) -
        // the opposite of this screen's render()/camera Y-UP convention (0 at
        // the bottom, matching camera.setToOrtho(false, ...)). Flipping here
        // once is simpler than re-deriving every band boundary in Y-down terms.
        val height = Gdx.graphics.height.toFloat()
        val worldY = height - screenY
        when {
            worldY in height * BUY_BAND_BOTTOM_FRACTION..height * BUY_BAND_TOP_FRACTION -> {
                // Return value deliberately ignored - a false result (can't
                // afford it, or already maxed) means nothing changed, and
                // the very next frame's redraw already reflects whatever
                // actually happened either way. See purchaseHpUpgrade's own
                // doc comment.
                SaveManager.purchaseHpUpgrade()
            }
            worldY in height * BACK_BAND_BOTTOM_FRACTION..height * BACK_BAND_TOP_FRACTION -> {
                game.setScreen(MenuScreen(game))
            }
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

    companion object {
        // Fractions of screen height (0 = bottom, 1 = top, matching this
        // screen's Y-up render/camera convention) - see touchDown's own doc
        // comment for why touch input needs its Y flipped to compare against
        // these.
        private const val BUY_BAND_BOTTOM_FRACTION = 0.35f
        private const val BUY_BAND_TOP_FRACTION = 0.55f
        private const val BACK_BAND_BOTTOM_FRACTION = 0.05f
        private const val BACK_BAND_TOP_FRACTION = 0.25f
    }
}
