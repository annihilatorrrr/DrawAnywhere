package com.shezik.drawanywhere

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.DrawObject
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DrawControllerTest {

    private fun newController() = DrawController().apply {
        setPenConfig(PenConfig())
    }

    private fun newStroke() = DrawObject.Stroke(
        points = mutableStateListOf(Offset(0f, 0f)),
        color = Color.Red,
        width = 5f,
        alpha = 1f
    )

    @Test
    fun createStrokeAddsToList() {
        val c = newController()
        c.createStroke(Offset(10f, 10f))
        assertEquals(1, c.strokeList.size)
    }

    @Test
    fun createStrokeWithoutPenConfigThrows() {
        val c = DrawController()
        assertThrows(IllegalStateException::class.java) {
            c.createStroke(Offset(10f, 10f))
        }
    }

    @Test
    fun updateLatestStrokeWithoutPenConfigThrows() {
        val c = DrawController()
        assertThrows(IllegalStateException::class.java) {
            c.updateLatestStroke(Offset(10f, 10f))
        }
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
        // Create stroke with some points, then somehow make it empty...
        // Actually, finishStroke() checks if the latest is empty
        c.createStroke(Offset(0f, 0f))
        // Remove its points manually to simulate an edge case
        c.strokeList[0].points.clear()
        c.finishStroke()
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun canClearFalseWhenEmpty() = runTest {
        val c = newController()
        assertFalse(c.canClearPaths.first())
    }

    @Test
    fun canClearTrueAfterStroke() = runTest {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()
        assertTrue(c.canClearPaths.first())
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

        // Switch to eraser and erase
        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 50f))
        // Need the eraserRadius to hit — createPath triggers eraseStroke for PenType.StrokeEraser
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
        // create 60 strokes, finish all — should keep only last 50 undo steps
        repeat(60) {
            c.createStroke(Offset(it.toFloat(), 0f))
            c.updateLatestStroke(Offset(it.toFloat() + 1f, 1f))
            c.finishStroke()
        }
        // undo 50+ times — after 50 it should stop (canUndo becomes false)
        repeat(50) { c.undo() }
        assertFalse(c.canUndo.first())  // went through all undo entries
        assertTrue(c.canRedo.first())   // redo stack should have items
    }

    @Test
    fun eraseStrokeRemovesTarget() {
        val c = newController()
        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 100f))
        // Create a stroke first with pen
        c.setPenConfig(PenConfig(penType = PenType.Pen, width = 5f))
        c.createStroke(Offset(0f, 0f))
        c.updateLatestStroke(Offset(100f, 100f))
        c.finishStroke()

        assertEquals(1, c.strokeList.size)

        // Now erase
        c.setPenConfig(PenConfig(penType = PenType.StrokeEraser, width = 100f))
        c.createStroke(Offset(50f, 50f))  // hits the stroke, should erase it
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun onPathsChangedCallbackFires() {
        val c = newController()
        var fired = false
        c.onPathsChanged = { fired = true }
        c.createStroke(Offset(0f, 0f))
        c.updateLatestStroke(Offset(10f, 10f))
        c.finishStroke()
        assertTrue(fired)
    }

    @Test
    fun onPathsChangedFiresOnClear() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()
        var fired = false
        c.onPathsChanged = { fired = true }
        c.clearStrokes()
        assertTrue(fired)
    }

    @Test
    fun onPathsChangedFiresOnUndo() {
        val c = newController()
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()
        c.clearStrokes()
        var fired = false
        c.onPathsChanged = { fired = true }
        c.undo()
        assertTrue(fired)
    }
}
