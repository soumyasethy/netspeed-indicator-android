package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.sin

/**
 * Concept 1 — Comet trail. Space backdrop, 18 twinkling stars, a comet crossing
 * left→right on a sine path that straightens as speed rises. Trail length, halo
 * and velocity all derive from sc. Sputters (lurching velocity) below 1 MB/s;
 * tier-ups burst 6 sparks; entering Blazing leaves a 2 s white sonic streak.
 */
class CometScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rng = SceneRng(7)

    private val starX = FloatArray(STARS)
    private val starY = FloatArray(STARS)
    private val starP = FloatArray(STARS)

    private var x = 0f
    private var hasX = false
    private var gapS = 0f
    private var prevTier = -1
    private var streakS = 0f

    private val sparkX = FloatArray(SPARKS)
    private val sparkY = FloatArray(SPARKS)
    private val sparkVX = FloatArray(SPARKS)
    private val sparkVY = FloatArray(SPARKS)
    private val sparkLife = FloatArray(SPARKS)

    init {
        val r = SceneRng(7)
        for (i in 0 until STARS) {
            starX[i] = r.next()
            starY[i] = r.next()
            starP[i] = r.next() * 6f
        }
    }

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = h / 70f
        val sc = s.sc

        paint.style = Paint.Style.FILL
        if (!s.transparentBg) {
            paint.color = 0xFF0B1026.toInt()
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        for (i in 0 until STARS) {
            val a = 0.25f + 0.35f * abs(sin(s.timeS * 2f + starP[i]))
            paint.color = argbWithAlpha(WHITE, a)
            val sx = starX[i] * w
            val sy = starY[i] * h
            canvas.drawRect(sx, sy, sx + 1.4f * k, sy + 1.4f * k, paint)
        }

        // Velocity: gallery is px/frame at 60 fps in 208x70 ref space.
        var vel = (1f + sc * 5.5f) * 60f * k
        if (s.mbps < 1f) vel *= 0.6f + 0.4f * sin(s.timeS * 3f)   // Crawling sputter

        val margin = 30f * k
        if (s.dtS > 0f) {
            if (!hasX) { x = -margin; hasX = true }
            if (gapS > 0f) {
                gapS -= s.dtS
            } else {
                x += vel * s.dtS
                if (x > w + margin) {
                    x = -margin
                    gapS = rng.next() * 0.4f      // random 0–400 ms re-entry gap
                }
            }
        } else {
            // Static frame: analytic position, no state stepping.
            val span = w + 2f * margin
            x = ((s.timeS * vel) % span + span) % span - margin
        }

        val wave = 12f * k * (1f - sc * 0.6f)
        val y = pathY(x, h, k, wave)

        // Blazing sonic streak (thin white line tracing the recent path).
        if (s.dtS > 0f) {
            if (s.tier == 4 && prevTier in 0..3) streakS = 2f
            if (streakS > 0f) streakS -= s.dtS
        }
        if (streakS > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * k
            paint.color = argbWithAlpha(WHITE, 0.5f * (streakS / 2f))
            var px = x - 70f * k
            var py = pathY(px, h, k, wave)
            while (px < x) {
                val nx = px + 6f * k
                val ny = pathY(nx, h, k, wave)
                canvas.drawLine(px, py, nx, ny, paint)
                px = nx
                py = ny
            }
            paint.style = Paint.Style.FILL
        }

        // Trail — analytic from the path function, zero state.
        val tl = (4 + sc * 22f).toInt().coerceAtLeast(1)
        val spacing = (2f + sc * 3f) * k
        for (i in tl downTo 1) {
            val px = x - i * spacing
            val py = pathY(px, h, k, wave)
            val f = 1f - i / tl.toFloat()
            paint.color = argbWithAlpha(s.accentArgb, f * 0.5f)
            canvas.drawCircle(px, py, f * 4f * k + 0.5f * k, paint)
        }

        // Head + halo.
        paint.color = WHITE
        canvas.drawCircle(x, y, (3.4f + sc * 1.5f) * k, paint)
        paint.color = argbWithAlpha(s.accentArgb, 0.25f)
        canvas.drawCircle(x, y, (7f + sc * 5f) * k, paint)

        // Tier-up sparks.
        if (s.dtS > 0f) {
            if (prevTier in 0..3 && s.tier > prevTier) {
                for (i in 0 until SPARKS) {
                    sparkX[i] = x
                    sparkY[i] = y
                    sparkVX[i] = (rng.next() - 0.5f) * 120f * k
                    sparkVY[i] = (rng.next() - 0.5f) * 120f * k
                    sparkLife[i] = 1f
                }
            }
            for (i in 0 until SPARKS) {
                if (sparkLife[i] <= 0f) continue
                sparkLife[i] -= s.dtS * 2f
                sparkX[i] += sparkVX[i] * s.dtS
                sparkY[i] += sparkVY[i] * s.dtS
                if (sparkLife[i] > 0f) {
                    paint.color = argbWithAlpha(s.accentArgb, sparkLife[i])
                    canvas.drawCircle(sparkX[i], sparkY[i], 1.5f * k, paint)
                }
            }
            prevTier = s.tier
        }
    }

    private fun pathY(px: Float, h: Float, k: Float, wave: Float): Float =
        h * 0.5f + sin(px / k * 0.04f) * wave

    private companion object {
        const val STARS = 18
        const val SPARKS = 6
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
