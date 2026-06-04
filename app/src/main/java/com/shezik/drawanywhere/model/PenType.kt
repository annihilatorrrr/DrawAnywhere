package com.shezik.drawanywhere.model

import androidx.compose.ui.graphics.Color

enum class PenType {
    Pen, Rectangle, Ellipse, StrokeEraser
}

data class PenConfig(
    val penType: PenType = PenType.Pen,
    val color: Color = Color.Red,
    val width: Float = 5f,
    val alpha: Float = 1f
)
