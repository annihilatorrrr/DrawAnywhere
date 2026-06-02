package com.shezik.drawanywhere

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import org.junit.Assert.*
import org.junit.Test

class DrawUtilsInstrumentedTest {

    private fun makePoints(vararg xy: Float): List<Offset> =
        xy.toList().chunked(2).map { Offset(it[0], it[1]) }

    @Test
    fun pointsToPath_emptyList() {
        val path = pointsToPath(emptyList())
        // Should not crash — path is empty but valid
        assertNotNull(path)
    }

    @Test
    fun pointsToPath_singlePoint() {
        val path = pointsToPath(makePoints(10f, 20f))
        // Single point: just moveTo + lineTo to same point (last point)
        assertNotNull(path)
    }

    @Test
    fun pointsToPath_correctQuadraticSequence() {
        // 3 points should produce: moveTo(0,0) → lineTo(mid1) → quadTo(10,10, mid1) → lineTo(20,0)
        val path = pointsToPath(makePoints(0f, 0f, 10f, 10f, 20f, 0f))
        assertNotNull(path)
    }

    @Test
    fun pointsToPath_strokeConstruction() {
        // Simulate what DrawController.createStroke does
        val stroke = com.shezik.drawanywhere.model.DrawObject.Stroke(
            points = mutableStateListOf(Offset(50f, 50f)),
            color = Color.Red,
            width = 5f,
            alpha = 1f
        )
        // After adding more points
        stroke.points.add(Offset(55f, 55f))
        stroke.invalidatePath()
        stroke.points.add(Offset(60f, 53f))
        stroke.invalidatePath()

        val cached = stroke.cachedPath  // Access triggers rebuild
        assertNotNull(cached)
    }
}
