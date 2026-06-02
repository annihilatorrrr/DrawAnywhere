package com.shezik.drawanywhere.view.toolbar

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shezik.drawanywhere.UiState

@Composable
fun DraggableToolbarCard(
    modifier: Modifier = Modifier,
    uiState: UiState,
    haptics: HapticFeedback,
    onPositionChange: (Offset) -> Unit,
    onPositionSaved: () -> Unit,
    onToolbarInteracted: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .testTag("toolbar_card")
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        onToolbarInteracted()
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onPositionChange(dragAmount)
                    },
                    onDragEnd = { onPositionSaved() }
                )
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
        )
    ) {
        content()
    }
}
