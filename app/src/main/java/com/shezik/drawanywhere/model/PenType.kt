package com.shezik.drawanywhere.model

import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.R
import com.shezik.drawanywhere.drawing.PenTool
import com.shezik.drawanywhere.drawing.PixelEraserTool
import com.shezik.drawanywhere.drawing.ShapeTool
import com.shezik.drawanywhere.drawing.StrokeEraserTool
import com.shezik.drawanywhere.drawing.StrokeTool
import com.shezik.drawanywhere.drawing.ToolContext

enum class PenType(
    /** Label string resource for UI display. */
    val labelResId: Int,
    /** True for eraser types — color picker and alpha slider are not applicable. */
    val isEraser: Boolean = false,
) {
    Pen(R.string.pen),
    Rectangle(R.string.rectangle),
    Ellipse(R.string.ellipse),
    StrokeEraser(R.string.stroke_eraser, isEraser = true),
    PixelEraser(R.string.pixel_eraser, isEraser = true);

    fun createTool(ctx: ToolContext): StrokeTool = when (this) {
        Pen -> PenTool(ctx)
        Rectangle, Ellipse -> ShapeTool(ctx)
        StrokeEraser -> StrokeEraserTool(ctx)
        PixelEraser -> PixelEraserTool(ctx)
    }
}

data class PenConfig(
    val penType: PenType = PenType.Pen,
    val color: Color = Color.Red,
    val width: Float = 5f,
    val alpha: Float = 1f
)
