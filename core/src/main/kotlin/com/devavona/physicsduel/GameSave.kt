package com.devavona.physicsduel

/**
 * Everything persisted to local disk between app runs. Deliberately flat and
 * generic - Phase 6 only proves the save/load plumbing works, it isn't
 * gameplay yet, so the only field here is a counter. Future phases add
 * fields to this class (settings, best scores, unlocked content, ...)
 * rather than inventing a second save file - [SaveManager] and the on-disk
 * format don't need to change, only this schema.
 *
 * [schemaVersion] exists so a future format change can detect (and safely
 * discard, via [SaveManager]) an older save instead of silently misreading
 * it once the shape of this class changes.
 *
 * No primary-constructor parameters, on purpose: LibGDX's reflection-based
 * [com.badlogic.gdx.utils.Json] needs a true zero-argument constructor to
 * instantiate this class before populating its fields, and a Kotlin
 * constructor with default-valued parameters does NOT reliably generate
 * one. A parameterless class body with `var` properties does.
 */
class GameSave {
    var schemaVersion: Int = CURRENT_SCHEMA_VERSION
    var runCount: Int = 0

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
