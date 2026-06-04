package com.shezik.drawanywhere.view.canvas

import android.os.Looper
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TapGestureDetectorTest {

    private fun fakeMotionEvent(
        action: Int, pointerCount: Int, downTime: Long, eventTime: Long,
        vararg idsAndXY: Number,
    ): MotionEvent {
        val ids = IntArray(pointerCount) { idsAndXY[it * 3].toInt() }
        val xs = FloatArray(pointerCount) { idsAndXY[it * 3 + 1].toFloat() }
        val ys = FloatArray(pointerCount) { idsAndXY[it * 3 + 2].toFloat() }
        val pointers = arrayOfNulls<MotionEvent.PointerProperties>(pointerCount)
        val coords = arrayOfNulls<MotionEvent.PointerCoords>(pointerCount)
        for (i in 0 until pointerCount) {
            pointers[i] = MotionEvent.PointerProperties().apply { id = ids[i] }
            coords[i] = MotionEvent.PointerCoords().apply { x = xs[i]; y = ys[i] }
        }
        return MotionEvent.obtain(
            downTime, eventTime, action, pointerCount,
            pointers, coords, 0, 0, 1f, 1f, 0, 0, 0, 0
        )
    }

    @Test
    fun singleTap_noCallback() {
        val log = mutableListOf<String>()
        val d = TapGestureDetector(
            onTwoFingerDoubleTap = { log.add("2d") }, onTwoFingerTripleTap = { log.add("2t") },
            onThreeFingerDoubleTap = { log.add("3d") }, onThreeFingerTripleTap = { log.add("3t") },
            twoFingerDoubleTapDelayMs = 0L,
        )
        d.onMultiTouchEnter(2, 1000, mapOf(0 to (100f to 100f), 1 to (200f to 200f)))
        val evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 1000, 1050,
            0, 100f, 100f, 1, 200f, 200f)
        d.onMultiTouchEnd(evt)
        evt.recycle()
        assertTrue(log.isEmpty())
    }

    @Test
    fun doubleTap_immediate() {
        val log = mutableListOf<String>()
        val d = TapGestureDetector(
            onTwoFingerDoubleTap = { log.add("2d") }, onTwoFingerTripleTap = { log.add("2t") },
            onThreeFingerDoubleTap = { log.add("3d") }, onThreeFingerTripleTap = { log.add("3t") },
            twoFingerDoubleTapDelayMs = 0L,
        )
        d.onMultiTouchEnter(2, 1000, mapOf(0 to (100f to 100f), 1 to (200f to 200f)))
        var evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 1000, 1020,
            0, 100f, 100f, 1, 200f, 200f)
        d.onMultiTouchEnd(evt); evt.recycle()

        d.onMultiTouchEnter(2, 1100, mapOf(0 to (110f to 110f), 1 to (210f to 210f)))
        evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 1100, 1120,
            0, 110f, 110f, 1, 210f, 210f)
        d.onMultiTouchEnd(evt); evt.recycle()

        assertTrue(log.contains("2d"))
    }

    @Test
    fun movementExceedsThreshold_resets() {
        val log = mutableListOf<String>()
        val d = TapGestureDetector(
            onTwoFingerDoubleTap = { log.add("2d") }, onTwoFingerTripleTap = { log.add("2t") },
            onThreeFingerDoubleTap = { log.add("3d") }, onThreeFingerTripleTap = { log.add("3t") },
            twoFingerDoubleTapDelayMs = 0L,
        )
        d.onMultiTouchEnter(2, 1000, mapOf(0 to (100f to 100f), 1 to (200f to 200f)))
        var evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 1000, 1020,
            0, 100f, 100f, 1, 200f, 200f)
        d.onMultiTouchEnd(evt); evt.recycle()

        // Move 55px > 40px
        d.onMultiTouchEnter(2, 1100, mapOf(0 to (100f to 100f), 1 to (200f to 200f)))
        evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 1100, 1120,
            0, 160f, 100f, 1, 200f, 200f)  // 60px movement
        d.onMultiTouchEnd(evt); evt.recycle()
        assertTrue(log.isEmpty())
    }

    @Test
    fun durationExceedsThreshold_resets() {
        val log = mutableListOf<String>()
        val d = TapGestureDetector(
            onTwoFingerDoubleTap = { log.add("2d") }, onTwoFingerTripleTap = { log.add("2t") },
            onThreeFingerDoubleTap = { log.add("3d") }, onThreeFingerTripleTap = { log.add("3t") },
            twoFingerDoubleTapDelayMs = 0L,
        )
        d.onMultiTouchEnter(2, 1000, mapOf(0 to (100f to 100f), 1 to (200f to 200f)))
        val evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 1000, 1400,
            0, 100f, 100f, 1, 200f, 200f)  // 400ms > 300ms
        d.onMultiTouchEnd(evt); evt.recycle()
        assertTrue(log.isEmpty())
    }

    @Test
    fun intervalReset_separateSequences() {
        val log = mutableListOf<String>()
        val d = TapGestureDetector(
            onTwoFingerDoubleTap = { log.add("2d") }, onTwoFingerTripleTap = { log.add("2t") },
            onThreeFingerDoubleTap = { log.add("3d") }, onThreeFingerTripleTap = { log.add("3t") },
            twoFingerDoubleTapDelayMs = 0L,
        )
        d.onMultiTouchEnter(2, 1000, mapOf(0 to (100f to 100f), 1 to (200f to 200f)))
        var evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 1000, 1020,
            0, 100f, 100f, 1, 200f, 200f)
        d.onMultiTouchEnd(evt); evt.recycle()

        // 500ms later > 200ms interval
        d.onMultiTouchEnter(2, 1600, mapOf(0 to (110f to 110f), 1 to (210f to 210f)))
        evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 2, 1600, 1620,
            0, 110f, 110f, 1, 210f, 210f)
        d.onMultiTouchEnd(evt); evt.recycle()
        assertTrue(log.isEmpty())
    }

    @Test
    fun maxPointerCount_tracksMax() {
        val d = TapGestureDetector(
            onTwoFingerDoubleTap = {}, onTwoFingerTripleTap = {},
            onThreeFingerDoubleTap = {}, onThreeFingerTripleTap = {},
        )
        d.onMultiTouchEnter(2, 1000, mapOf(0 to (100f to 100f), 1 to (200f to 200f)))
        d.onPointerDown(3, 1050, mapOf(0 to (100f to 100f), 1 to (200f to 200f), 2 to (150f to 150f)))
        assertEquals(3, d.maxPointerCount)

        val evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 3, 1000, 1100,
            0, 100f, 100f, 1, 200f, 200f, 2, 150f, 150f)
        d.onMultiTouchEnd(evt); evt.recycle()
        assertEquals(3, d.maxPointerCount)
    }

    @Test
    fun threeFingerDoubleTap_firesCorrectCallback() {
        val log = mutableListOf<String>()
        val d = TapGestureDetector(
            onTwoFingerDoubleTap = { log.add("2d") }, onTwoFingerTripleTap = { log.add("2t") },
            onThreeFingerDoubleTap = { log.add("3d") }, onThreeFingerTripleTap = { log.add("3t") },
            threeFingerDoubleTapDelayMs = 0L,
        )
        d.onMultiTouchEnter(3, 1000, mapOf(0 to (100f to 100f), 1 to (200f to 200f), 2 to (150f to 150f)))
        var evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 3, 1000, 1020,
            0, 100f, 100f, 1, 200f, 200f, 2, 150f, 150f)
        d.onMultiTouchEnd(evt); evt.recycle()

        d.onMultiTouchEnter(3, 1140, mapOf(0 to (100f to 100f), 1 to (200f to 200f), 2 to (150f to 150f)))
        evt = fakeMotionEvent(MotionEvent.ACTION_POINTER_UP, 3, 1140, 1160,
            0, 100f, 100f, 1, 200f, 200f, 2, 150f, 150f)
        d.onMultiTouchEnd(evt); evt.recycle()

        assertTrue(log.contains("3d"))
        assertFalse(log.contains("2d"))
    }
}
