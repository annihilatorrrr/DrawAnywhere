package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.model.DrawAction

/**
 * Stroke-level eraser — erases one complete stroke at a time by checking
 * line-segment distance (Pen) or edge hit-test (Rectangle/Ellipse).
 */
class StrokeEraserTool(private val ctx: ToolContext) : StrokeTool {

    override fun onStart(point: Offset) {
        eraseAt(point)
    }

    override fun onMove(point: Offset) {
        eraseAt(point)
    }

    override fun onFinish() {
        // Each hit pushes its own EraseStroke action — nothing to do here.
    }

    private fun eraseAt(point: Offset) {
        val eraserRadius = ctx.penConfig.width / 2
        var indexToErase: Int? = null
        val strokes = ctx.strokes
        for (i in strokes.indices.reversed()) {
            val stroke = strokes[i]
            val r = stroke.width / 2 + eraserRadius
            if (stroke.penType.hitTester.hitTest(stroke, point, r)) {
                indexToErase = i
                break
            }
        }
        indexToErase?.let {
            val erased = strokes.removeAt(it)
            ctx.pushUndo(DrawAction.EraseStroke(erased))
            ctx.notifyChanged()
        }
    }
}
