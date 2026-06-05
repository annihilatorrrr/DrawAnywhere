package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.DrawObject

/**
 * Freehand pen — creates a single [DrawObject.Stroke] and appends points on move.
 * The render lambda draws a smooth quadratic-bezier path through the points.
 */
class PenTool(private val ctx: ToolContext) : StrokeTool {

    override fun onStart(point: Offset) {
        val stroke = DrawObject.Stroke(
            _points = mutableListOf(point),
            color = ctx.penConfig.color,
            width = ctx.penConfig.width,
            alpha = ctx.penConfig.alpha,
            penType = ctx.penConfig.penType,
        )
        stroke.onRender = { s, canvas, paint -> canvas.drawPath(s.buildPath(), paint) }
        ctx.strokes.add(stroke)
    }

    override fun onMove(point: Offset) {
        ctx.strokes.lastOrNull()?._points?.add(point)
    }

    override fun onFinish() {
        val stroke = ctx.strokes.lastOrNull() ?: return
        if (stroke._points.isEmpty()) {
            ctx.strokes.removeAt(ctx.strokes.lastIndex)
            return
        }
        ctx.pushUndo(DrawAction.AddStroke(stroke))
        ctx.notifyChanged()
    }
}
