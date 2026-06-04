package com.shezik.drawanywhere.view.canvas

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class CanvasViewportTest {

    private val vp = CanvasViewport()

    @Test
    fun defaultIsIdentity() {
        assertEquals(1f, vp.zoom)
        assertEquals(0f, vp.panX)
        assertEquals(0f, vp.panY)
        assertFalse(vp.zoomLocked)
    }

    @Test
    fun screenToWorld_identity() {
        val wp = vp.screenToWorld(Offset(100f, 200f))
        assertEquals(100f, wp.x)
        assertEquals(200f, wp.y)
    }

    @Test
    fun screenToWorld_withPan() {
        val panned = vp.copy(panX = 50f, panY = -30f)
        // screenToWorld: (screen + pan) / zoom
        val wp = panned.screenToWorld(Offset(100f, 200f))
        assertEquals(150f, wp.x)  // (100 + 50) / 1
        assertEquals(170f, wp.y)  // (200 - 30) / 1
    }

    @Test
    fun screenToWorld_withZoom() {
        val zoomed = vp.copy(zoom = 2f)
        val wp = zoomed.screenToWorld(Offset(100f, 200f))
        assertEquals(50f, wp.x)   // 100 / 2
        assertEquals(100f, wp.y)  // 200 / 2
    }

    @Test
    fun screenToWorld_withPanAndZoom() {
        val pz = vp.copy(zoom = 2f, panX = 100f, panY = 50f)
        // screenToWorld = (screen/zoom) + pan
        val wp = pz.screenToWorld(Offset(100f, 200f))
        assertEquals(150f, wp.x)  // 100/2 + 100 = 150
        assertEquals(150f, wp.y)  // 200/2 + 50 = 150
    }

    @Test
    fun pan_movesCorrectly() {
        val panned = vp.pan(10f, 20f)
        assertEquals(-10f, panned.panX)
        assertEquals(-20f, panned.panY)
    }

    @Test
    fun pan_accountsForZoom() {
        val zoomed = vp.copy(zoom = 2f)
        val panned = zoomed.pan(20f, 40f)
        // pan = -screen delta / zoom, so -20/2 = -10 world units
        assertEquals(-10f, panned.panX)
        assertEquals(-20f, panned.panY)
    }

    @Test
    fun zoomAt_centerScreen_staysCentered() {
        // Zoom centered on screen center: world pivot should stay under the same screen point
        val result = vp.zoomAt(2f, Offset(50f, 50f))
        assertEquals(2f, result.zoom)
        // The point at screen(50,50) should map to the same world point before & after
        val wpBefore = vp.screenToWorld(Offset(50f, 50f))
        val wpAfter = result.screenToWorld(Offset(50f, 50f))
        assertEquals(wpBefore.x, wpAfter.x, 0.01f)
        assertEquals(wpBefore.y, wpAfter.y, 0.01f)
    }

    @Test
    fun zoomAt_respectsMinZoom() {
        val result = vp.zoomAt(0.0001f, Offset(0f, 0f))
        assertEquals(CanvasViewport.MIN_ZOOM, result.zoom)
    }

    @Test
    fun zoomAt_locked_doesNotChangeZoom() {
        val locked = vp.copy(zoomLocked = true, zoom = 1.5f)
        val result = locked.zoomAt(2f, Offset(0f, 0f))
        assertEquals(1.5f, result.zoom)  // unchanged
    }

    @Test
    fun resetAt_preservesCenter() {
        val modified = vp.copy(zoom = 3f, panX = 100f, panY = -50f)
        // Screen center at (540, 960) — the world point under it should stay after reset
        val screenCenter = Offset(540f, 960f)
        val worldCenter = modified.screenToWorld(screenCenter)

        val reset = modified.resetAt(screenCenter)
        assertEquals(1f, reset.zoom)

        // After reset, the same world point should map back to the same screen position
        val wpAfter = reset.screenToWorld(screenCenter)
        assertEquals(worldCenter.x, wpAfter.x, 0.01f)
        assertEquals(worldCenter.y, wpAfter.y, 0.01f)
    }

    @Test
    fun withZoomLock_togglesCorrectly() {
        val locked = vp.withZoomLock(true)
        assertTrue(locked.zoomLocked)
        val unlocked = locked.withZoomLock(false)
        assertFalse(unlocked.zoomLocked)
    }

    @Test
    fun zoomAt_toSameZoom_identityReturn() {
        val result = vp.zoomAt(1f, Offset(0f, 0f))
        assertEquals(vp, result)  // no change → same instance fields
    }

    @Test
    fun screenToWorld_roundTrip() {
        val original = vp.copy(zoom = 2.5f, panX = 30f, panY = -20f)
        val screen = Offset(200f, 300f)
        val world = original.screenToWorld(screen)
        // Reconstruct: world * zoom - pan*zoom should give back screen coords...
        // Actually: screenToWorld = screen/zoom + pan
        // So: screen = (world - pan) * zoom
        val reconstructedScreen = Offset(
            (world.x - original.panX) * original.zoom,
            (world.y - original.panY) * original.zoom
        )
        assertEquals(screen.x, reconstructedScreen.x, 0.01f)
        assertEquals(screen.y, reconstructedScreen.y, 0.01f)
    }
}
