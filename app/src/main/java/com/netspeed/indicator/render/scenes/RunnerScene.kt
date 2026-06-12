package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Concept 8 — Stick runner. Dark track with a dashed ground line scrolling at
 * 40*(0.2+sc*2.4) px/s; a stick figure whose run-cycle phase advances by
 * 0.05+sc*0.5 per 60 fps frame. Forward lean (sc*6) and head bob (|sin ph|*2.5)
 * sell the effort. Below sc 0.06 he sits with two drifting "z" glyphs; above
 * 0.85 two ghost clones trail at phase −0.6/−1.2 (motion-blur multiples). A
 * >70% speed drop between ~1 s samples trips a 300 ms forward tumble.
 */
class RunnerScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val limbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val zBigPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val zSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var ph = 0f
    private var prevMbps = -1f
    private var sampleS = 0f
    private var tumbleS = 0f

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = h / 70f
        val sc = s.sc

        paint.style = Paint.Style.FILL
        if (!s.transparentBg) {
            paint.color = 0xFF151A2A.toInt()
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        val gy = h * 0.78f

        // Scrolling ground — manual dash segments (9k on, 11k off); no
        // DashPathEffect so the offset animates without per-frame allocation.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * k
        paint.color = argbWithAlpha(WHITE, 0.25f)
        val period = 20f * k
        val off = (s.timeS * 40f * (0.2f + sc * 2.4f) * k) % period
        val lineY = gy + 8f * k
        var dx = off - period
        while (dx < w) {
            canvas.drawLine(dx, lineY, dx + 9f * k, lineY, paint)
            dx += period
        }
        paint.style = Paint.Style.FILL

        // Run-cycle phase: gallery advances 0.05+sc*0.5 per 60 fps frame.
        val rate = (0.05f + sc * 0.5f) * 60f
        val phase: Float
        if (s.dtS > 0f) {
            ph += rate * s.dtS
            if (ph > TWO_PI) ph -= TWO_PI
            phase = ph
        } else {
            phase = s.timeS * rate          // static frame: analytic
        }

        // Trip detector: ~1 Hz samples of scene time; a >70% drop tumbles.
        if (s.dtS > 0f) {
            sampleS += s.dtS
            if (sampleS >= 1f) {
                sampleS = 0f
                if (prevMbps > 0.5f && s.mbps < prevMbps * 0.3f) tumbleS = TUMBLE_S
                prevMbps = s.mbps
            }
            if (tumbleS > 0f) tumbleS -= s.dtS
        }

        val cx = w * 0.45f
        val tumbling = tumbleS > 0f
        if (tumbling) {
            // Forward tumble: out-and-back ease to ~70° pivoting at the feet.
            val p = 1f - tumbleS / TUMBLE_S
            canvas.save()
            canvas.rotate(70f * sin(p * PIF), cx, gy)
        }
        if (sc > 0.85f) {
            drawMan(canvas, k, gy, cx - 16f * k, 0.12f, phase - 1.2f, sc, s.timeS)
            drawMan(canvas, k, gy, cx - 8f * k, 0.25f, phase - 0.6f, sc, s.timeS)
        }
        drawMan(canvas, k, gy, cx, 1f, phase, sc, s.timeS)
        if (tumbling) canvas.restore()
    }

    private fun drawMan(canvas: Canvas, k: Float, gy: Float, cx: Float, alpha: Float, phase: Float, sc: Float, timeS: Float) {
        val ink = argbWithAlpha(INK, alpha)
        paint.color = ink
        limbPaint.color = ink
        limbPaint.strokeWidth = 2.4f * k

        if (sc < 0.06f) {
            // Sitting: folded static legs, one propping arm, drifting Zzz.
            canvas.drawCircle(cx, gy - 19f * k, 5f * k, paint)
            canvas.drawLine(cx, gy - 14f * k, cx, gy - 5f * k, limbPaint)
            canvas.drawLine(cx, gy - 5f * k, cx + 7f * k, gy - 5f * k, limbPaint)
            canvas.drawLine(cx + 7f * k, gy - 5f * k, cx + 7f * k, gy, limbPaint)
            canvas.drawLine(cx, gy - 12f * k, cx + 6f * k, gy - 9f * k, limbPaint)
            zBigPaint.textSize = 9f * k
            zBigPaint.color = ink
            canvas.drawText("z", cx + 9f * k, gy - 26f * k + sin(timeS * 2f) * 2f * k, zBigPaint)
            zSmallPaint.textSize = 7f * k
            zSmallPaint.color = ink
            canvas.drawText("z", cx + 14f * k, gy - 31f * k + sin(timeS * 2f + 1f) * 2f * k, zSmallPaint)
            return
        }

        val lean = sc * 6f * k
        val bob = abs(sin(phase)) * 2.5f * k
        val hx = cx + lean
        val hy = gy - 26f * k - bob
        canvas.drawCircle(hx, hy, 5f * k, paint)

        val hipY = gy - 10f * k - bob
        canvas.drawLine(hx - lean * 0.4f, hy + 5f * k, cx, hipY, limbPaint)   // torso

        // Legs: two 2-segment limbs half a cycle apart, hip→knee→foot.
        val l1 = sin(phase)
        val l2 = sin(phase + PIF)
        canvas.drawLine(cx, hipY, cx + l1 * 7f * k, gy - 4f * k, limbPaint)
        canvas.drawLine(cx + l1 * 7f * k, gy - 4f * k, cx + l1 * 11f * k, gy, limbPaint)
        canvas.drawLine(cx, hipY, cx + l2 * 7f * k, gy - 4f * k, limbPaint)
        canvas.drawLine(cx + l2 * 7f * k, gy - 4f * k, cx + l2 * 11f * k, gy, limbPaint)

        // Arms counter-swing from the shoulder.
        val shX = hx - lean * 0.3f
        val shY = hy + 8f * k
        canvas.drawLine(shX, shY, hx + l2 * 8f * k, hy + 13f * k, limbPaint)
        canvas.drawLine(shX, shY, hx + l1 * 8f * k, hy + 13f * k, limbPaint)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val INK = 0xFFEEF1F8.toInt()
        const val PIF = PI.toFloat()
        const val TWO_PI = (PI * 2.0).toFloat()
        const val TUMBLE_S = 0.3f
    }
}
