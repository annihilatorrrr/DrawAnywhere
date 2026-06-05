package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.DrawObject
import com.shezik.drawanywhere.model.PenType

/**
 * Point-level eraser — removes individual points within radius, splitting
 * affected strokes into segments. Uses a full snapshot [DrawAction.CanvasSnapshot]
 * for undo/redo.
 */
class PixelEraserTool(private val ctx: ToolContext) : StrokeTool {

    private var snapshot: List<DrawObject.Stroke>? = null

    override fun onStart(point: Offset) {
        snapshot = null
        eraseAt(point)
    }

    override fun onMove(point: Offset) {
        eraseAt(point)
    }

    override fun onFinish() {
        val saved = snapshot ?: return
        snapshot = null
        ctx.pushUndo(DrawAction.CanvasSnapshot(saved, ctx.strokes.toList()))
    }

    private fun eraseAt(point: Offset) {
        val r = ctx.penConfig.width / 2
        val r2 = r * r
        val strokes = ctx.strokes

        // Reachability check — don't snapshot if nothing is hit
        var hit = false
        for (stroke in strokes) {
            if (stroke.penType != PenType.Pen) continue
            if (!bboxHits(stroke, point, r)) continue
            for (pt in stroke.points) {
                val dx = pt.x - point.x; val dy = pt.y - point.y
                if (dx * dx + dy * dy <= r2) { hit = true; break }
            }
            if (hit) break
        }
        if (!hit) return

        // First hit — capture full snapshot
        if (snapshot == null) {
            snapshot = strokes.toList()
        }

        // Erase in reverse: remove points within radius, split into segments
        var i = strokes.size - 1
        while (i >= 0) {
            val stroke = strokes[i]
            if (stroke.penType != PenType.Pen) { i--; continue }
            if (!bboxHits(stroke, point, r)) { i--; continue }

            val pts = stroke.points
            val keep = BooleanArray(pts.size) { idx ->
                val dx = pts[idx].x - point.x; val dy = pts[idx].y - point.y
                dx * dx + dy * dy > r2
            }
            if (keep.all { it }) { i--; continue }

            val segments = mutableListOf<MutableList<Offset>>()
            var seg: MutableList<Offset>? = null
            for (j in pts.indices) {
                if (keep[j]) {
                    if (seg == null) seg = mutableListOf()
                    seg.add(pts[j])
                } else {
                    if (!seg.isNullOrEmpty()) {
                        segments.add(seg)
                        seg = null
                    }
                }
            }
            if (!seg.isNullOrEmpty()) segments.add(seg)

            strokes.removeAt(i)
            for (segPts in segments.reversed()) {
                strokes.add(i, DrawObject.Stroke(
                    _points = segPts,
                    color = stroke.color,
                    width = stroke.width,
                    alpha = stroke.alpha,
                    penType = stroke.penType,
                ).also { it.onRender = stroke.onRender })
            }
            i--
        }
        ctx.notifyChanged()
    }

    private fun bboxHits(stroke: DrawObject.Stroke, pt: Offset, radius: Float): Boolean {
        val pts = stroke.points
        if (pts.isEmpty()) return false
        var minX = pts[0].x; var maxX = pts[0].x
        var minY = pts[0].y; var maxY = pts[0].y
        for (p in pts) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return !(pt.x + radius < minX || pt.x - radius > maxX ||
                 pt.y + radius < minY || pt.y - radius > maxY)
    }
}
