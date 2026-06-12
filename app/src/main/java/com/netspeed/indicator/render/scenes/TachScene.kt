package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Concept 10 — Tachometer + gear shift. WITHIN-tier mapping: the RPM bar fills
 * with [SceneState.tierFrac] (exact per user tier bounds) and RESETS on shift;
 * the gear box shows tier+1. Tier changes kick: gear box scales 1+kick*0.5 and
 * lit segments flash (white up, amber down). Blazing strobes segments 17–20 at
 * 4 Hz. Carbon-dark backdrop with faint diagonal texture.
 */
class TachScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = 0xFFFFFFFF.toInt()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0x80FFFFFF.toInt()
    }
    private val rect = RectF()

    private var kick = 0f
    private var kickDown = false
    private var prevTier = -1

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = h / 70f

        if (!s.transparentBg) {
            paint.style = Paint.Style.FILL
            paint.color = 0xFF101218.toInt()
            canvas.drawRect(0f, 0f, w, h, paint)

            // Faint diagonal carbon texture.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * k
            paint.color = 0x0DFFFFFF
            var tx = 0f
            while (tx < w) {
                canvas.drawLine(tx, 0f, tx + 20f * k, h, paint)
                tx += 40f * k
            }
        }
        paint.style = Paint.Style.FILL

        // Shift kick (white up, amber down), ~330 ms decay.
        if (s.dtS > 0f) {
            if (prevTier >= 0 && s.tier != prevTier) {
                kick = 1f
                kickDown = s.tier < prevTier
            }
            prevTier = s.tier
            kick = (kick - 3f * s.dtS).coerceAtLeast(0f)
        }

        // RPM bar — 20 segments, frac is exact within the user's tier bounds.
        val frac = s.tierFrac
        val bx = 14f * k
        val bw = (w - 92f * k - bx) / SEGS
        val strobeOff = s.tier == 4 && ((s.timeS * 4f).toInt() and 1) == 1
        for (i in 0 until SEGS) {
            val on = i / SEGS.toFloat() < frac
            var color = when {
                !on -> 0x14FFFFFF
                i < 12 -> 0xFF22C55E.toInt()
                i < 16 -> 0xFFFBBF24.toInt()
                else -> 0xFFEF4444.toInt()
            }
            if (on && kick > 0f) {
                color = if (kickDown) argbWithAlpha(0xFFFBBF24.toInt(), 0.4f + kick * 0.6f)
                else argbWithAlpha(0xFFFFFFFF.toInt(), 0.4f + kick * 0.6f)
            }
            if (on && i >= 16 && strobeOff) color = argbWithAlpha(0xFFEF4444.toInt(), 0.25f)
            paint.color = color
            val left = bx + i * bw
            val top = (26f - i * 0.5f) * k
            rect.set(left, top, left + bw - 2f * k, top + (18f + i) * k)
            canvas.drawRoundRect(rect, 2f * k, 2f * k, paint)
        }

        // Gear box, right side, kick-scaled.
        val gs = 1f + kick * 0.5f
        canvas.save()
        canvas.translate(w - 46f * k, h / 2f)
        canvas.scale(gs, gs)
        rect.set(-20f * k, -22f * k, 20f * k, 22f * k)
        paint.color = 0x0FFFFFFF
        canvas.drawRoundRect(rect, 9f * k, 9f * k, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * k
        paint.color = s.accentArgb
        canvas.drawRoundRect(rect, 9f * k, 9f * k, paint)
        paint.style = Paint.Style.FILL
        digitPaint.textSize = 24f * k
        val fm = digitPaint.fontMetrics
        canvas.drawText(GEARS[s.tier.coerceIn(0, 4)], 0f, 1f * k - (fm.ascent + fm.descent) / 2f, digitPaint)
        labelPaint.textSize = 8f * k
        canvas.drawText("GEAR", 0f, 18f * k, labelPaint)
        canvas.restore()

        // RPM caption.
        labelPaint.textAlign = Paint.Align.LEFT
        labelPaint.textSize = 8f * k
        canvas.drawText("RPM", bx, 16f * k, labelPaint)
        labelPaint.textAlign = Paint.Align.CENTER
    }

    private companion object {
        const val SEGS = 20
        val GEARS = arrayOf("1", "2", "3", "4", "5")
    }
}
