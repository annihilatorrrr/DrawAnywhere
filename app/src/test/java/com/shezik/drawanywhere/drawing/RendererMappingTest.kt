package com.shezik.drawanywhere.drawing

import com.shezik.drawanywhere.model.PenType
import org.junit.Assert.*
import org.junit.Test

class RendererMappingTest {

    @Test
    fun everyPenType_hasNonNullRenderer() {
        for (type in PenType.entries) {
            assertNotNull("${type.name}.renderer is null", type.renderer)
        }
    }

    @Test
    fun everyPenType_hasNonNullHitTester() {
        for (type in PenType.entries) {
            assertNotNull("${type.name}.hitTester is null", type.hitTester)
        }
    }

    @Test
    fun penUsesPenRenderer() {
        assertSame(PenRenderer, PenType.Pen.renderer)
    }

    @Test
    fun laserUsesLaserRenderer() {
        assertSame(LaserRenderer, PenType.Laser.renderer)
    }

    @Test
    fun shapeTypesUseCorrectRenderers() {
        assertSame(RectRenderer, PenType.Rectangle.renderer)
        assertSame(OvalRenderer, PenType.Ellipse.renderer)
    }

    @Test
    fun isEphemeral_matchesTtlMs() {
        for (type in PenType.entries) {
            assertEquals(
                "${type.name}: isEphemeral should match ttlMs != null",
                type.ttlMs != null,
                type.isEphemeral,
            )
        }
    }

    @Test
    fun onlyErasers_haveIsEraser() {
        for (type in PenType.entries) {
            if (type.isEraser) {
                assertTrue("${type.name} should be an eraser type",
                    type == PenType.StrokeEraser || type == PenType.PixelEraser)
            } else {
                assertFalse("${type.name} should not be an eraser", type.isEraser)
            }
        }
    }

    @Test
    fun laserHasFiniteTtl() {
        assertNotNull(PenType.Laser.ttlMs)
        assertTrue(PenType.Laser.isEphemeral)
    }

    @Test
    fun permanentTypesHaveNullTtl() {
        val permanent = listOf(PenType.Pen, PenType.Rectangle, PenType.Ellipse,
            PenType.StrokeEraser, PenType.PixelEraser)
        for (type in permanent) {
            assertNull("${type.name}.ttlMs should be null", type.ttlMs)
            assertFalse("${type.name} should not be ephemeral", type.isEphemeral)
        }
    }

    @Test
    fun freehandTypesUseSegmentHitTester() {
        assertEquals(SegmentHitTester, PenType.Pen.hitTester)
        assertEquals(SegmentHitTester, PenType.Laser.hitTester)
    }

    @Test
    fun shapeTypesUseEdgeHitTester() {
        assertEquals(EdgeHitTester, PenType.Rectangle.hitTester)
        assertEquals(EdgeHitTester, PenType.Ellipse.hitTester)
    }

    @Test
    fun eraserTypesHaveCorrectMappings() {
        assertEquals(SegmentHitTester, PenType.StrokeEraser.hitTester)
        assertEquals(SegmentHitTester, PenType.PixelEraser.hitTester)
        assertTrue(PenType.StrokeEraser.isEraser)
        assertTrue(PenType.PixelEraser.isEraser)
    }
}
