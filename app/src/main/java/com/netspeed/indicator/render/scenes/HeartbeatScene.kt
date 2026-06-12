package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.sin

/**
 * Concept 2 — Heartbeat orb. Flat #10131F backdrop, centered accent orb pulsing
 * at 30→180 BPM with sc. The systolic kick is sin(phase) clamped ≥0 raised to
 * the 6th power (sharp attack, soft decay); every beat emits one expanding ring
 * from a fixed pool of 8. Below 1 MB/s the pulse turns faint AND irregular — a
 * weak heart — via a per-beat interval factor in [0.9, 1.4] from the seeded RNG.
 */
class HeartbeatScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rng = SceneRng(2)

    private var phase = 0f
    private var intervalFactor = 1f

    private val ringR = FloatArray(RINGS)
    private val ringA = FloatArray(RINGS)

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = sceneScale(w, h)
        val sc = s.sc
        val cx = w / 2f
        val cy = h / 2f

        paint.style = Paint.Style.FILL
        if (!s.transparentBg) {
            paint.color = 0xFF10131F.toInt()
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        val crawling = s.tier == 0
        val faint = if (crawling) 0.45f else 1f
        val bpm = 30f + sc * 150f
        var rate = bpm / 60f * TWO_PI                 // rad/sec
        if (crawling) rate /= intervalFactor          // stretched, uneven beats

        // Gallery ring growth/fade are px-per-frame at 60 fps in 208x70 ref space.
        val grow = (1.4f + sc * 1.6f) * 60f * k
        val fade = 0.012f * 60f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * k
        if (s.dtS > 0f) {
            phase += rate * s.dtS
            if (phase >= TWO_PI) {                    // floor(phase/2π) advanced = beat
                phase %= TWO_PI
                spawnRing(faint, k)
                intervalFactor = 0.9f + rng.next() * 0.5f
            }
            for (i in 0 until RINGS) {
                if (ringA[i] <= 0f) continue
                ringR[i] += grow * s.dtS
                ringA[i] -= fade * s.dtS
                if (ringA[i] <= 0f) continue
                paint.color = argbWithAlpha(s.accentArgb, ringA[i])
                canvas.drawCircle(cx, cy, ringR[i], paint)
            }
        } else {
            // Static frame: analytic phase, ring train at regular beat spacing
            // (the irregular per-beat factor is stateful, so it only animates).
            phase = (s.timeS * rate) % TWO_PI
            val period = TWO_PI / rate
            var age = phase / rate                    // seconds since last beat
            for (i in 0 until RINGS) {
                val a = 0.55f * faint - fade * age
                if (a <= 0f) break
                paint.color = argbWithAlpha(s.accentArgb, a)
                canvas.drawCircle(cx, cy, 12f * k + grow * age, paint)
                age += period
            }
        }
        paint.style = Paint.Style.FILL

        // Systolic kick: sin^6 multiplied out (b2*b2*b2), no Math.pow.
        val b = sin(phase).coerceAtLeast(0f)
        val b2 = b * b
        val scale = 1f + b2 * b2 * b2 * 0.28f * faint

        paint.color = argbWithAlpha(s.accentArgb, 0.18f)
        canvas.drawCircle(cx, cy, (17f * scale + 6f) * k, paint)
        paint.color = argbWithAlpha(s.accentArgb, 0.95f)
        canvas.drawCircle(cx, cy, 13f * scale * k, paint)

        // Specular highlight, upper-left, scaling with the orb.
        paint.color = argbWithAlpha(WHITE, 0.35f)
        canvas.drawCircle(cx - 4f * k, cy - 5f * k, 4f * scale * k, paint)
    }

    /** Reuse a dead slot if any, else the oldest (lowest-alpha) ring. */
    private fun spawnRing(faint: Float, k: Float) {
        var slot = 0
        var minA = Float.MAX_VALUE
        for (i in 0 until RINGS) {
            if (ringA[i] <= 0f) { slot = i; break }
            if (ringA[i] < minA) { minA = ringA[i]; slot = i }
        }
        ringR[slot] = 12f * k
        ringA[slot] = 0.55f * faint
    }

    private companion object {
        const val RINGS = 8
        const val TWO_PI = 6.2831855f
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
