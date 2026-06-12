package com.netspeed.indicator.render.scenes

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Concept 11 — Journey. Speed is altitude: one continuous world climbing from a
 * dawn-gray snail crawl through field (bicycle), day road (car) and open sky
 * (plane) into space (rocket). Sky/ground palettes blend with
 * [SceneState.tierProgress] (never snap); the vehicle follows the committed
 * [SceneState.tier] and swaps with a ~370 ms squash → pop-overshoot plus an
 * expanding ring. Wind streaks, drifting clouds, the receding road and exhaust
 * puffs all share sc; the camera shakes past tierProgress 3.55. The vehicle
 * rides left-of-center so host text over the right third stays readable.
 */
class JourneyScene : SpeedScene {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val rect = RectF()
    private val rng = SceneRng(23)            // free-running: puff spawns
    private val flickerRng = SceneRng(1)      // reset with floor(timeS*30) salts: shake, flame

    private val starX = FloatArray(STARS)
    private val starY = FloatArray(STARS)
    private val starR = FloatArray(STARS)
    private val starP = FloatArray(STARS)

    private val cloudX0 = FloatArray(CLOUDS)
    private val cloudY = FloatArray(CLOUDS)
    private val cloudR = FloatArray(CLOUDS)
    private val cloudX = FloatArray(CLOUDS)
    private var cloudInit = false

    private val streakY = FloatArray(STREAKS)
    private val streakO = FloatArray(STREAKS)
    private val streakL = FloatArray(STREAKS)
    private val streakA = FloatArray(STREAKS)

    private val puffX = FloatArray(PUFFS)
    private val puffY = FloatArray(PUFFS)
    private val puffR = FloatArray(PUFFS)
    private val puffA = FloatArray(PUFFS)
    private val puffAmber = BooleanArray(PUFFS)

    private var wheel = 0f
    private var prevTier = -1
    private var fromTier = 0
    private var swapP = 1f

    init {
        val r = SceneRng(23)
        for (i in 0 until STARS) {
            starX[i] = r.next()
            starY[i] = r.next()
            starR[i] = 0.6f + r.next() * 1.1f
            starP[i] = r.next() * 6f
        }
        for (i in 0 until CLOUDS) {
            cloudX0[i] = r.next()
            cloudY[i] = 8f + r.next() * 30f
            cloudR[i] = 11f + r.next() * 9f
        }
        for (i in 0 until STREAKS) {
            streakY[i] = 6f + r.next() * 58f
            streakO[i] = r.next()
            streakL[i] = 10f + r.next() * 20f
            streakA[i] = 0.12f + r.next() * 0.22f
        }
    }

    override fun render(canvas: Canvas, w: Float, h: Float, s: SceneState) {
        val k = h / 70f
        val sc = s.sc
        val tb = s.tierProgress.coerceIn(0f, 4f)
        val t = s.timeS

        // Environment palette: piecewise lerp between tier palettes — env never snaps.
        val ei = min(3, tb.toInt())
        val ef = tb - ei
        val topC = lerpArgb(TOPS[ei], TOPS[ei + 1], ef)
        val botC = lerpArgb(BOTS[ei], BOTS[ei + 1], ef)
        val gndC = if (ei >= 2) GNDS[2] else lerpArgb(GNDS[ei], GNDS[ei + 1], ef)

        // Sky: banded vertical gradient (colors move with tb, so no cached shader).
        paint.style = Paint.Style.FILL
        if (!s.transparentBg) {
            for (i in 0 until BANDS) {
                paint.color = lerpArgb(topC, botC, (i + 0.5f) / BANDS)
                canvas.drawRect(0f, h * i / BANDS, w, h * (i + 1) / BANDS, paint)
            }
        }

        // Camera shake during the rocket climb.
        canvas.save()
        if (tb > 3.55f) {
            flickerRng.reset((t * 30f).toInt() * 7 + 1)
            val mag = (tb - 3.55f) * 4f * k
            canvas.translate(
                (flickerRng.next() - 0.5f) * 2f * mag,
                (flickerRng.next() - 0.5f) * 2f * mag
            )
        }

        // Stars fade in above the atmosphere (tb 3→4).
        val starBase = (tb - 3f).coerceIn(0f, 1f)
        if (starBase > 0f) {
            for (i in 0 until STARS) {
                val a = starBase * (0.4f + 0.5f * abs(sin(t * 2f + starP[i])))
                paint.color = argbWithAlpha(WHITE, a)
                val sx = starX[i] * w
                val sy = starY[i] * h
                val sr = starR[i] * 2f * k
                canvas.drawRect(sx, sy, sx + sr, sy + sr, paint)
            }
        }

        // Clouds: strongest around the plane tier, a hint near the fast road.
        val cA = (1f - abs(tb - 3f)).coerceAtLeast(0f) * 0.9f +
            (1f - abs(tb - 2.4f)).coerceAtLeast(0f) * 0.25f
        val cSpan = w + 50f * k
        val cVel = (0.4f + sc * 2.4f) * 60f * k
        if (s.dtS > 0f) {
            if (!cloudInit) {
                for (i in 0 until CLOUDS) cloudX[i] = cloudX0[i] * cSpan - 30f * k
                cloudInit = true
            }
            for (i in 0 until CLOUDS) {
                cloudX[i] -= cVel * s.dtS
                if (cloudX[i] < -30f * k) cloudX[i] += cSpan
            }
        } else {
            // Static frame: analytic drift, no state stepping.
            for (i in 0 until CLOUDS) {
                cloudX[i] = w + 20f * k - ((cloudX0[i] * cSpan + t * cVel) % cSpan)
            }
        }
        if (cA > 0.01f) {
            paint.color = argbWithAlpha(WHITE, 0.8f * cA)
            for (i in 0 until CLOUDS) {
                val rx = cloudR[i] * k
                rect.set(cloudX[i] - rx, cloudY[i] * k - rx * 0.36f, cloudX[i] + rx, cloudY[i] * k + rx * 0.36f)
                canvas.drawOval(rect, paint)
            }
        }

        // Ground recedes downward and fades out as the journey lifts off (gone by tb 3).
        val gA = (3f - tb).coerceIn(0f, 1f)
        val gy = h * 0.74f + (tb - 2f).coerceAtLeast(0f) * h * 0.5f
        if (gA > 0f && gy < h) {
            paint.color = argbWithAlpha(gndC, gA)
            canvas.drawRect(0f, gy, w, h, paint)
            val dy = gy + 7f * k
            if (dy < h) {
                // Road dashes: manual 9/11 dash loop scrolling left with speed.
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * k
                paint.color = argbWithAlpha(WHITE, 0.4f * gA)
                val period = 20f * k
                var dx = -((t * 40f * (0.2f + sc * 2f) * k) % period)
                while (dx < w) {
                    canvas.drawLine(dx, dy, dx + 9f * k, dy, paint)
                    dx += period
                }
                paint.style = Paint.Style.FILL
            }
        }

        // Wind streaks: analytic from the clock — zero state.
        val ns = (2f + sc * 14f).toInt().coerceAtMost(STREAKS)
        val sSpan = w + 40f * k
        val sVel = (60f + sc * 340f) * k
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * k
        for (i in 0 until ns) {
            val x = w + 20f * k - ((t * sVel + streakO[i] * sSpan) % sSpan)
            paint.color = argbWithAlpha(WHITE, streakA[i])
            canvas.drawLine(x, streakY[i] * k, x + streakL[i] * (0.5f + sc) * k, streakY[i] * k, paint)
        }
        paint.style = Paint.Style.FILL

        // Vehicle altitude: ground tiers ride the road, air tiers float mid-sky.
        val tier = s.tier.coerceIn(0, 4)
        val vy = if (tier <= 2) gy - 7f * k else h * 0.46f + sin(t * 1.6f) * 3f * k
        var vyF = min(vy, gy - 7f * k)
        if (tier <= 2) vyF += sin(t * 7f) * (0.5f + sc) * k
        val vx = 62f * k

        // Exhaust puffs — stateful pool, never stepped on static frames.
        if (s.dtS > 0f) {
            val spawnP = (sc * 0.7f + if (tier == 4) 0.4f else 0f) * s.dtS * 60f
            if (rng.next() < spawnP) {
                for (i in 0 until PUFFS) {
                    if (puffA[i] > 0f) continue
                    puffX[i] = vx - (if (tier == 0) 14f else 18f) * k
                    puffY[i] = vyF + (if (tier == 4) 0f else 4f) * k
                    puffR[i] = (1.5f + rng.next() * 2f) * k
                    puffA[i] = 0.6f
                    puffAmber[i] = tier == 4
                    break
                }
            }
            for (i in 0 until PUFFS) {
                if (puffA[i] <= 0f) continue
                puffX[i] -= (1f + sc * 3f) * 60f * k * s.dtS
                puffA[i] -= 1.08f * s.dtS
                puffR[i] += 3.6f * k * s.dtS
            }
        }
        for (i in 0 until PUFFS) {
            if (puffA[i] <= 0f) continue
            paint.color = if (puffAmber[i]) argbWithAlpha(AMBER, puffA[i])
            else argbWithAlpha(WHITE, puffA[i] * 0.7f)
            canvas.drawCircle(puffX[i], puffY[i], puffR[i], paint)
        }

        // Vehicle swap: ~370 ms squash → swap → overshoot. Static frames draw plain.
        if (s.dtS > 0f) {
            if (prevTier >= 0 && tier != prevTier) {
                swapP = 0f
                fromTier = prevTier
            }
            prevTier = tier
            if (swapP < 1f) swapP = (swapP + 2.7f * s.dtS).coerceAtMost(1f)
            wheel += (0.08f + sc * 0.5f) * 60f * s.dtS
        }
        val wheelNow = if (s.dtS > 0f) wheel else t * (0.08f + sc * 0.5f) * 60f
        val swapping = s.dtS > 0f && swapP < 1f
        val drawTier = if (swapping && swapP < 0.4f) fromTier else tier
        val vs = when {
            !swapping -> 1f
            swapP < 0.4f -> {
                val q = 1f - swapP / 0.4f
                q * q
            }
            else -> {
                val q = (swapP - 0.4f) / 0.6f
                if (q < 0.8f) q / 0.8f * 1.15f else 1.15f - (q - 0.8f) / 0.2f * 0.15f
            }
        }

        canvas.save()
        canvas.translate(vx, vyF)
        canvas.scale(vs * k, vs * k)   // vehicle fns use ref-space literals; k is folded in here
        when (drawTier) {
            0 -> drawSnail(canvas, t)
            1 -> drawBicycle(canvas, wheelNow)
            2 -> drawCar(canvas, wheelNow)
            3 -> drawPlane(canvas)
            else -> drawRocket(canvas, t)
        }
        canvas.restore()

        if (swapping) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * k
            paint.color = argbWithAlpha(WHITE, 0.7f * (1f - swapP))
            canvas.drawCircle(vx, vyF, (4f + swapP * 26f) * k, paint)
            paint.style = Paint.Style.FILL
        }

        canvas.restore()   // camera shake
    }

    private fun drawSnail(c: Canvas, t: Float) {
        paint.color = argbWithAlpha(WHITE, 0.25f)           // slime trail
        c.drawRect(-26f, 5f, -4f, 7f, paint)
        paint.color = SNAIL_BODY
        rect.set(-13f, -4f, 13f, 8f)
        c.drawOval(rect, paint)
        paint.color = SHELL
        c.drawCircle(-2f, -4f, 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = SHELL_SPIRAL
        c.drawCircle(-2f, -4f, 4.5f, paint)
        paint.color = SNAIL_BODY                            // eye stalks
        c.drawLine(9f, -3f, 12f, -9f, paint)
        c.drawLine(11f, -2f, 15f, -7f, paint)
        paint.style = Paint.Style.FILL
        paint.color = STALK_DOT
        c.drawCircle(12f, -9f, 1.4f, paint)
        c.drawCircle(15f, -7f, 1.4f, paint)
        val bl = if (sin(t * 1.8f) > 0.97f) 0.15f else 1f   // googly-eye blink
        paint.color = WHITE
        rect.set(6f, -1f - 2f * bl, 10f, -1f + 2f * bl)
        c.drawOval(rect, paint)
        rect.set(10f, -1f - 2f * bl, 14f, -1f + 2f * bl)
        c.drawOval(rect, paint)
        paint.color = INK
        rect.set(7.1f, -1f - 0.9f * bl, 8.9f, -1f + 0.9f * bl)
        c.drawOval(rect, paint)
        rect.set(11.1f, -1f - 0.9f * bl, 12.9f, -1f + 0.9f * bl)
        c.drawOval(rect, paint)
    }

    private fun drawBicycle(c: Canvas, wheel: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = LIGHT
        c.drawCircle(-9f, 4f, 6.5f, paint)
        c.drawCircle(9f, 4f, 6.5f, paint)
        paint.strokeWidth = 0.9f
        for (j in 0 until 3) {                              // spokes rotate with the wheel
            val a = wheel + j * 2.0944f
            val dx = cos(a) * 6.5f
            val dy = sin(a) * 6.5f
            c.drawLine(-9f, 4f, -9f + dx, 4f + dy, paint)
            c.drawLine(9f, 4f, 9f + dx, 4f + dy, paint)
        }
        paint.strokeWidth = 1.8f
        paint.color = SKY_BLUE
        c.drawLine(-9f, 4f, -2f, -5f, paint)
        c.drawLine(-2f, -5f, 9f, 4f, paint)
        c.drawLine(-2f, -5f, 2f, -5f, paint)
        c.drawLine(9f, 4f, 7f, -6f, paint)
        c.drawLine(7f, -6f, 10f, -7f, paint)
        c.drawLine(-2f, -5f, -4f, -8f, paint)
        c.drawLine(-4f, -8f, -6f, -8f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawCar(c: Canvas, wheel: Float) {
        paint.color = RED
        rect.set(-17f, -6f, 17f, 5f)
        c.drawRoundRect(rect, 4f, 4f, paint)
        paint.color = CABIN
        rect.set(-9f, -12f, 8f, -4f)
        c.drawRoundRect(rect, 3f, 3f, paint)
        paint.color = PALE_BLUE
        c.drawRect(-6f, -10f, -1f, -5f, paint)
        c.drawRect(2f, -10f, 7f, -5f, paint)
        paint.color = WHEEL_DARK
        c.drawCircle(-9f, 6f, 4.4f, paint)
        c.drawCircle(9f, 6f, 4.4f, paint)
        paint.color = HUB
        val hx = cos(wheel) * 2.2f
        val hy = sin(wheel) * 2.2f
        c.drawCircle(-9f + hx, 6f + hy, 1.1f, paint)
        c.drawCircle(9f + hx, 6f + hy, 1.1f, paint)
    }

    private fun drawPlane(c: Canvas) {
        paint.color = LIGHT
        rect.set(-17f, -5.5f, 17f, 5.5f)
        c.drawOval(rect, paint)
        paint.color = RED
        path.rewind()
        path.moveTo(17f, 0f); path.lineTo(11f, -3f); path.lineTo(11f, 3f); path.close()
        c.drawPath(path, paint)
        paint.color = PALE_BLUE
        path.rewind()
        path.moveTo(-2f, -2f); path.lineTo(-12f, -11f); path.lineTo(-5f, -2f); path.close()
        c.drawPath(path, paint)
        paint.color = RED
        path.rewind()
        path.moveTo(-15f, -1f); path.lineTo(-21f, -8f); path.lineTo(-14f, -3f); path.close()
        c.drawPath(path, paint)
        paint.color = PLANE_WIN
        c.drawCircle(-4f, -1f, 1.3f, paint)
        c.drawCircle(1f, -1f, 1.3f, paint)
        c.drawCircle(6f, -1f, 1.3f, paint)
    }

    private fun drawRocket(c: Canvas, t: Float) {
        flickerRng.reset((t * 30f).toInt() * 13 + 5)        // per-frame flame flicker
        val fl = 8f + flickerRng.next() * 9f
        paint.color = FLAME
        path.rewind()
        path.moveTo(-13f, -3f); path.lineTo(-13f - fl, 0f); path.lineTo(-13f, 3f); path.close()
        c.drawPath(path, paint)
        paint.color = AMBER
        path.rewind()
        path.moveTo(-13f, -1.8f); path.lineTo(-13f - fl * 0.6f, 0f); path.lineTo(-13f, 1.8f); path.close()
        c.drawPath(path, paint)
        paint.color = RED                                   // fins
        path.rewind()
        path.moveTo(-13f, -5.5f); path.lineTo(-19f, -10f); path.lineTo(-13f, -1f); path.close()
        c.drawPath(path, paint)
        path.rewind()
        path.moveTo(-13f, 5.5f); path.lineTo(-19f, 10f); path.lineTo(-13f, 1f); path.close()
        c.drawPath(path, paint)
        paint.color = LIGHT
        rect.set(-13f, -5.5f, 13f, 5.5f)
        c.drawRoundRect(rect, 5.5f, 5.5f, paint)
        paint.color = RED                                   // nose cone
        path.rewind()
        path.moveTo(13f, -5.5f); path.quadTo(22f, 0f, 13f, 5.5f); path.close()
        c.drawPath(path, paint)
        paint.color = SKY_BLUE
        c.drawCircle(3f, 0f, 3.2f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = WHITE
        c.drawCircle(3f, 0f, 3.2f, paint)
        paint.style = Paint.Style.FILL
    }

    private companion object {
        const val BANDS = 20
        const val STARS = 22
        const val CLOUDS = 4
        const val STREAKS = 16
        const val PUFFS = 8

        // Env palettes per tier: dawn gray / field / day road / open sky / space.
        val TOPS = intArrayOf(
            0xFF6B7A8C.toInt(), 0xFF8FB8D8.toInt(), 0xFF7FB2E5.toInt(),
            0xFF4A90D9.toInt(), 0xFF0B1026.toInt()
        )
        val BOTS = intArrayOf(
            0xFF586370.toInt(), 0xFFB0D0B0.toInt(), 0xFF98A2AC.toInt(),
            0xFFA8D0F0.toInt(), 0xFF1E2446.toInt()
        )
        val GNDS = intArrayOf(0xFF404A56.toInt(), 0xFF5D8A4A.toInt(), 0xFF46505E.toInt())

        const val WHITE = 0xFFFFFFFF.toInt()
        const val AMBER = 0xFFFBBF24.toInt()
        const val LIGHT = 0xFFE2E8F0.toInt()
        const val RED = 0xFFF56565.toInt()
        const val SKY_BLUE = 0xFF63B3ED.toInt()
        const val PALE_BLUE = 0xFF90CDF4.toInt()
        const val SNAIL_BODY = 0xFF9AE6B4.toInt()
        const val SHELL = 0xFFF6AD55.toInt()
        const val SHELL_SPIRAL = 0xFFC05621.toInt()
        const val STALK_DOT = 0xFF2F855A.toInt()
        const val INK = 0xFF1A202C.toInt()
        const val CABIN = 0xFFFEB2B2.toInt()
        const val WHEEL_DARK = 0xFF2D3748.toInt()
        const val HUB = 0xFFA0AEC0.toInt()
        const val PLANE_WIN = 0xFF4A5568.toInt()
        const val FLAME = 0xFFF97316.toInt()
    }
}
