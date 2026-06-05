package com.shezik.drawanywhere.drawing

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.util.distance
import com.shezik.drawanywhere.util.distancePointToLineSegment
import com.shezik.drawanywhere.util.hitTestRectEdge
import com.shezik.drawanywhere.model.Stroke

/** Renders a stroke onto [canvas] using [paint]. */
interface Renderer {
    fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long)
}

/** Tests whether [point] within [radius] hits this stroke. */
interface HitTester {
    fun hitTest(stroke: Stroke, point: Offset, radius: Float): Boolean
}

// ── Concrete renderers ──────────────────────────────────────────────

object PenRenderer : Renderer {
    override fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long) {
        canvas.drawPath(buildPath(stroke._points), paint)
    }

    fun buildPath(points: List<Offset>): Path {
        val p = Path()
        if (points.isEmpty()) return p
        p.moveTo(points[0].x, points[0].y)
        points.zipWithNext().forEachIndexed { i, (a, b) ->
            val mx = (a.x + b.x) / 2f; val my = (a.y + b.y) / 2f
            if (i == 0) p.lineTo(mx, my) else p.quadTo(a.x, a.y, mx, my)
        }
        p.lineTo(points.last().x, points.last().y)
        return p
    }
}

object RectRenderer : Renderer {
    override fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long) {
        val pts = stroke.points
        if (pts.size < 2) return
        val (p0, p1) = pts[0] to pts[1]
        canvas.drawRect(minOf(p0.x, p1.x), minOf(p0.y, p1.y), maxOf(p0.x, p1.x), maxOf(p0.y, p1.y), paint)
    }
}

object OvalRenderer : Renderer {
    override fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long) {
        val pts = stroke.points
        if (pts.size < 2) return
        val (p0, p1) = pts[0] to pts[1]
        canvas.drawOval(RectF(minOf(p0.x, p1.x), minOf(p0.y, p1.y), maxOf(p0.x, p1.x), maxOf(p0.y, p1.y)), paint)
    }
}

object LaserRenderer : Renderer {
    override fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long) {
        if (now - stroke.createdAt > stroke.penType.ttlMs) return
        paint.strokeWidth = stroke.width * 2
        paint.alpha = 40
        canvas.drawPath(PenRenderer.buildPath(stroke.points), paint)
        paint.strokeWidth = stroke.width
        paint.alpha = 200
        canvas.drawPath(PenRenderer.buildPath(stroke.points), paint)
    }
}

// ── Concrete hit testers ────────────────────────────────────────────

object SegmentHitTester : HitTester {
    override fun hitTest(stroke: Stroke, point: Offset, radius: Float): Boolean {
        val pts = stroke.points
        if (pts.size == 1) return distance(pts[0], point) <= radius
        return pts.zipWithNext().any { (a, b) -> distancePointToLineSegment(point, a, b) <= radius }
    }
}

object EdgeHitTester : HitTester {
    override fun hitTest(stroke: Stroke, point: Offset, radius: Float): Boolean {
        val pts = stroke.points
        if (pts.size < 2) return false
        val p0 = pts[0]; val p1 = pts[1]
        val left = minOf(p0.x, p1.x); val top = minOf(p0.y, p1.y)
        val right = maxOf(p0.x, p1.x); val bottom = maxOf(p0.y, p1.y)
        return hitTestRectEdge(point, left, top, right, bottom, radius)
    }
}
