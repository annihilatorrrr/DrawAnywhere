/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025-2026 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.shezik.drawanywhere.view.canvas

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import kotlin.math.sqrt

/**
 * Detects multi-finger double/triple taps. Finger-count agnostic — add new
 * finger counts by adding entries to [states] and corresponding callback
 * parameters.
 *
 * @param twoFingerDoubleTapDelayMs 0 = immediate, TAP_INTERVAL_MS = defer to wait for triple-tap
 * @param threeFingerDoubleTapDelayMs same semantics for 3-finger
 */
class TapGestureDetector(
    private val onTwoFingerDoubleTap: () -> Unit,
    private val onTwoFingerTripleTap: () -> Unit,
    private val onThreeFingerDoubleTap: () -> Unit,
    private val onThreeFingerTripleTap: () -> Unit,
    private val twoFingerDoubleTapDelayMs: Long = 0L,
    private val threeFingerDoubleTapDelayMs: Long = TAP_INTERVAL_MS,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    companion object {
        private const val TAG = "TapDetect"
        const val TAP_MAX_DURATION_MS = 300L
        const val TAP_MAX_MOVEMENT_PX = 40f
        const val TAP_INTERVAL_MS = 200L
    }

    private val gestureAnchors = mutableMapOf<Int, Pair<Float, Float>>()

    /** Maximum finger count seen during the current multi-touch gesture. */
    var maxPointerCount: Int = 0
        private set

    private inner class State(val doubleTapDelayMs: Long) {
        var tapCount: Int = 0
        var lastTapTime: Long = 0L
        var downTime: Long = 0L
        var pendingDoubleTap: Runnable? = null
    }

    private val states: Map<Int, State> = mapOf(
        2 to State(twoFingerDoubleTapDelayMs),
        3 to State(threeFingerDoubleTapDelayMs),
    )

    // ═══════════════════════════════════════════════════════════════
    //  Public API — called by CanvasTouchHandler
    // ═══════════════════════════════════════════════════════════════

    fun onMultiTouchEnter(pointerCount: Int, eventTime: Long, idToPos: Map<Int, Pair<Float, Float>>) {
        cancelPending()
        maxPointerCount = pointerCount
        states.forEach { (count, state) ->
            if (count <= pointerCount) state.downTime = eventTime
        }
        gestureAnchors.clear()
        gestureAnchors.putAll(idToPos)
    }

    fun onPointerDown(pointerCount: Int, eventTime: Long, idToPos: Map<Int, Pair<Float, Float>>) {
        maxPointerCount = maxOf(maxPointerCount, pointerCount)
        if (pointerCount >= 3) {
            states[pointerCount]?.downTime = eventTime
            gestureAnchors.clear()
            gestureAnchors.putAll(idToPos)
        }
    }

    fun onMultiTouchEnd(event: MotionEvent) {
        val state = states[maxPointerCount] ?: return
        val moved = maxPointerMovement(event)
        val duration = event.eventTime - state.downTime
        finishTap(state, duration, moved, event.eventTime)
    }

    fun cancelPending() {
        states.values.forEach { state ->
            state.pendingDoubleTap?.let { handler.removeCallbacks(it) }
            state.pendingDoubleTap = null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Tap state machine
    // ═══════════════════════════════════════════════════════════════

    private fun finishTap(state: State, duration: Long, moved: Float, now: Long) {
        if (duration < TAP_MAX_DURATION_MS && moved < TAP_MAX_MOVEMENT_PX) {
            if (now - state.lastTapTime < TAP_INTERVAL_MS) {
                state.tapCount++
            } else {
                state.tapCount = 1
            }
            state.lastTapTime = now

            when (state.tapCount) {
                2 -> {
                    state.pendingDoubleTap?.let { handler.removeCallbacks(it) }
                    if (state.doubleTapDelayMs > 0L) {
                        Log.d(TAG, "  → double-tap: deferred ${state.doubleTapDelayMs}ms")
                        state.pendingDoubleTap = Runnable {
                            dispatchDoubleTap()
                            state.pendingDoubleTap = null
                        }.also { handler.postDelayed(it, state.doubleTapDelayMs) }
                    } else {
                        Log.d(TAG, "  → double-tap: immediate")
                        dispatchDoubleTap()
                    }
                }
                3 -> {
                    Log.d(TAG, "  → triple-tap")
                    state.pendingDoubleTap?.let { handler.removeCallbacks(it) }
                    state.pendingDoubleTap = null
                    dispatchTripleTap()
                    state.tapCount = 0
                }
            }
        } else {
            Log.d(TAG, "  → tap sequence reset (moved=${moved.toInt()}px, duration=${duration}ms)")
            state.lastTapTime = 0L
            state.tapCount = 0
            state.pendingDoubleTap?.let { handler.removeCallbacks(it) }
            state.pendingDoubleTap = null
        }
    }

    private fun dispatchDoubleTap() {
        when (maxPointerCount) {
            2 -> onTwoFingerDoubleTap()
            3 -> onThreeFingerDoubleTap()
        }
    }

    private fun dispatchTripleTap() {
        when (maxPointerCount) {
            2 -> onTwoFingerTripleTap()
            3 -> onThreeFingerTripleTap()
        }
    }

    private fun maxPointerMovement(event: MotionEvent): Float {
        var maxMoved = 0f
        for (i in 0 until event.pointerCount) {
            val anchor = gestureAnchors[event.getPointerId(i)] ?: continue
            val dx = event.getX(i) - anchor.first
            val dy = event.getY(i) - anchor.second
            maxMoved = maxOf(maxMoved, sqrt(dx * dx + dy * dy))
        }
        return maxMoved
    }
}
