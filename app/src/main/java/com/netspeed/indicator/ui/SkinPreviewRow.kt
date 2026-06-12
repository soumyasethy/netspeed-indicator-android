package com.netspeed.indicator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.netspeed.indicator.billing.Entitlement
import com.netspeed.indicator.billing.FeatureGate
import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.data.ColorSkin
import com.netspeed.indicator.data.HeroTheme
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.ui.hero.drawHeroBackground
import com.netspeed.indicator.ui.hero.rememberReducedMotion
import kotlin.math.sin

/**
 * Skin picker as LIVE mini hero banners: every card runs the hero animation in
 * that skin's palette with the speed sample rendered in the skin's own
 * typeface — exactly what the whole app will look like, animated, not a name
 * to guess from. Same perf shape as the other preview rows (LazyRow, one
 * shared ~30 fps clock, draw-phase reads).
 */
@Composable
fun SkinPreviewRow(
    selected: ColorSkin,
    theme: HeroTheme,
    live: LiveSpeed,
    unlocked: Boolean,
    onSelect: (ColorSkin) -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    var resumed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(resumed, reducedMotion) {
        if (!resumed || reducedMotion) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last == 0L) {
                    last = t
                } else {
                    val dt = (t - last) / 1_000_000_000f
                    if (dt >= 0.03f) {
                        clock += dt
                        last = t
                    }
                }
            }
        }
    }
    val liveMbps = if (live.running) live.downMBps else 0f
    // Scene themes paint their own palettes — preview skins on the flowing
    // gradient instead so each card actually shows ITS colours.
    val previewTheme = if (theme.isScene) HeroTheme.TIER_FLOW else theme

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Skin — each card is your hero banner in that palette",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 20.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(ColorSkin.entries.size) { i ->
                val skin = ColorSkin.entries[i]
                val locked = !FeatureGate.skinAllowed(skin.ordinal, Entitlement(unlocked))
                SkinCard(
                    skin = skin,
                    previewTheme = previewTheme,
                    selected = skin == selected,
                    locked = locked,
                    clock = { clock },
                    liveMbps = liveMbps,
                    onPick = { if (locked) onLocked() else onSelect(skin) },
                )
            }
        }
    }
}

@Composable
private fun SkinCard(
    skin: ColorSkin,
    previewTheme: HeroTheme,
    selected: Boolean,
    locked: Boolean,
    clock: () -> Float,
    liveMbps: Float,
    onPick: () -> Unit,
) {
    val shown = remember { floatArrayOf(0f) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.size(width = 116.dp, height = 92.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 116.dp, height = 64.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onPick),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val t = clock()
                val target = if (liveMbps > 0.2f) liveMbps else demoSweep(t)
                shown[0] += (target - shown[0]) * 0.12f
                val mbps = shown[0]
                val tierSkin = skin == ColorSkin.TIER
                val (c1, c2) = SpeedTiers.blendColors(mbps)
                val gradColors = if (tierSkin) listOf(c1, c2) else skin.heroColors
                val accent = if (tierSkin) c2 else skin.accent
                drawHeroBackground(previewTheme, t, mbps, gradColors, accent, dark = true)
            }
            Text(
                "8.4",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                fontFamily = if (skin.mono) FontFamily.Monospace else FontFamily.Default,
                color = skin.heroFg,
                modifier = Modifier.align(Alignment.Center),
            )
            if (locked) {
                Text(
                    "🔒",
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp),
                )
            }
        }
        Text(
            skin.label,
            fontSize = 10.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.65f),
        )
    }
}

private fun demoSweep(t: Float): Float {
    // Quadratic sweep 0.05..14 MB/s: lingers in the real-world low tiers,
    // still reaches the rocket era each ~25 s cycle.
    val x = sin(t * 0.25f) * 0.5f + 0.5f
    return 0.05f + x * x * 14f
}
