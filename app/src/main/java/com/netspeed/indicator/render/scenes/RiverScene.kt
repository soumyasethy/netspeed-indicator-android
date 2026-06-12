package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.sin

/**
 * Concept 4 — Data river. Three faint lanes carry a 26-packet pool left→right
 * at 0.4+sc*4.2 px/frame; a traveling sine "jam wave" subtracts velocity at low
 * speed so packets visibly clump and stutter — the congestion IS the diagnosis.
 * Below sc 0.08, 30% of respawns are red dropped packets that vanish mid-lane.
 * Tier change pulses every packet +1dp for a beat.
 */
class RiverScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rng = SceneRng(3)
    private val flickRng = SceneRng(1)

    private val x = FloatArray(PACKETS)
    private val xFrac = FloatArray(PACKETS)
    private val lane = IntArray(PACKETS)
    private val jamOffset = FloatArray(PACKETS)
    private val bad = BooleanArray(PACKETS)

    private var seeded = false
    private var prevTier = -1
    private var pulse = 0f

    init {
        val r = SceneRng(3)
        for (i in 0 until PACKETS) {
            xFrac[i] = r.next()
            lane[i] = (r.next() * 3f).toInt().coerceIn(0, 2)
            jamOffset[i] = r.next() * 6f
        }
    }

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = h / 70f
        val sc = s.sc

        paint.style = Paint.Style.FILL
        paint.color = 0xFF0E1626.toInt()
        canvas.drawRect(0f, 0f, w, h, paint)

        // Three lane strips: 10-wide white 6% lines at y 18/35/52 in ref space.
        paint.color = 0x0FFFFFFF
        for (l in 0 until 3) {
            val yc = (18f + l * 17f) * k
            canvas.drawRect(0f, yc - 5f * k, w, yc + 5f * k, paint)
        }

        // Tier-change pulse: packets briefly grow +1dp, ~125 ms decay.
        if (s.dtS > 0f) {
            if (prevTier >= 0 && s.tier != prevTier) pulse = 1f
            prevTier = s.tier
            pulse = (pulse - 8f * s.dtS).coerceAtLeast(0f)
            if (!seeded) {
                for (i in 0 until PACKETS) x[i] = xFrac[i] * w
                seeded = true
            }
        }

        val grow = pulse * 0.5f * k
        for (i in 0 until PACKETS) {
            val px: Float
            val isBad: Boolean
            if (s.dtS > 0f) {
                // Jam wave keyed on ref-space x so the clump pattern is scale-free.
                val jam = (1f - sc) * max(0f, sin(x[i] / k * 0.08f + jamOffset[i]))
                val v = (0.4f + sc * 4.2f - jam * (0.35f - sc * 0.3f)) * 60f * k
                x[i] += v * s.dtS
                if (x[i] > w + 6f * k) {
                    x[i] = -6f * k
                    bad[i] = sc < 0.08f && rng.next() < 0.3f
                }
                px = x[i]
                isBad = bad[i]
            } else {
                // Static frame: analytic slot position at the jam-free mean velocity.
                val span = w + 12f * k
                val vAvg = (0.4f + sc * 4.2f) * 60f * k
                px = ((jamOffset[i] * w + s.timeS * vAvg) % span + span) % span - 6f * k
                flickRng.reset(i * 131 + s.timeS.toInt() * 7 + 1)
                isBad = sc < 0.08f && flickRng.next() < 0.3f
            }

            val yc = (18f + lane[i] * 17f) * k
            paint.color = if (isBad) argbWithAlpha(0xFFEF4444.toInt(), 0.9f)
            else argbWithAlpha(s.accentArgb, 0.9f)
            rect.set(px - 4f * k - grow, yc - 3.5f * k - grow, px + 4f * k + grow, yc + 3.5f * k + grow)
            canvas.drawRoundRect(rect, 2f * k, 2f * k, paint)

            // Dropped packets vanish mid-lane: random early respawn (~3/s odds).
            if (s.dtS > 0f && isBad && rng.next() < 0.05f * s.dtS * 60f) x[i] = w + 7f * k
        }
    }

    private companion object {
        const val PACKETS = 26
    }
}
