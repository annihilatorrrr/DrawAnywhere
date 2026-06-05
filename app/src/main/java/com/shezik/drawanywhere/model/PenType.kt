package com.shezik.drawanywhere.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.shezik.drawanywhere.R
import com.shezik.drawanywhere.drawing.EllipseTool
import com.shezik.drawanywhere.drawing.PenTool
import com.shezik.drawanywhere.drawing.PixelEraserTool
import com.shezik.drawanywhere.drawing.RectangleTool
import com.shezik.drawanywhere.drawing.StrokeEraserTool
import com.shezik.drawanywhere.drawing.StrokeTool
import com.shezik.drawanywhere.drawing.ToolContext
import com.shezik.drawanywhere.view.toolbar.InkEraser24Px

enum class PenType(
    /** Label string resource for UI display. */
    val labelResId: Int,
    /** Toolbar button icon. */
    val icon: ImageVector,
    /** True for eraser types — color picker and alpha slider are not applicable. */
    val isEraser: Boolean = false,
) {
    Pen(R.string.pen, Icons.Default.Edit),
    Rectangle(R.string.rectangle, Icons.Default.CropSquare),
    Ellipse(R.string.ellipse, Icons.Default.RadioButtonUnchecked),
    StrokeEraser(R.string.stroke_eraser, InkEraser24Px, isEraser = true),
    PixelEraser(R.string.pixel_eraser, Icons.Default.BlurOn, isEraser = true);

    fun createTool(ctx: ToolContext): StrokeTool = when (this) {
        Pen -> PenTool(ctx)
        Rectangle -> RectangleTool(ctx)
        Ellipse -> EllipseTool(ctx)
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
