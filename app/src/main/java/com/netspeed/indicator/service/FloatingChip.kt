package com.netspeed.indicator.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.netspeed.indicator.ui.MainActivity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The floating speed bubble: a small draggable chip drawn over every app via a
 * [WindowManager] overlay (requires the user-granted SYSTEM_ALERT_WINDOW
 * permission). Unlike the status-bar icon, WE own this surface — its size is
 * fully configurable and never squeezed by an OS icon slot, so the text is
 * always crisply legible.
 *
 * Drag anywhere; a small movement is treated as a tap and opens the app. The
 * drop position is reported through [onDropped] so it persists across restarts.
 */
class FloatingChip(
    private val context: Context,
    private val onDropped: (x: Int, y: Int) -> Unit,
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    val isShown: Boolean get() = view != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(x: Int, y: Int, scale: Float) {
        if (view != null) { applyScale(scale); return }
        val density = context.resources.displayMetrics.density
        val tv = TextView(context).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 64f * density
                setColor(0xE6101218.toInt())
                setStroke((1.5f * density).roundToInt(), 0x33FFFFFF)
            }
            text = "↓ 0 KB/s"
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
        tv.setOnTouchListener { _, ev ->
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

        view = tv
        params = lp
        applyScale(scale)
        runCatching { wm.addView(tv, lp) }.onFailure { view = null; params = null }
    }

    /** Bubble size: text + padding scale together, so the chip grows as one. */
    private fun applyScale(scale: Float) {
        val tv = view ?: return
        val density = context.resources.displayMetrics.density
        tv.textSize = 13f * scale
        val padH = (12f * scale * density).roundToInt()
        val padV = (6f * scale * density).roundToInt()
        tv.setPadding(padH, padV, padH, padV)
    }

    /**
     * Feeds live text and the user's chosen ICON colours. The floating chip is our
     * own surface (not an OS-tinted status-bar slot), so unlike the status-bar icon
     * the custom background / text / outline colours actually render here in full.
     */
    fun update(
        text: CharSequence,
        bgColorArgb: Int,
        fgColorArgb: Int,
        borderColorArgb: Int,
        borderWidth: Int,
        accentArgb: Int,
    ) {
        val tv = view ?: return
        if (tv.text != text) tv.text = text
        tv.setTextColor(if (Color.alpha(fgColorArgb) == 0) Color.WHITE else fgColorArgb)
        val density = context.resources.displayMetrics.density
        (tv.background as? GradientDrawable)?.apply {
            // Default to a legible translucent dark pill when the user left the
            // background transparent (an overlay needs SOME body to read against).
            setColor(if (Color.alpha(bgColorArgb) == 0) 0xE6101218.toInt() else bgColorArgb)
            val stroke = if (Color.alpha(borderColorArgb) != 0) borderColorArgb else accentArgb
            setStroke((borderWidth.coerceIn(1, 3) * density).roundToInt(), stroke)
        }
    }

    fun hide() {
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        params = null
    }
}
