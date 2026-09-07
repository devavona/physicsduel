package com.devavona.physicsduel

import com.badlogic.gdx.math.Vector2

/**
 * Phase 12's minimal AI opponent: the first thing on the target side that
 * can actually act instead of just sitting there. When [startTurn] is
 * called (wired to [AvatarMovementController]'s turn hand-off - see
 * [PlayScreen]), it waits [thinkDelaySeconds] so the hand-off is visible
 * rather than instant, then fires one shot from [launchPoint] straight at
 * wherever the player's avatar was standing *at the moment the turn
 * started* - a fixed snapshot, not a moving target, since the player can't
 * act again until this turn completes anyway (see PROJECT_STATE.md's
 * "Phase 12" entry for why input is disabled during this window).
 *
 * Deliberately the simplest possible aim: a straight line toward the
 * target, completely ignoring how gravity will curve the shot in flight -
 * no movement of its own yet either. This phase is purely about proving
 * the turn hands off to something else and that something else can act;
 * a smarter aim (or one that accounts for gravity, or lets the AI move
 * first) is real future work once this hand-off itself is confirmed to
 * work.
 */
class AiTurnController(
    private val launchPoint: Vector2,
    private val aimSpeed: Float,
    private val thinkDelaySeconds: Float,
    private val onFire: (velocity: Vector2) -> Unit,
    private val onTurnComplete: () -> Unit
) {

    private val targetPosition = Vector2()
    private var timeRemaining = 0f
    private var active = false

    /** True from [startTurn] until this turn's shot has actually fired - [PlayScreen] uses this to gate/restore player input and show an "AI's turn" readout. */
    val isTurnActive: Boolean get() = active

    fun startTurn(targetPosition: Vector2) {
        this.targetPosition.set(targetPosition)
        timeRemaining = thinkDelaySeconds
        active = true
    }

    /** Must be called every frame (see [PlayScreen.render]) - just counts down [thinkDelaySeconds], no physics of its own. */
    fun update(delta: Float) {
        if (!active) return
        timeRemaining -= delta
        if (timeRemaining <= 0f) {
            fire()
        }
    }

    private fun fire() {
        val velocity = Vector2(targetPosition).sub(launchPoint).nor().scl(aimSpeed)
        onFire(velocity)
        active = false
        onTurnComplete()
    }
}
