package com.netspeed.indicator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.data.ColorSkin
import com.netspeed.indicator.data.HeroTheme
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.render.scenes.SceneState
import com.netspeed.indicator.ui.hero.drawHeroBackground
import kotlin.math.sin

/**
 * Shared building blocks for every live preview card — the settings rows and
 * the Style Studio grid render the SAME canvases, so a card always shows
 * exactly what applying it will look like.
 */

/** Idle demo drive: quadratic 0.05..14 MB/s sweep (~25 s), lingers low where
 *  real-world connections live, still reaches the rocket era each cycle. */
fun demoSweep(t: Float): Float {
    val x = sin(t * 0.25f) * 0.5f + 0.5f
    return 0.05f + x * x * 14f
}

/** The chrome every studio/row card shares: clip, selected border, lock badge,
 *  label underneath. [content] fills the preview area. */
@Composable
fun StudioCardFrame(
    selected: Boolean,
    locked: Boolean,
    label: String,
    previewHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onClick),
        ) {
            content()
            if (locked) {
                Text(
                    "🔒",
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }
        }
        Text(
            label,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.65f),
        )
    }
}

/**
 * Live canvas for a HERO THEME card: scene themes run the shared SpeedScene
 * renderer, classic themes run the hero's own DrawScope code. State is held
 * per-composable; [clock] is read in the draw phase (draw-only invalidation).
 */
@Composable
fun ThemeCanvas(
    theme: HeroTheme,
    skin: ColorSkin,
    clock: () -> Float,
    liveMbps: Float,
    dark: Boolean = true,
) {
    val scene = remember(theme) { SceneRegistry.fromThemeKey(theme.storageKey)?.factory?.invoke() }
    val sceneState = remember { SceneState() }
    val lastT = remember { floatArrayOf(0f) }
    val shown = remember { floatArrayOf(0f) }
    Canvas(Modifier.fillMaxSize()) {
        val t = clock()
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
            sceneState.dark = dark
            drawIntoCanvas { scene.render(it.nativeCanvas, size.width, size.height, sceneState) }
        } else {
            val tierSkin = skin == ColorSkin.TIER
            val (c1, c2) = SpeedTiers.blendColors(mbps)
            val gradColors = if (tierSkin) listOf(c1, c2) else skin.heroColors
            val accent = if (tierSkin) c2 else skin.accent
            drawHeroBackground(theme, t, mbps, gradColors, accent, dark = true)
        }
    }
}

/** Live canvas for a SKIN card: the flowing hero animation in that palette. */
@Composable
fun SkinCanvas(
    skin: ColorSkin,
    previewTheme: HeroTheme,
    clock: () -> Float,
    liveMbps: Float,
) {
    val shown = remember { floatArrayOf(0f) }
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
}

/** Live canvas for a SPEED SCENE card (bubble picker semantics: light variant). */
@Composable
fun SceneCanvas(
    entry: SceneRegistry.Entry,
    clock: () -> Float,
    liveMbps: Float,
    dark: Boolean = false,
) {
    val scene = remember(entry.id) { entry.factory() }
    val state = remember { SceneState() }
    val lastT = remember { floatArrayOf(0f) }
    val shown = remember { floatArrayOf(0f) }
    Canvas(Modifier.fillMaxSize()) {
        val t = clock()
        val target = if (liveMbps > 0.2f) liveMbps else demoSweep(t)
        shown[0] += (target - shown[0]) * 0.12f
        val mbps = shown[0]
        state.dtS = (t - lastT[0]).coerceIn(0f, 0.3f)
        lastT[0] = t
        state.timeS = t
        state.mbps = mbps
        state.sc = SpeedTiers.norm(mbps)
        state.tier = SpeedTiers.rawIndex(mbps)
        state.tierFrac = SpeedTiers.tierFrac(mbps)
        state.tierProgress = SpeedTiers.tierProgress(mbps)
        state.accentArgb = SpeedTiers.blendAccentArgb(mbps)
        state.dark = dark
        drawIntoCanvas { scene.render(it.nativeCanvas, size.width, size.height, state) }
    }
}
