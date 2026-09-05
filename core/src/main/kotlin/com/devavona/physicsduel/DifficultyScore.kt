package com.devavona.physicsduel

/**
 * Turns a [DifficultySelection] into a single aggregate difficulty number:
 * each selected modifier contributes its chosen rank times its
 * [DifficultyModifier.pointsPerRank], and the total is the sum across every
 * modifier passed in. A rank outside a modifier's valid range (above
 * [DifficultyModifier.maxRank], or negative) is clamped first, so a
 * selection can't score more than a modifier's own ceiling allows, or go
 * negative.
 *
 * Deliberately just this one calculation - no actual modifiers are defined
 * anywhere yet (see [DifficultyModifier]'s doc comment), no save-schema
 * field persists a selection yet, and no reward curve maps a score to a
 * payout yet. All three are real future work, once there's actual gameplay
 * content and an economy system to hang them on - this class exists now so
 * that work has a scoring mechanism to plug into instead of a retrofit.
 */
object DifficultyScore {
    fun compute(selection: DifficultySelection, modifiers: List<DifficultyModifier>): Int =
        modifiers.sumOf { modifier ->
            val rank = selection.rankFor(modifier.id).coerceIn(0, modifier.maxRank)
            rank * modifier.pointsPerRank
        }
}
