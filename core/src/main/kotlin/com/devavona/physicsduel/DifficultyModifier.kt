package com.devavona.physicsduel

/**
 * Describes one selectable difficulty knob - what a modifier *is*, not what
 * it *does*. No gameplay effect lives here: this is deliberately just a
 * scorable, capped definition (id, display name, point value per rank, and
 * how many ranks it goes up to), left generic on purpose because no real
 * gameplay modifiers (harder AI opponents, faster bodies, tighter turn
 * timers, ...) exist yet to attach effects to. See [DifficultyScore] for how
 * a set of chosen ranks turns into a single aggregate difficulty number.
 *
 * [id] is expected to be unique among the modifiers passed to a given
 * [DifficultyScore] calculation - nothing enforces that here (a future
 * modifier *registry*, once real modifiers exist, would be the natural
 * place to enforce it).
 */
class DifficultyModifier(
    val id: String,
    val displayName: String,
    val pointsPerRank: Int,
    val maxRank: Int
)
