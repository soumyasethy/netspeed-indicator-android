package com.netspeed.indicator.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import com.netspeed.indicator.ui.MainActivity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The floating speed bubble: a small draggable chip drawn over every app via a
 * [WindowManager] overlay (requires the user-granted SYSTEM_ALERT_WINDOW
 * permission). It shows the SAME icon bitmap the status bar uses — rendered by
 * [IconRenderer.renderChip] — but in full colour: because WE own this surface
 * there is no OS tint, so the chosen icon style, unit, colours, outline, font
 * size and upload value all render exactly as configured.
 *
 * Drag anywhere; a small movement is treated as a tap and opens the app. The
 * drop position is reported through [onDropped] so it persists across restarts.
 */
class FloatingChip(
    private val context: Context,
    private val onDropped: (x: Int, y: Int) -> Unit,
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: ImageView? = null
    private var params: WindowManager.LayoutParams? = null

    /** Display height of the chip in dp at scale 1.0 (the bubble-size slider scales this). */
    private var scale: Float = 1f

    val isShown: Boolean get() = view != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(x: Int, y: Int, scale: Float) {
        this.scale = scale
        if (view != null) return
        val iv = ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var dragged = false
        iv.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = lp.x; startY = lp.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX; val dy = ev.rawY - downY
                    if (abs(dx) > 12 || abs(dy) > 12) dragged = true
                    lp.x = startX + dx.roundToInt()
                    lp.y = startY + dy.roundToInt()
                    view?.let { wm.updateViewLayout(it, lp) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) {
                        onDropped(lp.x, lp.y)
                    } else {
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    true
                }
                else -> false
            }
        }

        view = iv
        params = lp
        runCatching { wm.addView(iv, lp) }.onFailure { view = null; params = null }
    }

    fun applyScale(scale: Float) {
        this.scale = scale
    }

    /**
     * Shows the latest rendered chip bitmap, scaled to the bubble-size setting. The
     * bitmap already carries the chosen style / colours / outline, so the bubble is
     * just a scaled image of it — no separate colour plumbing needed.
     */
    fun update(bitmap: Bitmap) {
        val iv = view ?: return
        val density = context.resources.displayMetrics.density
        val targetH = BASE_HEIGHT_DP * scale * density
        val s = targetH / bitmap.height.coerceAtLeast(1)
        val targetW = (bitmap.width * s).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH.roundToInt().coerceAtLeast(1), true)
        iv.setImageBitmap(scaled)
        params?.let { lp -> runCatching { wm.updateViewLayout(iv, lp) } }
    }

    fun hide() {
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        params = null
    }

    private companion object {
        /** Chip height in dp at 100% — the bubble-size slider (0.8–1.6) scales it. */
        const val BASE_HEIGHT_DP = 30f
    }
}
