package com.shezik.drawanywhere.view.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shezik.drawanywhere.R
import com.shezik.drawanywhere.model.PenType

@Composable
fun PenTypeSelector(
    currentPenType: PenType,
    onPenTypeSwitch: (PenType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tools),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        val penTypes = PenType.entries.map { type ->
            type to stringResource(
                when (type) {
                    PenType.Pen -> R.string.pen
                    PenType.Rectangle -> R.string.rectangle
                    PenType.Ellipse -> R.string.ellipse
                    PenType.StrokeEraser -> R.string.stroke_eraser
                }
            )
        }
        penTypes.forEach { (penType, label) ->
            val isSelected = currentPenType == penType
            val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.surface
            val fg = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                     else MaterialTheme.colorScheme.onSurface
            Button(
                onClick = { onPenTypeSwitch(penType) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
                shape = RoundedCornerShape(8.dp)
            ) { Text(text = label, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
