package com.netspeed.indicator.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Choreographer
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The floating bubble's canvas: draws the chip bitmap plus an optional
 * SPEED-REACTIVE procedural effect (flame / glow / sparks) behind it. Pure
 * Canvas maths — zero assets, ~kilobytes of code — so the APK budget holds.
 *
 * Battery discipline:
 *  - The [Choreographer] frame loop runs ONLY while an effect is selected,
 *    [intensity] > 0 and the view is attached. Idle (no traffic) = a static
 *    bitmap, zero frames.
 *  - Frames are capped (~30 fps) by skipping alternate vsyncs.
 *  - No allocations on the frame path: paints/paths/particle pool reused.
 *
 * The view is intentionally LARGER than the chip ([fxPad] margin all around):
 * flames need headroom, and the margin doubles as a bigger touch target.
 */
@SuppressLint("ViewConstructor")
class BubbleFxView(context: Context) : View(context) {

    enum class Fx { NONE, FLAME, GLOW, SPARKS }

    private var chip: Bitmap? = null
    private var fx: Fx = Fx.NONE
    private var accent: Int = 0xFF7C3AED.toInt()

    /** 0..1 — how hard the network is working; drives every effect. */
    var intensity: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            syncLoop()
        }

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flamePath = Path()

    /** Recycled spark pool: x,y in fractions of chip box; life 0..1. */
    private val sparkX = FloatArray(SPARK_POOL)
    private val sparkY = FloatArray(SPARK_POOL)
    private val sparkLife = FloatArray(SPARK_POOL)
    private var sparkSeed = 7

    private var running = false
    private var skip = false
    private var timeMs = 0L

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            skip = !skip
            if (!skip) {                       // ~30 fps on a 60 Hz panel
                timeMs = frameTimeNanos / 1_000_000
                stepSparks()
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setChip(bitmap: Bitmap) {
        chip = bitmap
        requestLayout()
        invalidate()
    }

    fun setEffect(key: String, accentArgb: Int) {
        fx = when (key) {
            "flame" -> Fx.FLAME
            "glow" -> Fx.GLOW
            "sparks" -> Fx.SPARKS
            else -> Fx.NONE
        }
        accent = accentArgb
        syncLoop()
        invalidate()
    }

    /** Margin around the chip reserved for the effect (and easier grabbing). */
    private fun fxPad(): Int {
        val c = chip ?: return 0
        return if (fx == Fx.NONE) 0 else (c.height * 0.55f).roundToInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val c = chip
        if (c == null) { setMeasuredDimension(1, 1); return }
        val pad = fxPad()
        setMeasuredDimension(c.width + pad * 2, c.height + pad * 2)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncLoop()
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
        super.onDetachedFromWindow()
    }

    private fun syncLoop() {
        val want = isAttachedToWindow && fx != Fx.NONE && intensity > 0.01f
        if (want && !running) {
            running = true
            Choreographer.getInstance().postFrameCallback(frame)
        } else if (!want && running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(frame)
            invalidate()                        // settle on a clean static frame
        }
    }

    override fun onDraw(canvas: Canvas) {
        val c = chip ?: return
        val pad = fxPad().toFloat()
        if (running) when (fx) {
            Fx.FLAME -> drawFlames(canvas, pad, c)
            Fx.GLOW -> drawGlow(canvas, pad, c)
            Fx.SPARKS -> drawSparks(canvas, pad, c)
            Fx.NONE -> Unit
        }
        canvas.drawBitmap(c, pad, pad, bmpPaint)
    }

    // --- flame: tongues licking up from the chip's top edge ---------------------

    private fun drawFlames(canvas: Canvas, pad: Float, c: Bitmap) {
        val t = timeMs / 1000f
        val baseY = pad + c.height * 0.30f          // flames root behind the chip top
        val maxH = pad * 1.6f * intensity
        if (maxH < 1f) return
        fxPaint.style = Paint.Style.FILL
        val tongues = 7
        val span = c.width.toFloat()
        for (i in 0 until tongues) {
            val cx = pad + span * (i + 0.5f) / tongues
            val wob = sin(t * (3.1f + i * 0.83f) + i * 1.7f)
            val h = maxH * (0.55f + 0.45f * wob)
            if (h < 2f) continue
            val w = span / tongues * (0.8f + 0.2f * sin(t * 2.3f + i))
            flamePath.reset()
            flamePath.moveTo(cx - w / 2f, baseY)
            flamePath.quadTo(cx - w * 0.18f, baseY - h * 0.55f, cx + w * 0.06f * wob, baseY - h)
            flamePath.quadTo(cx + w * 0.22f, baseY - h * 0.5f, cx + w / 2f, baseY)
            flamePath.close()
            fxPaint.shader = LinearGradient(
                cx, baseY, cx, baseY - h,
                intArrayOf(accent, blend(accent, 0xFFFFC533.toInt(), 0.6f), 0x00FFFFFF),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(flamePath, fxPaint)
        }
        fxPaint.shader = null
    }

    // --- glow: breathing halo ----------------------------------------------------

    private fun drawGlow(canvas: Canvas, pad: Float, c: Bitmap) {
        val t = timeMs / 1000f
        val cx = pad + c.width / 2f
        val cy = pad + c.height / 2f
        val breathe = 0.5f + 0.5f * sin(t * (1f + 4f * intensity) * 2f)
        val radius = min(width, height) / 2f * (0.65f + 0.35f * breathe)
        val alpha = (0x38 + 0x58 * intensity * (0.4f + 0.6f * breathe)).toInt().coerceAtMost(0xB0)
        fxPaint.shader = RadialGradient(
            cx, cy, radius.coerceAtLeast(1f),
            intArrayOf((accent and 0x00FFFFFF) or (alpha shl 24), accent and 0x00FFFFFF),
            floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, fxPaint)
        fxPaint.shader = null
    }

    // --- sparks: particles streaming upward ---------------------------------------

    private fun stepSparks() {
        if (fx != Fx.SPARKS) return
        for (i in 0 until SPARK_POOL) {
            if (sparkLife[i] > 0f) {
                sparkLife[i] -= 0.045f
                sparkY[i] -= 0.035f * (0.5f + intensity)
                sparkX[i] += (nextRand(i) - 0.5f) * 0.02f
            } else if (nextRand(i + timeMs.toInt()) < 0.10f + 0.5f * intensity) {
                // respawn at the chip's rim
                sparkLife[i] = 1f
                sparkX[i] = nextRand(i * 31 + 7)
                sparkY[i] = 1f
            }
        }
    }

    private fun drawSparks(canvas: Canvas, pad: Float, c: Bitmap) {
        fxPaint.style = Paint.Style.FILL
        val r = c.height * 0.05f
        for (i in 0 until SPARK_POOL) {
            val life = sparkLife[i]
            if (life <= 0f) continue
            val x = pad + sparkX[i] * c.width
            val y = pad + sparkY[i] * c.height - (1f - life) * pad * 1.4f
            fxPaint.color = (accent and 0x00FFFFFF) or ((0xE0 * life).toInt() shl 24)
            canvas.drawCircle(x, y, r * (0.5f + life * 0.8f), fxPaint)
        }
    }

    /** Cheap deterministic pseudo-random in [0,1) — no allocations, stable. */
    private fun nextRand(salt: Int): Float {
        sparkSeed = sparkSeed * 1103515245 + 12345 + salt
        return ((sparkSeed ushr 16) and 0x7FFF) / 32768f
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.argb(
            ch(Color.alpha(a), Color.alpha(b)), ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)),
        )
    }

    private companion object {
        const val SPARK_POOL = 24
    }
}
