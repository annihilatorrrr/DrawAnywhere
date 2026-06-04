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

package com.shezik.drawanywhere

import androidx.compose.ui.geometry.Offset
import com.shezik.drawanywhere.controller.UndoRedoManager
import com.shezik.drawanywhere.model.DrawAction
import com.shezik.drawanywhere.model.DrawObject
import com.shezik.drawanywhere.model.PenConfig
import com.shezik.drawanywhere.model.PenType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DrawController(initialConfig: PenConfig) {
    var penConfig: PenConfig = initialConfig
        private set

    fun setPenConfig(config: PenConfig) {
        penConfig = config
    }

    var onStrokesChanged: (() -> Unit)? = null

    private val _strokeList = mutableListOf<DrawObject.Stroke>()
    val strokeList: List<DrawObject.Stroke>
        get() = _strokeList

    private val undoRedo = UndoRedoManager()
    val canUndo: StateFlow<Boolean> = undoRedo.canUndo
    val canRedo: StateFlow<Boolean> = undoRedo.canRedo

    private val _canClear = MutableStateFlow(false)
    val canClearStrokes: StateFlow<Boolean> = _canClear.asStateFlow()

    private fun notifyChanged() {
        updateClearStrokesState()
        onStrokesChanged?.invoke()
    }

    fun updateLatestStroke(newPoint: Offset) {
        if (penConfig.penType == PenType.StrokeEraser) {
            eraseStroke(newPoint)
            return
        }
        _strokeList.lastOrNull()?.let { stroke ->
            when (stroke.penType) {
                PenType.Rectangle, PenType.Ellipse -> stroke._points[1] = newPoint
                else -> stroke._points.add(newPoint)
            }
        }
    }

    fun createStroke(newPoint: Offset) {
        if (penConfig.penType == PenType.StrokeEraser) {
            eraseStroke(newPoint)
            return
        }
        val points = when (penConfig.penType) {
            PenType.Rectangle, PenType.Ellipse -> mutableListOf(newPoint, newPoint)
            else -> mutableListOf(newPoint)
        }
        _strokeList.add(DrawObject.Stroke(
            _points = points,
            color = penConfig.color,
            width = penConfig.width,
            alpha = penConfig.alpha,
            penType = penConfig.penType,
        ))
    }

    fun finishStroke() {
        if (penConfig.penType == PenType.StrokeEraser) return
        if (_strokeList.isEmpty()) return
        val latest = _strokeList.last()

        // Normalize rectangle/ellipse: ensure left < right, top < bottom; discard if too small
        if (latest.penType == PenType.Rectangle || latest.penType == PenType.Ellipse) {
            val p0 = latest.points[0]; val p1 = latest.points[1]
            val left = minOf(p0.x, p1.x); val top = minOf(p0.y, p1.y)
            val right = maxOf(p0.x, p1.x); val bottom = maxOf(p0.y, p1.y)
            if (right - left < 4f && bottom - top < 4f) {
                _strokeList.removeAt(_strokeList.lastIndex)
                return
            }
            latest._points[0] = Offset(left, top)
            latest._points[1] = Offset(right, bottom)
        } else {
            if (latest.points.isEmpty()) {
                _strokeList.removeAt(_strokeList.lastIndex)
                return
            }
        }
        undoRedo.push(DrawAction.AddStroke(latest))
        notifyChanged()
    }

    private fun eraseStroke(point: Offset) {
        val eraserRadius = penConfig.width / 2
        var indexToErase: Int? = null
        for (i in _strokeList.indices.reversed()) {
            val stroke = _strokeList[i]
            val r = stroke.width / 2 + eraserRadius

            when (stroke.penType) {
                PenType.Rectangle, PenType.Ellipse -> {
                    if (stroke.points.size < 2) continue
                    val p0 = stroke.points[0]; val p1 = stroke.points[1]
                    if (hitTestRectEdge(point, minOf(p0.x, p1.x), minOf(p0.y, p1.y),
                            maxOf(p0.x, p1.x), maxOf(p0.y, p1.y), r)) {
                        indexToErase = i
                    }
                }
                else -> {
                    if (stroke.points.size > 1) {
                        stroke.points.zipWithNext().forEach { (p1, p2) ->
                            if (distancePointToLineSegment(point, p1, p2) <= r) {
                                indexToErase = i; return@forEach
                            }
                        }
                    } else {
                        stroke.points.firstOrNull()?.let {
                            if (distance(point, it) <= r) indexToErase = i
                        }
                    }
                }
            }
            if (indexToErase != null) break
        }
        indexToErase?.let {
            val erased = _strokeList.removeAt(it)
            undoRedo.push(DrawAction.EraseStroke(erased))
            notifyChanged()
        }
    }

    fun clearStrokes() {
        if (_strokeList.isEmpty()) return
        undoRedo.push(DrawAction.ClearStrokes(_strokeList.toList()))
        _strokeList.clear()
        notifyChanged()
    }

    private fun updateClearStrokesState() {
        _canClear.value = _strokeList.isNotEmpty()
    }

    fun undo() {
        val action = undoRedo.popUndo() ?: return
        when (action) {
            is DrawAction.AddStroke -> {
                if (_strokeList.remove(action.stroke)) {
                    undoRedo.pushRedo(action)
                }
            }
            is DrawAction.EraseStroke -> {
                _strokeList.add(action.stroke)
                undoRedo.pushRedo(action)
            }
            is DrawAction.ClearStrokes -> {
                _strokeList.addAll(action.strokes)
                undoRedo.pushRedo(action)
            }
        }
        notifyChanged()
    }

    fun redo() {
        val action = undoRedo.popRedo() ?: return
        when (action) {
            is DrawAction.AddStroke -> {
                _strokeList.add(action.stroke)
                undoRedo.push(action, clearRedo = false)
            }
            is DrawAction.EraseStroke -> {
                if (_strokeList.remove(action.stroke)) {
                    undoRedo.push(action, clearRedo = false)
                }
            }
            is DrawAction.ClearStrokes -> {
                _strokeList.removeAll(action.strokes)
                undoRedo.push(action, clearRedo = false)
            }
        }
        notifyChanged()
    }
}
