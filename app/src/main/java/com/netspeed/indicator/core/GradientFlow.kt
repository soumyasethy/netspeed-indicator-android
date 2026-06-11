package com.netspeed.indicator.core

import android.graphics.LinearGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The "gemini" flowing-gradient engine: a wrapped palette
 * (`linear-gradient(120deg, c1..cn, c1)`) tiled along the 120° axis and translated
 * at CONSTANT velocity, wrapping seamlessly each period.
 *
 * Why constant velocity (not the CSS ping-pong): the colour bands drift in one
 * direction forever with zero acceleration — the motion-graphic feel. A ping-pong
 * pan surges mid-cycle and reverses, which reads as jerky, especially on the
 * once-per-second surfaces where each update is a discrete step.
 *
 * Two cadences, one look:
 *  - the in-app hero animates per display frame ([HERO_PERIOD_S]);
 *  - the notification card and widgets re-render once per second from the service
 *    tick ([STEPPED_PERIOD_MS] is long, so each step nudges the pattern ~2.8% —
 *    it reads as continuous drift, never a jump).
 */
object GradientFlow {

    /** Flow period for frame-driven surfaces (the in-app hero, 60–120 fps). */
    const val HERO_PERIOD_S = 12f

    /** Flow period for once-per-second surfaces (notification card, widgets). */
    const val STEPPED_PERIOD_MS = 36_000L

    /** Reference gradient axis (CSS 120deg = 30° below +x, flowing right-down). */
    private const val ANGLE_DEG = 30.0

    /** Wall-time → cycle phase in [0, 1). */
    fun phase(nowMs: Long, periodMs: Long = STEPPED_PERIOD_MS): Float =
        (nowMs % periodMs) / periodMs.toFloat()

    /** Appends the first colour so the tiled gradient loops seamlessly (…,cn,c1). */
    fun wrapped(colors: IntArray): IntArray = colors + colors.first()

    /**
     * The flowing gradient as a shader for a w×h surface. One palette tile spans
     * 2× the diagonal (stretched bands, no visible repeats on screen) and the
     * whole pattern is translated by `phase × tile` along the axis — REPEAT tiling
     * plus the wrapped stop list make the phase 1→0 wrap pixel-identical.
     */
    fun shader(w: Float, h: Float, colors: IntArray, phaseValue: Float): Shader {
        val stops = if (colors.size >= 2) wrapped(colors)
        else intArrayOf(0xFF2563EB.toInt(), 0xFF7C3AED.toInt(), 0xFF2563EB.toInt())
        val rad = Math.toRadians(ANGLE_DEG)
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val tile = 2f * sqrt(w * w + h * h)
        val offset = (phaseValue.mod(1f)) * tile
        val x0 = -offset * dx
        val y0 = -offset * dy
        return LinearGradient(
            x0, y0, x0 + dx * tile, y0 + dy * tile,
            stops, null, Shader.TileMode.REPEAT,
        )
    }
}
