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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.round
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.shezik.drawanywhere.lifecycle.ToolbarLifecycleOwner
import com.shezik.drawanywhere.view.canvas.NativeDrawCanvasView
import com.shezik.drawanywhere.view.toolbar.DrawToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainService : Service() {
    companion object {
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "default_channel"
    }

    private val toolbarLifecycleOwner = ToolbarLifecycleOwner()
    private lateinit var drawController: DrawController
    private lateinit var windowManager: WindowManager
    private lateinit var canvasView: NativeDrawCanvasView
    private lateinit var toolbarView: ComposeView
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: DrawViewModel
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        preferencesManager = PreferencesManager(this)
        val (initialUiState, initialServiceState) = runBlocking {
            preferencesManager.getSavedUiState() to preferencesManager.getSavedServiceState()
        }
        drawController = DrawController(initialUiState.currentPenConfig)
        viewModel = DrawViewModel(
            controller = drawController,
            preferencesMgr = preferencesManager,
            initialUiState = initialUiState,
            initialServiceState = initialServiceState,
            stopService = { stopSelf() }
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        // -------- Setup native canvas --------
        canvasView = NativeDrawCanvasView(this, drawController, viewModel)
        drawController.onPathsChanged = { canvasView.invalidate() }

        val canvasParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        applyCanvasPassthrough(canvasParams, initialUiState.canvasPassthrough)

        // -------- Setup toolbar (Compose) --------
        toolbarLifecycleOwner.start()
        toolbarView = ComposeView(this).apply {
            setContent { DrawToolbar(viewModel = viewModel) }
        }
        toolbarLifecycleOwner.attachTo(toolbarView)

        val toolbarParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        applyToolbarPosition(toolbarParams, initialServiceState)
        // ---------------------------------------

        windowManager.addView(canvasView, canvasParams)
        windowManager.addView(toolbarView, toolbarParams)

        // Defer toolbar position validation until layout is complete
        toolbarView.post {
            applyToolbarPosition(toolbarParams, viewModel.serviceState.value)
            windowManager.updateViewLayout(toolbarView, toolbarParams)
        }

        // Observe UI state changes
        serviceScope.launch {
            viewModel.uiState.collect { state ->
                applyCanvasPassthrough(canvasParams, state.canvasPassthrough)
                windowManager.updateViewLayout(canvasView, canvasParams)
                canvasView.visibility = if (state.canvasVisible) View.VISIBLE else View.GONE
            }
        }

        // Observe service state changes
        serviceScope.launch {
            viewModel.serviceState.collect { state ->
                applyToolbarPosition(toolbarParams, state)
                windowManager.updateViewLayout(toolbarView, toolbarParams)

                val targetAlpha = if (state.toolbarActive) 1.0f else 0.5f
                toolbarView.animate()
                    .alpha(targetAlpha)
                    .setDuration(300)
                    .start()
            }
        }
    }

    private fun applyCanvasPassthrough(params: LayoutParams, passthrough: Boolean) {
        params.flags = if (passthrough)
            params.flags or LayoutParams.FLAG_NOT_TOUCHABLE
        else
            params.flags and LayoutParams.FLAG_NOT_TOUCHABLE.inv()
    }

    private fun applyToolbarPosition(params: LayoutParams, state: ServiceState) {
        val rounded = state.toolbarPosition.round()
        val (screenWidth, screenHeight) = getUsableScreenSize(windowManager)
        params.x = rounded.x.coerceIn(0, screenWidth - toolbarView.width)
        params.y = rounded.y.coerceIn(0, screenHeight - toolbarView.height)
        viewModel.setToolbarPosition(
            Offset(params.x.toFloat(), params.y.toFloat())
        )
    }

    @Suppress("DEPRECATION")
    private fun getUsableScreenSize(wm: WindowManager): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.maximumWindowMetrics
            val insets = metrics.windowInsets.getInsets(WindowInsets.Type.navigationBars())
            val b = metrics.bounds
            (b.width() - insets.left - insets.right) to (b.height() - insets.top - insets.bottom)
        } else {
            val size = Point()
            wm.defaultDisplay.getSize(size)
            size.x to size.y
        }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::toolbarView.isInitialized && toolbarView.isAttachedToWindow)
            windowManager.removeView(toolbarView)
        if (::canvasView.isInitialized && canvasView.isAttachedToWindow)
            windowManager.removeView(canvasView)
        toolbarLifecycleOwner.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
