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
        // Over a transparent backdrop (bubble on the wallpaper) ink must be
        // white to survive any background; otherwise it pairs with the paper.
        val ink = if (s.transparentBg || s.dark) WHITE else INK

        paint.style = Paint.Style.FILL
        if (!s.transparentBg) {
            paint.color = if (s.dark) INK else PAPER
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        val cx = w * 0.62f
        val cy = h * 0.5f

        // Screentone — the halftone-dot shading every manga page carries. Two
        // corner fades keep the panel rich even when nothing else moves.
        paint.style = Paint.Style.FILL
        screentone(canvas, w, h, k, ink, s.timeS)

        if (s.dtS > 0f) {
            if (prevTier >= 0 && s.tier != prevTier) flashFrames = 2   // impact frame
            prevTier = s.tier
        }

        paint.style = Paint.Style.STROKE
        // Lazy ink sweep: seven long faint strokes slowly orbiting the dot.
        // Drawn at EVERY low speed (fading out by ~0.5 sc): the random burst
        // alone is statistically identical frame-to-frame at low line counts,
        // so without a coherent rotation underneath it reads as static.
        val sweepFade = (1f - sc * 2f).coerceIn(0f, 1f)
        if (sweepFade > 0.02f) {
            val base = s.timeS * 0.35f
            for (i in 0 until 7) {
                val a = base + i * (TWO_PI / 7f)
                val l0 = (16f + 3f * sin(s.timeS * 0.7f + i)) * k
                val l1 = (30f + 8f * sin(s.timeS * 0.5f + i * 2f)) * k
                paint.color = argbWithAlpha(ink, (0.10f + 0.05f * sin(s.timeS + i)) * sweepFade)
                paint.strokeWidth = 1f * k
                canvas.drawLine(
                    cx + cos(a) * l0, cy + sin(a) * l0 * 0.5f,
                    cx + cos(a) * (l0 + l1), cy + sin(a) * (l0 + l1) * 0.5f, paint,
                )
            }
        }
        if (s.tier == 0) {
            // Crawling: 3–4 droopy down-right strokes (the manga idiom for
            // pathetic) — sagging on a slow sine with breathing ink.
            rng.reset(CRAWL_SEED)
            val n = 3 + if (rng.next() < 0.5f) 0 else 1
            val breathe = 0.8f + 0.2f * sin(s.timeS * 0.9f)
            for (i in 0 until n) {
                val sag = sin(s.timeS * 1.1f + i * 1.9f) * 0.08f
                val a = 0.3f + rng.next() * 0.45f + sag    // ~17°–43° below horizontal
                val l0 = (13f + rng.next() * 6f) * k
                val len = (20f + rng.next() * 16f) * k
                paint.color = argbWithAlpha(ink, (0.45f + rng.next() * 0.25f) * breathe)
                paint.strokeWidth = (1.3f + rng.next() * 0.8f) * k
                val sx = cx + cos(a) * l0
                val sy = cy + sin(a) * l0 * 0.5f
                val mx = sx + cos(a) * len * 0.6f
                val my = sy + sin(a) * len * 0.6f
                // Second half bends further down — the droop.
                val ex = mx + cos(a + 0.6f + sag * 2f) * len * 0.4f
                val ey = my + sin(a + 0.6f + sag * 2f) * len * 0.4f
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

        // Focal dot; gently pulses at idle, shakes at 30 Hz near the ceiling
        // (burn the first draw — an LCG's first output is linear in the seed
        // and would creep, not jitter).
        var sh = 0f
        if (sc > 0.85f) {
            rng.reset(floor(s.timeS * 30f).toInt())
            rng.next()
            sh = (rng.next() - 0.5f) * 2.5f * k
        }
        val pulse = 1f + 0.09f * sin(s.timeS * 2.1f)
        val r = (9f + sc * 4f) * k * pulse
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

    /** Halftone shading fading out of the top-right and bottom-left corners. */
    private fun screentone(canvas: Canvas, w: Float, h: Float, k: Float, ink: Int, timeS: Float) {
        val step = 7f * k
        val r = 1.1f * k
        var row = 0
        var y = step / 2f
        while (y < h * 0.45f) {
            val reach = w * (0.30f - row * 0.045f)
            if (reach <= 0f) break
            var x = w - step / 2f - (row % 2) * step / 2f
            while (x > w - reach) {
                paint.color = argbWithAlpha(ink, 0.08f + 0.05f * sin(timeS * 1.3f + row))
                canvas.drawCircle(x, y, r, paint)
                x -= step
            }
            // Mirror dot on the bottom-left fade.
            var x2 = step / 2f + (row % 2) * step / 2f
            while (x2 < reach * 0.8f) {
                paint.color = argbWithAlpha(ink, 0.08f)
                canvas.drawCircle(x2, h - y, r, paint)
                x2 += step
            }
            row++
            y += step
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
