package com.shezik.drawanywhere

import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.view.canvas.CanvasViewport
import com.shezik.drawanywhere.view.canvas.NativeDrawCanvasView
import org.junit.Assert.*
import org.junit.Test

/**
 * Touch → stroke pipeline tests (no WindowManager).
 * Covers: finger/stylus drawing, debounce, multi-touch, tap gestures,
 *         viewport restore, pending double-tap cancellation.
 */
class NativeDrawCanvasTouchTest {

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ═══════════════════════════════════════════════════════════════
    //  Finger drawing
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun downMoveUpCreatesStrokeAfterDebounce() {
        val (controller, view) = setup()

        val downTime = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        assertEquals(0, controller.strokeList.size)  // still pending

        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 70, MotionEvent.ACTION_MOVE, 110f, 210f, 0))
        assertEquals(1, controller.strokeList.size)
        assertEquals(2, controller.strokeList[0].points.size)  // DOWN + MOVE
    }

    @Test
    fun fingerTapCreatesDot() {
        val (controller, view) = setup()
        val t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, 100f, 200f, 0))
        assertEquals(1, controller.strokeList.size)
        assertEquals(1, controller.strokeList[0].points.size)
    }

    @Test
    fun fingerMovesThenLiftsBeforeDebounceCreatesStroke() {
        val (controller, view) = setup()
        val t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_MOVE, 110f, 210f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_MOVE, 120f, 220f, 0))
        assertEquals(0, controller.strokeList.size)
        // Lift at 30ms — before debounce → flush accumulated points
        view.onTouchEvent(MotionEvent.obtain(t, t + 30, MotionEvent.ACTION_UP, 120f, 220f, 0))
        assertEquals(1, controller.strokeList.size)
        assertEquals(3, controller.strokeList[0].points.size)  // DOWN + 2 MOVEs
    }

    @Test
    fun pendingAccumulatesMovePointsDuringDebounce() {
        val (controller, view) = setup()
        val downTime = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 10, MotionEvent.ACTION_MOVE, 105f, 205f, 0))
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 20, MotionEvent.ACTION_MOVE, 110f, 210f, 0))
        assertEquals(0, controller.strokeList.size)
        // Debounce expires
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 70, MotionEvent.ACTION_MOVE, 115f, 215f, 0))
        assertEquals(1, controller.strokeList.size)
        assertEquals(4, controller.strokeList[0].points.size)  // DOWN + 2 accumulated + flush MOVE
    }

    @Test
    fun cancelWhilePendingStillCreatesDot() {
        val (controller, view) = setup()
        val t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 0f, 0f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))
        assertEquals(1, controller.strokeList.size)
    }

    // ═══════════════════════════════════════════════════════════════
    //  Multi-touch entry & cancellation
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun secondFingerCancelsPendingTap() {
        val (controller, view) = setup()
        val t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        val finger2 = multiTouchEvent(t, t + 20, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(0 to MotionEvent.TOOL_TYPE_FINGER, 1 to MotionEvent.TOOL_TYPE_FINGER),
            arrayOf(100f to 200f, 300f to 200f))
        view.onTouchEvent(finger2); finger2.recycle()
        view.onTouchEvent(MotionEvent.obtain(t, t + 40, MotionEvent.ACTION_UP, 100f, 200f, 0))
        assertEquals(0, controller.strokeList.size)
    }

    @Test
    fun cancelDuringMultiTouchRestoresViewportAndAllowsSubsequentDown() {
        val (_, view, vm) = setupWithViewModel()
        val t = System.currentTimeMillis()

        // Pan to a known state
        vm.setViewport(CanvasViewport(panX = 50f, panY = 100f, zoom = 1.5f))

        // Enter multi-touch
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 200f, 0))
        val finger2 = multiTouchEvent(t, t + 10, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(0 to MotionEvent.TOOL_TYPE_FINGER, 1 to MotionEvent.TOOL_TYPE_FINGER),
            arrayOf(100f to 200f, 300f to 200f))
        view.onTouchEvent(finger2); finger2.recycle()

        // Pan — viewport changes
        view.onTouchEvent(genMove(t, t + 15, 110f, 210f, 310f, 210f))
        assertNotEquals(1.5f, vm.viewport.value.zoom)  // zoom may change on move

        // CANCEL (stylus approaching) → viewport restored
        view.onTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))
        assertEquals(50f, vm.viewport.value.panX, 0.01f)
        assertEquals(100f, vm.viewport.value.panY, 0.01f)
        assertEquals(1.5f, vm.viewport.value.zoom, 0.01f)

        // Subsequent DOWN must be processed (not stuck in multi-touch)
        view.onTouchEvent(MotionEvent.obtain(t, t + 25, MotionEvent.ACTION_DOWN, 500f, 300f, 0))
        assertEquals(0, view.drawController.strokeList.size)  // finger-pending, no move yet
    }

    // ═══════════════════════════════════════════════════════════════
    //  Two-finger tap gestures
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun twoFingerDoubleTapResetsViewport() {
        val (_, view, vm) = setupWithViewModel()
        vm.setViewport(CanvasViewport(panX = 200f, panY = -100f, zoom = 3f))
        assertNotEquals(1f, vm.viewport.value.zoom)

        // Two quick two-finger taps
        var t = System.currentTimeMillis()
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)
        t += 100
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)

        // Viewport zoom reset to 1, centered on view center
        assertEquals(1f, vm.viewport.value.zoom, 0.01f)
    }

    @Test
    fun twoFingerDoubleTapIgnoredWhenZoomLocked() {
        val (_, view, vm) = setupWithViewModel()
        vm.setViewport(CanvasViewport(panX = 200f, panY = -100f, zoom = 3f, zoomLocked = true))

        var t = System.currentTimeMillis()
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)
        t += 100
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)

        // Zoom unchanged — locked
        assertEquals(3f, vm.viewport.value.zoom, 0.01f)
    }

    @Test
    fun twoFingerTripleTapResetsToOrigin() {
        val (_, view, vm) = setupWithViewModel()
        vm.setViewport(CanvasViewport(panX = 200f, panY = -100f, zoom = 3f))

        var t = System.currentTimeMillis()
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)
        t += 100
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)
        t += 100
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)

        assertEquals(0f, vm.viewport.value.panX, 0.01f)
        assertEquals(0f, vm.viewport.value.panY, 0.01f)
        assertEquals(1f, vm.viewport.value.zoom, 0.01f)
    }

    @Test
    fun twoFingerTripleTapKeepsZoomWhenLocked() {
        val (_, view, vm) = setupWithViewModel()
        vm.setViewport(CanvasViewport(panX = 200f, panY = -100f, zoom = 3f, zoomLocked = true))

        var t = System.currentTimeMillis()
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)
        t += 100
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)
        t += 100
        simulateTwoFingerTap(view, t, 100f, 200f, 300f, 200f)

        assertEquals(0f, vm.viewport.value.panX, 0.01f)
        assertEquals(0f, vm.viewport.value.panY, 0.01f)
        assertEquals(3f, vm.viewport.value.zoom, 0.01f)     // zoom preserved
        assertTrue(vm.viewport.value.zoomLocked)             // lock preserved
    }

    // ═══════════════════════════════════════════════════════════════
    //  Three-finger tap gestures
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun threeFingerDoubleTapTogglesPassthroughAfterDelay() {
        val (_, view, vm) = setupWithViewModel()
        assertFalse(vm.uiState.value.canvasPassthrough)

        var t = System.currentTimeMillis()
        simulateThreeFingerTap(view, t, 100f, 200f, 300f, 200f, 500f, 200f)
        t += 100
        simulateThreeFingerTap(view, t, 100f, 200f, 300f, 200f, 500f, 200f)

        // Not yet — deferred 400ms
        assertFalse(vm.uiState.value.canvasPassthrough)

        // Wait for deferred callback
        Thread.sleep(450)
        assertTrue(vm.uiState.value.canvasPassthrough)
    }

    @Test
    fun threeFingerTripleTapHidesCanvas() {
        val (_, view, vm) = setupWithViewModel()
        assertTrue(vm.uiState.value.canvasVisible)

        var t = System.currentTimeMillis()
        simulateThreeFingerTap(view, t, 100f, 200f, 300f, 200f, 500f, 200f)
        t += 100
        simulateThreeFingerTap(view, t, 100f, 200f, 300f, 200f, 500f, 200f)
        t += 100
        simulateThreeFingerTap(view, t, 100f, 200f, 300f, 200f, 500f, 200f)

        assertFalse(vm.uiState.value.canvasVisible)
        // Passthrough NOT toggled (triple-tap cancels double-tap)
        assertFalse(vm.uiState.value.canvasPassthrough)
    }

    @Test
    fun newPointerDownCancelsPendingDoubleTap() {
        val (_, view, vm) = setupWithViewModel()

        // Three-finger double-tap → deferred passthrough pending
        var t = System.currentTimeMillis()
        simulateThreeFingerTap(view, t, 100f, 200f, 300f, 200f, 500f, 200f)
        t += 100
        simulateThreeFingerTap(view, t, 100f, 200f, 300f, 200f, 500f, 200f)

        // Immediately start drawing with finger → cancels pending
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_DOWN, 100f, 300f, 0))
        Thread.sleep(450)

        // Passthrough should NOT have toggled
        assertFalse(vm.uiState.value.canvasPassthrough)
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private fun setup(): Pair<DrawController, NativeDrawCanvasView> {
        val (c, v, _) = setupWithViewModel()
        return c to v
    }

    private fun setupWithViewModel(): Triple<DrawController, NativeDrawCanvasView, DrawViewModel> {
        val controller = DrawController(PenConfig())
        val vm = DrawViewModel(
            controller = controller,
            preferencesMgr = PreferencesManager(appContext),
            initialUiState = UiState(),
            initialServiceState = ServiceState(),
            stopService = {}
        )
        val view = NativeDrawCanvasView(appContext, controller, vm)
        return Triple(controller, view, vm)
    }

    private val NativeDrawCanvasView.drawController: DrawController
        get() {
            val field = NativeDrawCanvasView::class.java.getDeclaredField("drawController")
            field.isAccessible = true
            return field.get(this) as DrawController
        }

    private fun simulateTwoFingerTap(
        view: NativeDrawCanvasView, downTime: Long,
        x0: Float, y0: Float, x1: Float, y1: Float
    ) {
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x0, y0, 0))
        val ptrDown = multiTouchEvent(downTime, downTime + 5, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(0 to MotionEvent.TOOL_TYPE_FINGER, 1 to MotionEvent.TOOL_TYPE_FINGER),
            arrayOf(x0 to y0, x1 to y1))
        view.onTouchEvent(ptrDown); ptrDown.recycle()
        // Lift both fingers quickly
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 110, MotionEvent.ACTION_POINTER_UP,
            x0, y0, 0).apply {
            // We need 2 pointers in the PTR_UP event for the gesture to finish properly
        })
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 120, MotionEvent.ACTION_UP, x1, y1, 0))
    }

    private fun simulateThreeFingerTap(
        view: NativeDrawCanvasView, downTime: Long,
        x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float
    ) {
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x0, y0, 0))

        // Second finger → enters multi-touch
        val ptrDown2 = multiTouchEvent(downTime, downTime + 5, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(0 to MotionEvent.TOOL_TYPE_FINGER, 1 to MotionEvent.TOOL_TYPE_FINGER),
            arrayOf(x0 to y0, x1 to y1))
        view.onTouchEvent(ptrDown2); ptrDown2.recycle()

        // Third finger → PTR_DOWN with 3 pointers (goes through handleMultiTouchMode)
        val ptrDown3 = multiTouchEvent(downTime, downTime + 10, MotionEvent.ACTION_POINTER_DOWN,
            arrayOf(0 to MotionEvent.TOOL_TYPE_FINGER, 1 to MotionEvent.TOOL_TYPE_FINGER, 2 to MotionEvent.TOOL_TYPE_FINGER),
            arrayOf(x0 to y0, x1 to y1, x2 to y2))
        view.onTouchEvent(ptrDown3); ptrDown3.recycle()

        // Lift fingers (PTR_UP from 3→2, then PTR_UP from 2→1 or UP)
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 100, MotionEvent.ACTION_POINTER_UP,
            x0, y0, 0))
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 110, MotionEvent.ACTION_POINTER_UP,
            x1, y1, 0))
        view.onTouchEvent(MotionEvent.obtain(downTime, downTime + 120, MotionEvent.ACTION_UP,
            x2, y2, 0))
    }

    private fun genMove(
        downTime: Long, eventTime: Long,
        x0: Float, y0: Float, x1: Float, y1: Float
    ): MotionEvent {
        val props = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER },
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = x0; y = y0 },
            MotionEvent.PointerCoords().apply { x = x1; y = y1 },
        )
        return MotionEvent.obtain(
            downTime, eventTime, MotionEvent.ACTION_MOVE,
            2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
    }

    private fun multiTouchEvent(
        downTime: Long, eventTime: Long, action: Int,
        pointerSpecs: Array<Pair<Int, Int>>,
        pointCoords: Array<Pair<Float, Float>>
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
