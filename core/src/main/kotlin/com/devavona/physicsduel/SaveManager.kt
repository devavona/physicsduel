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

    private val json = Json()
    private val saveFile: FileHandle = Gdx.files.local("save.json")
    private val backupFile: FileHandle = Gdx.files.local("save.json.bak")
    private val tmpFile: FileHandle = Gdx.files.local("save.json.tmp")

    // Loaded once per process and cached; every read/write after that goes
    // through this instance so the in-memory and on-disk copies can't drift.
    private val current: GameSave by lazy { load() }

    /** Current run count, loading from disk on first access (logs the result). */
    fun currentRunCount(): Int = current.runCount

    /** Call when a run genuinely ends (not on pause) - see [PauseScreen]. */
    fun recordRunEnded() {
        current.runCount += 1
        persist(current)
    }

    private fun persist(data: GameSave) {
        try {
            if (saveFile.exists()) {
                saveFile.copyTo(backupFile)
            }
            tmpFile.writeString(json.toJson(data), false)
            val renamed = tmpFile.file().renameTo(saveFile.file())
            if (!renamed) {
                // renameTo can fail across filesystems/providers; fall back to a
                // plain copy+delete so a save is never silently lost.
                tmpFile.copyTo(saveFile)
                tmpFile.delete()
            }
            Gdx.app.log(TAG, "Saved: schemaVersion=${data.schemaVersion} runCount=${data.runCount}")
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Save failed - previous save (if any) left untouched", e)
        }
    }

    private fun load(): GameSave {
        readValid(saveFile)?.let {
            Gdx.app.log(TAG, "Loaded save: runCount=${it.runCount}")
            return it
        }
        readValid(backupFile)?.let {
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
            if (data == null || data.schemaVersion != GameSave.CURRENT_SCHEMA_VERSION) {
                Gdx.app.error(TAG, "Save at ${file.name()} has an unexpected schema - ignoring it")
                null
            } else {
                data
            }
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Save at ${file.name()} is corrupt - ignoring it", e)
            null
        }
    }
}
