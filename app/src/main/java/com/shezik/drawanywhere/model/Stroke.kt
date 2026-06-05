package com.shezik.drawanywhere.model

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

data class Stroke(
    internal val _points: MutableList<Offset> = mutableListOf(),
    val color: Color,
    val width: Float,
    val alpha: Float,
    val penType: PenType = PenType.Pen,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val points: List<Offset> get() = _points

    fun render(canvas: Canvas, paint: Paint) {
        if (_points.isEmpty()) return
        paint.strokeWidth = width
        val argb = color.toArgb()
        val combinedAlpha = (color.alpha * alpha * 255).toInt().coerceIn(0, 255)
        paint.color = (argb and 0x00FFFFFF) or (combinedAlpha shl 24)
        penType.renderer.render(this, canvas, paint, System.currentTimeMillis())
    }
}

sealed class DrawAction {
    data class AddStroke(val stroke: Stroke) : DrawAction()
    data class EraseStroke(val stroke: Stroke) : DrawAction()
    data class ClearStrokes(val strokes: List<Stroke>) : DrawAction()
    data class CanvasSnapshot(val before: List<Stroke>, val after: List<Stroke>) : DrawAction()

    /** Returns this action with ephemeral strokes removed, or null if nothing remains. */
    fun withoutEphemeral(): DrawAction? = when (this) {
        is AddStroke -> if (stroke.penType.isEphemeral) null else this
        is EraseStroke -> if (stroke.penType.isEphemeral) null else this
        is ClearStrokes -> {
            val f = strokes.filter { !it.penType.isEphemeral }
            if (f.isEmpty()) null else copy(strokes = f)
        }
        is CanvasSnapshot -> {
            val b = before.filter { !it.penType.isEphemeral }
            val a = after.filter { !it.penType.isEphemeral }
            if (b.isEmpty() && a.isEmpty()) null
            else if (b == before && a == after) this
            else copy(before = b, after = a)
        }
    }
}
