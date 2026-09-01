package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.*

class OverlayManager(private val context: Context) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayJob: Job? = null
    private var hideJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    @SuppressLint("ClickableViewAccessibility")
    fun showOverlay() {
        if (overlayView != null) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val size = (80 * context.resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 150

        val layout = FrameLayout(context).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#99FFD700")) // 60% transparent yellow
                shape = GradientDrawable.OVAL
                setStroke(6, Color.parseColor("#0A0A0A"))
            }
            background = bg
        }

        val stopIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.parseColor("#0A0A0A"))
            layoutParams = FrameLayout.LayoutParams(size/2, size/2, Gravity.CENTER)
        }

        layout.addView(stopIcon)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        layout.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    resetHideTimer()
                    layout.alpha = 1f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(layout, params)
                    resetHideTimer()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.rawX - initialTouchX
                    val diffY = event.rawY - initialTouchY
                    if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                        val stopIntent = Intent(context, RecordingForegroundService::class.java).apply {
                            action = RecordingForegroundService.ACTION_STOP
                        }
                        context.startService(stopIntent)
                    }
                    resetHideTimer()
                    true
                }
                else -> false
            }
        }

        overlayView = layout
        windowManager?.addView(overlayView, params)
        resetHideTimer()
    }

    private fun resetHideTimer() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(5000)
            overlayView?.animate()?.alpha(0.3f)?.setDuration(500)?.start()
        }
    }

    fun hideOverlay() {
        hideJob?.cancel()
        overlayJob?.cancel()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
    }
}
