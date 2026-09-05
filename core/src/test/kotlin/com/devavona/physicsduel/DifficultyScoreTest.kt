package com.devavona.physicsduel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises [DifficultyScore]'s pure scoring math directly - no Gdx/Box2D
 * bootstrap needed (unlike [PhysicsSystemTest]/[SaveManagerTest]) since
 * this class touches neither. The two modifiers below are generic
 * placeholders for a not-yet-designed real modifier set, not final names.
 */
class DifficultyScoreTest {

    private val fasterOpponents = DifficultyModifier(
        id = "faster_opponents", displayName = "Faster Opponents", pointsPerRank = 5, maxRank = 5
    )
    private val extraOpponents = DifficultyModifier(
        id = "extra_opponents", displayName = "Extra Opponents", pointsPerRank = 10, maxRank = 3
    )

    @Test
    fun emptySelection_scoresZero() {
        val score = DifficultyScore.compute(DifficultySelection(), listOf(fasterOpponents, extraOpponents))
        assertEquals(0, score)
    }

    @Test
    fun noModifiersPassedIn_scoresZeroEvenWithASelection() {
        val selection = DifficultySelection(mapOf(fasterOpponents.id to 3))
        val score = DifficultyScore.compute(selection, emptyList())
        assertEquals(0, score)
    }

    @Test
    fun singleModifier_scoresRankTimesPointsPerRank() {
        val selection = DifficultySelection(mapOf(fasterOpponents.id to 2))
        val score = DifficultyScore.compute(selection, listOf(fasterOpponents))
        assertEquals(10, score) // 2 ranks * 5 points
    }

    @Test
    fun multipleModifiers_sumTogether() {
        val selection = DifficultySelection(mapOf(fasterOpponents.id to 2, extraOpponents.id to 1))
        val score = DifficultyScore.compute(selection, listOf(fasterOpponents, extraOpponents))
        assertEquals(20, score) // (2*5) + (1*10)
    }

    @Test
    fun modifierMissingFromSelection_countsAsRankZero() {
        val selection = DifficultySelection(mapOf(fasterOpponents.id to 2)) // extraOpponents never mentioned
        val score = DifficultyScore.compute(selection, listOf(fasterOpponents, extraOpponents))
        assertEquals(10, score)
    }

    @Test
    fun rankAboveMaxRank_isClampedDown() {
        val selection = DifficultySelection(mapOf(fasterOpponents.id to 99)) // way past maxRank of 5
        val score = DifficultyScore.compute(selection, listOf(fasterOpponents))
        assertEquals(25, score) // clamped to 5 ranks * 5 points, not 99*5
    }

    @Test
    fun negativeRank_isClampedToZero() {
        val selection = DifficultySelection(mapOf(fasterOpponents.id to -3))
        val score = DifficultyScore.compute(selection, listOf(fasterOpponents))
        assertEquals(0, score)
    }

    @Test
    fun unknownModifierIdInSelection_isIgnored() {
        // References a modifier id not present in the modifiers list at all
        // (e.g. stale data from a modifier that no longer exists) - silently
        // ignored, not a crash, since compute() only iterates the known list.
        val selection = DifficultySelection(mapOf("no_such_modifier" to 5, fasterOpponents.id to 1))
        val score = DifficultyScore.compute(selection, listOf(fasterOpponents))
        assertEquals(5, score)
    }
}
