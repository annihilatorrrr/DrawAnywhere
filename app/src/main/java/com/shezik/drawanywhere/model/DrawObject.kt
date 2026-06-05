package com.shezik.drawanywhere.model

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

sealed class DrawObject {
    data class Stroke(
        internal val _points: MutableList<Offset> = mutableListOf(),
        val color: Color,
        val width: Float,
        val alpha: Float,
        val penType: PenType = PenType.Pen,
    ) : DrawObject() {
        val points: List<Offset> get() = _points

        /** Set by the tool that created this stroke; defines shape-specific drawing.
         *  Receives [this] as first argument — safe to copy between strokes. */
        internal var onRender: ((Stroke, Canvas, Paint) -> Unit)? = null

        fun render(canvas: Canvas, paint: Paint) {
            val impl = onRender ?: return
            if (_points.isEmpty()) return
            paint.strokeWidth = width
            val argb = color.toArgb()
            val combinedAlpha = (color.alpha * alpha * 255).toInt().coerceIn(0, 255)
            paint.color = (argb and 0x00FFFFFF) or (combinedAlpha shl 24)
            impl(this, canvas, paint)
        }

        internal fun buildPath(): Path {
            val p = Path()
            if (_points.isEmpty()) return p
            p.moveTo(_points[0].x, _points[0].y)
            _points.zipWithNext().forEachIndexed { i, (a, b) ->
                val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
                if (i == 0) p.lineTo(mx, my) else p.quadTo(a.x, a.y, mx, my)
            }
            p.lineTo(_points.last().x, _points.last().y)
            return p
        }
    }
}

sealed class DrawAction {
    data class AddStroke(val stroke: DrawObject.Stroke) : DrawAction()
    data class EraseStroke(val stroke: DrawObject.Stroke) : DrawAction()
    data class ClearStrokes(val strokes: List<DrawObject.Stroke>) : DrawAction()
    data class CanvasSnapshot(val before: List<DrawObject.Stroke>, val after: List<DrawObject.Stroke>) : DrawAction()
}
