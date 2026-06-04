package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.DrawObject
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import org.junit.Assert.*
import org.junit.Test

class PixelEraserToolTest {

    private fun toolContext(
        strokes: MutableList<DrawObject.Stroke> = mutableListOf(),
        penConfig: PenConfig = PenConfig(penType = PenType.PixelEraser, width = 20f, color = Color.LightGray),
        undoActions: MutableList<DrawAction> = mutableListOf(),
    ): ToolContext = ToolContext(
        strokes = strokes,
        penConfig = penConfig,
        onUndoPush = { undoActions.add(it) },
        onChanged = {},
    )

    private fun penStroke(points: List<Offset>): DrawObject.Stroke =
        DrawObject.Stroke(
            _points = points.toMutableList(),
            color = Color.Red,
            width = 4f,
            alpha = 1f,
            penType = PenType.Pen,
        )

    @Test
    fun singlePointHit_removesStroke() {
        val strokes = mutableListOf(penStroke(listOf(Offset(10f, 10f))))
        val undo = mutableListOf<DrawAction>()
        val tool = PixelEraserTool(toolContext(strokes, undoActions = undo))

        tool.onStart(Offset(12f, 12f))
        tool.onFinish()

        assertEquals(0, strokes.size)
        assertTrue(undo.single() is DrawAction.CanvasSnapshot)
    }

    @Test
    fun middleHit_splitsStroke() {
        val strokes = mutableListOf(penStroke(listOf(
            Offset(0f, 0f), Offset(10f, 0f), Offset(20f, 0f), Offset(30f, 0f), Offset(40f, 0f)
        )))
        val tool = PixelEraserTool(toolContext(strokes))

        tool.onStart(Offset(20f, 5f))
        tool.onFinish()

        assertEquals(2, strokes.size)
        assertEquals(2, strokes[0].points.size)
        assertEquals(2, strokes[1].points.size)
    }

    @Test
    fun edgeHit_trimsEnd() {
        val strokes = mutableListOf(penStroke(listOf(
            Offset(0f, 0f), Offset(5f, 0f), Offset(20f, 0f)
        )))
        val tool = PixelEraserTool(toolContext(strokes))

        tool.onStart(Offset(5f, 5f))
        tool.onFinish()

        assertEquals(1, strokes.size)
        assertEquals(1, strokes[0].points.size) // only (20,0) remains
    }

    @Test
    fun multipleHitPoints_removesAll() {
        val strokes = mutableListOf(penStroke(listOf(
            Offset(0f, 0f), Offset(2f, 0f), Offset(4f, 0f)
        )))
        val tool = PixelEraserTool(toolContext(strokes))

        tool.onStart(Offset(2f, 5f))
        tool.onFinish()

        assertEquals(0, strokes.size)
    }

    @Test
    fun noHit_noChange() {
        val strokes = mutableListOf(penStroke(listOf(Offset(0f, 0f), Offset(100f, 100f))))
        val undo = mutableListOf<DrawAction>()
        val tool = PixelEraserTool(toolContext(strokes, undoActions = undo))

        tool.onStart(Offset(200f, 200f))
        tool.onFinish()

        assertEquals(1, strokes.size)
        assertEquals(2, strokes[0].points.size)
        assertTrue(undo.isEmpty())
    }

    @Test
    fun bboxFiltersOutDistantStroke() {
        val strokes = mutableListOf(
            penStroke(listOf(Offset(0f, 0f), Offset(100f, 100f))),
            penStroke(listOf(Offset(200f, 200f), Offset(250f, 200f))),
        )
        val tool = PixelEraserTool(toolContext(strokes))

        tool.onStart(Offset(500f, 500f))

        assertEquals(2, strokes.size)
    }

    @Test
    fun nonPenStrokesIgnored() {
        val rectStroke = DrawObject.Stroke(
            _points = mutableListOf(Offset(0f, 0f), Offset(50f, 50f)),
            color = Color.Red, width = 4f, alpha = 1f, penType = PenType.Rectangle,
        )
        val strokes = mutableListOf(rectStroke)
        val tool = PixelEraserTool(toolContext(strokes))

        tool.onStart(Offset(25f, 25f))
        tool.onFinish()

        assertEquals(1, strokes.size)
    }

    @Test
    fun consecutiveMoves_accumulate() {
        val strokes = mutableListOf(penStroke(listOf(
            Offset(0f, 0f), Offset(20f, 0f), Offset(40f, 0f)
        )))
        val undo = mutableListOf<DrawAction>()
        val tool = PixelEraserTool(toolContext(strokes, undoActions = undo))

        tool.onStart(Offset(2f, 5f))
        tool.onMove(Offset(38f, 5f))
        tool.onFinish()

        assertEquals(1, strokes.size)
        assertEquals(1, strokes[0].points.size)
        assertEquals(1, undo.size)
    }

    @Test
    fun emptyCanvas_noCrash() {
        val tool = PixelEraserTool(toolContext())
        tool.onStart(Offset(10f, 10f))
        tool.onMove(Offset(20f, 20f))
        tool.onFinish()
    }

    @Test
    fun undoRestoresFullCanvas() {
        val strokes = mutableListOf(
            penStroke(listOf(Offset(0f, 0f), Offset(50f, 0f))),
            penStroke(listOf(Offset(100f, 100f), Offset(150f, 100f))),
        )
        val undo = mutableListOf<DrawAction>()
        val tool = PixelEraserTool(toolContext(strokes, undoActions = undo))

        // Eraser with r=15, hit the midpoint of first stroke — both points hit (25,0) is 0 away
        // First stroke (0,0)→(50,0): points at 0,50; eraser at (25,0) hits first
        val ctx = toolContext(strokes, penConfig = PenConfig(penType = PenType.PixelEraser, width = 30f, color = Color.LightGray), undoActions = undo)
        val tool2 = PixelEraserTool(ctx)
        tool2.onStart(Offset(25f, 5f))
        tool2.onFinish()

        // Both points of first stroke within r=15 of (25,5): 0→√(25²+5²)=25.5>15? No wait, that's too far.
        // Let me just hit point (0,0) directly: distance 0
        strokes.clear()
        strokes.addAll(listOf(
            penStroke(listOf(Offset(0f, 0f), Offset(50f, 0f))),
            penStroke(listOf(Offset(100f, 100f), Offset(150f, 100f))),
        ))
        val undo2 = mutableListOf<DrawAction>()
        val ctx2 = toolContext(strokes, penConfig = PenConfig(penType = PenType.PixelEraser, width = 20f, color = Color.LightGray), undoActions = undo2)
        val t = PixelEraserTool(ctx2)
        t.onStart(Offset(10f, 0f))  // r=10, hits point (0,0): dist=10 ≤ 10, hits (50,0): dist=40 > 10
        t.onFinish()

        // First stroke split: point (0,0) removed, only (50,0) remains as a single-point stroke
        assertEquals(2, strokes.size) // single-point from original + second stroke
        assertEquals(1, undo2.size)
        val action = undo2.single() as DrawAction.CanvasSnapshot
        assertEquals(2, action.before.size)
        assertEquals(2, action.after.size)
    }
}
