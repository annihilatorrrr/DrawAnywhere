package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class DrawUtilsInstrumentedTest {

    private fun makePoints(vararg xy: Float): List<Offset> =
        xy.toList().chunked(2).map { Offset(it[0], it[1]) }

    @Test
    fun pointsToPath_emptyList() {
        val path = pointsToPath(emptyList())
        assertNotNull(path)
    }

    @Test
    fun pointsToPath_singlePoint() {
        val path = pointsToPath(makePoints(10f, 20f))
        assertNotNull(path)
    }

    @Test
    fun pointsToPath_correctQuadraticSequence() {
        val path = pointsToPath(makePoints(0f, 0f, 10f, 10f, 20f, 0f))
        assertNotNull(path)
    }

    @Test
    fun pointsToPath_incrementalAdd() {
        val points = mutableListOf(Offset(50f, 50f))
        var path = pointsToPath(points)
        assertNotNull(path)

        points.add(Offset(55f, 55f))
        points.add(Offset(60f, 53f))
        path = pointsToPath(points)
        assertNotNull(path)
    }
}
