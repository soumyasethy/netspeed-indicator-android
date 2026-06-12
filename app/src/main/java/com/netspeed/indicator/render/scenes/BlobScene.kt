package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin

/**
 * Concept 6 — Elastic blob. A jelly creature at 45% width: round and wobbling
 * at rest, streamlining into a bullet as sc rises (rx grows, ry shrinks, the
 * wobble damps to zero). Eyes squint into the wind — eye height shrinks with
 * sc and the pupils drift toward travel; the squint carries the charm. Wind
 * streaks fade in with speed, tier changes squash the blob for 120 ms, and at
 * Crawling it occasionally slow-blinks and slumps 2dp.
 */
class BlobScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private val body = Path()
    private val rect = RectF()
    private val rng = SceneRng(11)

    // Background gradient is horizontal — cache the shader, rebuild on resize.
    private var bgW = -1f

    private var kick = 0f                 // 120 ms tier-change squash, 1→0
    private var prevTier = -1
    private var blinkS = 0f               // remaining blink time, 0 = open
    private var blinkGapS = 3f + rng.next() * 3f

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = sceneScale(w, h)
        val sc = s.sc

        if (!s.transparentBg) {
            if (bgW != w) {
                bgPaint.shader = LinearGradient(
                    0f, 0f, w, 0f,
                    0xFF131726.toInt(), 0xFF1A2033.toInt(), Shader.TileMode.CLAMP
                )
                bgW = w
            }
            canvas.drawRect(0f, 0f, w, h, bgPaint)
        }

        // Wind streaks, right→left at 200*sc dp/s — analytic, fine at dtS=0.
        val streaks = (sc * 8f).toInt()
        if (streaks > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * k
            paint.color = argbWithAlpha(WHITE, 0.08f + sc * 0.15f)
            val span = w + 30f * k
            val len = (14f + sc * 18f) * k
            for (i in 0 until streaks) {
                val y = (10f + i * 7f) * k
                val sx = w - ((s.timeS * 200f * sc * k + i * 40f * k) % span)
                canvas.drawLine(sx, y, sx + len, y, paint)
            }
            paint.style = Paint.Style.FILL
        }

        if (s.dtS > 0f) {
            if (prevTier >= 0 && s.tier != prevTier) kick = 1f
            prevTier = s.tier
            kick = (kick - s.dtS / 0.12f).coerceAtLeast(0f)
            if (s.tier == 0) {            // Crawling: slow blink every 3–6 s
                if (blinkS > 0f) {
                    blinkS -= s.dtS
                } else {
                    blinkGapS -= s.dtS
                    if (blinkGapS <= 0f) {
                        blinkS = BLINK_DUR_S
                        blinkGapS = 3f + rng.next() * 3f
                    }
                }
            } else {
                blinkS = 0f
            }
        }
        // 0→1→0 envelope over the blink; also drives the 2dp slump.
        val blink = if (blinkS > 0f)
            sin(PI.toFloat() * (1f - (blinkS / BLINK_DUR_S).coerceIn(0f, 1f))) else 0f

        val cx = w * 0.45f
        val cy = h / 2f + 2f * k * blink
        val wob = (1f - sc) * sin(s.timeS * 5f) * 2.5f * k
        val rx = (13f + sc * 12f) * k * (1f - kick * 0.2f)
        val ry = ((13f - sc * 5f) * k + wob) * (1f + kick * 0.3f)

        // Body: one path, two cubics — round trailing edge, pointed apex at +rx.
        body.rewind()
        body.moveTo(cx - rx * 0.9f, cy)
        body.cubicTo(cx - rx * 0.9f, cy - ry, cx + rx * 0.4f, cy - ry, cx + rx, cy)
        body.cubicTo(cx + rx * 0.4f, cy + ry, cx - rx * 0.9f, cy + ry, cx - rx * 0.9f, cy)
        body.close()
        paint.color = argbWithAlpha(s.accentArgb, 0.95f)
        canvas.drawPath(body, paint)

        // Eyes: the squint IS the charm — height collapses with sc (floor 0.5dp).
        val ey = cy - 2f * k
        val eh = (2.4f * k * (1f - sc * 0.72f) * (1f - blink)).coerceAtLeast(0.5f * k)
        paint.color = WHITE
        rect.set(cx + rx * 0.35f - 2.3f * k, ey - eh, cx + rx * 0.35f + 2.3f * k, ey + eh)
        canvas.drawOval(rect, paint)
        rect.set(cx + rx * 0.62f - 2.3f * k, ey - eh, cx + rx * 0.62f + 2.3f * k, ey + eh)
        canvas.drawOval(rect, paint)

        // Pupils drift 0.5dp toward travel with speed.
        val drift = sc * 0.5f * k
        val ph = (eh * 0.55f).coerceAtLeast(0.4f * k)
        paint.color = 0xFF10131F.toInt()
        rect.set(cx + rx * 0.38f + drift - 1f * k, ey - ph, cx + rx * 0.38f + drift + 1f * k, ey + ph)
        canvas.drawOval(rect, paint)
        rect.set(cx + rx * 0.65f + drift - 1f * k, ey - ph, cx + rx * 0.65f + drift + 1f * k, ey + ph)
        canvas.drawOval(rect, paint)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLINK_DUR_S = 0.5f
    }
}
