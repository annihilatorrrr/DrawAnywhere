/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.model.StrokeModifier
import com.shezik.drawanywhere.view.toolbar.ToolbarOrientation
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceState(
    val toolbarPosition: Offset = Offset(32f, 64f),
    val toolbarActive: Boolean = true
)

data class UiState(
    val canvasVisible: Boolean = true,
    val canvasPassthrough: Boolean = false,
    val autoClearCanvas: Boolean = false,
    val visibleOnStart: Boolean = true,

    val currentPenType: PenType = PenType.Pen,
    val penConfigs: Map<PenType, PenConfig> = defaultPenConfigs(),

    val toolbarOrientation: ToolbarOrientation = ToolbarOrientation.HORIZONTAL,
    val firstDrawerOpen: Boolean = canvasVisible,
    val secondDrawerOpen: Boolean = false,

    val firstDrawerButtons: Set<String> = setOf(
        "undo", "clear", "tool_controls", "color_picker"
    ),
    val secondDrawerButtons: Set<String> = setOf(
        "passthrough", "redo", "settings"
    ),
    val secondDrawerPinnedButtons: Set<String> = emptySet()
) {
    val currentPenConfig: PenConfig
        get() = penConfigs[currentPenType] ?: PenConfig()
}

fun defaultPenConfigs(): Map<PenType, PenConfig> = mapOf(
    PenType.Pen to PenConfig(penType = PenType.Pen),
    PenType.StrokeEraser to PenConfig(penType = PenType.StrokeEraser, width = 50f)
)

@OptIn(FlowPreview::class)
class DrawViewModel(
    private val controller: DrawController,
    private val preferencesMgr: PreferencesManager,
    initialUiState: UiState,
    initialServiceState: ServiceState,
    private val stopService: () -> Unit
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialUiState)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _serviceState = MutableStateFlow(initialServiceState)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    val canUndo: StateFlow<Boolean> = controller.canUndo
    val canRedo: StateFlow<Boolean> = controller.canRedo
    val canClearCanvas: StateFlow<Boolean> = controller.canClearPaths

    init {
        controller.setPenConfig(initialUiState.currentPenConfig)

        _uiState
            .debounce(300)
            .onEach { state -> preferencesMgr.saveUiState(state) }
            .launchIn(viewModelScope)

        _uiState
            .onEach { state -> controller.setPenConfig(state.currentPenConfig) }
            .launchIn(viewModelScope)

        resetToolbarTimer()
    }

    override fun onCleared() {
        super.onCleared()
        dimmingJob?.cancel()
    }

    fun switchToPen(type: PenType) =
        _uiState.update { it.copy(currentPenType = type) }

    fun resolvePenType(modifier: StrokeModifier) =
        when (modifier) {
            StrokeModifier.PrimaryButton   -> PenType.StrokeEraser
            StrokeModifier.SecondaryButton -> PenType.StrokeEraser
            StrokeModifier.Both            -> PenType.StrokeEraser
            StrokeModifier.None            -> uiState.value.currentPenType
        }

    private var previousPenType: PenType? = null
    private var isStrokeDown: Boolean = false

    fun startStroke(point: Offset, modifier: StrokeModifier) {
        finishStroke()

        val newPenType = resolvePenType(modifier)
        if (newPenType != uiState.value.currentPenType) {
            previousPenType = uiState.value.currentPenType
            switchToPen(newPenType)
        }

        controller.createStroke(point)
        isStrokeDown = true
    }

    fun updateStroke(point: Offset) {
        if (!isStrokeDown) return
        controller.updateLatestStroke(point)
    }

    fun finishStroke() {
        if (!isStrokeDown) return

        controller.finishStroke()

        previousPenType?.let {
            switchToPen(it)
            previousPenType = null
        }
        isStrokeDown = false
    }

    fun toggleCanvasVisibility() =
        setCanvasVisibility(!uiState.value.canvasVisible)

    fun setCanvasVisibility(visible: Boolean) {
        var currentPassthrough = uiState.value.canvasPassthrough
        var currentPinned = uiState.value.secondDrawerPinnedButtons

        if (uiState.value.autoClearCanvas && !visible) {
            clearCanvas()
            currentPassthrough = false
            currentPinned = getPinSecondDrawerButtonResult("passthrough", false)
        }

        val currentFirstOpen = uiState.value.firstDrawerOpen
        _uiState.update { it.copy(
            canvasVisible = visible,
            canvasPassthrough = currentPassthrough,
            firstDrawerOpen = !currentFirstOpen,
            secondDrawerPinnedButtons = currentPinned
        ) }
    }

    fun toggleCanvasPassthrough() =
        setCanvasPassthrough(!uiState.value.canvasPassthrough)

    fun setCanvasPassthrough(passthrough: Boolean) {
        val newPinned = getPinSecondDrawerButtonResult("passthrough", passthrough)
        _uiState.update { it.copy(canvasPassthrough = passthrough, secondDrawerPinnedButtons = newPinned) }
    }

    fun setPenColor(color: Color) =
        _uiState.update {
            with(it) {
                val newConfigs = penConfigs.toMutableMap()
                val newConfig = newConfigs[currentPenType]?.copy(color = color)
                    ?: PenConfig(color = color, penType = currentPenType)
                newConfigs[currentPenType] = newConfig
                copy(penConfigs = newConfigs)
            }
        }

    fun setStrokeWidth(width: Float) =
        _uiState.update {
            with(it) {
                val newConfigs = penConfigs.toMutableMap()
                val newConfig = newConfigs[currentPenType]?.copy(width = width)
                    ?: PenConfig(width = width, penType = currentPenType)
                newConfigs[currentPenType] = newConfig
                copy(penConfigs = newConfigs)
            }
        }

    fun setStrokeAlpha(alpha: Float) =
        _uiState.update {
            with(it) {
                val newConfigs = penConfigs.toMutableMap()
                val newConfig = newConfigs[currentPenType]?.copy(alpha = alpha)
                    ?: PenConfig(alpha = alpha, penType = currentPenType)
                newConfigs[currentPenType] = newConfig
                copy(penConfigs = newConfigs)
            }
        }

    fun setToolbarPosition(position: Offset) =
        _serviceState.update { it.copy(toolbarPosition = position) }

    fun updateToolbarPosition(offset: Offset) =
        setToolbarPosition(serviceState.value.toolbarPosition + offset)

    fun saveToolbarPosition() = viewModelScope.launch {
        preferencesMgr.saveServiceState(serviceState.value)
    }

    fun clearCanvas() = controller.clearStrokes()
    fun undo() = controller.undo()
    fun redo() = controller.redo()

    private var dimmingJob: Job? = null

    fun resetToolbarTimer() {
        dimmingJob?.cancel()
        setToolbarActive(true)
        dimmingJob = viewModelScope.launch {
            delay(3000L)  // 3 seconds
            setToolbarActive(false)
        }
    }

    fun setToolbarActive(state: Boolean) =
        _serviceState.update { it.copy(toolbarActive = state) }

    fun toggleToolbarOrientation() =
        setToolbarOrientation(
            when (uiState.value.toolbarOrientation) {
                ToolbarOrientation.VERTICAL -> ToolbarOrientation.HORIZONTAL
                ToolbarOrientation.HORIZONTAL -> ToolbarOrientation.VERTICAL
            }
        )

    fun setToolbarOrientation(orientation: ToolbarOrientation) =
        _uiState.update { it.copy(toolbarOrientation = orientation) }

    fun toggleFirstDrawer() =
        setFirstDrawerOpen(!uiState.value.firstDrawerOpen)

    fun setFirstDrawerOpen(state: Boolean) =
        _uiState.update { it.copy(firstDrawerOpen = state) }

    fun toggleSecondDrawer() =
        setSecondDrawerOpen(!uiState.value.secondDrawerOpen)

    fun setSecondDrawerOpen(state: Boolean) =
        _uiState.update { it.copy(secondDrawerOpen = state) }

    fun toggleSecondDrawerPinned(id: String) {
        val currentPinned = uiState.value.secondDrawerPinnedButtons
        pinSecondDrawerButton(id, !currentPinned.contains(id))
    }

    fun pinSecondDrawerButton(id: String, pinned: Boolean) =
        _uiState.update { it.copy(secondDrawerPinnedButtons = getPinSecondDrawerButtonResult(id, pinned)) }

    private fun getPinSecondDrawerButtonResult(id: String, pinned: Boolean): Set<String> {
        val currentPinned = uiState.value.secondDrawerPinnedButtons
        if (currentPinned.contains(id) == pinned) return currentPinned
        return if (pinned) currentPinned + id else currentPinned - id
    }

    fun setAutoClearCanvas(state: Boolean) =
        _uiState.update { it.copy(autoClearCanvas = state) }

    fun setVisibleOnStart(state: Boolean) =
        _uiState.update { it.copy(visibleOnStart = state) }

    fun quitApplication() {
        viewModelScope.launch {
            preferencesMgr.saveUiState(uiState.value)
            preferencesMgr.saveServiceState(serviceState.value)
            stopService()
        }
    }
}
