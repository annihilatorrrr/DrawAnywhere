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
import com.shezik.drawanywhere.ui.theme.Spacing

@Composable
fun PenTypeSelector(
    currentPenType: PenType,
    onPenTypeSwitch: (PenType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy((-1).dp)) {
        Text(
            text = stringResource(R.string.tools),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        val penTypes = PenType.entries.map { type ->
            type to stringResource(type.labelResId)
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
                shape = RoundedCornerShape(Spacing.sm)
            ) { Text(text = label, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
