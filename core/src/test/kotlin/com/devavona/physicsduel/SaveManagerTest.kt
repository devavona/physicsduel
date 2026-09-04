package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises [SaveManager]'s corruption-safe write/read algorithm directly,
 * via its `internal` `persistTo`/`loadFrom` functions - see the doc comment
 * on those in [SaveManager] for why the tests go through those instead of
 * the singleton's own `currentRunCount()`/`recordRunEnded()`: `current` is
 * lazily cached once per JVM, so testing through it would make every test
 * after the first see stale, already-cached state instead of a fresh
 * scenario. Each test gets its own disposable scratch directory via JUnit's
 * [TemporaryFolder] rule, so tests can't interfere with each other or with
 * a real save file.
 */
class SaveManagerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var primary: FileHandle
    private lateinit var backup: FileHandle
    private lateinit var tmp: FileHandle

    @Before
    fun setUp() {
        GdxTestBootstrap.ensureRunning()
        val root = tempFolder.root
        primary = Gdx.files.absolute(root.resolve("save.json").absolutePath)
        backup = Gdx.files.absolute(root.resolve("save.json.bak").absolutePath)
        tmp = Gdx.files.absolute(root.resolve("save.json.tmp").absolutePath)
    }

    @Test
    fun loadWithNoFilesAtAll_returnsDefaults() {
        val result = SaveManager.loadFrom(primary, backup)

        assertEquals(0, result.runCount)
        assertEquals(GameSave.CURRENT_SCHEMA_VERSION, result.schemaVersion)
    }

    @Test
    fun persistThenLoad_roundTripsCorrectly() {
        val data = GameSave().apply { runCount = 7 }
        SaveManager.persistTo(data, tmp, primary, backup)

        val loaded = SaveManager.loadFrom(primary, backup)

        assertEquals(7, loaded.runCount)
    }

    @Test
    fun corruptPrimary_fallsBackToBackup() {
        // First save: nothing to back up yet.
        SaveManager.persistTo(GameSave().apply { runCount = 3 }, tmp, primary, backup)
        // Second save: primary (runCount=3) gets copied to backup before the
        // new value is written, so backup ends up holding the *previous*
        // good state.
        SaveManager.persistTo(GameSave().apply { runCount = 4 }, tmp, primary, backup)

        // Simulate a crash/corruption mid-write on the primary file.
        primary.writeString("{ not valid json !!", false)

        val loaded = SaveManager.loadFrom(primary, backup)

        assertEquals(3, loaded.runCount) // recovered from backup, not lost or defaulted
    }

    @Test
    fun corruptPrimaryAndNoBackup_fallsBackToDefaultsWithoutCrashing() {
        primary.writeString("{ not valid json !!", false)

        val loaded = SaveManager.loadFrom(primary, backup)

        assertEquals(0, loaded.runCount) // no backup exists either - fail soft, not a crash
    }

    @Test
    fun newerSchemaVersion_isRejected() {
        // Simulates a save written by a build newer than this one (e.g. the
        // app was downgraded) - there's no safe way to know what a field
        // added after this code was written means, so it must be rejected,
        // same as a corrupt file - never guessed at.
        val fromTheFuture = GameSave().apply {
            schemaVersion = GameSave.CURRENT_SCHEMA_VERSION + 1
            runCount = 42
        }
        SaveManager.persistTo(fromTheFuture, tmp, primary, backup)

        val loaded = SaveManager.loadFrom(primary, backup)

        assertEquals(0, loaded.runCount) // rejected, not misread as real data
    }

    @Test
    fun olderSchemaVersion_migratesForwardInsteadOfBeingDiscarded() {
        // Simulates a save written by an actual older build: same shape
        // GameSave has today, but missing the field(s) added since - exactly
        // what a real v1 file looks like once parsed, since every schema
        // change so far has been purely additive (see GameSave's "Schema
        // history" doc comment). Hand-written because SaveManager.persistTo
        // always writes the *current* full shape - there's no way to
        // produce an old-shape file through it, which is the whole point of
        // this test.
        primary.writeString("""{"schemaVersion":1,"runCount":5}""", false)

        val loaded = SaveManager.loadFrom(primary, backup)

        assertEquals(5, loaded.runCount) // preserved, not discarded
        assertEquals(0, loaded.appLaunchCount) // field didn't exist in v1 - defaults cleanly
        assertEquals(GameSave.CURRENT_SCHEMA_VERSION, loaded.schemaVersion) // upgraded in memory
    }
}
