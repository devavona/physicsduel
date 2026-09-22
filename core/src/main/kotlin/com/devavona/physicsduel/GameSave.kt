package com.devavona.physicsduel

/**
 * Everything persisted to local disk between app runs. Deliberately flat and
 * generic - Phase 6 only proves the save/load plumbing works, it isn't
 * gameplay yet, so the fields here are just counters. Future phases add
 * fields to this class (settings, best scores, unlocked content, ...)
 * rather than inventing a second save file - [SaveManager] and the on-disk
 * format don't need to change, only this schema.
 *
 * [schemaVersion] exists so a future format change can be told apart from
 * the current one. [SaveManager] doesn't just discard an older save when it
 * sees a mismatch, though - see its `readValid()` for how a purely additive
 * change (a new field, like [appLaunchCount] below) gets migrated forward
 * instead of losing the save.
 *
 * No primary-constructor parameters, on purpose: LibGDX's reflection-based
 * [com.badlogic.gdx.utils.Json] needs a true zero-argument constructor to
 * instantiate this class before populating its fields, and a Kotlin
 * constructor with default-valued parameters does NOT reliably generate
 * one. A parameterless class body with `var` properties does.
 *
 * Schema history:
 * - v1 (Phase 6): `schemaVersion`, `runCount`.
 * - v2: added `appLaunchCount`. Purely additive - an old v1 save simply
 *   doesn't have this field, so it comes back at its Kotlin default (0)
 *   when a v1 file is read on a v2 build, rather than being discarded.
 * - v3 (Phase 23): added `winCount` - the Campaign progression ladder's
 *   persistent, win-only counter (see PROJECT_STATE.md's "Campaign
 *   progression ladder" entry - never decremented or reset by a loss,
 *   deliberately not a roguelite streak). Purely additive, same as v2.
 * - v4 (Sept 2026 session, Gravitons economy Step 1): added `gravitons` -
 *   the persistent currency earned at the end of every game (see
 *   [SaveManager.awardGravitons]), win or lose - unlike [winCount], this
 *   one deliberately DOES accrue on a loss, just less of it. Purely
 *   additive, same migration story as v2/v3.
 * - v5 (Sept 2026 session, Gravitons economy Step 2): added
 *   `hpUpgradeLevel` - how many times the permanent Max HP upgrade has
 *   been purchased (see [SaveManager.purchaseHpUpgrade]), capped at
 *   [SaveManager.MAX_HP_UPGRADE_LEVEL]. Purely additive, same as every
 *   schema bump before it.
 */
class GameSave {
    var schemaVersion: Int = CURRENT_SCHEMA_VERSION
    var runCount: Int = 0
    /** How many times the app has been cold-started - see [PhysicsDuelGame.create]. */
    var appLaunchCount: Int = 0
    /** Wins only - see [SaveManager.recordWin] and this class's v3 schema-history note. */
    var winCount: Int = 0
    /** Persistent currency - see [SaveManager.awardGravitons] and this class's v4 schema-history note. */
    var gravitons: Int = 0
    /** Max HP upgrade level - see [SaveManager.purchaseHpUpgrade] and this class's v5 schema-history note. */
    var hpUpgradeLevel: Int = 0

    companion object {
        const val CURRENT_SCHEMA_VERSION = 5
    }
}
