package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import com.shezik.drawanywhere.model.Stroke
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EphemeralStrokeTest {

    private fun toolContext(
        strokes: MutableList<Stroke> = mutableListOf(),
        penConfig: PenConfig = PenConfig(penType = PenType.Laser, width = 5f, color = Color.Red),
        undoActions: MutableList<DrawAction> = mutableListOf(),
    ): ToolContext = ToolContext(
        strokes = strokes,
        penConfig = penConfig,
        onUndoPush = { undoActions.add(it) },
        onChanged = {},
    )

    private fun laserStroke(ttlMs: Long = 3_000L): Stroke = Stroke(
        _points = mutableListOf(Offset(0f, 0f), Offset(50f, 0f)),
        color = Color.Red, width = 5f, alpha = 1f,
        penType = PenType.Laser,
    )

    private fun penStroke(): Stroke = Stroke(
        _points = mutableListOf(Offset(0f, 0f), Offset(50f, 0f)),
        color = Color.Red, width = 5f, alpha = 1f,
        penType = PenType.Pen,
    )

    // ── DrawAction.withoutEphemeral ──────────────────────────────────

    @Test
    fun addEphemeralStroke_returnsNull() {
        val action = DrawAction.AddStroke(laserStroke())
        assertNull(action.withoutEphemeral())
    }

    @Test
    fun addPermanentStroke_returnsItself() {
        val action = DrawAction.AddStroke(penStroke())
        assertSame(action, action.withoutEphemeral())
    }

    @Test
    fun eraseEphemeralStroke_returnsNull() {
        val action = DrawAction.EraseStroke(laserStroke())
        assertNull(action.withoutEphemeral())
    }

    @Test
    fun erasePermanentStroke_returnsItself() {
        val action = DrawAction.EraseStroke(penStroke())
        assertSame(action, action.withoutEphemeral())
    }

    @Test
    fun clearMixedStrokes_filtersEphemeral() {
        val action = DrawAction.ClearStrokes(listOf(laserStroke(), penStroke(), laserStroke()))
        val filtered = action.withoutEphemeral()
        assertNotNull(filtered)
        val clear = filtered as DrawAction.ClearStrokes
        assertEquals(1, clear.strokes.size)
        assertEquals(PenType.Pen, clear.strokes[0].penType)
    }

    @Test
    fun clearOnlyEphemeral_returnsNull() {
        val action = DrawAction.ClearStrokes(listOf(laserStroke(), laserStroke()))
        assertNull(action.withoutEphemeral())
    }

    @Test
    fun snapshotOnlyEphemeral_returnsNull() {
        val action = DrawAction.CanvasSnapshot(
            before = listOf(laserStroke(), laserStroke()),
            after = listOf(laserStroke()),
        )
        assertNull(action.withoutEphemeral())
    }

    @Test
    fun snapshotMixed_keepsPermanent() {
        val action = DrawAction.CanvasSnapshot(
            before = listOf(laserStroke(), penStroke()),
            after = listOf(penStroke()),
        )
        val filtered = action.withoutEphemeral()!!
        val snap = filtered as DrawAction.CanvasSnapshot
        assertEquals(1, snap.before.size)
        assertEquals(1, snap.after.size)
        assertEquals(PenType.Pen, snap.before[0].penType)
    }

    @Test
    fun snapshotUnchanged_returnsSelf() {
        val action = DrawAction.CanvasSnapshot(
            before = listOf(penStroke()),
            after = listOf(penStroke()),
        )
        assertSame(action, action.withoutEphemeral())
    }

    // ── UndoRedoManager filtering ─────────────────────────────────────

    @Test
    fun pushEphemeral_doesNotAddToStack() = runTest {
        val um = com.shezik.drawanywhere.UndoRedoManager()
        um.push(DrawAction.AddStroke(laserStroke()))
        assertFalse(um.canUndo.first())
        assertNull(um.popUndo())
    }

    @Test
    fun pushPermanent_addsToStack() = runTest {
        val um = com.shezik.drawanywhere.UndoRedoManager()
        um.push(DrawAction.AddStroke(penStroke()))
        assertTrue(um.canUndo.first())
    }

    @Test
    fun pushEphemeralDoesNotClearRedo() = runTest {
        val um = com.shezik.drawanywhere.UndoRedoManager()
        // Push and undo a permanent action
        um.push(DrawAction.AddStroke(penStroke()))
        um.popUndo()
        um.pushRedo(DrawAction.AddStroke(penStroke()))
        assertTrue(um.canRedo.first())

        // Push ephemeral — should NOT clear redo stack
        um.push(DrawAction.AddStroke(laserStroke()))
        assertTrue(um.canRedo.first())
    }

    // ── DrawController.removeExpiredStrokes ──────────────────────────

    @Test
    fun removeExpired_keepsRecentLaser() {
        val c = com.shezik.drawanywhere.DrawController(PenConfig(penType = PenType.Laser))
        c.createStroke(Offset(0f, 0f))
        c.updateLatestStroke(Offset(50f, 0f))
        c.finishStroke()
        assertEquals(1, c.strokeList.size)

        // Stroke was just created — TTL is 3s, so it should survive
        c.removeExpiredStrokes(System.currentTimeMillis())
        assertEquals(1, c.strokeList.size)
    }

    @Test
    fun removeExpired_clearsExpiredLaser() {
        val c = com.shezik.drawanywhere.DrawController(PenConfig(penType = PenType.Laser))
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()
        assertEquals(1, c.strokeList.size)

        // Advance time 10s past creation — stroke should expire (TTL=3s)
        c.removeExpiredStrokes(System.currentTimeMillis() + 10_000L)
        assertEquals(0, c.strokeList.size)
    }

    @Test
    fun removeExpired_keepsPermanent() {
        val c = com.shezik.drawanywhere.DrawController(PenConfig())
        c.createStroke(Offset(0f, 0f))
        c.updateLatestStroke(Offset(50f, 0f))
        c.finishStroke()
        assertEquals(1, c.strokeList.size)

        c.removeExpiredStrokes(System.currentTimeMillis() + 100_000L)
        assertEquals(1, c.strokeList.size)
    }

    @Test
    fun removeExpired_noNotificationWhenUnchanged() {
        val c = com.shezik.drawanywhere.DrawController(PenConfig())
        c.createStroke(Offset(0f, 0f))
        c.finishStroke()

        var changed = false
        c.onStrokesChanged = { changed = true }

        // Permanent stroke — nothing to remove
        c.removeExpiredStrokes(System.currentTimeMillis() + 100_000L)
        assertFalse(changed)
    }

    // ── Laser stroke via FreehandTool ─────────────────────────────────

    @Test
    fun freehandToolCreatesLaserStroke() {
        val undo = mutableListOf<DrawAction>()
        val ctx = toolContext(undoActions = undo)
        val tool = FreehandTool(ctx)

        tool.onStart(Offset(10f, 10f))
        tool.onMove(Offset(50f, 50f))
        tool.onFinish()

        assertEquals(1, ctx.strokes.size)
        assertEquals(PenType.Laser, ctx.strokes[0].penType)
        assertTrue(ctx.strokes[0].penType.isEphemeral)
        assertEquals(1, undo.size)
    }
}
