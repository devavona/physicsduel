package com.devavona.physicsduel

import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.Viewport

/**
 * Sept 2026 session - Boo: "I don't want [the play field] to always fit on
 * one screen... pinch to zoom and ability to scroll around and zoom in and
 * out. that way the game can scale as need be." See PROJECT_STATE.md's
 * "Multi-character combat: turn order, camera, and stray-shot lifecycle"
 * design note for the full discussion.
 *
 * Deliberately 2-finger only, confirmed with Boo ("2 fingers to
 * scroll/pan"): 1-finger touch stays entirely reserved for
 * [AvatarMovementController]'s move buttons and [SlingshotInputProcessor]'s
 * aim-and-release drag, unchanged from how they've always worked. This
 * class does not claim a touch at all while only one pointer is down - a
 * lone finger always falls through to whichever single-finger processor
 * wants it. It only starts consuming events (touchDragged/touchUp, for
 * either pointer) the instant a SECOND pointer goes down, and stops the
 * moment the pointer count drops back below two.
 *
 * Must be registered FIRST in [PlayScreen]'s InputMultiplexer, ahead of
 * [SlingshotInputProcessor] especially - that class doesn't filter events
 * by pointer index (see its own class doc comment), so without this class
 * going first and swallowing the second pointer's events outright, a stray
 * second finger could get misread as continuing whatever the first finger
 * was already dragging. [onGestureEngaged] fires the instant the gesture
 * begins, purely so [PlayScreen] can tell [SlingshotInputProcessor] to
 * abandon any single-finger aim already in progress - see
 * [SlingshotInputProcessor.cancelAim] - rather than leaving it stuck
 * mid-drag once this class starts eating its events. After a pinch/pan
 * ends, a finger still resting on the screen does nothing further until
 * it's lifted and touched down again fresh - simpler and safer than trying
 * to seamlessly hand a gesture back mid-drag, at the cost of needing a
 * fresh touch to resume aiming/moving right after a pinch. Worth a
 * feel-check on-device; not expected to come up often in practice.
 *
 * **The math.** Each drag event while two pointers are down solves for the
 * camera position that keeps the world point which was under the gesture's
 * STARTING midpoint anchored under the CURRENT midpoint, given whatever
 * the current pinch-derived zoom is - the same "the point between your
 * fingers stays put" feel as photo pinch-zoom, and ordinary drag-to-pan,
 * both falling out of one calculation. Recomputed from the gesture's own
 * fixed start state every event, never incrementally frame-to-frame, so
 * there's nothing that can drift/compound across a long drag.
 */
class CameraGestureController(
    private val camera: OrthographicCamera,
    private val viewport: Viewport,
    private val onGestureEngaged: () -> Unit = {}
) : InputAdapter() {

    companion object {
        // How far pinch-zoom is allowed to go. camera.zoom is LibGDX's own
        // convention: smaller = more zoomed in (less world visible per
        // screen pixel), larger = more zoomed out (more world visible).
        // Starting guesses, same "tune later via on-device feel-testing"
        // pattern as every other constant in this project - not derived
        // from anything, and not yet aware of the future play-field-size
        // cap (PROJECT_STATE.md, still undecided) which will likely want
        // to inform MAX_ZOOM once it exists.
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 2.5f
    }

    private val pointers = LinkedHashMap<Int, Vector2>()

    private var gestureActive = false
    private var startDistance = 1f
    private var startZoom = 1f
    private val startCameraPos = Vector2()
    private val startMidpointWorld = Vector2()

    // Scratch vectors reused every drag event instead of allocating fresh
    // ones each frame - this runs on every touchDragged callback while
    // gesturing, potentially many times a frame's worth of events on a fast
    // drag.
    private val scratchMidpoint = Vector2()
    private val scratchWorldPoint = Vector2()
    private val scratchCorrection = Vector2()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        pointers[pointer] = Vector2(screenX.toFloat(), screenY.toFloat())
        if (pointers.size == 2) {
            beginGesture()
            return true
        }
        // Exactly one pointer down (or an already-ignored 3rd+ while not
        // gesturing) - don't claim it, let it fall through untouched.
        return gestureActive
    }

    private fun beginGesture() {
        val pair = pointerPair() ?: return
        startDistance = pair.first.dst(pair.second).coerceAtLeast(1f)
        startZoom = camera.zoom
        startCameraPos.set(camera.position.x, camera.position.y)
        scratchMidpoint.set(pair.first).add(pair.second).scl(0.5f)
        startMidpointWorld.set(viewport.unproject(Vector2(scratchMidpoint)))
        gestureActive = true
        onGestureEngaged()
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!pointers.containsKey(pointer)) return gestureActive
        pointers[pointer]!!.set(screenX.toFloat(), screenY.toFloat())
        if (!gestureActive || pointers.size < 2) return gestureActive

        val pair = pointerPair() ?: return true
        val currentDistance = pair.first.dst(pair.second).coerceAtLeast(1f)
        camera.zoom = (startZoom * startDistance / currentDistance).coerceIn(MIN_ZOOM, MAX_ZOOM)

        // Solve for the camera position that keeps startMidpointWorld
        // anchored under the current on-screen midpoint at this new zoom -
        // see the class doc comment's "The math" paragraph. Sit the camera
        // back at its gesture-start position first so this event's
        // unproject isn't polluted by a correction applied on a previous
        // event this same gesture.
        camera.position.set(startCameraPos.x, startCameraPos.y, 0f)
        camera.update()
        scratchMidpoint.set(pair.first).add(pair.second).scl(0.5f)
        scratchWorldPoint.set(viewport.unproject(Vector2(scratchMidpoint)))
        scratchCorrection.set(startMidpointWorld).sub(scratchWorldPoint)
        camera.position.set(startCameraPos.x + scratchCorrection.x, startCameraPos.y + scratchCorrection.y, 0f)
        camera.update()
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val wasGesturing = gestureActive
        pointers.remove(pointer)
        if (pointers.size < 2) {
            gestureActive = false
        }
        return wasGesturing
    }

    private fun pointerPair(): Pair<Vector2, Vector2>? {
        if (pointers.size < 2) return null
        val iterator = pointers.values.iterator()
        val a = iterator.next()
        val b = iterator.next()
        return a to b
    }
}
