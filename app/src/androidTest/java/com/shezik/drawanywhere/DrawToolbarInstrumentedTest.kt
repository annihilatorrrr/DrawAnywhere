package com.shezik.drawanywhere

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.platform.app.InstrumentationRegistry
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.view.toolbar.DrawToolbar
import com.shezik.drawanywhere.view.toolbar.ToolbarOrientation
import org.junit.Rule
import org.junit.Test

class DrawToolbarInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int) = appContext.getString(id)

    private fun createViewModel(
        canvasVisible: Boolean = true,
        canvasPassthrough: Boolean = false,
        toolbarOrientation: ToolbarOrientation = ToolbarOrientation.HORIZONTAL,
        currentPenType: PenType = PenType.Pen,
    ): DrawViewModel {
        val controller = DrawController(PenConfig())
        val prefs = PreferencesManager(appContext)
        return DrawViewModel(
            controller = controller,
            preferencesMgr = prefs,
            initialUiState = UiState(
                canvasVisible = canvasVisible,
                canvasPassthrough = canvasPassthrough,
                toolbarOrientation = toolbarOrientation,
                currentPenType = currentPenType,
            ),
            initialServiceState = ServiceState(),
            stopService = {}
        )
    }

    @Test
    fun visibilityButtonIsDisplayed() {
        composeRule.setContent { DrawToolbar(viewModel = createViewModel()) }
        composeRule
            .onNodeWithContentDescription(string(R.string.hide_canvas))
            .assertIsDisplayed()
    }

    @Test
    fun undoButtonDisabledWhenNoStrokeHistory() {
        composeRule.setContent { DrawToolbar(viewModel = createViewModel()) }
        composeRule
            .onNodeWithContentDescription(string(R.string.undo))
            .assertIsNotEnabled()
    }

    @Test
    fun toggleVisibilityChangesIcon() {
        val vm = createViewModel(canvasVisible = true)
        composeRule.setContent { DrawToolbar(viewModel = vm) }
        val hideDesc = string(R.string.hide_canvas)
        composeRule.onNodeWithContentDescription(hideDesc).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(hideDesc).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.show_canvas)).assertIsDisplayed()
    }

    @Test
    fun passthroughButtonVisibleWhenPinned() {
        val vm = createViewModel(canvasPassthrough = false)
        // Passthrough is in second drawer — pin it to make it visible
        vm.pinSecondDrawerButton("passthrough", true)
        composeRule.setContent { DrawToolbar(viewModel = vm) }
        composeRule
            .onNodeWithContentDescription(string(R.string.enable_passthrough))
            .assertIsDisplayed()
    }

    @Test
    fun clearButtonDisabledWhenCanvasEmpty() {
        composeRule.setContent { DrawToolbar(viewModel = createViewModel()) }
        composeRule
            .onNodeWithContentDescription(string(R.string.clear_canvas))
            .assertIsNotEnabled()
    }
}
