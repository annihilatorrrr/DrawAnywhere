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
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.graphics.toArgb
import com.shezik.drawanywhere.DrawController
import com.shezik.drawanywhere.DrawViewModel
import com.shezik.drawanywhere.model.StrokeModifier

class NativeDrawCanvasView(
    context: Context,
    private val drawController: DrawController,
    private val viewModel: DrawViewModel,
) : View(context) {

    private var activePointerId: Int = -1
    private var strokeInProgress: Boolean = false

    private val pathPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (stroke in drawController.strokeList) {
            if (stroke.points.isEmpty()) continue
            pathPaint.strokeWidth = stroke.width

            // Combine color's intrinsic alpha with stroke-level opacity.
            val colorArgb = stroke.color.toArgb()
            val colorAlpha = stroke.color.alpha
            val combinedAlpha = (colorAlpha * stroke.alpha * 255).toInt().coerceIn(0, 255)
            pathPaint.color = (colorArgb and 0x00FFFFFF) or (combinedAlpha shl 24)

            canvas.drawPath(stroke.cachedAndroidPath, pathPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                val modifier = detectStrokeModifier(event)
                viewModel.startStroke(
                    point = androidx.compose.ui.geometry.Offset(event.x, event.y),
                    modifier = modifier
                )
                strokeInProgress = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!strokeInProgress) return false
                val pi = event.findPointerIndex(activePointerId)
                if (pi < 0) return false

                for (i in 0 until event.historySize) {
                    viewModel.updateStroke(androidx.compose.ui.geometry.Offset(
                        event.getHistoricalX(pi, i), event.getHistoricalY(pi, i)))
                }
                viewModel.updateStroke(androidx.compose.ui.geometry.Offset(
                    event.getX(pi), event.getY(pi)))
                invalidate()
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                strokeInProgress = false
                activePointerId = -1
                viewModel.finishStroke()
                invalidate()
            }
        }
        return true
    }

    private fun detectStrokeModifier(event: MotionEvent): StrokeModifier {
        if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) return StrokeModifier.None
        val b = event.buttonState
        val primary = (b and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        val secondary = (b and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0
        return when {
            primary && secondary -> StrokeModifier.Both
            primary -> StrokeModifier.PrimaryButton
            secondary -> StrokeModifier.SecondaryButton
            else -> StrokeModifier.None
        }
    }
}
