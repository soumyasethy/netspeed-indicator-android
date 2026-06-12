package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Concept 5 — Firefly swarm. Night gradient over a dark ground ellipse; 14
 * amber fireflies whose energy e = sc drives everything: wander radius
 * 8+e*46, wander rate 0.3+e*1.4 rad/s, blink rate 0.8+e*3 rad/s and glow
 * brightness .35+e*.6. At e≈0 they settle onto the ground line (staggered
 * heights) and blink slowly in place; lift-off is lerped by min(1, e*4) so
 * takeoff happens early. Per-firefly phase offsets on position AND blink keep
 * the swarm desynchronized. Fully analytic from timeS — static frames free.
 */
class FireflyScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint()
    private val rect = RectF()

    // Per-firefly phase offsets: a = wander x, b = wander y, p = blink.
    private val phaseA = FloatArray(COUNT)
    private val phaseB = FloatArray(COUNT)
    private val phaseP = FloatArray(COUNT)

    // Night-sky gradient shader, cached and rebuilt only when the height changes.
    private var gradH = -1f

    init {
        val r = SceneRng(5)
        for (i in 0 until COUNT) {
            phaseA[i] = r.next() * 6f
            phaseB[i] = r.next() * 6f
            phaseP[i] = r.next() * 6f
        }
    }

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = sceneScale(w, h)
        val e = s.sc
        val t = s.timeS

        if (!s.transparentBg) {
            if (gradH != h) {
                bgPaint.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    0xFF0D1B2A.toInt(), 0xFF16283C.toInt(), Shader.TileMode.CLAMP
                )
                gradH = h
            }
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            // Ground ellipse hugging the bottom edge.
            paint.color = 0xFF0A1420.toInt()
            val gy = h + 14f * k
            rect.set(w / 2f - w * 0.7f, gy - 22f * k, w / 2f + w * 0.7f, gy + 22f * k)
            canvas.drawOval(rect, paint)
        }

        val wanderX = (8f + e * 46f) * k * 1.6f
        val wanderY = (8f + e * 22f) * k
        val lift = min(1f, e * 4f)
        val coreR = (1.6f + e * 1.2f) * k
        val haloR = (5f + e * 4f) * k
        val coreA = 0.35f + e * 0.6f

        for (i in 0 until COUNT) {
            val fx = w / 2f + sin(t * (0.3f + e * 1.4f) + phaseA[i]) * wanderX
            val restY = h - 12f * k - (i % 3) * 3f * k
            val flyY = h / 2f + cos(t * (0.4f + e * 1.6f) + phaseB[i]) * wanderY
            val fy = restY + (flyY - restY) * lift
            val bl = 0.3f + 0.7f * abs(sin(t * (0.8f + e * 3f) + phaseP[i]))

            paint.color = argbWithAlpha(AMBER, bl * coreA)
            canvas.drawCircle(fx, fy, coreR, paint)
            paint.color = argbWithAlpha(AMBER, bl * 0.12f)
            canvas.drawCircle(fx, fy, haloR, paint)
        }
    }

    private companion object {
        const val COUNT = 14
        const val AMBER = 0xFFFCD34D.toInt()
    }
}
