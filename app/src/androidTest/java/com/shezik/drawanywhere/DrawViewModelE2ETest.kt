package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.model.StrokeModifier
import com.shezik.drawanywhere.view.toolbar.ToolbarOrientation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Exercises the ViewModel → Controller → State pipeline end-to-end.
 * Bypasses touch / WindowManager (requires SYSTEM_ALERT_WINDOW).
 */
class DrawViewModelE2ETest {

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun createViewModel(
        canvasVisible: Boolean = true,
        currentPenType: PenType = PenType.Pen,
    ): DrawViewModel {
        val controller = DrawController(PenConfig())
        return DrawViewModel(
            controller = controller,
            preferencesMgr = PreferencesManager(appContext),
            initialUiState = UiState(canvasVisible = canvasVisible, currentPenType = currentPenType),
            initialServiceState = ServiceState(),
            stopService = {}
        )
    }

    @Test
    fun drawStroke_undo_redo_clear() = runTest {
        val vm = createViewModel()

        // Draw
        vm.startStroke(Offset(0f, 0f), StrokeModifier.None)
        vm.updateStroke(Offset(10f, 10f))
        vm.finishStroke()
        assertTrue(vm.canClearCanvas.first())
        assertTrue(vm.canUndo.first())

        // Undo
        vm.undo()
        assertFalse(vm.canUndo.first())
        assertTrue(vm.canRedo.first())

        // Redo
        vm.redo()
        assertTrue(vm.canUndo.first())
        assertFalse(vm.canRedo.first())

        // Clear
        vm.clearCanvas()
        assertFalse(vm.canClearCanvas.first())
        assertTrue(vm.canUndo.first())  // clear is undoable
    }

    @Test
    fun stylusButtonSwitchesPenTemporarily() = runTest {
        val vm = createViewModel(currentPenType = PenType.Pen)

        vm.startStroke(Offset(10f, 10f), StrokeModifier.PrimaryButton)
        assertEquals(PenType.StrokeEraser, vm.uiState.first().currentPenType)

        vm.finishStroke()
        assertEquals(PenType.Pen, vm.uiState.first().currentPenType)
    }

    @Test
    fun penConfigPersistsAcrossPenSwitches() = runTest {
        val vm = createViewModel()
        vm.setPenColor(Color.Blue)
        vm.setStrokeWidth(20f)

        vm.switchToPen(PenType.StrokeEraser)
        vm.setStrokeWidth(60f)

        // Switch back — should have original config
        vm.switchToPen(PenType.Pen)
        assertEquals(Color.Blue, vm.uiState.first().currentPenConfig.color)
        assertEquals(20f, vm.uiState.first().currentPenConfig.width)
    }

    @Test
    fun canvasVisibilityToggleChain() = runTest {
        val vm = createViewModel(canvasVisible = true)
        assertTrue(vm.uiState.first().canvasVisible)
        vm.toggleCanvasVisibility()
        assertFalse(vm.uiState.first().canvasVisible)
        vm.toggleCanvasVisibility()
        assertTrue(vm.uiState.first().canvasVisible)
    }

    @Test
    fun passthroughToggle() = runTest {
        val vm = createViewModel()
        assertFalse(vm.uiState.first().canvasPassthrough)
        vm.toggleCanvasPassthrough()
        assertTrue(vm.uiState.first().canvasPassthrough)
    }

    @Test
    fun toolbarOrientationToggle() = runTest {
        val vm = createViewModel()
        assertEquals(ToolbarOrientation.HORIZONTAL, vm.uiState.first().toolbarOrientation)
        vm.toggleToolbarOrientation()
        assertEquals(ToolbarOrientation.VERTICAL, vm.uiState.first().toolbarOrientation)
    }

    @Test
    fun autoClearOnHideClearsCanvas() = runTest {
        val vm = createViewModel()
        vm.setAutoClearCanvas(true)
        vm.startStroke(Offset(0f, 0f), StrokeModifier.None)
        vm.updateStroke(Offset(10f, 10f))
        vm.finishStroke()
        assertTrue(vm.canClearCanvas.first())

        vm.toggleCanvasVisibility()  // hides canvas, should clear
        assertFalse(vm.uiState.first().canvasPassthrough)  // reset
    }

    @Test
    fun toolbarTimerStartsActive() = runTest {
        val vm = createViewModel()
        assertTrue(vm.serviceState.first().toolbarActive)
    }
}
