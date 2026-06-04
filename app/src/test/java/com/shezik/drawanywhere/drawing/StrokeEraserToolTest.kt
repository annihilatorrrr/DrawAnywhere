package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.DrawObject
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import org.junit.Assert.*
import org.junit.Test

class StrokeEraserToolTest {

    private fun toolContext(
        strokes: MutableList<DrawObject.Stroke> = mutableListOf(),
        penConfig: PenConfig = PenConfig(penType = PenType.StrokeEraser, width = 20f, color = Color.LightGray),
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
            color = Color.Red, width = 4f, alpha = 1f, penType = PenType.Pen,
        )

    @Test
    fun singlePointHit_removesDot() {
        val strokes = mutableListOf(penStroke(listOf(Offset(10f, 10f))))
        val undo = mutableListOf<DrawAction>()
        val tool = StrokeEraserTool(toolContext(strokes, undoActions = undo))

        tool.onStart(Offset(15f, 15f))  // distance ≈ 7, r=10 → hit
        tool.onFinish()

        assertEquals(0, strokes.size)
        assertTrue(undo.single() is DrawAction.EraseStroke)
    }

    @Test
    fun lineSegmentHit_removesStroke() {
        val strokes = mutableListOf(penStroke(listOf(Offset(0f, 0f), Offset(50f, 0f))))
        val undo = mutableListOf<DrawAction>()
        val tool = StrokeEraserTool(toolContext(strokes, undoActions = undo))

        tool.onStart(Offset(25f, 5f))  // distance to segment ≈ 5, r=10 → hit
        tool.onFinish()

        assertEquals(0, strokes.size)
        assertEquals(1, undo.size)
    }

    @Test
    fun missDoesNothing() {
        val strokes = mutableListOf(penStroke(listOf(Offset(0f, 0f), Offset(100f, 0f))))
        val undo = mutableListOf<DrawAction>()
        val tool = StrokeEraserTool(toolContext(
            strokes, penConfig = PenConfig(penType = PenType.StrokeEraser, width = 4f, color = Color.LightGray),
            undoActions = undo
        ))

        tool.onStart(Offset(50f, 50f))  // distance ≈ 50, r=2 → miss
        tool.onFinish()

        assertEquals(1, strokes.size)
        assertTrue(undo.isEmpty())
    }

    @Test
    fun erasesLastStrokeWhenMultipleHit() {
        val strokes = mutableListOf(
            penStroke(listOf(Offset(0f, 0f), Offset(100f, 0f))),
            penStroke(listOf(Offset(0f, 50f), Offset(100f, 50f))),
        )
        val tool = StrokeEraserTool(toolContext(strokes))

        tool.onStart(Offset(50f, 50f))  // hits the second stroke

        assertEquals(1, strokes.size)
        assertEquals(0f, strokes[0].points[0].y)  // first stroke survives
    }

    @Test
    fun erasesOnlyFirstHitInReverseOrder() {
        val strokes = mutableListOf(
            penStroke(listOf(Offset(50f, 0f), Offset(50f, 100f))),
            penStroke(listOf(Offset(0f, 50f), Offset(100f, 50f))),
        )
        val tool = StrokeEraserTool(toolContext(strokes))

        // Both strokes pass through (50,50), eraser hits the last one (reverse order)
        tool.onStart(Offset(50f, 50f))
        tool.onFinish()

        assertEquals(1, strokes.size)
        // The FIRST stroke (vertical) survives because erase iterates in reverse
        assertEquals(Offset(50f, 0f), strokes[0].points[0])
        assertTrue(strokes[0].points[1].y > 0f)
    }

    @Test
    fun onMoveErasesContinuously() {
        val strokes = mutableListOf(
            penStroke(listOf(Offset(0f, 0f), Offset(100f, 0f))),
            penStroke(listOf(Offset(0f, 100f), Offset(100f, 100f))),
        )
        val undo = mutableListOf<DrawAction>()
        val tool = StrokeEraserTool(toolContext(strokes, undoActions = undo))

        tool.onStart(Offset(50f, 5f))   // erases first stroke (y=0)
        tool.onMove(Offset(50f, 95f))   // erases second stroke (y=100)
        tool.onFinish()

        assertEquals(0, strokes.size)
        assertEquals(2, undo.size)
    }

    @Test
    fun emptyCanvas_noCrash() {
        val tool = StrokeEraserTool(toolContext())
        tool.onStart(Offset(10f, 10f))
        tool.onMove(Offset(20f, 20f))
        tool.onFinish()
    }

    // ── Rectangle / Ellipse edge-hit tests ───────────────────────

    @Test
    fun rectangleEdgeHit_removesShape() {
        val rect = DrawObject.Stroke(
            _points = mutableListOf(Offset(10f, 10f), Offset(100f, 100f)),
            color = Color.Red, width = 4f, alpha = 1f, penType = PenType.Rectangle,
        )
        val strokes = mutableListOf(rect)
        val tool = StrokeEraserTool(toolContext(strokes))

        // Hit top edge of rectangle
        tool.onStart(Offset(55f, 10f))
        tool.onFinish()

        assertEquals(0, strokes.size)
    }

    @Test
    fun rectangleInteriorMiss_survives() {
        val rect = DrawObject.Stroke(
            _points = mutableListOf(Offset(10f, 10f), Offset(100f, 100f)),
            color = Color.Red, width = 4f, alpha = 1f, penType = PenType.Rectangle,
        )
        val strokes = mutableListOf(rect)
        val tool = StrokeEraserTool(toolContext(
            strokes, penConfig = PenConfig(penType = PenType.StrokeEraser, width = 10f, color = Color.LightGray),
        ))

        // Hit inside the rectangle — interior should NOT be erased (edge-only)
        tool.onStart(Offset(55f, 55f))
        tool.onFinish()

        assertEquals(1, strokes.size)
    }

    @Test
    fun ellipseEdgeHit_worksLikeRectangle() {
        val ellipse = DrawObject.Stroke(
            _points = mutableListOf(Offset(10f, 10f), Offset(100f, 100f)),
            color = Color.Red, width = 4f, alpha = 1f, penType = PenType.Ellipse,
        )
        val strokes = mutableListOf(ellipse)
        val tool = StrokeEraserTool(toolContext(strokes))

        tool.onStart(Offset(55f, 10f))  // top edge
        tool.onFinish()

        assertEquals(0, strokes.size)
    }
}
