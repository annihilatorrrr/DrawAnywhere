package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class DrawUtilsTest {

    @Test
    fun distanceSimple() {
        assertEquals(5f, distance(Offset(0f, 0f), Offset(3f, 4f)), 0.001f)
    }

    @Test
    fun distanceZero() {
        assertEquals(0f, distance(Offset(1f, 1f), Offset(1f, 1f)), 0.001f)
    }

    @Test
    fun distanceSquaredMatchesDistance() {
        val d2 = distanceSquared(Offset(7f, 3f), Offset(2f, 15f))
        val d = distance(Offset(7f, 3f), Offset(2f, 15f))
        assertEquals(d * d, d2, 0.001f)
    }

    @Test
    fun distancePointToLineSegment_pointOnSegment() {
        val dist = distancePointToLineSegment(
            Offset(3f, 3f),
            Offset(1f, 1f),
            Offset(5f, 5f)
        )
        assertEquals(0f, dist, 0.001f)
    }

    @Test
    fun distancePointToLineSegment_closestToStart() {
        // Point is closer to 'a' than any point on segment
        val dist = distancePointToLineSegment(
            Offset(0f, 0f),
            Offset(5f, 5f),
            Offset(10f, 10f)
        )
        assertEquals(distance(Offset(0f, 0f), Offset(5f, 5f)), dist, 0.001f)
    }

    @Test
    fun distancePointToLineSegment_closestToEnd() {
        val dist = distancePointToLineSegment(
            Offset(15f, 15f),
            Offset(5f, 5f),
            Offset(10f, 10f)
        )
        assertEquals(distance(Offset(15f, 15f), Offset(10f, 10f)), dist, 0.001f)
    }

    @Test
    fun distancePointToLineSegment_degenerateSegment() {
        val dist = distancePointToLineSegment(
            Offset(3f, 4f),
            Offset(1f, 1f),
            Offset(1f, 1f)
        )
        // distance from (1,1) to (3,4) = sqrt((2)^2+(3)^2) = sqrt(13)
        assertEquals(3.6055f, dist, 0.01f)
    }

    @Test
    fun hitTestRectEdge_topEdgeHit() {
        assertTrue(hitTestRectEdge(
            Offset(50f, 10f), 10f, 10f, 100f, 100f, threshold = 10f
        ))
    }

    @Test
    fun hitTestRectEdge_missesInterior() {
        assertFalse(hitTestRectEdge(
            Offset(55f, 55f), 10f, 10f, 100f, 100f, threshold = 5f
        ))
    }

    @Test
    fun hitTestRectEdge_missesFarAway() {
        assertFalse(hitTestRectEdge(
            Offset(200f, 200f), 10f, 10f, 100f, 100f, threshold = 10f
        ))
    }
}
