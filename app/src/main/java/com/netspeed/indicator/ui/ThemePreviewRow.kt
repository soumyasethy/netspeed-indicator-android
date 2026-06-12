package com.netspeed.indicator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.netspeed.indicator.billing.Entitlement
import com.netspeed.indicator.billing.FeatureGate
import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.data.ColorSkin
import com.netspeed.indicator.data.HeroTheme
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.render.scenes.SceneState
import com.netspeed.indicator.ui.hero.drawHeroBackground
import com.netspeed.indicator.ui.hero.rememberReducedMotion
import kotlin.math.sin

/**
 * Theme picker as LIVE preview cards: every card plays its theme's real
 * animation (the exact draw code the hero uses — scenes included), so choosing
 * is see-then-tap instead of guessing from a name. Same perf shape as
 * [ScenePreviewRow]: LazyRow (only visible cards exist), one shared ~30 fps
 * clock read in the draw phase, demo speed sweep when the network is idle.
 */
@Composable
fun ThemePreviewRow(
    selected: HeroTheme,
    skin: ColorSkin,
    live: LiveSpeed,
    unlocked: Boolean,
    onSelect: (HeroTheme) -> Unit,
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Live themes — previews play the real animation",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 20.dp),
        )
        // Scenes lead the row (the lively, premium sell); classic themes follow.
        // DISPLAY order only — gating stays on the stable enum ordinal.
        val display = remember { HeroTheme.entries.sortedByDescending { it.isScene } }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) {
            items(display.size) { i ->
                val theme = display[i]
                val locked = !FeatureGate.themeAllowed(theme.ordinal, Entitlement(unlocked))
                ThemeCard(
                    theme = theme,
                    skin = skin,
                    selected = theme == selected,
                    locked = locked,
                    clock = { clock },
                    liveMbps = liveMbps,
                    onPick = { if (locked) onLocked() else onSelect(theme) },
                )
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: HeroTheme,
    skin: ColorSkin,
    selected: Boolean,
    locked: Boolean,
    clock: () -> Float,
    liveMbps: Float,
    onPick: () -> Unit,
) {
    val scene = remember(theme) { SceneRegistry.fromThemeKey(theme.storageKey)?.factory?.invoke() }
    val sceneState = remember { SceneState() }
    val lastT = remember { floatArrayOf(0f) }
    val shown = remember { floatArrayOf(0f) }
    val emoji = SceneRegistry.fromThemeKey(theme.storageKey)?.emoji
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
                val t = clock()                    // draw-phase read → draw-only ticks
                val target = if (liveMbps > 0.2f) liveMbps else demoSweep(t)
                shown[0] += (target - shown[0]) * 0.12f
                val mbps = shown[0]
                if (scene != null) {
                    sceneState.dtS = (t - lastT[0]).coerceIn(0f, 0.3f)
                    lastT[0] = t
                    sceneState.timeS = t
                    sceneState.mbps = mbps
                    sceneState.sc = SpeedTiers.norm(mbps)
                    sceneState.tier = SpeedTiers.rawIndex(mbps)
                    sceneState.tierFrac = SpeedTiers.tierFrac(mbps)
                    sceneState.tierProgress = SpeedTiers.tierProgress(mbps)
                    sceneState.accentArgb = SpeedTiers.blendAccentArgb(mbps)
                    sceneState.dark = true
                    drawIntoCanvas { scene.render(it.nativeCanvas, size.width, size.height, sceneState) }
                } else {
                    // Classic themes: the SAME DrawScope code the hero runs.
                    val tierSkin = skin == ColorSkin.TIER
                    val (c1, c2) = SpeedTiers.blendColors(mbps)
                    val gradColors = if (tierSkin) listOf(c1, c2) else skin.heroColors
                    val accent = if (tierSkin) c2 else skin.accent
                    drawHeroBackground(theme, t, mbps, gradColors, accent, dark = true)
                }
            }
            if (locked) {
                Text(
                    "🔒",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }
        }
        Text(
            if (emoji != null) "$emoji ${theme.label}" else theme.label,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.65f),
        )
    }
}

/** Idle demo drive shared with the scene picker (~25 s tier sweep). */
private fun demoSweep(t: Float): Float = (sin(t * 0.25f) * 0.5f + 0.5f) * 46f + 1f
