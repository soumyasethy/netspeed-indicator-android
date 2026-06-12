package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Concept 7 — Wind turbine. Pastoral diorama: day-sky gradient, green hill,
 * two parallax clouds and nine swaying grass blades that all share the rotor's
 * wind value (ω = 0.02+sc*0.55 rad/frame) so the whole scene agrees on how
 * windy it is. Above sc 0.82 the blades swap INSTANTLY for a translucent
 * motion-blur disc — the threshold payoff, never faded. dark=true is the night
 * variant: silhouette turbine over a #0E1626 sky with a red aviation light
 * blinking at a constant rate (real aviation lights don't care about bandwidth).
 */
class TurbineScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val blade = Path()

    private var wheel = 0f

    // Day sky gradient — vertical, so rebuild only when the height changes.
    private var skyShader: LinearGradient? = null
    private var skyH = -1f

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = sceneScale(w, h)
        val sc = s.sc
        val night = s.dark

        paint.style = Paint.Style.FILL
        if (!s.transparentBg) {
            if (night) {
                paint.color = 0xFF0E1626.toInt()
                canvas.drawRect(0f, 0f, w, h, paint)
            } else {
                if (skyH != h) {
                    skyShader = LinearGradient(
                        0f, 0f, 0f, h,
                        0xFF9CC7EE.toInt(), 0xFFCFE6F7.toInt(), Shader.TileMode.CLAMP
                    )
                    skyH = h
                }
                paint.shader = skyShader
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null
            }
        }

        // Clouds — analytic parallax drift (same wind as the rotor), wrapping
        // over w+40 so they re-enter from the left like the gallery.
        val drift = s.timeS * 8f * (0.3f + sc * 2f) * k
        val span = w + 40f * k
        paint.color = if (night) argbWithAlpha(0xFF8FA3B8.toInt(), 0.12f)
        else argbWithAlpha(WHITE, 0.85f)
        for (i in 0 until 2) {
            val cx = (CLOUD_X[i] * w + drift) % span - 20f * k
            val cy = CLOUD_Y[i] * k
            val cr = CLOUD_R[i] * k
            rect.set(cx - cr, cy - cr * 0.34f, cx + cr, cy + cr * 0.34f)
            canvas.drawOval(rect, paint)
        }

        // Hill.
        paint.color = if (night) 0xFF16202B.toInt() else 0xFF7BA05B.toInt()
        rect.set(w * 0.5f - w * 0.8f, h + (30f - 44f) * k, w * 0.5f + w * 0.8f, h + (30f + 44f) * k)
        canvas.drawOval(rect, paint)

        // Grass — nine blades whose tip sway shares the rotor's wind value.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 1.6f * k
        paint.color = if (night) 0xFF22303B.toInt() else 0xFF5D8A4A.toInt()
        for (i in 0 until 9) {
            val gx = (14f + i * 22f) / 208f * w
            val sw = sin(s.timeS * 3f + i) * sc * 4f * k
            blade.rewind()
            blade.moveTo(gx, h)
            blade.quadTo(gx + sw, h - 7f * k, gx + sw * 1.6f, h - 11f * k)
            canvas.drawPath(blade, paint)
        }

        // Tower.
        val tx = w * 0.6f
        val ty = h * 0.56f
        val structure = if (night) SILHOUETTE else 0xFFE8EDF2.toInt()
        paint.strokeWidth = 4f * k
        paint.color = structure
        canvas.drawLine(tx, h, tx, ty, paint)

        // Rotor angle: gallery is rad/frame at 60 fps; integrate when animating,
        // derive analytically on static frames. Wrapped to keep float precision.
        val rate = (0.02f + sc * 0.55f) * 60f
        val angle: Float
        if (s.dtS > 0f) {
            wheel += rate * s.dtS
            if (wheel > TWO_PI) wheel -= TWO_PI
            angle = wheel
        } else {
            angle = s.timeS * rate
        }

        val r = 21f * k
        if (sc > 0.82f) {
            // Motion-blur disc — instant swap at the threshold, both directions.
            paint.style = Paint.Style.FILL
            paint.color = argbWithAlpha(structure, 0.4f)
            canvas.drawCircle(tx, ty, r, paint)
        } else {
            paint.strokeWidth = 3.4f * k
            paint.color = if (night) SILHOUETTE else 0xFFF5F8FA.toInt()
            for (b in 0 until 3) {
                val a = angle + b * 2.094f
                canvas.drawLine(tx, ty, tx + cos(a) * r, ty + sin(a) * r, paint)
            }
        }

        // Hub.
        paint.style = Paint.Style.FILL
        paint.color = if (night) SILHOUETTE else 0xFFCFD7DE.toInt()
        canvas.drawCircle(tx, ty, 3.4f * k, paint)

        // Night aviation light — constant 1.2 s blink, deliberately NOT
        // speed-driven.
        if (night && s.timeS % 1.2f < 0.12f) {
            paint.color = argbWithAlpha(RED, 0.3f)
            canvas.drawCircle(tx, ty - 5f * k, 3.5f * k, paint)
            paint.color = RED
            canvas.drawCircle(tx, ty - 5f * k, 1.5f * k, paint)
        }
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val SILHOUETTE = 0xFF2A3242.toInt()
        const val RED = 0xFFEF4444.toInt()
        const val TWO_PI = (PI * 2.0).toFloat()
        // Gallery cloud anchors [[30,14,16],[150,22,20]] in 208x70 ref space;
        // x as width fractions so spacing scales with wide canvases.
        val CLOUD_X = floatArrayOf(30f / 208f, 150f / 208f)
        val CLOUD_Y = floatArrayOf(14f, 22f)
        val CLOUD_R = floatArrayOf(16f, 20f)
    }
}
