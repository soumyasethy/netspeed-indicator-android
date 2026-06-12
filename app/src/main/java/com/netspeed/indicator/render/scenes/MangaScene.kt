package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Concept 3 — Manga speed lines. Comic-panel paper (light #F5F2EA / dark
 * #1A1D28) with a radial burst of inked action lines around a focal dot at
 * (62% w, 50% h). Speed maps to line count (4..38), reach and ink weight; the
 * rng is reseeded at 12 Hz so lines FLICKER like hand-redrawn frames — they
 * never rotate smoothly, the jitter IS the style. Above sc 0.85 the dot shakes
 * ±2.5; Crawling collapses to 3–4 droopy down-right strokes (the manga idiom
 * for pathetic); tier changes drop a 2-frame 12% white impact flash.
 */
class MangaScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rng = SceneRng(CRAWL_SEED)

    private var prevTier = -1
    private var flashFrames = 0

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = sceneScale(w, h)
        val sc = s.sc
        val ink = if (s.dark) WHITE else INK   // INK doubles as the dark paper

        paint.style = Paint.Style.FILL
        if (!s.transparentBg) {
            paint.color = if (s.dark) INK else PAPER
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        val cx = w * 0.62f
        val cy = h * 0.5f

        if (s.dtS > 0f) {
            if (prevTier >= 0 && s.tier != prevTier) flashFrames = 2   // impact frame
            prevTier = s.tier
        }

        paint.style = Paint.Style.STROKE
        if (s.mbps < 1f) {
            // Crawling: exactly 3–4 short droopy down-right strokes from a FIXED
            // seed — a calm, pathetic page with zero flicker.
            rng.reset(CRAWL_SEED)
            val n = 3 + if (rng.next() < 0.5f) 0 else 1
            for (i in 0 until n) {
                val a = 0.3f + rng.next() * 0.45f          // ~17°–43° below horizontal
                val l0 = (13f + rng.next() * 6f) * k
                val len = (14f + rng.next() * 12f) * k
                paint.color = argbWithAlpha(ink, 0.3f + rng.next() * 0.25f)
                paint.strokeWidth = (1f + rng.next() * 0.8f) * k
                val sx = cx + cos(a) * l0
                val sy = cy + sin(a) * l0 * 0.5f
                val mx = sx + cos(a) * len * 0.6f
                val my = sy + sin(a) * len * 0.6f
                // Second half bends further down — the droop.
                val ex = mx + cos(a + 0.6f) * len * 0.4f
                val ey = my + sin(a + 0.6f) * len * 0.4f
                canvas.drawLine(sx, sy, mx, my, paint)
                canvas.drawLine(mx, my, ex, ey, paint)
            }
        } else {
            // Burst reseeded at 12 Hz: per-line draw order matches the gallery
            // (angle, extension, start, alpha, width) for identical statistics.
            rng.reset(floor(s.timeS * 12f).toInt())
            val n = (4f + sc * 34f).toInt()
            for (i in 0 until n) {
                val a = rng.next() * TWO_PI
                val l1 = (26f + rng.next() * 60f) * k
                val l0 = (14f + rng.next() * 10f) * k
                paint.color = argbWithAlpha(ink, 0.25f + rng.next() * 0.5f)
                paint.strokeWidth = (0.8f + rng.next() * 1.6f) * k
                val ca = cos(a)
                val sa = sin(a) * 0.5f                     // vertical squash → horizontal burst
                canvas.drawLine(cx + ca * l0, cy + sa * l0,
                    cx + ca * (l0 + l1), cy + sa * (l0 + l1), paint)
            }
        }

        // Focal dot; shakes at 30 Hz near the ceiling (burn the first draw —
        // an LCG's first output is linear in the seed and would creep, not jitter).
        var sh = 0f
        if (sc > 0.85f) {
            rng.reset(floor(s.timeS * 30f).toInt())
            rng.next()
            sh = (rng.next() - 0.5f) * 2.5f * k
        }
        val r = (9f + sc * 4f) * k
        paint.style = Paint.Style.FILL
        paint.color = s.accentArgb
        canvas.drawCircle(cx + sh, cy + sh, r, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * k
        paint.color = ink
        canvas.drawCircle(cx + sh, cy + sh, r, paint)

        // Tier-change impact flash: 12% white, exactly two animated frames.
        if (flashFrames > 0) {
            paint.style = Paint.Style.FILL
            paint.color = argbWithAlpha(WHITE, 0.12f)
            canvas.drawRect(0f, 0f, w, h, paint)
            if (s.dtS > 0f) flashFrames--
        }
    }

    private companion object {
        const val CRAWL_SEED = 11                    // gallery init seed, reused for the fixed droop
        const val TWO_PI = 6.2831855f
        const val WHITE = 0xFFFFFFFF.toInt()
        const val INK = 0xFF1A1D28.toInt()
        const val PAPER = 0xFFF5F2EA.toInt()
    }
}
