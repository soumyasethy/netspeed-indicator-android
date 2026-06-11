package com.netspeed.indicator.ui.hero

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.netspeed.indicator.core.SpeedTier
import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.core.TierIcon
import com.netspeed.indicator.core.TierTracker
import com.netspeed.indicator.data.ColorSkin
import com.netspeed.indicator.data.HeroTheme
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.service.SpeedFormatter
import kotlin.math.cos
import kotlin.math.sin

/** Maps the engine's abstract tier icon to a concrete Compose glyph. */
fun TierIcon.imageVector(): ImageVector = when (this) {
    TierIcon.SNOWFLAKE -> Icons.Filled.AcUnit
    TierIcon.CLOUD -> Icons.Filled.Cloud
    TierIcon.WIND -> Icons.Filled.Air
    TierIcon.BOLT -> Icons.Filled.Bolt
    TierIcon.FLAME -> Icons.Filled.LocalFireDepartment
}

/** True when the OS animator scale is 0 — honor the user's reduced-motion choice. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * The default "Tier flow" hero: an edge-to-edge gradient whose colors morph
 * continuously with speed (via [SpeedTiers.blendColors]) while the tier word
 * snaps at thresholds. The number is rendered Kinetic-style — its size and
 * weight grow with speed.
 *
 * Battery rules: the gradient-angle / blob drift runs on a single
 * [withFrameNanos] loop that is active ONLY while the activity is RESUMED and
 * reduced-motion is off. Off-screen, the loop suspends and the hero is a static
 * gradient — the foreground service never drives this.
 */
@Composable
fun TierFlowHero(
    live: LiveSpeed,
    theme: HeroTheme = HeroTheme.DEFAULT,
    skin: ColorSkin = ColorSkin.DEFAULT,
    thresholds: FloatArray = SpeedTiers.DEFAULT_THRESHOLDS,
    tierNames: List<String> = SpeedTiers.ALL.map { it.defaultWord },
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val reducedMotion = rememberReducedMotion()

    // --- resume gating --------------------------------------------------------
    var resumed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }

    // --- single frame clock (seconds), paused off-screen / under reduced motion
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(resumed, reducedMotion) {
        if (!resumed || reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) clock += (t - last) / 1_000_000_000f
                last = t
            }
        }
    }

    // --- smoothed speed: glide toward each 1 Hz sample ------------------------
    val targetMBps = if (live.running) live.downMBps else 0f
    val smoothedMBps by animateFloatAsState(
        targetValue = targetMBps,
        animationSpec = tween(800),
        label = "speed",
    )

    // --- tier (with hysteresis, user thresholds) -----------------------------
    val tracker = remember { TierTracker(thresholds) }
    LaunchedEffect(thresholds) { tracker.setThresholds(thresholds) }
    var tier by remember { mutableStateOf(tracker.current) }
    LaunchedEffect(live.downBytesPerSec, live.running) {
        tier = tracker.update(if (live.running) live.downMBps else 0f, 1000)
    }
    val tierWord = tierNames.getOrElse(tier.index) { tier.defaultWord }

    val (c1Target, c2Target) = SpeedTiers.blendColors(smoothedMBps)
    val c1 by animateColorAsState(c1Target, tween(800), label = "c1")
    val c2 by animateColorAsState(c2Target, tween(800), label = "c2")

    // Skin resolution: TIER keeps the live tier-driven colours; any other skin
    // overrides the gradient + accent with its fixed identity.
    val tierSkin = skin == ColorSkin.TIER
    val gradColors = if (tierSkin) listOf(c1, c2) else skin.heroColors
    val accent = if (tierSkin) c2 else skin.accent
    val fg = skin.heroFg
    val mono = skin.mono

    // Rolling history of recent smoothed speeds — feeds the Terminal sparkline.
    val history = remember { ArrayDeque<Float>() }
    LaunchedEffect(live.downBytesPerSec, live.running) {
        history.addLast(if (live.running) live.downMBps else 0f)
        while (history.size > 40) history.removeFirst()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Theme ALWAYS drives the hero; skin only supplies the colours.
                drawHeroBackground(theme, clock, smoothedMBps, gradColors, accent, dark)
            },
        contentAlignment = Alignment.Center,
    ) {
        when (theme) {
            HeroTheme.TERMINAL ->
                TerminalHero(tierWord, smoothedMBps, live, accent, history.toList())
            HeroTheme.BRUTALIST ->
                BrutalHero(tierWord, smoothedMBps, live, accent, dark)
            HeroTheme.GLASS ->
                GlassHero(tier, tierWord, smoothedMBps, live, fg)
            HeroTheme.BENTO ->
                BentoContent(tier = tier, smoothedMBps = smoothedMBps, live = live, fg = fg, mono = mono)
            else ->
                HeroContent(tier = tier, tierWord = tierWord, smoothedMBps = smoothedMBps, live = live, fg = fg, mono = mono)
        }
    }
}

@Composable
private fun HeroContent(
    tier: SpeedTier,
    tierWord: String,
    smoothedMBps: Float,
    live: LiveSpeed,
    fg: Color,
    mono: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TierTag(tier = tier, word = tierWord, fg = fg)
        Spacer(Modifier.size(14.dp))
        KineticNumber(mbps = smoothedMBps, fg = fg, mono = mono)
        Spacer(Modifier.size(6.dp))
        androidx.compose.material3.Text(
            text = if (live.running) tier.defaultSubtitle else "Indicator is off",
            color = fg.copy(alpha = 0.85f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(10.dp))
        androidx.compose.material3.Text(
            text = "▲ ${SpeedFormatter.inline(live.upBytesPerSec)}  ·  ${SpeedFormatter.total(live.todayBytes)} today",
            color = fg.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Bento tile grid: a big live-download tile + Today / Peak / Upload tiles. */
@Composable
private fun BentoContent(
    tier: SpeedTier,
    smoothedMBps: Float,
    live: LiveSpeed,
    fg: Color,
    mono: Boolean,
) {
    val tileBg = tier.c2.copy(alpha = 0.16f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Big download tile.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.4f)
                .clip(RoundedCornerShape(14.dp))
                .background(tileBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            androidx.compose.material3.Text("Download", color = fg.copy(alpha = 0.7f), fontSize = 12.sp)
            androidx.compose.material3.Text(
                text = "${formatNumber(smoothedMBps)} MB/s",
                color = fg,
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BentoTile("Today", SpeedFormatter.total(live.todayBytes), tileBg, fg, Modifier.weight(1f))
            BentoTile("Peak", SpeedFormatter.inline(live.peakBytesPerSec), tileBg, fg, Modifier.weight(1f))
            BentoTile("Upload", SpeedFormatter.inline(live.upBytesPerSec), tileBg, fg, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BentoTile(label: String, value: String, bg: Color, fg: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Text(label, color = fg.copy(alpha = 0.7f), fontSize = 11.sp)
        androidx.compose.material3.Text(
            value, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

// ---------------------------------------------------------------------------
// Skin-signature hero content
// ---------------------------------------------------------------------------

/** Terminal: htop-style green mono readout with a block-char sparkline + cursor. */
@Composable
private fun TerminalHero(word: String, mbps: Float, live: LiveSpeed, accent: Color, history: List<Float>) {
    var cursorOn by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(530); cursorOn = !cursorOn } }
    val mono = androidx.compose.ui.text.font.FontFamily.Monospace
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Text(
            "netspeed@local:~\$ speed --watch", color = accent.copy(alpha = 0.7f),
            fontFamily = mono, fontSize = 12.sp, maxLines = 1,
        )
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            androidx.compose.material3.Text(
                "▼ ${formatNumber(mbps)}", color = accent, fontFamily = mono,
                fontSize = 44.sp, fontWeight = FontWeight.Medium,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
            androidx.compose.material3.Text(
                " MB/s", color = accent.copy(alpha = 0.8f), fontFamily = mono, fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            androidx.compose.material3.Text(
                "█", color = if (cursorOn) accent else Color.Transparent,
                fontFamily = mono, fontSize = 36.sp,
            )
        }
        Spacer(Modifier.size(8.dp))
        androidx.compose.material3.Text(
            blockSparkline(history), color = accent, fontFamily = mono, fontSize = 18.sp, maxLines = 1,
        )
        Spacer(Modifier.size(8.dp))
        androidx.compose.material3.Text(
            "▲ ${SpeedFormatter.inline(live.upBytesPerSec)}  ·  ${SpeedFormatter.total(live.todayBytes)} today",
            color = accent.copy(alpha = 0.6f), fontFamily = mono, fontSize = 12.sp,
        )
    }
}

private fun blockSparkline(history: List<Float>): String {
    val blocks = " ▁▂▃▄▅▆▇█"
    if (history.isEmpty()) return "▁".repeat(28)
    return history.takeLast(28).joinToString("") { v ->
        blocks[(v / 48f * 8f).toInt().coerceIn(0, 8)].toString()
    }
}

/** Neo-brutal: flat colour, the number in a hard-bordered block with offset shadow. */
@Composable
private fun BrutalHero(word: String, mbps: Float, live: LiveSpeed, accent: Color, dark: Boolean) {
    val ink = Color.Black
    val block = if (dark) Color(0xFF1F1F26) else Color.White
    val txt = if (dark) Color(0xFFFAFAFA) else Color(0xFF1C1917)
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Hard offset block shadow (no blur) — the brutalist signature.
                    drawRect(ink, topLeft = Offset(9.dp.toPx(), 9.dp.toPx()), size = size)
                }
                .background(block)
                .border(3.dp, ink)
                .padding(20.dp),
        ) {
            androidx.compose.material3.Text(
                word.uppercase(), color = accent, fontWeight = FontWeight.Black, fontSize = 14.sp,
            )
            androidx.compose.material3.Text(
                "${formatNumber(mbps)} MB/s", color = txt, fontWeight = FontWeight.Black, fontSize = 46.sp,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            )
            androidx.compose.material3.Text(
                "▲ ${SpeedFormatter.inline(live.upBytesPerSec)} · ${SpeedFormatter.total(live.todayBytes)} today",
                color = txt.copy(alpha = 0.7f), fontSize = 12.sp,
            )
        }
    }
}

/** Glasswave: a frosted translucent card floating over the blurred blob background. */
@Composable
private fun GlassHero(tier: SpeedTier, word: String, mbps: Float, live: LiveSpeed, fg: Color) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TierTag(tier = tier, word = word, fg = fg)
            Spacer(Modifier.size(12.dp))
            KineticNumber(mbps = mbps, fg = fg, mono = false)
            Spacer(Modifier.size(8.dp))
            androidx.compose.material3.Text(
                "▲ ${SpeedFormatter.inline(live.upBytesPerSec)}  ·  ${SpeedFormatter.total(live.todayBytes)} today",
                color = fg.copy(alpha = 0.75f), fontSize = 12.sp,
            )
        }
    }
}

/** Tier pill that pops (1.0 → 1.18 → 1.0, ~250ms) whenever the tier changes. */
@Composable
private fun TierTag(tier: SpeedTier, word: String, fg: Color) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(tier.index) {
        scale.animateTo(1.18f, tween(120))
        scale.animateTo(1f, tween(130))
    }
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(Color.Black.copy(alpha = 0.22f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            imageVector = tier.icon.imageVector(),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        androidx.compose.material3.Text(
            text = word,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Number whose size (48→64sp) and weight (300→800) grow with speed. */
@Composable
private fun KineticNumber(mbps: Float, fg: Color, mono: Boolean) {
    val t = (mbps / 48f).coerceIn(0f, 1f)
    val size = (48f + 16f * t).sp
    val weight = FontWeight(((300 + (500 * t)).toInt()).coerceIn(100, 900))
    Row(verticalAlignment = Alignment.Bottom) {
        androidx.compose.material3.Text(
            text = formatNumber(mbps),
            color = fg,
            fontSize = size,
            fontWeight = weight,
            fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            // Tabular numerals so the digits never jitter horizontally as they ease.
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.size(6.dp))
        androidx.compose.material3.Text(
            text = "MB/s",
            color = fg.copy(alpha = 0.85f),
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
    }
}

/** One decimal under 10 MB/s, integer above — matches the status-bar grammar. */
private fun formatNumber(mbps: Float): String =
    if (mbps >= 10f) mbps.toInt().toString()
    else {
        val tenths = (mbps * 10).toInt()
        "${tenths / 10}.${tenths % 10}"
    }

// --- canvas helpers (DrawScope) ----------------------------------------------

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGradient(
    c1: Color,
    c2: Color,
    angleDeg: Float,
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val half = maxOf(size.width, size.height)
    val center = Offset(size.width / 2f, size.height / 2f)
    val start = Offset(center.x - dx * half, center.y - dy * half)
    val end = Offset(center.x + dx * half, center.y + dy * half)
    drawRect(brush = Brush.linearGradient(listOf(c1, c2), start = start, end = end))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlob(
    clockSec: Float,
    sizeDp: Float,
    alpha: Float,
    seed: Float,
    drift: Float,
) {
    val r = sizeDp * density
    val px = size.width * (0.5f + 0.32f * sin(clockSec * 0.15f * drift + seed))
    val py = size.height * (0.5f + 0.30f * cos(clockSec * 0.11f * drift + seed))
    drawCircle(color = Color.White.copy(alpha = alpha), radius = r, center = Offset(px, py))
}
