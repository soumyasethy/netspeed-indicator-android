package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.sin

/**
 * Concept 9 — Lightning jar. Bottled electricity in a centered glass jar:
 * charge glow, arc spawn rate (0.04+sc*0.5 per 33 ms step), segment count and
 * side branching all rise with sc. Arcs are pooled (max 6) and replay a seeded
 * RNG so each keeps one jagged shape while fading. At Crawling a sad spark
 * paces the jar floor; tier-ups fire one bolt escaping through the cork.
 * Static frames draw the glow plus one analytic arc seeded from whole seconds.
 */
class JarScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val rng = SceneRng(11)      // spawn decisions + fresh arc seeds
    private val arcRng = SceneRng(11)   // replay-only, reset(seed) per arc draw

    private val arcSeed = IntArray(ARCS)
    private val arcAlpha = FloatArray(ARCS)
    private var stepAcc = 0f
    private var prevTier = -1
    private var boltAlpha = 0f
    private var boltSeed = 1

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = sceneScale(w, h)
        val sc = s.sc

        paint.style = Paint.Style.FILL
        if (!s.transparentBg) {
            paint.color = 0xFF0D1020.toInt()
            canvas.drawRect(0f, 0f, w, h, paint)
        }

        val jx = w / 2f
        val jt = h * 14f / 70f
        val jb = h * 62f / 70f
        val jw = 27f * k        // half-width: jar spans 54 ref units

        // Charge-level glow filling the glass.
        paint.color = argbWithAlpha(s.accentArgb, 0.06f + sc * 0.22f)
        rect.set(jx - jw, jt, jx + jw, jb)
        canvas.drawRoundRect(rect, 9f * k, 9f * k, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * k
        paint.color = GLASS
        canvas.drawRoundRect(rect, 9f * k, 9f * k, paint)
        paint.style = Paint.Style.FILL

        // Cork.
        paint.color = 0xFF8A6D4A.toInt()
        rect.set(jx - 10f * k, jt - 7f * k, jx + 10f * k, jt + 1f * k)
        canvas.drawRoundRect(rect, 2f * k, 2f * k, paint)

        var anyArc = false
        if (s.dtS > 0f) {
            // Pool stepping at the gallery's frame cadence: spawn probability
            // and the .09 alpha decay are per ~33 ms step, NOT per render call.
            stepAcc = min(stepAcc + s.dtS, 0.25f)   // bound catch-up after pauses
            while (stepAcc >= STEP_S) {
                stepAcc -= STEP_S
                if (rng.next() < 0.04f + sc * 0.5f) {
                    for (i in 0 until ARCS) {
                        if (arcAlpha[i] <= 0f) {
                            arcSeed[i] = (rng.next() * 999f).toInt()
                            arcAlpha[i] = 1f
                            break
                        }
                    }
                }
                for (i in 0 until ARCS) if (arcAlpha[i] > 0f) arcAlpha[i] -= 0.09f
                if (boltAlpha > 0f) boltAlpha -= 0.09f
            }

            // Tier-up: one arc escapes through the cork as a vertical bolt.
            if (prevTier in 0..3 && s.tier > prevTier) {
                boltAlpha = 1f
                boltSeed = (rng.next() * 999f).toInt()
            }
            prevTier = s.tier

            for (i in 0 until ARCS) {
                if (arcAlpha[i] <= 0f) continue
                anyArc = true
                drawArc(canvas, jx, jt, jb, jw, k, sc, arcAlpha[i], arcSeed[i])
            }
            if (boltAlpha > 0f) drawBolt(canvas, jx, jt, k, boltAlpha)
        } else {
            // Static frame: one analytic arc seeded from whole seconds so
            // widgets aren't an empty jar (likelier the faster we go).
            arcRng.reset(s.timeS.toInt() * 131 + 17)
            if (arcRng.next() < 0.3f + sc * 0.7f) {
                anyArc = true
                drawArc(canvas, jx, jt, jb, jw, k, sc, 0.8f, s.timeS.toInt() * 131 + 53)
            }
        }

        // Crawling: a single sad spark easing back and forth along the floor.
        if (s.tier == 0) {
            val sx = jx + sin(s.timeS * 1.3f) * (jw - 7f * k)
            val sy = jb - 4f * k
            paint.color = argbWithAlpha(ARC_BLUE, 0.25f)
            canvas.drawCircle(sx, sy, 3f * k, paint)
            paint.color = argbWithAlpha(ARC_BLUE, 0.9f)
            canvas.drawCircle(sx, sy, 1.3f * k, paint)
        }

        // Inner ambient glow on top — the glass "catches" live arcs (+.06).
        paint.color = argbWithAlpha(ARC_BLUE, 0.05f + sc * 0.12f + if (anyArc) 0.06f else 0f)
        rect.set(jx - jw + 3f * k, jt + 3f * k, jx + jw - 3f * k, jb - 3f * k)
        canvas.drawRoundRect(rect, 7f * k, 7f * k, paint)
    }

    /** Replays a fixed RNG sequence so the arc keeps one shape while fading. */
    private fun drawArc(
        canvas: Canvas, jx: Float, jt: Float, jb: Float, jw: Float,
        k: Float, sc: Float, alpha: Float, seed: Int
    ) {
        arcRng.reset(seed)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * k
        paint.strokeCap = Paint.Cap.ROUND   // hides drawLine polyline joints
        paint.color = argbWithAlpha(ARC_BLUE, alpha)
        var px = jx + (arcRng.next() - 0.5f) * 20f * k
        var py = jt + 6f * k
        val segs = 4 + (sc * 4f).toInt()
        val dy = (jb - jt - 14f * k) / segs
        for (i in 0 until segs) {
            val nx = (px + (arcRng.next() - 0.5f) * 26f * k)
                .coerceIn(jx - jw + 5f * k, jx + jw - 5f * k)   // stay inside glass
            val ny = py + dy
            canvas.drawLine(px, py, nx, ny, paint)
            px = nx
            py = ny
        }
        if (sc > 0.5f && arcRng.next() < sc) {   // thin side branch near the tip
            paint.strokeWidth = 0.8f * k
            canvas.drawLine(px, py - 12f * k, px + (arcRng.next() - 0.5f) * 22f * k, py - 2f * k, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    /** Tier-up celebration: a short jagged bolt rising from the cork top. */
    private fun drawBolt(canvas: Canvas, jx: Float, jt: Float, k: Float, alpha: Float) {
        arcRng.reset(boltSeed)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * k
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = argbWithAlpha(ARC_BLUE, alpha)
        var px = jx
        var py = jt - 7f * k
        val dy = (py - 2f * k) / BOLT_SEGS
        for (i in 0 until BOLT_SEGS) {
            val nx = jx + (arcRng.next() - 0.5f) * 10f * k
            val ny = py - dy
            canvas.drawLine(px, py, nx, ny, paint)
            px = nx
            py = ny
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private companion object {
        const val ARCS = 6
        const val BOLT_SEGS = 4
        const val STEP_S = 1f / 30f                    // gallery frame ≈ 33 ms
        const val GLASS = 0x80C8D7EB.toInt()           // rgba(200,215,235,.5)
        const val ARC_BLUE = 0xFFA0D2FF.toInt()        // rgba(160,210,255)
    }
}
