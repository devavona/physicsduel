package com.devavona.physicsduel

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import kotlin.math.atan2

/**
 * Phase 12's minimal AI opponent, extended in Phase 14 with repositioning
 * and in Phase 15 with a real gravity-aware aim. When [startTurn] is
 * called (wired to [AvatarMovementController]'s turn hand-off - see
 * [PlayScreen]), it first tries to [reposition] itself around
 * [planetCenter] for a clear line to the target, then waits
 * [thinkDelaySeconds] so the hand-off is visible rather than instant, then
 * [searchAim]s for the best available shot before firing at wherever the
 * player's avatar was standing *at the moment the turn started* - a fixed
 * snapshot, not a moving target, since the player can't act again until
 * this turn completes anyway (see PROJECT_STATE.md's "Phase 12" entry for
 * why input is disabled during this window).
 *
 * **Phase 14 milestone (repositioning).** [reposition] searches nearby
 * angles (closest first, same movement budget the player gets per phase)
 * for one with a clear straight-line path to the target, via
 * [obstructionSeverity] - the fix for "the AI fires blind into a planet
 * that's directly in the way."
 *
 * **Phase 15 milestone (gravity-aware aim).** Repositioning alone doesn't
 * fix everything: even from a spot with a clear *straight* line, the real
 * missile still flies a gravity-curved path (per [GravitySystem]), so a
 * shot aimed straight at the target can still miss - "there is not
 * intelligence to the shot back" was Boo's Phase 13 feedback, and Phase 14
 * only answered half of it (the "on the other side of the planet" half).
 * [searchAim] answers the rest: instead of always firing a straight line
 * at [aimSpeed], it sweeps a range of aim angles and speeds, actually
 * simulates each candidate's flight under the same gravity math
 * [GravitySystem.applyForces] itself uses ([simulateClosestApproach]), and
 * fires whichever candidate's simulated path gets closest to the target -
 * including, in principle, a curved path that clears an obstacle a
 * straight line couldn't. Still deliberately bounded, not exhaustive: a
 * fixed angle/speed sweep (see [AimSearchConfig]), not a full optimizer,
 * and it still only ever picks from the candidates it actually tries - a
 * better shot outside that sweep's range simply won't be found. A genuine
 * next step (not this phase) would widen or adapt that sweep, or search
 * position and aim together instead of one after the other.
 */
class AiTurnController(
    private val planetCenter: Vector2,
    private val planetRadius: Float,
    private val heightAboveSurface: Float,
    private val stepsPerPhase: Int,
    private val stepAngleDegrees: Float,
    startAngleDegrees: Float,
    private val aimSpeed: Float,
    private val thinkDelaySeconds: Float,
    private val obstacles: List<Obstacle>,
    private val aimSearch: AimSearchConfig,
    // Phase 15 - the exact gravity math AiTurnController's own
    // trajectory simulation needs, mirrored from GravitySystem rather than
    // holding a reference to it directly (this class shouldn't need to
    // know GravitySystem exists as a whole - just the numbers). The two
    // lambdas are called once per aim search (not once per simulated
    // step - see searchAim), so they always reflect whatever's live *at
    // the moment this turn fires*, including gravityMultiplier's live
    // on-device tuning and a target planet's mass shrinking from damage.
    private val gravitationalConstant: Float,
    private val gravityMinDistance: Float,
    private val gravityMultiplier: () -> Float,
    // (trajectorySimulator, below, is built from gravitationalConstant/
    // gravityMinDistance right after the primary constructor - Phase 16
    // shares it with PlayScreen's aim preview instead of duplicating the
    // stepping math in both places.)
    private val gravitySources: () -> List<Pair<Vector2, Float>>,
    // Phase 17 - ShotSpeedTuning's live, on-device-tunable dial, read once
    // per aim search (same timing as gravityMultiplier/gravitySources
    // above - see searchAim). Scales both aimSpeed itself and, inversely,
    // how long a candidate is allowed to simulate for - a slower shot
    // takes longer to reach anywhere, so the simulation window needs to
    // stretch to match, or a slowed-down shot would look like it "can't
    // reach" the target when it just needed more simulated time.
    private val shotSpeedMultiplier: () -> Float,
    // origin: wherever `position` ended up after this turn's reposition -
    // the AI's own current position is the single source of truth for both
    // "where its body is drawn" and "where its shot spawns from", same
    // pattern AvatarMovementController.position already is for the player.
    private val onFire: (origin: Vector2, velocity: Vector2) -> Unit,
    private val onTurnComplete: () -> Unit
) {

    private val trajectorySimulator = TrajectorySimulator(gravitationalConstant, gravityMinDistance)

    /** One circular obstacle a straight-line shot can be blocked by - see [obstructionSeverity]. Fixed geometry (a planet/star's center+radius), not a live Box2D reference. */
    data class Obstacle(val center: Vector2, val radius: Float)

    /**
     * Tuning for Phase 15's gravity-aware aim search - see [searchAim].
     * [angleSearchDegrees]/[angleStepDegrees] sweep aim direction around
     * the straight line to the target (+-angleSearchDegrees, in
     * angleStepDegrees increments); [speedMultipliers] additionally vary
     * launch speed (as a multiplier on [aimSpeed]), since how much a shot
     * curves depends on how fast it's moving. [simStepSeconds] should
     * match PhysicsSystem's own fixed tick so the simulated path tracks
     * the real one closely; [simMaxSeconds] caps how long one candidate is
     * simulated before giving up on it.
     */
    data class AimSearchConfig(
        val angleSearchDegrees: Float,
        val angleStepDegrees: Float,
        val speedMultipliers: List<Float>,
        val simMaxSeconds: Float,
        val simStepSeconds: Float
    )

    var angleDegrees: Float = startAngleDegrees
        private set

    /** The AI's current world position - same "fixed height above a planet's surface, at an angle" formula as [AvatarMovementController.position]. [PlayScreen] syncs the AI's Box2D body to this every frame. */
    val position: Vector2 get() = positionAt(angleDegrees)

    private val targetPosition = Vector2()
    private var timeRemaining = 0f
    private var active = false

    /** True from [startTurn] until this turn's shot has actually fired - [PlayScreen] uses this to gate/restore player input and show an "AI's turn" readout. */
    val isTurnActive: Boolean get() = active

    fun startTurn(targetPosition: Vector2) {
        this.targetPosition.set(targetPosition)
        reposition(targetPosition)
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

    /**
     * Searches candidate angles around [planetCenter] - starting from
     * wherever the AI already is (0 steps), then out to +-1 step, +-2
     * steps, up to +-[stepsPerPhase] - for the first with a fully clear
     * straight line to [target] (per [obstructionSeverity]), and moves
     * [angleDegrees] there. If none in range is fully clear, falls back to
     * whichever candidate had the lowest (least-blocked) severity, so the
     * AI always ends up somewhere reasonable instead of refusing to act.
     * Trying closest-first means an already-clear shot (the common case)
     * costs no movement at all - the AI only relocates when it actually
     * needs to.
     */
    private fun reposition(target: Vector2) {
        var bestAngle = angleDegrees
        var bestSeverity = Float.MAX_VALUE
        for (steps in 0..stepsPerPhase) {
            val offsets = if (steps == 0) listOf(0f) else listOf(steps * stepAngleDegrees, -steps * stepAngleDegrees)
            for (offset in offsets) {
                val candidateAngle = angleDegrees + offset
                val severity = obstructionSeverity(positionAt(candidateAngle), target)
                if (severity < bestSeverity) {
                    bestSeverity = severity
                    bestAngle = candidateAngle
                }
                if (severity <= 0f) {
                    angleDegrees = bestAngle
                    return
                }
            }
        }
        angleDegrees = bestAngle
    }

    private fun positionAt(angle: Float): Vector2 {
        val rad = angle * MathUtils.degreesToRadians
        val r = planetRadius + heightAboveSurface
        return Vector2(planetCenter.x + r * MathUtils.cos(rad), planetCenter.y + r * MathUtils.sin(rad))
    }

    /**
     * How badly the straight line from [from] to [to] cuts through the
     * worst-offending [obstacles] entry: positive means blocked (and by
     * how much - the obstacle's radius minus the line's closest approach
     * to its center), zero or negative means clear (with that much margin
     * to spare). Takes the *worst* obstacle, not the sum, since a shot
     * either has a clear path or it doesn't - being blocked by two planets
     * at once isn't "twice as bad" as being blocked by one.
     */
    private fun obstructionSeverity(from: Vector2, to: Vector2): Float {
        var worst = -Float.MAX_VALUE
        for (obstacle in obstacles) {
            val clearance = obstacle.radius - distanceFromSegment(from, to, obstacle.center)
            if (clearance > worst) worst = clearance
        }
        return worst
    }

    /** Shortest distance from [point] to the line segment [from]-[to]. */
    private fun distanceFromSegment(from: Vector2, to: Vector2, point: Vector2): Float {
        val segment = Vector2(to).sub(from)
        val lengthSq = segment.len2()
        val t = if (lengthSq > 0.0001f) {
            (((point.x - from.x) * segment.x + (point.y - from.y) * segment.y) / lengthSq).coerceIn(0f, 1f)
        } else 0f
        val closest = Vector2(from).add(segment.scl(t))
        return closest.dst(point)
    }

    private fun fire() {
        val origin = position
        val velocity = searchAim(origin, targetPosition)
        onFire(origin, velocity)
        active = false
        onTurnComplete()
    }

    /**
     * Phase 15: replaces "always aim a straight line at the target" with a
     * real search. Fetches [gravitySources]/[gravityMultiplier] exactly
     * once here (not once per candidate or per simulated step - they
     * don't change mid-search, and this turn already committed to
     * whatever they are the instant it started firing), then sweeps aim
     * angle/speed (see [AimSearchConfig]), simulating each candidate's
     * actual gravity-curved flight ([simulateClosestApproach]) and keeping
     * whichever gets closest to [target]. The plain straight-line shot is
     * scored and included as one of the candidates (not just an assumed
     * fallback), so it's only ever replaced by something that actually
     * does better.
     *
     * **The sweep is centered on [angleDegrees] itself - [origin]'s own
     * outward-facing direction, away from [planetCenter] - not on a
     * straight line toward [target].** First on-device test after Phase
     * 15 caught exactly why that matters: from the far side of its own
     * planet, a straight line toward the target points directly *into*
     * that same planet - every candidate angle near that line
     * self-collides in the very first simulated step or two, so none of
     * them could ever beat the useless straight-line default the search
     * started with, and the AI ended up firing "in the same direction...
     * like it didn't notice the planet it was on." [angleDegrees]'s
     * direction, by construction, always points straight away from the
     * AI's own surface - guaranteed clear of self-collision at the start
     * of every candidate - so centering the sweep there instead means
     * every candidate actually gets a fair, uninterrupted simulation.
     * [AimSearchConfig.angleSearchDegrees] (120°) is wide enough to still
     * cover the straight-line-to-target direction whenever *that* happens
     * to be unobstructed (the common case, and exactly what Phase 14's
     * [reposition] already tries to arrange) - this isn't a narrower
     * search, just a correctly-centered one.
     */
    private fun searchAim(origin: Vector2, target: Vector2): Vector2 {
        val sources = gravitySources()
        val multiplier = gravityMultiplier()
        val speedTuning = shotSpeedMultiplier()
        val effectiveAimSpeed = aimSpeed * speedTuning
        // Inverse of speedTuning - a slower shot takes proportionally
        // longer to travel the same distance, so it needs proportionally
        // more simulated time to be judged fairly (see the constructor's
        // shotSpeedMultiplier doc comment).
        val effectiveSimMaxSeconds = aimSearch.simMaxSeconds / speedTuning

        val baseAngleRadians = angleDegrees * MathUtils.degreesToRadians
        var bestVelocity = Vector2(target).sub(origin).nor().scl(effectiveAimSpeed)
        var bestApproach = simulateClosestApproach(origin, bestVelocity, target, sources, multiplier, effectiveSimMaxSeconds)

        val stepCount = (2 * aimSearch.angleSearchDegrees / aimSearch.angleStepDegrees).toInt()
        for (step in 0..stepCount) {
            val angleOffsetDegrees = -aimSearch.angleSearchDegrees + step * aimSearch.angleStepDegrees
            val angleRadians = baseAngleRadians + angleOffsetDegrees * MathUtils.degreesToRadians
            val direction = Vector2(MathUtils.cos(angleRadians), MathUtils.sin(angleRadians))
            for (speedMultiplier in aimSearch.speedMultipliers) {
                val candidateVelocity = Vector2(direction).scl(effectiveAimSpeed * speedMultiplier)
                val approach = simulateClosestApproach(origin, candidateVelocity, target, sources, multiplier, effectiveSimMaxSeconds)
                if (approach < bestApproach) {
                    bestApproach = approach
                    bestVelocity = candidateVelocity
                }
            }
        }

        // Diagnostic - Boo's Phase 15 feedback was "if it is curving it's
        // very difficult to tell" from watching the missile alone; this
        // gives a hard number (how far the chosen aim actually deviates
        // from a straight line) to check alongside the new drawn trail
        // (see PlayScreen.renderProjectileTrails), in case the trail still
        // isn't conclusive on-device. Cheap (once per AI turn) - fine to
        // leave in rather than strip out once this is confirmed working.
        val straightLineAngleRadians = atan2(target.y - origin.y, target.x - origin.x)
        val chosenAngleRadians = atan2(bestVelocity.y, bestVelocity.x)
        val offsetFromStraightDegrees = (chosenAngleRadians - straightLineAngleRadians) * MathUtils.radiansToDegrees
        Gdx.app.log(
            "AiTurnController",
            "Aim chosen: angleOffsetFromStraightLine=%.1f degrees, speed=%.2f, predicted closest approach=%.2f"
                .format(offsetFromStraightDegrees, bestVelocity.len(), bestApproach)
        )

        return bestVelocity
    }

    /**
     * Simulates one candidate shot's flight with a simple point-mass
     * integrator (semi-implicit Euler: velocity updates first each step,
     * then position uses the updated velocity) using the exact
     * inverse-square gravity math [GravitySystem.applyForces] itself
     * applies - mass-independent for the body being pulled (Newton's law
     * gives acceleration = G*sourceMass/r^2, the missile's own mass
     * cancels out, exactly like real gravity), so no real Box2D body is
     * needed to predict it. Stepped at [AimSearchConfig.simStepSeconds]
     * for up to [AimSearchConfig.simMaxSeconds] of simulated flight.
     * Returns the closest the simulated missile ever gets to [target]
     * before either time runs out or the path first touches an obstacle -
     * a real missile would be destroyed there, so nothing after that
     * point counts.
     */
    private fun simulateClosestApproach(
        origin: Vector2,
        velocity: Vector2,
        target: Vector2,
        sources: List<Pair<Vector2, Float>>,
        multiplier: Float,
        simMaxSeconds: Float
    ): Float {
        val position = Vector2(origin)
        val currentVelocity = Vector2(velocity)
        var closest = position.dst(target)
        var elapsed = 0f
        while (elapsed < simMaxSeconds) {
            trajectorySimulator.step(position, currentVelocity, aimSearch.simStepSeconds, sources, multiplier)
            elapsed += aimSearch.simStepSeconds

            val distanceToTarget = position.dst(target)
            if (distanceToTarget < closest) closest = distanceToTarget

            for (obstacle in obstacles) {
                if (position.dst(obstacle.center) <= obstacle.radius) {
                    return closest
                }
            }
        }
        return closest
    }
}
