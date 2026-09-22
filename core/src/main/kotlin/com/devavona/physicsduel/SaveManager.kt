package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Json

/**
 * Generic local save/load for [GameSave], with a corruption-safe write/read
 * pattern:
 *
 * - **Write**: the previous good save is copied to a `.bak` file before the
 *   new one is written, and the new save is written to a `.tmp` file first
 *   and only renamed over the real save file once the write fully succeeds.
 *   A process death or crash mid-write leaves either the old save or a
 *   stray `.tmp` file - never a half-written `save.json`.
 * - **Read**: try the primary save; if it's missing or fails to parse, fall
 *   back to the `.bak` copy; if that also fails, fall back to defaults. A
 *   corrupt save file can never crash the app on startup.
 * - **Schema migration**: a save written by an older build (lower
 *   [GameSave.schemaVersion]) is migrated forward, not discarded - see
 *   `readValid()`. A save claiming a *newer* schema than this build
 *   understands (e.g. the app was downgraded) is still rejected, same as a
 *   corrupt file - there's no safe way to guess what a field added after
 *   this code was written means.
 *
 * This is intentionally generic app-state persistence, not physics-state
 * persistence - it doesn't touch Box2D bodies or [PlayScreen] at all. Any
 * future phase that needs to persist something (settings, best score,
 * unlocked mechanics) adds a field to [GameSave] and reads/writes through
 * here, rather than each feature inventing its own file I/O.
 *
 * Uses LibGDX's cross-platform [FileHandle]/[Json], not any Android-specific
 * storage API, so this stays valid if `core` is ever run on a non-Android
 * backend.
 */
object SaveManager {

    private const val TAG = "SaveManager"

    // Gravitons economy, Step 1 - see [awardGravitons]'s doc comment for why
    // these are separate from win/loss handling and why the numbers
    // themselves are just a starting guess.
    private const val GRAVITONS_PER_WIN = 10
    private const val GRAVITONS_PER_LOSS = 3

    // Gravitons economy, Step 2 - see [purchaseHpUpgrade]'s doc comment.
    // HP_UPGRADE_COSTS[i] is the Gravitons cost of the (i+1)-th purchase -
    // an increasing curve on purpose, so the early, cheap levels help most
    // with the "2v1 feels too hard" complaint that started this whole
    // economy, while the later, expensive ones are a longer grind that
    // roughly tracks reaching the campaign ladder's higher tiers. First-
    // guess numbers, not derived from anything - same tune-on-device
    // treatment as GRAVITONS_PER_WIN/GRAVITONS_PER_LOSS above.
    private val HP_UPGRADE_COSTS = intArrayOf(15, 30, 50, 75, 105)

    /** How much Max HP each purchased level adds - see [hpUpgradeBonus]. */
    const val HP_PER_UPGRADE_LEVEL = 10

    /** The fixed ceiling on [purchaseHpUpgrade] - see that function's doc comment for why this is capped at all. */
    val MAX_HP_UPGRADE_LEVEL = HP_UPGRADE_COSTS.size

    private val json = Json()
    private val saveFile: FileHandle = Gdx.files.local("save.json")
    private val backupFile: FileHandle = Gdx.files.local("save.json.bak")
    private val tmpFile: FileHandle = Gdx.files.local("save.json.tmp")

    // Loaded once per process and cached; every read/write after that goes
    // through this instance so the in-memory and on-disk copies can't drift.
    private val current: GameSave by lazy { loadFrom(saveFile, backupFile) }

    /** Current run count, loading from disk on first access (logs the result). */
    fun currentRunCount(): Int = current.runCount

    /** Current app-launch count, loading from disk on first access. */
    fun currentAppLaunchCount(): Int = current.appLaunchCount

    /** Current win count, loading from disk on first access - see [recordWin]. */
    fun currentWinCount(): Int = current.winCount

    /** Current Gravitons balance, loading from disk on first access - see [awardGravitons]. */
    fun currentGravitons(): Int = current.gravitons

    /** Current Max HP upgrade level (0..[MAX_HP_UPGRADE_LEVEL]), loading from disk on first access. */
    fun currentHpUpgradeLevel(): Int = current.hpUpgradeLevel

    /** How much the Max HP upgrade currently adds - see [PlayScreen]'s playerCharacters construction, which reads this. */
    fun hpUpgradeBonus(): Int = current.hpUpgradeLevel * HP_PER_UPGRADE_LEVEL

    /** Gravitons cost of the NEXT purchase, or null if already at [MAX_HP_UPGRADE_LEVEL] - [UpgradesScreen] reads this to render the buy button. */
    fun nextHpUpgradeCost(): Int? {
        val level = current.hpUpgradeLevel
        return if (level >= MAX_HP_UPGRADE_LEVEL) null else HP_UPGRADE_COSTS[level]
    }

    /** Call when a run genuinely ends (not on pause) - see [PauseScreen]. */
    fun recordRunEnded() {
        current.runCount += 1
        persistTo(current, tmpFile, saveFile, backupFile)
    }

    // Phase 23 - the Campaign progression ladder's persistent counter (see
    // PROJECT_STATE.md's "Campaign progression ladder" entry). Deliberately
    // separate from recordRunEnded() above and called ONLY on an actual win
    // - a loss still counts as a completed run (recordRunEnded() still
    // fires) but never touches this counter, and nothing here ever
    // decrements it. Callers are expected to call both when a run ends in
    // a win - see PlayScreen's win branch.
    fun recordWin() {
        current.winCount += 1
        persistTo(current, tmpFile, saveFile, backupFile)
    }

    // Sept 2026 session - Step D1's WinCountDebugController (see
    // PROJECT_STATE.md's "Phase 33, Step D" entry) - a debug-only shortcut
    // for testing the Campaign progression ladder's tiers without grinding
    // real wins. Also happens to be the same "reset progress to zero"
    // behavior the ladder design calls for at 30 wins, just not yet wired
    // to a real in-game menu option - see that controller's own doc
    // comment. Never called from any non-debug path.
    fun resetWinCount() {
        current.winCount = 0
        persistTo(current, tmpFile, saveFile, backupFile)
    }

    // Sept 2026 session - Gravitons economy, Step 1 (see PROJECT_STATE.md's
    // "Bug fixes found via Phase 34 on-device testing"-adjacent design
    // discussion: Boo wants a persistent currency that rewards perseverance,
    // not just wins - "get a little more experience every time you lose,
    // and then you can buy new weapons, or more health, or health
    // regeneration"). Deliberately separate from [recordWin] - called from
    // BOTH of PlayScreen's win and loss branches (recordWin only fires on a
    // win), since the whole point is that a loss still moves you forward,
    // just by less. GRAVITONS_PER_WIN/GRAVITONS_PER_LOSS are first-guess
    // numbers, not derived from anything - same "ship a guess, tune it once
    // it's actually been played" treatment as every other feel constant in
    // this project (TURN_CONTINUE_PAUSE_SECONDS, ShotSpeedTuning's default,
    // etc.). Nothing spends Gravitons yet - that's Step 2.
    fun awardGravitons(won: Boolean) {
        current.gravitons += if (won) GRAVITONS_PER_WIN else GRAVITONS_PER_LOSS
        persistTo(current, tmpFile, saveFile, backupFile)
    }

    // Gravitons economy, Step 2 (Sept 2026 session) - the first real spend.
    // See PROJECT_STATE.md's "Gravitons economy" entry for the full design
    // discussion. Deliberately capped at MAX_HP_UPGRADE_LEVEL rather than
    // stacking forever (Boo's explicit call, tying back to the "never
    // overmatched" goal the whole economy exists to serve) - an unbounded
    // Max HP climb would eventually make the player's side trivially
    // durable against AI stats that never grow past whatever the campaign
    // ladder tier fixes them at.
    /**
     * Attempts to buy the next Max HP level. Returns true if the purchase
     * succeeded (enough Gravitons banked, not already maxed) and false
     * otherwise - in the false case nothing changed and nothing was
     * persisted. [UpgradesScreen] is the only caller; it doesn't currently
     * react differently to true vs false, since the very next frame's
     * redraw already reflects whatever actually happened (balance/level
     * unchanged on a failed attempt, both updated on success).
     */
    fun purchaseHpUpgrade(): Boolean {
        val cost = nextHpUpgradeCost() ?: return false
        if (current.gravitons < cost) return false
        current.gravitons -= cost
        current.hpUpgradeLevel += 1
        persistTo(current, tmpFile, saveFile, backupFile)
        return true
    }

    /** Call once per cold start, not on every screen change - see [PhysicsDuelGame.create]. */
    fun recordAppLaunched() {
        current.appLaunchCount += 1
        persistTo(current, tmpFile, saveFile, backupFile)
    }

    // --- Corruption-safe read/write algorithm, factored out so it's testable ---
    //
    // `current` above is a lazily-cached, process-wide singleton value - once
    // loaded, it can't safely be reset mid test-suite, which would make every
    // test after the first see stale state. These `internal` functions take
    // their file targets as parameters instead of reaching for the fixed
    // saveFile/backupFile/tmpFile fields, so tests (SaveManagerTest, in the
    // same module) can exercise the real algorithm against disposable scratch
    // files without touching the singleton's cache at all. Not `private` for
    // exactly that reason.

    internal fun persistTo(data: GameSave, tmp: FileHandle, primary: FileHandle, backup: FileHandle) {
        try {
            if (primary.exists()) {
                primary.copyTo(backup)
            }
            tmp.writeString(json.toJson(data), false)
            val renamed = tmp.file().renameTo(primary.file())
            if (!renamed) {
                // renameTo can fail across filesystems/providers; fall back to a
                // plain copy+delete so a save is never silently lost.
                tmp.copyTo(primary)
                tmp.delete()
            }
            Gdx.app.log(TAG, "Saved: schemaVersion=${data.schemaVersion} runCount=${data.runCount}")
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Save failed - previous save (if any) left untouched", e)
        }
    }

    internal fun loadFrom(primary: FileHandle, backup: FileHandle): GameSave {
        readValid(primary)?.let {
            Gdx.app.log(TAG, "Loaded save: runCount=${it.runCount}")
            return it
        }
        readValid(backup)?.let {
            Gdx.app.log(TAG, "Primary save missing/corrupt - recovered from backup: runCount=${it.runCount}")
            return it
        }
        Gdx.app.log(TAG, "No valid save found - starting fresh (runCount=0)")
        return GameSave()
    }

    private fun readValid(file: FileHandle): GameSave? {
        if (!file.exists()) return null
        return try {
            val data = json.fromJson(GameSave::class.java, file.readString())
            when {
                data == null -> {
                    Gdx.app.error(TAG, "Save at ${file.name()} parsed to nothing - ignoring it")
                    null
                }
                data.schemaVersion > GameSave.CURRENT_SCHEMA_VERSION -> {
                    // Newer than this build understands (e.g. the app was
                    // downgraded after a save from a later version was
                    // written) - no safe way to know what a field added
                    // after this code was written means, so this is treated
                    // the same as an unparseable file.
                    Gdx.app.error(
                        TAG,
                        "Save at ${file.name()} is schema v${data.schemaVersion}, newer than " +
                            "this build understands (v${GameSave.CURRENT_SCHEMA_VERSION}) - ignoring it"
                    )
                    null
                }
                data.schemaVersion < GameSave.CURRENT_SCHEMA_VERSION -> {
                    // Migrate forward instead of discarding. LibGDX's
                    // reflection-based Json reader already populated every
                    // field it found in the file and left any field that
                    // didn't exist yet at that schema version - like
                    // GameSave.appLaunchCount for a v1 file - at its normal
                    // Kotlin default. That covers every schema change made
                    // so far, since each one has been purely additive (a new
                    // field with a sensible default), so there's nothing
                    // further to do here beyond marking it current. A field
                    // that gets renamed or changes meaning in the future
                    // would need real handling added in this branch (e.g.
                    // reading the old field's raw value and copying it into
                    // the new one) - there's none of that yet because no
                    // schema change has needed it.
                    Gdx.app.log(
                        TAG,
                        "Migrating save at ${file.name()} from schema v${data.schemaVersion} " +
                            "to v${GameSave.CURRENT_SCHEMA_VERSION}"
                    )
                    data.schemaVersion = GameSave.CURRENT_SCHEMA_VERSION
                    data
                }
                else -> data
            }
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Save at ${file.name()} is corrupt - ignoring it", e)
            null
        }
    }
}
