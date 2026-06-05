package com.shezik.drawanywhere.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.shezik.drawanywhere.R
import com.shezik.drawanywhere.drawing.EdgeHitTester
import com.shezik.drawanywhere.drawing.FreehandTool
import com.shezik.drawanywhere.drawing.HitTester
import com.shezik.drawanywhere.drawing.OvalRenderer
import com.shezik.drawanywhere.drawing.PenRenderer
import com.shezik.drawanywhere.drawing.PixelEraserTool
import com.shezik.drawanywhere.drawing.RectRenderer
import com.shezik.drawanywhere.drawing.Renderer
import com.shezik.drawanywhere.drawing.SegmentHitTester
import com.shezik.drawanywhere.drawing.ShapeTool
import com.shezik.drawanywhere.drawing.StrokeEraserTool
import com.shezik.drawanywhere.drawing.StrokeTool
import com.shezik.drawanywhere.drawing.ToolContext
import com.shezik.drawanywhere.view.toolbar.InkEraser24Px

enum class PenType(
    val labelResId: Int,
    val icon: ImageVector,
    val renderer: Renderer,
    val hitTester: HitTester,
    val ttlMs: Long = Long.MAX_VALUE,
    val isEraser: Boolean = false,
) {
    Pen(R.string.pen, Icons.Default.Edit, PenRenderer, SegmentHitTester),
    Rectangle(R.string.rectangle, Icons.Default.CropSquare, RectRenderer, EdgeHitTester),
    Ellipse(R.string.ellipse, Icons.Default.RadioButtonUnchecked, OvalRenderer, EdgeHitTester),
    StrokeEraser(R.string.stroke_eraser, InkEraser24Px, PenRenderer, SegmentHitTester, isEraser = true),
    PixelEraser(R.string.pixel_eraser, Icons.Default.BlurOn, PenRenderer, SegmentHitTester, isEraser = true);

    fun createTool(ctx: ToolContext): StrokeTool = when (this) {
        Pen -> FreehandTool(ctx)
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
