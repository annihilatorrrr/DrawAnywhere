package com.shezik.drawanywhere.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class ObjectTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

sealed class DrawObject {
    data class Stroke(
        internal val _points: MutableList<Offset> = mutableListOf(),
        val color: Color,
        val width: Float,
        val alpha: Float,
        val penType: PenType = PenType.Pen,
        val transform: ObjectTransform = ObjectTransform(),
    ) : DrawObject() {
        val points: List<Offset> get() = _points
    }
}

sealed class DrawAction {
    data class AddPath(val stroke: DrawObject.Stroke) : DrawAction()
    data class ErasePath(val stroke: DrawObject.Stroke) : DrawAction()
    data class ClearPaths(val strokes: List<DrawObject.Stroke>) : DrawAction()
}
