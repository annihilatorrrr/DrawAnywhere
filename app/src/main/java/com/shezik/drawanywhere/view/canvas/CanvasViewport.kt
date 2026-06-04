package com.shezik.drawanywhere.view.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

data class CanvasViewport(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoomLocked: Boolean = false,
) {
    companion object {
        const val MIN_ZOOM = 0.01f
    }

    fun screenToWorld(screen: Offset): Offset =
        Offset(
            screen.x / zoom + panX,
            screen.y / zoom + panY
        )

    fun pan(dx: Float, dy: Float): CanvasViewport =
        copy(panX = panX - dx / zoom, panY = panY - dy / zoom)

    fun zoomAt(factor: Float, pivot: Offset): CanvasViewport {
        if (zoomLocked) return this
        val newZoom = (zoom * factor).coerceAtLeast(MIN_ZOOM)
        if (abs(newZoom - zoom) < 1e-6f) return this
        // Keep the point under the pivot fixed in screen space
        val worldPivot = screenToWorld(pivot)
        return copy(
            zoom = newZoom,
            panX = worldPivot.x - pivot.x / newZoom,
            panY = worldPivot.y - pivot.y / newZoom
        )
    }

    fun resetAt(screenCenter: Offset): CanvasViewport {
        val worldCenter = screenToWorld(screenCenter)
        return copy(
            zoom = 1f,
            panX = worldCenter.x - screenCenter.x,
            panY = worldCenter.y - screenCenter.y
        )
    }

    fun withZoomLock(locked: Boolean): CanvasViewport = copy(zoomLocked = locked)
}
