package com.shezik.drawanywhere.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

sealed class DrawObject {
    data class Stroke(
        internal val _points: MutableList<Offset> = mutableListOf(),
        val color: Color,
        val width: Float,
        val alpha: Float,
        val penType: PenType = PenType.Pen,
    ) : DrawObject() {
        val points: List<Offset> get() = _points
    }
}

sealed class DrawAction {
    data class AddStroke(val stroke: DrawObject.Stroke) : DrawAction()
    data class EraseStroke(val stroke: DrawObject.Stroke) : DrawAction()
    data class ClearStrokes(val strokes: List<DrawObject.Stroke>) : DrawAction()
    data class CanvasSnapshot(val before: List<DrawObject.Stroke>, val after: List<DrawObject.Stroke>) : DrawAction()
}
