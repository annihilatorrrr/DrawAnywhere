package com.shezik.drawanywhere.view.toolbar

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.shezik.drawanywhere.DrawViewModel
import com.shezik.drawanywhere.scrollFadingEdges
import com.shezik.drawanywhere.ui.theme.DrawAnywhereTheme

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun DrawToolbar(
    viewModel: DrawViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val canClearCanvas by viewModel.canClearCanvas.collectAsState()

    val haptics = LocalHapticFeedback.current
    val hScrollState = rememberScrollState()
    val vScrollState = rememberScrollState()

    val allButtonsMap = createAllToolbarButtons(
        uiState = uiState,
        canUndo = canUndo,
        canRedo = canRedo,
        canClearCanvas = canClearCanvas,
        onCanvasVisibilityToggle = viewModel::toggleCanvasVisibility,
        onCanvasPassthroughToggle = viewModel::toggleCanvasPassthrough,
        onClearCanvas = viewModel::clearCanvas,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onPenTypeSwitch = viewModel::switchToPen,
        onColorChange = viewModel::setPenColor,
        onStrokeWidthChange = viewModel::setStrokeWidth,
        onAlphaChange = viewModel::setStrokeAlpha,
        onChangeOrientation = viewModel::setToolbarOrientation,
        onChangeAutoClearCanvas = viewModel::setAutoClearCanvas,
        onChangeVisibleOnStart = viewModel::setVisibleOnStart,
        onQuitApplication = viewModel::quitApplication
    ).associateBy { it.id }

    DrawAnywhereTheme {
        BoxWithConstraints {
            DraggableToolbarCard(
                modifier = modifier
                    .wrapContentSize(unbounded = true)
                    .widthIn(max = maxWidth)
                    .heightIn(max = maxHeight)
                    .scrollFadingEdges(hScrollState, false)
                    .scrollFadingEdges(vScrollState, true)
                    .horizontalScroll(hScrollState)
                    .verticalScroll(vScrollState)
                    .padding(4.dp),
                uiState = uiState,
                haptics = haptics,
                onPositionChange = viewModel::updateToolbarPosition,
                onPositionSaved = viewModel::saveToolbarPosition,
                onToolbarInteracted = viewModel::resetToolbarTimer
            ) {
                ToolbarButtonsContainer(
                    modifier = Modifier.padding(8.dp),
                    uiState = uiState,
                    allButtonsMap = allButtonsMap,
                    onExpandToggleClick = viewModel::toggleSecondDrawer
                )
            }
        }
    }
}

@Composable
fun ToolbarButtonsContainer(
    modifier: Modifier = Modifier,
    uiState: com.shezik.drawanywhere.UiState,
    allButtonsMap: Map<String, ToolbarButton>,
    onExpandToggleClick: () -> Unit
) {
    val orientation = uiState.toolbarOrientation
    val isFirstDrawerOpen = uiState.firstDrawerOpen
    val isSecondDrawerOpen = uiState.secondDrawerOpen
    val firstDrawerButtonIds = uiState.firstDrawerButtons
    val secondDrawerButtonIds = uiState.secondDrawerButtons
    val secondDrawerPinnedButtons = uiState.secondDrawerPinnedButtons

    val standaloneButtonIds = allButtonsMap.keys.filter {
        it !in firstDrawerButtonIds && it !in secondDrawerButtonIds
    }
    val arrangement = Arrangement.spacedBy(8.dp)
    val popupAlignment = when (orientation) {
        ToolbarOrientation.HORIZONTAL -> Alignment.TopCenter
        ToolbarOrientation.VERTICAL -> Alignment.CenterEnd
    }
    val animMod = Modifier.animateContentSize(
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )
    val isDividerVisible = isFirstDrawerOpen &&
        (secondDrawerPinnedButtons.isNotEmpty() || (isSecondDrawerOpen && secondDrawerButtonIds.isNotEmpty()))
    val isExpandButtonVisible = isFirstDrawerOpen && secondDrawerButtonIds.isNotEmpty()

    val isHorizontal = orientation == ToolbarOrientation.HORIZONTAL

    @Composable
    fun ToolbarContent() {
        standaloneButtonIds.forEach { id -> allButtonsMap[id]?.let { RenderButton(it, popupAlignment) } }
        firstDrawerButtonIds.forEach { id ->
            allButtonsMap[id]?.let { btn ->
                AnimatedVisibility(isFirstDrawerOpen,
                    enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.5f),
                    exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.5f)
                ) { RenderButton(btn, popupAlignment) }
            }
        }
        AnimatedVisibility(isDividerVisible, enter = fadeIn(tween(300)), exit = fadeOut(tween(300))) {
            if (isHorizontal) VerticalDivider(
                Modifier.height(24.dp).padding(horizontal = 8.dp), thickness = 2.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            else HorizontalDivider(
                Modifier.width(24.dp).padding(vertical = 8.dp), thickness = 2.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
        secondDrawerButtonIds.forEach { id ->
            allButtonsMap[id]?.let { btn ->
                val visible = isFirstDrawerOpen && (isSecondDrawerOpen || id in secondDrawerPinnedButtons)
                AnimatedVisibility(visible,
                    enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.5f),
                    exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.5f)
                ) { RenderButton(btn, popupAlignment) }
            }
        }
        AnimatedVisibility(isExpandButtonVisible,
            enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.5f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.5f)
        ) { ToolbarExpandButton(Modifier, isSecondDrawerOpen, onExpandToggleClick, orientation) }
    }

    if (isHorizontal) {
        Row(modifier = modifier.then(animMod), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = arrangement) {
            ToolbarContent()
        }
    } else {
        Column(modifier = modifier.then(animMod), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = arrangement) {
            ToolbarContent()
        }
    }
}
