package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.distance
import com.shezik.drawanywhere.distancePointToLineSegment
import com.shezik.drawanywhere.hitTestRectEdge
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.PenType

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

            when (stroke.penType) {
                PenType.Rectangle, PenType.Ellipse -> {
                    if (stroke.points.size >= 2) {
                        val p0 = stroke.points[0]; val p1 = stroke.points[1]
                        if (hitTestRectEdge(point, minOf(p0.x, p1.x), minOf(p0.y, p1.y),
                                maxOf(p0.x, p1.x), maxOf(p0.y, p1.y), r)) {
                            indexToErase = i
                        }
                    }
                }
                else -> {
                    if (stroke.points.size > 1) {
                        stroke.points.zipWithNext().forEach { (p1, p2) ->
                            if (distancePointToLineSegment(point, p1, p2) <= r) {
                                indexToErase = i; return@forEach
                            }
                        }
                    } else {
                        stroke.points.firstOrNull()?.let {
                            if (distance(point, it) <= r) indexToErase = i
                        }
                    }
                }
            }
            if (indexToErase != null) break
        }
        indexToErase?.let {
            val erased = strokes.removeAt(it)
            ctx.pushUndo(DrawAction.EraseStroke(erased))
            ctx.notifyChanged()
        }
    }
}
