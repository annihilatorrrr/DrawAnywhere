package com.shezik.drawanywhere.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.shezik.drawanywhere.pointsToPath

sealed class DrawObject {
    data class Stroke(
        val points: SnapshotStateList<Offset>,
        val color: Color,
        val width: Float,
        val alpha: Float,
        val transform: ObjectTransform = ObjectTransform(),
        private var _cachedPath: MutableState<Path?> = mutableStateOf(null),
        private var _cachedPathInvalid: MutableState<Boolean> = mutableStateOf(true),
    ) : DrawObject() {

        val cachedPath: Path get() =
            if ((_cachedPath.value == null) || _cachedPathInvalid.value)
                rebuildPath()
            else
                _cachedPath.value!!

        private fun rebuildPath(): Path {
            _cachedPath.value = pointsToPath(points)
            _cachedPathInvalid.value = false
            return _cachedPath.value!!
        }

        fun invalidatePath() {
            _cachedPathInvalid.value = true
        }

        fun releasePath(): Stroke {
            _cachedPath.value = null
            invalidatePath()
            return this
        }
    }
}

sealed class DrawAction {
    data class AddPath(val stroke: DrawObject.Stroke) : DrawAction()
    data class ErasePath(val stroke: DrawObject.Stroke) : DrawAction()
    data class ClearPaths(val strokes: List<DrawObject.Stroke>) : DrawAction()
}
