package com.shezik.drawanywhere.controller

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.DrawObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UndoRedoManagerTest {

    private fun newStroke() = DrawObject.Stroke(
        _points = mutableListOf(Offset(0f, 0f)),
        color = Color.Red, width = 5f, alpha = 1f
    )

    private fun addAction() = DrawAction.AddStroke(newStroke())

    @Test
    fun initiallyCannotUndoOrRedo() = runTest {
        val m = UndoRedoManager()
        assertFalse(m.canUndo.first())
        assertFalse(m.canRedo.first())
    }

    @Test
    fun pushEnablesUndo() = runTest {
        val m = UndoRedoManager()
        m.push(addAction())
        assertTrue(m.canUndo.first())
        assertFalse(m.canRedo.first())
    }

    @Test
    fun popUndoReturnsPushedAction() = runTest {
        val m = UndoRedoManager()
        val action = addAction()
        m.push(action)
        val popped = m.popUndo()
        assertSame(action, popped)
    }

    @Test
    fun popUndoDisablesUndoWhenEmpty() = runTest {
        val m = UndoRedoManager()
        m.push(addAction())
        m.popUndo()
        assertFalse(m.canUndo.first())
    }

    @Test
    fun undoThenPushRedoEnablesRedo() = runTest {
        val m = UndoRedoManager()
        val action = addAction()
        m.push(action)
        val popped = m.popUndo()
        m.pushRedo(popped!!)
        assertTrue(m.canRedo.first())
    }

    @Test
    fun popRedoReturnsPushedRedoAction() = runTest {
        val m = UndoRedoManager()
        val action = addAction()
        m.push(action)
        val popped = m.popUndo()
        m.pushRedo(popped!!)
        assertSame(popped, m.popRedo())
    }

    @Test
    fun pushClearsRedo() = runTest {
        val m = UndoRedoManager()
        val action = addAction()
        m.push(action)
        val popped = m.popUndo()
        m.pushRedo(popped!!)
        assertTrue(m.canRedo.first())

        // New push should clear the redo stack
        m.push(addAction())
        assertFalse(m.canRedo.first())
    }

    @Test
    fun maxDepthTrimsOldestEntries() = runTest {
        val m = UndoRedoManager(maxDepth = 3)
        repeat(5) { m.push(addAction()) }

        repeat(3) { assertNotNull(m.popUndo()) }
        assertNull(m.popUndo())
        assertFalse(m.canUndo.first())
    }
}
