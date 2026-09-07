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
 *
 * **AI accuracy pass (Sept 2026 session).** Boo's feedback after playing
 * a few turns: the AI took the *exact same shot every time* when he
 * didn't move (true - [searchAim] was, and still is, a deterministic
 * search with no randomness anywhere), and its repositioning "seems
 * very primitive" (also true - [reposition] used to score candidate
 * positions with [obstructionSeverity], a straight-line-only check that
 * had no idea a real shot curves under gravity, while the shot itself
 * was scored with the much smarter gravity-aware simulation). Two
 * changes: [applyAimError] perturbs the search's "best" answer with a
 * small random angle/speed jitter right before firing, so shots stop
 * being perfectly repeatable without touching the search itself; and
 * [reposition] now scores every candidate position with [bestAimFor] -
 * the exact same gravity-aware simulation [searchAim] uses to score a
 * shot - instead of a separate, cruder straight-line heuristic, so
 * movement and aiming are finally judged by the same yardstick.
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
    // AI accuracy pass - a small random perturbation applied to the
    // search's genuinely-best answer right before firing, see
    // applyAimError. Illustrative defaults live in PlayScreen, same as
    // every other AI tuning knob - not meant to be a difficulty tier by
    // itself yet, just enough to stop shots being perfectly repeatable.
    private val aimErrorDegrees: Float,
    private val aimErrorSpeedFraction: Float,
    // origin: wherever `position` ended up after this turn's reposition -
    // the AI's own current position is the single source of truth for both
    // "where its body is drawn" and "where its shot spawns from", same
    // pattern AvatarMovementController.position already is for the player.
    private val onFire: (origin: Vector2, velocity: Vector2) -> Unit,
    private val onTurnComplete: () -> Unit
) {

    private val trajectorySimulator = TrajectorySimulator(gravitationalConstant, gravityMinDistance)

    // AI accuracy pass - reposition() stops scanning further candidate
    // positions once one predicts an approach this close (world units) -
    // effectively already a direct hit, nothing meaningful left to gain
    // by continuing to search the rest of the movement budget.
    private val repositionGoodEnoughApproach = 0.25f

    /** One circular obstacle a simulated shot can collide with - see [simulateClosestApproach]. Fixed geometry (a planet/star's center+radius), not a live Box2D reference. */
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
     * AI accuracy pass - searches candidate positions around
     * [planetCenter], closest-first (same 0, +-1 step, +-2 steps... up to
     * +-[stepsPerPhase] pattern as before), for whichever gives the best
     * PREDICTED SHOT via [bestAimFor] - not just an unobstructed straight
     * line like the old [obstructionSeverity]-based version. Movement and
     * aiming are now judged by the exact same yardstick: the same
     * gravity-aware simulation [searchAim] itself uses to score a shot.
     * Stops early once a candidate's predicted approach is already
     * essentially a direct hit ([repositionGoodEnoughApproach]) -
     * there's nothing meaningfully better to keep searching for - and
     * otherwise falls back to whichever candidate scored best across the
     * full budget, same "always end up somewhere reasonable" guarantee
     * as before.
     */
    private fun reposition(target: Vector2) {
        val sources = gravitySources()
        val multiplier = gravityMultiplier()
        val speedTuning = shotSpeedMultiplier()
        val effectiveAimSpeed = aimSpeed * speedTuning
        val effectiveSimMaxSeconds = aimSearch.simMaxSeconds / speedTuning

        var bestAngle = angleDegrees
        var bestApproach = Float.MAX_VALUE
        for (steps in 0..stepsPerPhase) {
            val offsets = if (steps == 0) listOf(0f) else listOf(steps * stepAngleDegrees, -steps * stepAngleDegrees)
            for (offset in offsets) {
                val candidateAngle = angleDegrees + offset
                val candidateOrigin = positionAt(candidateAngle)
                val (_, approach) = bestAimFor(
                    candidateOrigin, candidateAngle * MathUtils.degreesToRadians, target,
                    sources, multiplier, effectiveAimSpeed, effectiveSimMaxSeconds
                )
                if (approach < bestApproach) {
                    bestApproach = approach
                    bestAngle = candidateAngle
                }
                if (approach <= repositionGoodEnoughApproach) {
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

        val (bestVelocity, bestApproach) = bestAimFor(
            origin, baseAngleRadians, target, sources, multiplier, effectiveAimSpeed, effectiveSimMaxSeconds
        )
        val firedVelocity = applyAimError(bestVelocity)

        // Diagnostic - Boo's Phase 15 feedback was "if it is curving it's
        // very difficult to tell" from watching the missile alone; this
        // gives a hard number (how far the chosen aim actually deviates
        // from a straight line) to check alongside the new drawn trail
        // (see PlayScreen.renderProjectileTrails), in case the trail still
        // isn't conclusive on-device. Now also logs the search's true
        // best answer alongside what was actually fired, so an on-device
        // Logcat check can tell "the search found a bad shot" apart from
        // "the search found a good shot but the error jitter threw it
        // off" - useful while tuning aimErrorDegrees/aimErrorSpeedFraction.
        val straightLineAngleRadians = atan2(target.y - origin.y, target.x - origin.x)
        val chosenAngleRadians = atan2(bestVelocity.y, bestVelocity.x)
        val offsetFromStraightDegrees = (chosenAngleRadians - straightLineAngleRadians) * MathUtils.radiansToDegrees
        Gdx.app.log(
            "AiTurnController",
            "Aim chosen: angleOffsetFromStraightLine=%.1f degrees, speed=%.2f, predicted closest approach=%.2f, firedSpeed=%.2f"
                .format(offsetFromStraightDegrees, bestVelocity.len(), bestApproach, firedVelocity.len())
        )

        return firedVelocity
    }

    /**
     * Core of the aim search, shared by [searchAim] (scoring the AI's
     * actual, already-committed position/firing angle) and [reposition]
     * (scoring hypothetical candidate positions before committing to
     * one) - see the class doc comment's "AI accuracy pass" paragraph
     * for why [reposition] needs this instead of the old, cruder
     * straight-line-only check. Sweeps aim angle (centered on
     * [baseAngleRadians] - see [searchAim]'s original doc comment,
     * preserved below, for why that's the outward-facing direction and
     * not a straight line to target) and speed, simulating each
     * candidate's actual gravity-curved flight, and returns whichever
     * gets closest along with how close that was.
     *
     * **The sweep is centered on the position's own outward-facing
     * direction - not on a straight line toward [target].** First
     * on-device test after Phase 15 caught exactly why that matters:
     * from the far side of its own planet, a straight line toward the
     * target points directly *into* that same planet - every candidate
     * angle near that line self-collides in the very first simulated
     * step or two, so none of them could ever beat the useless
     * straight-line default the search started with, and the AI ended up
     * firing "in the same direction... like it didn't notice the planet
     * it was on." The outward-facing direction, by construction, always
     * points straight away from that position's own surface -
     * guaranteed clear of self-collision at the start of every candidate
     * - so centering the sweep there instead means every candidate
     * actually gets a fair, uninterrupted simulation.
     * [AimSearchConfig.angleSearchDegrees] (120°) is wide enough to still
     * cover the straight-line-to-target direction whenever *that* happens
     * to be unobstructed (the common case) - this isn't a narrower
     * search, just a correctly-centered one.
     */
    private fun bestAimFor(
        origin: Vector2,
        baseAngleRadians: Float,
        target: Vector2,
        sources: List<Pair<Vector2, Float>>,
        multiplier: Float,
        effectiveAimSpeed: Float,
        effectiveSimMaxSeconds: Float
    ): Pair<Vector2, Float> {
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
        return bestVelocity to bestApproach
    }

    /**
     * AI accuracy pass - applied to [searchAim]'s genuinely-best answer,
     * right before firing, never to the search itself (the search still
     * always finds the objectively best candidate it can; this just
     * simulates imperfect *execution* of that shot). A small uniform
     * random angle offset (+-[aimErrorDegrees]) and speed offset
     * (+-[aimErrorSpeedFraction] as a fraction of the intended speed) -
     * enough that two turns with an identical setup won't fire pixel-
     * identical shots any more (Boo, explicit: "it seems to take the
     * exact same shot every time... too easy to game"), small enough
     * that it still reads as a deliberate, competent shot rather than a
     * wild miss. A `<= 0f` value for either parameter disables that part
     * of the jitter entirely (exact behavior, useful for isolating bugs
     * without the randomness in the way).
     */
    private fun applyAimError(velocity: Vector2): Vector2 {
        if (aimErrorDegrees <= 0f && aimErrorSpeedFraction <= 0f) return velocity
        val angleErrorRadians = MathUtils.random(-aimErrorDegrees, aimErrorDegrees) * MathUtils.degreesToRadians
        val speedErrorFactor = 1f + MathUtils.random(-aimErrorSpeedFraction, aimErrorSpeedFraction)
        return Vector2(velocity).rotateRad(angleErrorRadians).scl(speedErrorFactor)
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
