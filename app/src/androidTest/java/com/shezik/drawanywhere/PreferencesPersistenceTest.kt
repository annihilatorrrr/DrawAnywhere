package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.model.ToolbarOrientation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PreferencesPersistenceTest {

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun uiStateSaveAndLoadRoundTrip() = runTest {
        val prefs = PreferencesManager(appContext)
        val original = UiState(
            canvasVisible = false,
            autoClearCanvas = true,
            visibleOnStart = false,
            currentPenType = PenType.StrokeEraser,
            toolbarOrientation = ToolbarOrientation.VERTICAL,
            firstDrawerOpen = false
        )
        prefs.saveUiState(original)
        val restored = prefs.getSavedUiState()

        assertEquals(original.canvasVisible, restored.canvasVisible)
        assertEquals(original.autoClearCanvas, restored.autoClearCanvas)
        assertEquals(original.currentPenType, restored.currentPenType)
        assertEquals(original.toolbarOrientation, restored.toolbarOrientation)
        assertEquals(original.firstDrawerOpen, restored.firstDrawerOpen)
        // Note: canvasPassthrough is NOT persisted (transient state)
    }

    @Test
    fun serviceStateSaveAndLoadRoundTrip() = runTest {
        val prefs = PreferencesManager(appContext)
        val original = ServiceState(toolbarPosition = Offset(123f, 456f))
        prefs.saveServiceState(original)
        val restored = prefs.getSavedServiceState()

        assertEquals(original.toolbarPosition.x, restored.toolbarPosition.x)
        assertEquals(original.toolbarPosition.y, restored.toolbarPosition.y)
        // Note: toolbarActive is NOT persisted (transient state)
    }

    @Test
    fun defaultStateIsReturnedWhenFresh() = runTest {
        val prefs = PreferencesManager(appContext)
        val state = prefs.getSavedUiState()

        assertTrue(state.canvasVisible)
        assertFalse(state.canvasPassthrough)
        assertEquals(PenType.Pen, state.currentPenType)
        assertEquals(ToolbarOrientation.HORIZONTAL, state.toolbarOrientation)
    }
}
