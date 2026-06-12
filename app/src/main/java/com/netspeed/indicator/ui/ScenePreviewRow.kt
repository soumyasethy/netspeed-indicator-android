package com.netspeed.indicator.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.render.scenes.SceneState
import com.netspeed.indicator.ui.hero.rememberReducedMotion
import kotlin.math.sin

/**
 * The bubble-animation picker: LIVE animated previews of every speed scene so
 * choosing a style is see-then-tap, not guess-from-a-name.
 *
 * Perf shape:
 *  - LazyRow → only visible cells exist; scrolled-away scenes are disposed.
 *  - ONE shared frame clock (~30 fps) drives every visible canvas; reads happen
 *    in the draw phase, so ticks invalidate draws, not compositions.
 *  - Paused while the activity isn't resumed and under reduced motion.
 *  - When real traffic is flowing the previews follow it; idle, they run a slow
 *    demo sweep through all five tiers (the gallery's "auto" mode) so the row
 *    is always alive.
 */
@Composable
fun ScenePreviewRow(
    label: String,
    selected: String,
    live: LiveSpeed,
    onPick: (String) -> Unit,
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
                    if (dt >= 0.03f) {            // ~30 fps cap for the whole row
                        clock += dt
                        last = t
                    }
                }
            }
        }
    }

    val liveMbps = if (live.running) live.downMBps else 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(BUILTINS.size) { i ->
                val (key, emoji, name) = BUILTINS[i]
                SwatchCell(
                    emoji = emoji,
                    name = name,
                    selected = selected == key,
                    onPick = { onPick(key) },
                )
            }
            items(SceneRegistry.ALL.size) { i ->
                val entry = SceneRegistry.ALL[i]
                SceneCell(
                    entry = entry,
                    selected = selected == entry.id,
                    clock = { clock },
                    liveMbps = liveMbps,
                    onPick = { onPick(entry.id) },
                )
            }
        }
    }
}

/** Static swatch for the legacy effects (none / aura fx / Lottie file). */
@Composable
private fun SwatchCell(emoji: String, name: String, selected: Boolean, onPick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ),
                    ),
                )
                .cellBorder(selected)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 18.sp)
        }
        CellLabel(name, selected)
    }
}

/** Live animated preview of one speed scene. */
@Composable
private fun SceneCell(
    entry: SceneRegistry.Entry,
    selected: Boolean,
    clock: () -> Float,
    liveMbps: Float,
    onPick: () -> Unit,
) {
    val scene = remember(entry.id) { entry.factory() }
    val state = remember { SceneState() }
    val lastT = remember { floatArrayOf(0f) }
    val shown = remember { floatArrayOf(0f) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .cellBorder(selected)
                .clickable(onClick = onPick),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val t = clock()                   // draw-phase read → draw-only ticks
                val target = if (liveMbps > 0.2f) liveMbps else demoMbps(t)
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
                state.dark = false                // previews match the bubble's variants
                drawIntoCanvas {
                    scene.render(it.nativeCanvas, size.width, size.height, state)
                }
            }
        }
        CellLabel("${entry.emoji} ${entry.label}", selected)
    }
}

@Composable
private fun CellLabel(text: String, selected: Boolean) {
    Text(
        text,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.65f),
    )
}

@Composable
private fun Modifier.cellBorder(selected: Boolean): Modifier = border(
    width = if (selected) 2.dp else 1.dp,
    color = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    shape = RoundedCornerShape(10.dp),
)

/** Idle demo drive: a slow sweep through all five tiers (~25 s loop). */
private fun demoMbps(t: Float): Float {
    // Quadratic sweep 0.05..14 MB/s: lingers in the real-world low tiers,
    // still reaches the rocket era each ~25 s cycle.
    val x = sin(t * 0.25f) * 0.5f + 0.5f
    return 0.05f + x * x * 14f
}

private val BUILTINS = listOf(
    Triple("none", "—", "None"),
    Triple("theme", "🎨", "Match theme"),
    Triple("flame", "🔥", "Flame"),
    Triple("glow", "✨", "Glow"),
    Triple("sparks", "⚡", "Sparks"),
    Triple("lottie", "✈️", "Lottie file"),
)
