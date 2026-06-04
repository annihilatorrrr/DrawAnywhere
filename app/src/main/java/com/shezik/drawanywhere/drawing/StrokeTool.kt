package com.shezik.drawanywhere.drawing

import androidx.compose.ui.geometry.Offset

/**
 * Lifecycle of a single stroke gesture: start → move (zero or more) → finish.
 *
 * Tools are instantiated per-gesture by [com.shezik.drawanywhere.model.PenType.createTool].
 * The same [ToolContext] is shared across all tools.
 */
interface StrokeTool {
    fun onStart(point: Offset)
    fun onMove(point: Offset)
    fun onFinish()
}
