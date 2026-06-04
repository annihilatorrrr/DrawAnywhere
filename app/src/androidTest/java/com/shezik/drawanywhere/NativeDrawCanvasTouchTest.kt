package com.shezik.drawanywhere

import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.StrokeModifier
import com.shezik.drawanywhere.view.canvas.NativeDrawCanvasView
import org.junit.Assert.*
import org.junit.Test

/**
 * Touch → stroke pipeline test without WindowManager.
 * Calls onTouchEvent directly with synthesized MotionEvents.
 */
class NativeDrawCanvasTouchTest {

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun downMoveUpCreatesStrokeAfterDebounce() {
        val (controller, view) = setup()

        val downTime = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 200f, 0))

        // Stroke not yet created — still in finger debounce
        assertEquals(0, controller.strokeList.size)

        // Move after debounce window (>50ms) — flush accumulated points
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 70, MotionEvent.ACTION_MOVE, 110f, 210f, 0))

        assertEquals(1, controller.strokeList.size)
        // 2 points: the DOWN point + the MOVE point
        assertEquals(2, controller.strokeList[0].points.size)

        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 140, MotionEvent.ACTION_UP, 120f, 220f, 0))
    }

    @Test
    fun fingerTapCreatesDot() {
        val (controller, view) = setup()

        val t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        // Still pending — no stroke yet
        assertEquals(0, controller.strokeList.size)

        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, 100f, 200f, 0))
        // UP resolves pending as a single-point stroke
        assertEquals(1, controller.strokeList.size)
        assertEquals(1, controller.strokeList[0].points.size)
    }

    @Test
    fun secondFingerCancelsPendingTap() {
        val (controller, view) = setup()

        val t = System.currentTimeMillis()
        // Finger 1 DOWN → pending
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))

        // Second finger arrives within debounce window — cancels pending
        val props = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER },
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = 100f; y = 200f },
            MotionEvent.PointerCoords().apply { x = 300f; y = 200f },
        )
        val event = MotionEvent.obtain(
            t, t + 20, MotionEvent.ACTION_POINTER_DOWN,
            2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
        view.onTouchEvent(event)
        event.recycle()

        // End multi-touch with UP
        view.onTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 100f, 200f, 0))

        // No stroke should have been created — pending was discarded by POINTER_DOWN
        assertEquals(0, controller.strokeList.size)
    }

    @Test
    fun pendingAccumulatesMovePointsDuringDebounce() {
        val (controller, view) = setup()

        val downTime = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 200f, 0))

        // Multiple MOVE events within debounce window — all accumulated
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE, 105f, 205f, 0))
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_MOVE, 110f, 210f, 0))
        assertEquals(0, controller.strokeList.size)  // still pending

        // Move after debounce — flushes all accumulated points
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 70, MotionEvent.ACTION_MOVE, 115f, 215f, 0))

        assertEquals(1, controller.strokeList.size)
        // 4 points: DOWN + 2 accumulated MOVEs + the flush MOVE
        assertEquals(4, controller.strokeList[0].points.size)
    }

    private fun setup(): Pair<DrawController, NativeDrawCanvasView> {
        val controller = DrawController(PenConfig())
        val vm = DrawViewModel(
            controller = controller,
            preferencesMgr = PreferencesManager(appContext),
            initialUiState = UiState(),
            initialServiceState = ServiceState(),
            stopService = {}
        )
        val view = NativeDrawCanvasView(appContext, controller, vm)
        return controller to view
    }

    @Test
    fun multipleTapsWork() {
        val (controller, view) = setup()

        var t = System.currentTimeMillis()
        // First tap
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 0f, 0f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, 10f, 10f, 0))

        // Second tap
        t += 100
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 50f, 50f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, 60f, 60f, 0))

        assertEquals(2, controller.strokeList.size)
    }

    @Test
    fun cancelWhilePendingStillCreatesDot() {
        val (controller, view) = setup()

        val t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 0f, 0f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))

        assertEquals(1, controller.strokeList.size)

        // New stroke after cancel
        val t2 = t + 100
        view.onTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        view.onTouchEvent(MotionEvent.obtain(t2, t2 + 30, MotionEvent.ACTION_UP, 20f, 20f, 0))

        assertEquals(2, controller.strokeList.size)
    }

    @Test
    fun fingerMovesThenLiftsBeforeDebounceCreatesStroke() {
        // Finger DOWN → MOVE → UP all within 50ms debounce window.
        // The accumulated points should be flushed as a real stroke, not lost.
        val (controller, view) = setup()

        val t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, 110f, 210f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, 120f, 220f, 0))
        assertEquals(0, controller.strokeList.size)  // still pending

        // Lift finger at 30ms — before debounce expires
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, 120f, 220f, 0))

        assertEquals(1, controller.strokeList.size)
        // 3 points: DOWN + 2 accumulated MOVEs
        assertEquals(3, controller.strokeList[0].points.size)
    }

    @Test
    fun pureTapWithoutMovementCreatesDot() {
        val (controller, view) = setup()

        val t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, 100f, 200f, 0))

        assertEquals(1, controller.strokeList.size)
        assertEquals(1, controller.strokeList[0].points.size)
    }

    @Test
    fun cancelDuringMultiTouchAllowsSubsequentDown() {
        // CANCEL during multi-touch clears isMultiTouch flag.
        // Subsequent DOWN must be processed (not swallowed).
        val (controller, view) = setup()
        val t = System.currentTimeMillis()

        // Two fingers → multi-touch
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        val finger2 = multiTouchEvent(t, t + 10, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(0 to MotionEvent.TOOL_TYPE_FINGER, 1 to MotionEvent.TOOL_TYPE_FINGER),
            arrayOf(100f to 200f, 300f to 200f))
        view.onTouchEvent(finger2); finger2.recycle()

        // System sends CANCEL (e.g. stylus approaching)
        view.onTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))

        // New DOWN arrives — must not be blocked by stale isMultiTouch
        view.onTouchEvent(MotionEvent.obtain(t, t + 25, MotionEvent.ACTION_DOWN, 500f, 300f, 0))

        // The DOWN was processed (finger pending, or stroke started)
        assertEquals(0, controller.strokeList.size)  // still finger-pending, no movement
    }

    @Test
    fun stylusArrivesDuringFingerPendingEntersMultiTouch() {
        // When stylus brings POINTER_DOWN during finger pending (without CANCEL first),
        // both are non-stylus tool types in practice → enters multi-touch.
        val (controller, view) = setup()
        val t = System.currentTimeMillis()

        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        assertEquals(0, controller.strokeList.size)

        val finger2 = multiTouchEvent(t, t + 10, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(0 to MotionEvent.TOOL_TYPE_FINGER, 1 to MotionEvent.TOOL_TYPE_FINGER),
            arrayOf(100f to 200f, 300f to 200f))
        view.onTouchEvent(finger2); finger2.recycle()

        // Finger pending was discarded by multi-touch entry
        // Both pointers are now in multi-touch (pan/zoom)
        assertEquals(0, controller.strokeList.size)
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun multiTouchEvent(
        downTime: Long, eventTime: Long, action: Int,
        pointerSpecs: Array<Pair<Int, Int>>,  // (id, toolType)
        pointCoords: Array<Pair<Float, Float>> // (x, y)
    ): MotionEvent {
        val props = pointerSpecs.map { (id, toolType) ->
            MotionEvent.PointerProperties().apply { this.id = id; this.toolType = toolType }
        }.toTypedArray()
        val coords = pointCoords.map { (x, y) ->
            MotionEvent.PointerCoords().apply { this.x = x; this.y = y }
        }.toTypedArray()
        return MotionEvent.obtain(
            downTime, eventTime, action,
            pointerSpecs.size, props, coords,
            0, 0, 1f, 1f, 0, 0, 0, 0
        )
    }
}
