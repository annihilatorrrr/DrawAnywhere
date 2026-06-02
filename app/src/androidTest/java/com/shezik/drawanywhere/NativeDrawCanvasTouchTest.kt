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
    fun downMoveUpCreatesStroke() {
        val controller = DrawController().apply { setPenConfig(PenConfig()) }
        val vm = DrawViewModel(
            controller = controller,
            preferencesMgr = PreferencesManager(appContext),
            initialUiState = UiState(),
            initialServiceState = ServiceState(),
            stopService = {}
        )
        val view = NativeDrawCanvasView(appContext, controller, vm)

        val downTime = System.currentTimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 200f, 0)
        view.onTouchEvent(down)
        down.recycle()

        assertEquals(1, controller.strokeList.size)

        val move = MotionEvent.obtain(downTime, downTime + 16, MotionEvent.ACTION_MOVE, 110f, 210f, 0)
        view.onTouchEvent(move)
        move.recycle()

        assertEquals(2, controller.strokeList[0].points.size)

        val up = MotionEvent.obtain(downTime, downTime + 32, MotionEvent.ACTION_UP, 120f, 220f, 0)
        view.onTouchEvent(up)
        up.recycle()

        // After up, stroke is finished
        // A new stroke requires a new DOWN
    }

    @Test
    fun multipleStrokesWork() {
        val controller = DrawController().apply { setPenConfig(PenConfig()) }
        val vm = DrawViewModel(
            controller = controller,
            preferencesMgr = PreferencesManager(appContext),
            initialUiState = UiState(),
            initialServiceState = ServiceState(),
            stopService = {}
        )
        val view = NativeDrawCanvasView(appContext, controller, vm)

        // First stroke
        var t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 0f, 0f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 10f, 10f, 0))

        // Second stroke
        t += 100
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 50f, 50f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, 60f, 60f, 0))

        assertEquals(2, controller.strokeList.size)
    }

    @Test
    fun cancellingStrokeStillFinishesStroke() {
        val controller = DrawController().apply { setPenConfig(PenConfig()) }
        val vm = DrawViewModel(
            controller = controller,
            preferencesMgr = PreferencesManager(appContext),
            initialUiState = UiState(),
            initialServiceState = ServiceState(),
            stopService = {}
        )
        val view = NativeDrawCanvasView(appContext, controller, vm)

        val t = System.currentTimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 0f, 0f, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_CANCEL, 0f, 0f, 0))

        // ACTION_CANCEL finishes the stroke (doesn't discard), so we have 1
        assertEquals(1, controller.strokeList.size)

        // New stroke after cancel
        val t2 = t + 100
        view.onTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        view.onTouchEvent(MotionEvent.obtain(t2, t2 + 10, MotionEvent.ACTION_UP, 20f, 20f, 0))

        assertEquals(2, controller.strokeList.size)
    }
}
