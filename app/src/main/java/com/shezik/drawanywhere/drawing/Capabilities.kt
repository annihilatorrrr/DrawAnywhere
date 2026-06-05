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
    /** Fraction of TTL before fade begins. */
    private const val FADE_START = 0.7f
    /** Glow width multiplier relative to stroke width. */
    private const val GLOW_WIDTH = 2.5f
    /** Glow alpha as fraction of the stroke's own alpha. */
    private const val GLOW_ALPHA = 0.4f

    override fun render(stroke: Stroke, canvas: Canvas, paint: Paint, now: Long) {
        val ttl = stroke.penType.ttlMs
        val elapsed = now - stroke.createdAt

        val fade = if (ttl == null) 1f
            else if (elapsed > ttl) return
            else if (elapsed < ttl * FADE_START) 1f
            else ((ttl - elapsed) / (ttl * (1f - FADE_START))).coerceIn(0f, 1f)

        val baseAlpha = paint.alpha.toFloat()

        // Glow: wider and dimmer, derived from the stroke's own color/alpha
        paint.strokeWidth = stroke.width * GLOW_WIDTH
        paint.alpha = (baseAlpha * GLOW_ALPHA * fade).toInt().coerceIn(0, 255)
        canvas.drawPath(PenRenderer.buildPath(stroke.points), paint)

        // Core: normal width, stroke's full alpha
        paint.strokeWidth = stroke.width
        paint.alpha = (baseAlpha * fade).toInt().coerceIn(0, 255)
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
