package com.shezik.drawanywhere.drawing

import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.Stroke
import com.shezik.drawanywhere.model.PenConfig

/**
 * Shared state injected into every [StrokeTool] so they can read/modify the
 * stroke list and push undo actions without touching internals of [DrawController].
 */
class ToolContext(
    /** Mutable access to the stroke list (package-private conceptually — only tools use it). */
    val strokes: MutableList<Stroke>,
    /** Current pen configuration (width, color, alpha, penType). */
    val penConfig: PenConfig,
    private val onUndoPush: (DrawAction) -> Unit,
    private val onChanged: () -> Unit,
) {
    fun pushUndo(action: DrawAction) = onUndoPush(action)
    fun notifyChanged() = onChanged()
}
