package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.DrawObject
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DrawControllerTest {

    private fun newController() = DrawController(PenConfig())

    private fun newStroke() = DrawObject.Stroke(
        _points = mutableListOf(Offset(0f, 0f)),
        color = Color.Red,
        width = 5f,
        alpha = 1f
    )

    @Test
    fun constructorSetsPenConfig() {
        val c = DrawController(PenConfig(color = Color.Blue, width = 10f))
        assertEquals(Color.Blue, c.penConfig.color)
        assertEquals(10f, c.penConfig.width)
    }

    @Test
    fun createStrokeAddsToList() {
        val c = newController()
        c.createStroke(Offset(10f, 10f))
        assertEquals(1, c.strokeList.size)
    }

    @Test
    fun updateLatestStrokeAddsPoint() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.updateLatestStroke(Offset(10f, 10f))
        assertEquals(2, c.strokeList[0].points.size)
    }

    @Test
    fun finishStrokePushesToUndo() = runTest {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.updateLatestStroke(Offset(10f, 10f))
        c.finishStroke()
        assertTrue(c.canUndo.first())
    }

    @Test
    fun emptyStrokeRemovedOnFinish() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.strokeList[0]._points.clear()
        c.finishStroke()
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun canClearFalseWhenEmpty() = runTest {
        val c = newController()
        assertFalse(c.canClearStrokes.first())
    }

    @Test
    fun canClearTrueAfterStroke() = runTest {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()
        assertTrue(c.canClearStrokes.first())
    }

    @Test
    fun clearStrokesRemovesAll() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.createStroke(Offset(10f, 10f))
        c.clearStrokes()
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun clearStrokesPushesToUndo() = runTest {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.clearStrokes()
        assertTrue(c.canUndo.first())
    }

    @Test
    fun undoRestoresErasedPath() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()
        assertEquals(1, c.strokeList.size)

        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 50f))
        c.createStroke(Offset(0f, 0f))  // This calls eraseStroke

        assertEquals(0, c.strokeList.size)

        c.undo()
        assertEquals(1, c.strokeList.size)
    }

    @Test
    fun redoAfterUndoRestoresAction() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()
        c.clearStrokes()
        assertEquals(0, c.strokeList.size)

        c.undo()
        assertEquals(1, c.strokeList.size)

        c.redo()
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun undoStackLimited() = runTest {
        val c = newController()
        repeat(60) {
            c.createStroke(Offset(it.toFloat(), 0f))
            c.updateLatestStroke(Offset(it.toFloat() + 1f, 1f))
            c.finishStroke()
        }
        repeat(50) { c.undo() }
        assertFalse(c.canUndo.first())
        assertTrue(c.canRedo.first())
    }

    @Test
    fun eraseStrokeRemovesTarget() {
        val c = newController()
        // Create a 2-point stroke
        c.createStroke(Offset(0f, 0f))
        c.updateLatestStroke(Offset(100f, 100f))
        c.finishStroke()

        assertEquals(1, c.strokeList.size)

        // Erase at midpoint — line-segment distance check
        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 100f))
        c.createStroke(Offset(50f, 50f))
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun eraseStrokeRemovesSinglePointTarget() {
        val c = newController()
        // Create a single-point stroke (just a dot, no line segments)
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()

        assertEquals(1, c.strokeList.size)

        // Erase close to the single point (direct distance check)
        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 100f))
        c.createStroke(Offset(5f, 5f))  // distance ≈ 7, eraserRadius=50 → hit
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun eraseStrokeMissesDistantTarget() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()

        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 10f))
        c.createStroke(Offset(100f, 100f))  // distance ≈ 141, eraserRadius=5 → miss
        assertEquals(1, c.strokeList.size)  // stroke survives
    }

    @Test
    fun onStrokesChangedCallbackFires() {
        val c = newController()
        var fired = false
        c.onStrokesChanged = { fired = true }
        c.createStroke(Offset(0f, 0f))
        c.updateLatestStroke(Offset(10f, 10f))
        c.finishStroke()
        assertTrue(fired)
    }

    @Test
    fun onStrokesChangedFiresOnClear() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()
        var fired = false
        c.onStrokesChanged = { fired = true }
        c.clearStrokes()
        assertTrue(fired)
    }

    @Test
    fun onStrokesChangedFiresOnUndo() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()
        c.clearStrokes()
        var fired = false
        c.onStrokesChanged = { fired = true }
        c.undo()
        assertTrue(fired)
    }

    // ── Rectangle / Ellipse shape tests ─────────────────────────

    @Test
    fun rectangleCreateStrokeHasTwoPoints() {
        val c = DrawController(PenConfig(penType = PenType.Rectangle, width = 5f))
        c.createStroke(Offset(10f, 20f))
        assertEquals(1, c.strokeList.size)
        assertEquals(2, c.strokeList[0].points.size)
        assertEquals(PenType.Rectangle, c.strokeList[0].penType)
    }

    @Test
    fun rectangleUpdateReplacesSecondPoint() {
        val c = DrawController(PenConfig(penType = PenType.Rectangle, width = 5f))
        c.createStroke(Offset(10f, 20f))
        c.updateLatestStroke(Offset(100f, 200f))
        val pts = c.strokeList[0].points
        assertEquals(2, pts.size)
        assertEquals(Offset(10f, 20f), pts[0])
        assertEquals(Offset(100f, 200f), pts[1])
    }

    @Test
    fun rectangleFinishNormalizesReverseDrag() {
        val c = DrawController(PenConfig(penType = PenType.Rectangle, width = 5f))
        c.createStroke(Offset(100f, 100f))
        c.updateLatestStroke(Offset(50f, 30f))
        c.finishStroke()
        val pts = c.strokeList[0].points
        assertEquals(Offset(50f, 30f), pts[0])
        assertEquals(Offset(100f, 100f), pts[1])
    }

    @Test
    fun rectangleDiscardsTooSmall() {
        val c = DrawController(PenConfig(penType = PenType.Rectangle, width = 5f))
        c.createStroke(Offset(10f, 10f))
        c.updateLatestStroke(Offset(11f, 11f))
        c.finishStroke()
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun rectangleFinishPushesToUndo() = runTest {
        val c = DrawController(PenConfig(penType = PenType.Rectangle, width = 5f))
        c.createStroke(Offset(10f, 10f))
        c.updateLatestStroke(Offset(100f, 100f))
        c.finishStroke()
        assertTrue(c.canUndo.first())
    }

    @Test
    fun eraseRectangleEdgeHitRemovesShape() {
        val c = DrawController(PenConfig(penType = PenType.Rectangle, width = 5f))
        c.createStroke(Offset(10f, 10f))
        c.updateLatestStroke(Offset(100f, 100f))
        c.finishStroke()
        assertEquals(1, c.strokeList.size)

        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 20f))
        c.createStroke(Offset(55f, 10f))
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun eraseRectangleMissesInterior() {
        val c = DrawController(PenConfig(penType = PenType.Rectangle, width = 5f))
        c.createStroke(Offset(10f, 10f))
        c.updateLatestStroke(Offset(100f, 100f))
        c.finishStroke()

        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 10f))
        c.createStroke(Offset(55f, 55f))
        assertEquals(1, c.strokeList.size)
    }

    @Test
    fun ellipseCreateStrokeHasCorrectPenType() {
        val c = DrawController(PenConfig(penType = PenType.Ellipse, width = 5f))
        c.createStroke(Offset(10f, 10f))
        assertEquals(PenType.Ellipse, c.strokeList[0].penType)
    }

    @Test
    fun multipleRedoPreservesStack() = runTest {
        val c = newController()
        // Create 3 strokes
        repeat(3) {
            c.createStroke(Offset(it.toFloat(), 0f))
            c.updateLatestStroke(Offset(it.toFloat() + 1f, 1f))
            c.finishStroke()
        }

        // Undo all three
        repeat(3) { c.undo() }
        assertFalse(c.canUndo.first())
        assertTrue(c.canRedo.first())

        // Redo two — should still have one left on redo stack
        repeat(2) { c.redo() }
        assertTrue(c.canRedo.first())  // third redo still possible

        // New action after partial redo clears remaining redo
        c.createStroke(Offset(100f, 100f))
        c.finishStroke()
        assertFalse(c.canRedo.first())  // redo cleared by new action
    }
}
