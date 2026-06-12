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

    /**
     * Free placement: the chip may dock over the status bar and push half-out of
     * any screen edge — a ≥[MIN_VISIBLE_DP] sliver always stays touchable. Off =
     * safe mode: fully on-screen, below the status bar. The "Reset bubble
     * position" button is the escape hatch either way (a chip under the status
     * bar is visible but the bar steals its touches).
     */
    var freePlacement: Boolean = true

    val isShown: Boolean get() = view != null
    val posX: Int get() = params?.x ?: 0
    val posY: Int get() = params?.y ?: 0

    /** Programmatic move (the Reset button) — clamped, applied live. */
    fun moveTo(x: Int, y: Int) {
        val lp = params ?: return
        lp.x = x; lp.y = y
        clamp(lp)
        view?.let { runCatching { wm.updateViewLayout(it, lp) } }
    }

    /**
     * Keeps the chip FULLY on screen. Without this a drag could park it in a
     * corner with only a sliver (or nothing) left visible — and since only the
     * visible part receives touches, it became impossible to drag back. Applied
     * on show (heals bad persisted positions), on every drag move, on drop and
     * after a size change.
     */
    private fun clamp(lp: WindowManager.LayoutParams) {
        val dm = context.resources.displayMetrics
        val vw = view?.width?.takeIf { it > 0 } ?: (48 * dm.density).roundToInt()
        val vh = view?.height?.takeIf { it > 0 } ?: (BASE_HEIGHT_DP * dm.density).roundToInt()
        if (freePlacement) {
            // Half-out docking: at least a MIN_VISIBLE sliver stays on screen on
            // both axes, and the chip may sit over the status bar (y from 0).
            val min = (MIN_VISIBLE_DP * dm.density).roundToInt()
            lp.x = lp.x.coerceIn(-(vw - min), dm.widthPixels - min)
            lp.y = lp.y.coerceIn(0, dm.heightPixels - min)
        } else {
            // Safe mode: fully on screen, never under the status bar (touches
            // there belong to the system shade pull → chip becomes un-draggable).
            val topInset = statusBarHeightPx()
            lp.x = lp.x.coerceIn(0, (dm.widthPixels - vw).coerceAtLeast(0))
            lp.y = lp.y.coerceIn(topInset, (dm.heightPixels - vh).coerceAtLeast(topInset))
        }
    }

    /** Status-bar height (framework dimen; sane fallback when missing). */
    private fun statusBarHeightPx(): Int {
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id)
        else (28 * res.displayMetrics.density).roundToInt()
    }

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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // Without NO_LIMITS the system silently forces the window fully
                // on-screen — half-out docking (free placement) needs it; OUR
                // clamp() is what keeps a touchable sliver visible.
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
                    clamp(lp)
                    view?.let { wm.updateViewLayout(it, lp) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) {
                        clamp(lp)
                        view?.let { wm.updateViewLayout(it, lp) }
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
        clamp(lp)   // heal positions persisted off-screen by older builds
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
        // Re-layout only when the chip's size actually changed (content width or
        // bubble-size slider) — a per-second updateViewLayout would fight an
        // in-progress drag. Clamp too: a grown chip must not poke off-screen.
        if (iv.width != targetW || iv.height != targetH.roundToInt()) {
            params?.let { lp ->
                clamp(lp)
                runCatching { wm.updateViewLayout(iv, lp) }
            }
        }
    }

    fun hide() {
        view?.let { v -> runCatching { wm.removeView(v) } }
        view = null
        params = null
    }

    private companion object {
        /** Chip height in dp at 100% — the bubble-size slider (0.8–1.6) scales it. */
        const val BASE_HEIGHT_DP = 30f

        /** Touchable sliver that must stay on screen in free-placement mode. */
        const val MIN_VISIBLE_DP = 24f
    }
}
