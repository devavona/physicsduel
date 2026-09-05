package com.devavona.physicsduel

/**
 * What a player has dialed in for a single run/attempt: a chosen rank (0 =
 * off) for each [DifficultyModifier] they've picked, keyed by the
 * modifier's [DifficultyModifier.id]. Deliberately just a data holder - see
 * [DifficultyScore] for turning a selection into a single number.
 *
 * A modifier absent from [ranks] is treated as rank 0 (not selected) by
 * [DifficultyScore] - callers don't need to populate every known modifier,
 * only the ones actually being dialed up.
 */
class DifficultySelection(private val ranks: Map<String, Int> = emptyMap()) {
    /** The chosen rank for [modifierId], or 0 if it isn't present in this selection. */
    fun rankFor(modifierId: String): Int = ranks[modifierId] ?: 0
}
