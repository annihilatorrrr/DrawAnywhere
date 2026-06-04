package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.DrawObject

/**
 * Freehand pen — creates a single [DrawObject.Stroke] and appends points on move.
 */
class PenTool(private val ctx: ToolContext) : StrokeTool {

    override fun onStart(point: Offset) {
        ctx.strokes.add(DrawObject.Stroke(
            _points = mutableListOf(point),
            color = ctx.penConfig.color,
            width = ctx.penConfig.width,
            alpha = ctx.penConfig.alpha,
            penType = ctx.penConfig.penType,
        ))
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
