package com.netspeed.indicator.ui.hero

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.netspeed.indicator.data.ColorSkin
import com.netspeed.indicator.data.HeroTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * All live-theme backgrounds in one place. Each is a pure [DrawScope] function
 * fed the shared frame clock (seconds), the smoothed speed, the current tier
 * colors and the dark/light flag. The hero calls [drawHeroBackground]; only
 * BENTO and KINETIC are handled by the composable layer instead (tiles / pure
 * number animation).
 *
 * Speed maps to a 0..1 "fill" via a 48 MB/s ceiling, the same scale the widgets
 * and notification sparkline use, so motion reads consistently across surfaces.
 */

private fun fillFraction(mbps: Float) = (mbps / 48f).coerceIn(0f, 1f)

/** Glasswave: deep base + soft BLURRED drifting blobs (frosted feel). */
private fun DrawScope.drawGlass(clock: Float, accent: Color, dark: Boolean) {
    drawRect(if (dark) Color(0xFF141226) else Color(0xFFFDF3F8))
    val violet = Color(0xFF8B5CF6); val amber = Color(0xFFF59E0B); val magenta = Color(0xFFEC4899)
    val blobs = listOf(magenta, violet, amber)
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = android.graphics.BlurMaskFilter(70f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        blobs.forEachIndexed { i, col ->
            val ph = clock * (0.13f + 0.05f * i) + i * 1.9f
            val x = size.width * (0.5f + 0.36f * sin(ph))
            val y = size.height * (0.5f + 0.34f * cos(ph * 0.8f))
            paint.color = col.copy(alpha = if (dark) 0.45f else 0.55f).toArgb()
            canvas.nativeCanvas.drawCircle(x, y, 90f * density, paint)
        }
    }
}

fun DrawScope.drawHeroBackground(
    theme: HeroTheme,
    clock: Float,
    mbps: Float,
    gradColors: List<Color>,
    accent: Color,
    dark: Boolean,
) {
    when (theme) {
        // Kinetic: the flowing gradient, no blobs — the NUMBER stays the subject.
        HeroTheme.KINETIC -> drawKineticBg(clock, gradColors)
        // Tier flow / Bento: the flowing gradient with drifting blobs.
        HeroTheme.TIER_FLOW, HeroTheme.BENTO -> drawGradientBg(clock, gradColors)
        HeroTheme.LIQUID -> drawLiquid(clock, mbps, accent, dark)
        HeroTheme.ECG -> drawEcg(clock, mbps, accent, dark)
        HeroTheme.DIAL -> drawDial(mbps, accent, dark)
        HeroTheme.RADAR -> drawRadar(clock, mbps, accent, dark)
        HeroTheme.PARTICLES -> drawParticles(clock, mbps, accent, dark)
        HeroTheme.CURTAINS -> drawCurtains(clock, accent, dark)
        HeroTheme.MATERIAL_YOU -> drawMaterialYou(clock, gradColors.first(), accent, dark)
        HeroTheme.SKY -> drawSky(clock, mbps, dark)
        HeroTheme.TERMINAL -> drawRect(if (dark) Color(0xFF050A06) else Color(0xFF0A140D))
        HeroTheme.BRUTALIST -> drawRect(gradColors.lastOrNull() ?: accent)   // flat, no gradient
        HeroTheme.GLASS -> drawGlass(clock, accent, dark)
        // Speedtest: clean light/dark canvas — the dual charts ARE the visual.
        HeroTheme.SPEEDTEST -> drawRect(if (dark) Color(0xFF0E1116) else Color(0xFFF7F8FA))
        // Speed scenes render in Hero.kt via the shared SpeedScene renderer;
        // this branch only paints the base if ever reached.
        else -> drawRect(baseColor(dark))
    }
}

/**
 * Scrim drawn OVER a speed scene, UNDER the hero text: a soft radial pool of
 * darkness centered on the TEXT COLUMN ([centerXFrac] of the width — 0.2 left,
 * 0.5 center, 0.8 right) so the digits never fight the diorama for contrast
 * while the scene's focal side keeps its full brightness.
 */
fun DrawScope.drawSceneScrim(centerXFrac: Float = 0.5f, centerYFrac: Float = 0.5f) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(size.width * centerXFrac, size.height * centerYFrac),
            radius = (minOf(size.width, size.height) * 0.85f).coerceAtLeast(1f),
        ),
    )
}

// Canvas themes always use a dark base so the white floating content stays
// readable regardless of the system light/dark setting.
private fun baseColor(dark: Boolean) = Color(0xFF0C0F17)

/**
 * The "gemini" flow shared by the gradient themes: a wrapped palette
 * (`linear-gradient(120deg, c1..cn, c1)`) tiled along the 120° axis and drifting
 * at CONSTANT velocity — one direction, zero acceleration, seamless wrap — the
 * motion-graphic feel (a ping-pong pan surges and reverses, which reads jerky).
 * Under reduced motion the clock is frozen, so it degrades to a still gradient.
 */
private fun DrawScope.drawFlowGradient(clock: Float, colors: List<Color>) {
    val base = if (colors.size >= 2) colors
    else listOf(colors.firstOrNull() ?: Color(0xFF2563EB), Color(0xFF7C3AED))
    val stops = base + base.first()                       // wrap: …, c1
    val rad = Math.toRadians(30.0)                        // CSS 120deg axis
    val dx = cos(rad).toFloat(); val dy = sin(rad).toFloat()
    val tile = 2f * kotlin.math.sqrt(size.width * size.width + size.height * size.height)
    val period = com.netspeed.indicator.core.GradientFlow.HERO_PERIOD_S
    val phase = (clock / period) % 1f
    val offset = phase * tile
    val start = Offset(-offset * dx, -offset * dy)
    drawRect(
        Brush.linearGradient(
            stops,
            start = start,
            end = Offset(start.x + dx * tile, start.y + dy * tile),
            tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
        ),
    )
}

/** Kinetic: the flowing gradient, kept clean (no blobs) so the number leads. */
private fun DrawScope.drawKineticBg(clock: Float, colors: List<Color>) {
    drawFlowGradient(clock, colors)
}

/**
 * Tier flow / Bento background: the gemini flow plus two soft drifting blobs for
 * extra depth.
 */
private fun DrawScope.drawGradientBg(clock: Float, colors: List<Color>) {
    drawFlowGradient(clock, colors)
    drawCircle(
        Color.White.copy(alpha = 0.12f), radius = 160f * density,
        center = Offset(size.width * (0.5f + 0.32f * sin(clock * 0.15f)), size.height * (0.5f + 0.30f * cos(clock * 0.11f))),
    )
    drawCircle(
        Color.White.copy(alpha = 0.09f), radius = 110f * density,
        center = Offset(size.width * (0.5f - 0.30f * sin(clock * 0.11f + 2.1f)), size.height * (0.5f - 0.28f * cos(clock * 0.15f + 2.1f))),
    )
}

/** Two overlapping sine waves filling to speed/48 of the height. */
private fun DrawScope.drawLiquid(clock: Float, mbps: Float, c2: Color, dark: Boolean) {
    drawRect(baseColor(dark))
    val f = fillFraction(mbps)
    val surfaceY = size.height * (1f - f * 0.9f) - size.height * 0.05f
    drawWave(surfaceY, amp = 7f * density, phase = clock * 1.4f, color = c2.copy(alpha = 0.38f))
    drawWave(surfaceY + 6f * density, amp = 7f * density, phase = clock * 1.9f + 1f, color = c2.copy(alpha = 0.22f))
}

private fun DrawScope.drawWave(baseY: Float, amp: Float, phase: Float, color: Color) {
    val path = Path()
    val steps = 48
    path.moveTo(0f, baseY)
    for (i in 0..steps) {
        val x = size.width * i / steps
        val y = baseY + amp * sin(phase + i * 0.45f).toFloat()
        path.lineTo(x, y)
    }
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(path, color)
}

/** Scrolling heartbeat waveform with a bright head dot at the right edge. */
private fun DrawScope.drawEcg(clock: Float, mbps: Float, c2: Color, dark: Boolean) {
    drawRect(baseColor(dark))
    val mid = size.height * 0.5f
    val period = 90f * density
    val amp = (8f + mbps * 1.1f) * density
    val scroll = clock * 120f * density
    val path = Path()
    val step = 3f
    var x = 0f
    var started = false
    while (x <= size.width) {
        val p = ((x + scroll) % period) / period   // 0..1 within a beat
        val y = mid - ecgShape(p) * amp
        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
        x += step
    }
    drawPath(path, c2.copy(alpha = 0.9f), style = Stroke(width = 2f * density))
    val headY = mid - ecgShape(((size.width + scroll) % period) / period) * amp
    drawCircle(c2, radius = 3.5f * density, center = Offset(size.width, headY))
}

/** A flat line with a single QRS-like spike around 0.5. */
private fun ecgShape(p: Float): Float = when {
    p < 0.45f || p > 0.62f -> 0f
    p < 0.50f -> (p - 0.45f) / 0.05f * -0.3f
    p < 0.54f -> -0.3f + (p - 0.50f) / 0.04f * 1.3f
    p < 0.58f -> 1.0f - (p - 0.54f) / 0.04f * 1.5f
    else -> -0.5f + (p - 0.58f) / 0.04f * 0.5f
}

/** Hero-sized 270° gauge: track + value arc + endpoint dot. */
private fun DrawScope.drawDial(mbps: Float, c2: Color, dark: Boolean) {
    drawRect(baseColor(dark))
    val f = fillFraction(mbps)
    val stroke = 11f * density
    val pad = stroke + 16f * density
    val diameter = minOf(size.width, size.height) * 0.7f
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    val arcSize = Size(diameter, diameter)
    val fg = if (dark) Color.White else Color(0xFF171A23)
    drawArc(
        color = fg.copy(alpha = 0.14f), startAngle = 135f, sweepAngle = 270f, useCenter = false,
        topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
    drawArc(
        color = c2, startAngle = 135f, sweepAngle = 270f * f, useCenter = false,
        topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
    val endAngle = Math.toRadians((135f + 270f * f).toDouble())
    val r = diameter / 2f
    val cx = topLeft.x + r; val cy = topLeft.y + r
    drawCircle(Color.White, radius = 5f * density, center = Offset(cx + r * cos(endAngle).toFloat(), cy + r * sin(endAngle).toFloat()))
}

/** Concentric rings + rotating sweep with a fading trail. */
private fun DrawScope.drawRadar(clock: Float, mbps: Float, c2: Color, dark: Boolean) {
    drawRect(baseColor(dark))
    val fg = if (dark) Color.White else Color(0xFF171A23)
    val cx = size.width / 2f; val cy = size.height / 2f
    val maxR = minOf(size.width, size.height) * 0.42f
    for (i in 1..3) {
        drawCircle(fg.copy(alpha = 0.12f), radius = maxR * i / 3f, center = Offset(cx, cy), style = Stroke(1.5f * density))
    }
    val base = clock * 1.4f
    for (t in 0 until 28) {
        val a = base - t * 0.05f
        val alpha = (1f - t / 28f) * 0.5f
        drawLine(
            c2.copy(alpha = alpha),
            start = Offset(cx, cy),
            end = Offset(cx + maxR * cos(a).toFloat(), cy + maxR * sin(a).toFloat()),
            strokeWidth = 2f * density,
        )
    }
    // A blip whose radius tracks speed.
    val blipR = maxR * fillFraction(mbps)
    drawCircle(c2, radius = 3f * density, center = Offset(cx + blipR * cos(base).toFloat(), cy + blipR * sin(base).toFloat()))
}

/** Deterministic per-index pseudo-random in [0,1) (no allocation, Random-free). */
private fun hash(n: Float): Float {
    val s = sin(n) * 43758.5453f
    return s - kotlin.math.floor(s)
}

/**
 * A true particle field: every particle has its OWN start position, speed, size
 * and twinkle phase, plus a gentle vertical drift — so it reads as drifting motes
 * rather than a marching grid. Count + velocity scale with speed.
 */
private fun DrawScope.drawParticles(clock: Float, mbps: Float, c2: Color, dark: Boolean) {
    drawRect(baseColor(dark))
    val f = fillFraction(mbps)
    val count = (24 + f * 60).toInt()
    val w = size.width; val h = size.height
    for (i in 0 until count) {
        val fi = i.toFloat()
        val baseX = hash(fi * 1.13f)
        val baseY = hash(fi * 2.71f)
        val speed = 0.35f + hash(fi * 3.31f)               // independent velocity
        val rad = (1f + hash(fi * 4.07f) * 2.6f) * density
        val vx = (24f + 200f * f) * speed * density
        val x = ((baseX * (w + 40f)) + clock * vx) % (w + 40f) - 20f
        val drift = sin(clock * 0.6f + fi * 1.7f) * 10f * density   // gentle bob
        val y = baseY * h + drift
        val twinkle = 0.35f + 0.55f * kotlin.math.abs(sin(clock * 1.4f + fi * 0.9f))
        // Short fade trail behind each mote.
        drawCircle(c2.copy(alpha = 0.10f * twinkle), radius = rad * 1.7f, center = Offset(x - vx * 0.03f, y))
        drawCircle(c2.copy(alpha = 0.65f * twinkle), radius = rad, center = Offset(x, y))
    }
}

/** Three drifting vertical aurora bands. */
private fun DrawScope.drawCurtains(clock: Float, c2: Color, dark: Boolean) {
    drawRect(baseColor(dark))
    val violet = Color(0xFF7C3AED)
    val bandW = 76f * density
    val colors = listOf(c2, violet, c2)
    for (i in 0 until 3) {
        val base = size.width * (0.25f + 0.25f * i)
        val x = base + 34f * density * sin(clock * 0.3f + i.toFloat())
        drawRect(
            Brush.verticalGradient(listOf(colors[i].copy(alpha = 0.32f), Color.Transparent)),
            topLeft = Offset(x - bandW / 2f, 0f),
            size = Size(bandW, size.height),
        )
    }
}

/** Material You: large soft circles drifting in tonal colors. */
private fun DrawScope.drawMaterialYou(clock: Float, c1: Color, c2: Color, dark: Boolean) {
    drawRect(baseColor(dark))
    val tones = listOf(c2.copy(alpha = 0.45f), c1.copy(alpha = 0.30f), c2.copy(alpha = 0.22f))
    val radii = listOf(92f, 78f, 68f)
    for (i in 0 until 3) {
        val phase = clock * (0.12f + 0.04f * i) + i
        drawCircle(
            tones[i], radius = radii[i] * density,
            center = Offset(size.width * (0.5f + 0.34f * sin(phase)), size.height * (0.5f + 0.32f * cos(phase * 0.8f))),
        )
    }
}

/** Day/night cycle with sun/moon, stars and a skyline. */
private fun DrawScope.drawSky(clock: Float, mbps: Float, dark: Boolean) {
    val cyc = (sin(clock * (2f * PI.toFloat() / 36f)) + 1f) / 2f   // 0 night .. 1 day
    val night = Color(0xFF0B1026); val day = Color(0xFF7DD3FC)
    val sky = lerpColor(night, day, cyc)
    drawRect(sky)
    // Sun/moon travels an arc.
    val t = (clock % 36f) / 36f
    val arcX = size.width * t
    val arcY = size.height * (0.65f - 0.45f * sin(t * PI.toFloat()))
    drawCircle(if (cyc > 0.5f) Color(0xFFFFE08A) else Color(0xFFE6ECff), radius = 13f * density, center = Offset(arcX, arcY))
    // Stars fade in at night.
    if (cyc < 0.5f) {
        val starAlpha = (0.5f - cyc) * 2f
        for (i in 0 until 26) {
            val s = i * 0.6180339f
            drawCircle(
                Color.White.copy(alpha = starAlpha * 0.8f), radius = 1.4f * density,
                center = Offset(size.width * ((s * 7) % 1f), size.height * 0.5f * ((s * 13) % 1f)),
            )
        }
    }
    // Flat skyline silhouette.
    val skyline = Color(0xFF0A0D16)
    val n = 10
    for (i in 0 until n) {
        val bw = size.width / n
        val bh = size.height * (0.12f + 0.10f * ((i * 0.6180339f * 17) % 1f))
        drawRect(skyline, topLeft = Offset(i * bw, size.height - bh), size = Size(bw * 0.92f, bh))
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
