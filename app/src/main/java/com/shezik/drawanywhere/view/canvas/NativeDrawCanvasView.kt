/*
DrawAnywhere: An Android application that lets you draw on top of other apps.
Copyright (C) 2025 shezik

This program is free software: you can redistribute it and/or modify it under the
terms of the GNU Affero General Public License as published by the Free Software
Foundation, either version 3 of the License, or any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License along
with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.shezik.drawanywhere.view.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import com.shezik.drawanywhere.DrawController
import com.shezik.drawanywhere.DrawViewModel
import com.shezik.drawanywhere.model.PenType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NativeDrawCanvasView(
    context: Context,
    private val drawController: DrawController,
    private val viewModel: DrawViewModel,
) : View(context) {

    // ── Touch input (delegated) ───────────────────────────────────

    private val touchHandler = CanvasTouchHandler(
        viewModel = viewModel,
        onInvalidate = { invalidate() },
        onTwoFingerDoubleTap = {
            if (!viewModel.viewport.value.zoomLocked) {
                viewModel.resetViewport(Offset(width / 2f, height / 2f))
            }
        },
        onTwoFingerTripleTap = {
            val zoomLocked = viewModel.viewport.value.zoomLocked
            val vp = if (zoomLocked)
                CanvasViewport(zoom = viewModel.viewport.value.zoom, zoomLocked = true)
            else
                CanvasViewport()
            viewModel.setViewport(vp)
        },
        onThreeFingerDoubleTap = { viewModel.toggleCanvasPassthrough() },
        onThreeFingerTripleTap = { viewModel.setCanvasVisibility(false) },
        fingerDrawingEnabled = { viewModel.uiState.value.fingerDrawingEnabled },
    )

    override fun onTouchEvent(event: MotionEvent): Boolean =
        touchHandler.handleEvent(event)

    // ── Hover (stylus/mouse size preview) ──────────────────────

    override fun onHoverEvent(event: MotionEvent): Boolean =
        touchHandler.handleEvent(event)

    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        touchHandler.handleEvent(event)

    // ── Rendering ─────────────────────────────────────────────────

    private val pathPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val hudPaint = Paint().apply {
        color = 0xCC_FFFFFF.toInt()
        textSize = 18f * context.resources.displayMetrics.density
        isAntiAlias = true
        setShadowLayer(4f, 0f, 1f, 0x88000000.toInt())
    }

    private val hoverPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val vp = viewModel.viewport.value

        canvas.save()
        canvas.translate(-vp.panX * vp.zoom, -vp.panY * vp.zoom)
        canvas.scale(vp.zoom, vp.zoom)

        for (stroke in drawController.strokeList) {
            if (stroke.points.isEmpty()) continue

            pathPaint.strokeWidth = stroke.width
            val colorArgb = stroke.color.toArgb()
            val colorAlpha = stroke.color.alpha
            val combinedAlpha = (colorAlpha * stroke.alpha * 255).toInt().coerceIn(0, 255)
            pathPaint.color = (colorArgb and 0x00FFFFFF) or (combinedAlpha shl 24)

            when (stroke.penType) {
                PenType.Rectangle, PenType.Ellipse -> {
                    if (stroke.points.size < 2) continue
                    val p0 = stroke.points[0]; val p1 = stroke.points[1]
                    val left = minOf(p0.x, p1.x); val top = minOf(p0.y, p1.y)
                    val right = maxOf(p0.x, p1.x); val bottom = maxOf(p0.y, p1.y)
                    if (stroke.penType == PenType.Rectangle) {
                        canvas.drawRect(left, top, right, bottom, pathPaint)
                    } else {
                        canvas.drawOval(RectF(left, top, right, bottom), pathPaint)
                    }
                }
                else -> {
                    val androidPath = buildAndroidPath(stroke.points)
                    canvas.drawPath(androidPath, pathPaint)
                }
            }
        }
        canvas.restore()

        // ── Hover size preview (screen space, constant border) ────
        touchHandler.hoverPoint?.let { pt ->
            val config = viewModel.uiState.value.currentPenConfig
            hoverPaint.color = config.color.toArgb()
            val screenR = (config.width * vp.zoom - hoverPaint.strokeWidth) / 2f
            if (screenR > 0f) canvas.drawCircle(pt.x, pt.y, screenR, hoverPaint)
        }

        // ── Zoom HUD ─────────────────────────────────────────────
        val zoomPct = (vp.zoom * 100).toInt()
        val label = if (zoomPct == 100) "" else "${zoomPct}%"
        val lockIcon = if (vp.zoomLocked) " 🔒" else ""
        if (label.isNotEmpty() || vp.zoomLocked) {
            canvas.drawText("$label$lockIcon", 24f, height - 48f, hudPaint)
        }
    }

    // ── Viewport observation (for HUD updates) ────────────────────

    private var viewportScope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewportScope = CoroutineScope(Dispatchers.Main).apply {
            launch { viewModel.viewport.collect { invalidate() } }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewportScope?.cancel()
    }

    // ── Path builder ─────────────────────────────────────────────

    private fun buildAndroidPath(
        points: List<Offset>
    ): Path {
        val p = Path()
        if (points.isEmpty()) return p
        val first = points.first()
        p.moveTo(first.x, first.y)
        points.zipWithNext().forEachIndexed { index, (start, end) ->
            val mx = (start.x + end.x) / 2f
            val my = (start.y + end.y) / 2f
            if (index == 0) p.lineTo(mx, my)
            else p.quadTo(start.x, start.y, mx, my)
        }
        val last = points.last()
        p.lineTo(last.x, last.y)
        return p
    }
}
