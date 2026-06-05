package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.Stroke

/**
 * Freehand pen / laser — appends points on move.
 * Rendering and hit-testing are delegated to [com.shezik.drawanywhere.model.PenType].
 */
class FreehandTool(private val ctx: ToolContext) : StrokeTool {

    override fun onStart(point: Offset) {
        ctx.strokes.add(Stroke(
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
