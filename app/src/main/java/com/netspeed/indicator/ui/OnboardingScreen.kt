package com.netspeed.indicator.ui

import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.netspeed.indicator.data.IconStyle
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.data.UnitStyle
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.service.IconRenderer
import com.netspeed.indicator.ui.hero.rememberReducedMotion
import kotlinx.coroutines.launch

// Demo traffic for every rendered preview — a believable mid-range moment.
private const val DEMO_DOWN = 8_651_234L     // ≈ 8.3 MB/s
private const val DEMO_UP = 1_153_433L       // ≈ 1.1 MB/s

/**
 * Three-card first-launch story, every visual a REAL render from the production
 * pipelines (scenes, IconRenderer chips, bubble badges) — the user sees the
 * actual product and its customisation axes, not illustrations.
 */
@Composable
fun OnboardingScreen(
    live: LiveSpeed,
    onDone: () -> Unit,
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
                if (last == 0L) last = t
                else {
                    val dt = (t - last) / 1_000_000_000f
                    if (dt >= 0.03f) { clock += dt; last = t }
                }
            }
        }
    }
    val clockFn = remember { { clock } }
    val liveMbps = if (live.running) live.downMBps else 0f
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Skip") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (page) {
                    0 -> ScenesPage(clockFn, liveMbps)
                    1 -> StatusBarPage()
                    else -> BubblePage(clockFn, liveMbps)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        ) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(if (pager.currentPage == i) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pager.currentPage == i) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        ),
                )
            }
        }
        Button(
            onClick = {
                if (pager.currentPage < 2) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                else onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (pager.currentPage < 2) "Next" else "Start →")
        }
    }
}

// ---------------------------------------------------------------- page 1

@Composable
private fun ScenesPage(clockFn: () -> Float, liveMbps: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        SceneCanvas(SceneRegistry.ALL.first(), clockFn, liveMbps, dark = true)
    }
    Spacer(Modifier.size(24.dp))
    PageTitle("Your speed becomes a story")
    PageSub("A snail when it crawls, a rocket when it flies — live on the hero, widgets, notification and bubble.")
    Spacer(Modifier.size(14.dp))
    Bullet("11 living scenes, driven by your real connection")
    Bullet("26 themes × 6 colour skins, all previewed live")
    Bullet("Pick everything by eye in the Style Studio")
}

// ---------------------------------------------------------------- page 2

@Composable
private fun StatusBarPage() {
    // Real chips from the production IconRenderer — exactly what lands beside
    // the clock, in four of the style / unit combinations.
    val barChip = remember { barChipImage(IconStyle.AUTO, UnitStyle.SHORT) }
    val styleChips = remember {
        listOf(
            "Arrows ↕" to barChipImage(IconStyle.ARROWS, UnitStyle.SHORT),
            "Arrows ↔" to barChipImage(IconStyle.ARROWS_H, UnitStyle.SHORT),
            "Stacked" to barChipImage(IconStyle.STACKED, UnitStyle.SHORT),
            "Full unit" to barChipImage(IconStyle.AUTO, UnitStyle.FULL),
        )
    }

    // Status-bar mock: clock left, our chip among the system icons.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF15171C))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("12:30", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Image(barChip, contentDescription = null, modifier = Modifier.height(24.dp))
        Spacer(Modifier.size(8.dp))
        Text("📶 🔋", fontSize = 13.sp)
    }
    Spacer(Modifier.size(10.dp))
    // Style shelf: the SAME renderer with different style / unit options.
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        styleChips.forEach { (label, img) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(vertical = 10.dp),
            ) {
                Image(img, contentDescription = label, modifier = Modifier.height(26.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    label, fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
    Spacer(Modifier.size(22.dp))
    PageTitle("Live speed in your status bar")
    PageSub("A crisp per-second readout beside the clock — these previews are the real renderer.")
    Spacer(Modifier.size(14.dp))
    Bullet("5 icon styles × 3 unit formats, any colours & text size")
    Bullet("Optional details row in the notification panel")
    Bullet("Auto-hide when idle · updates with the screen off")
    Bullet("No INTERNET permission — data never leaves the device")
}

// ---------------------------------------------------------------- page 3

@Composable
private fun BubblePage(clockFn: () -> Float, liveMbps: Float) {
    // Three real bubble faces over a wallpaper: solid badge, transparent
    // scene-as-badge, and a compact locked-size mono badge.
    val solid = remember { bubbleBadgeImage(Typeface.SANS_SERIF, bold = true) }
    val mono = remember { bubbleBadgeImage(Typeface.MONOSPACE, bold = false) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(listOf(Color(0xFF3E6FD8), Color(0xFF7B5BD6))),
            ),
    ) {
        Image(
            solid, contentDescription = "Bubble badge",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 18.dp, y = 18.dp)
                .height(34.dp),
        )
        // Scene placement "Background": the animation IS the badge.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-18).dp)
                .width(132.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp)),
        ) {
            SceneCanvas(SceneRegistry.ALL.first(), clockFn, liveMbps, dark = true)
            Text(
                "↓ 8.3 MB/s",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = Shadow(Color.Black, blurRadius = 8f)),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Image(
            mono, contentDescription = "Compact badge",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 38.dp, y = (-20).dp)
                .height(26.dp),
        )
        Text(
            "drag me ✥",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        )
    }
    Spacer(Modifier.size(22.dp))
    PageTitle("Float it anywhere")
    PageSub("A draggable speed bubble over any app — these are real badge renders, three of the looks you can build.")
    Spacer(Modifier.size(14.dp))
    Bullet("Drag anywhere, dock to any edge — tap it to open NetSpeed")
    Bullet("Lock the size, tune width & height, pick 4 fonts")
    Bullet("Scene animations beside the text — or AS the badge")
    Bullet("Truly transparent background floats on your wallpaper")
}

// ---------------------------------------------------------------- shared

@Composable
private fun PageTitle(s: String) {
    Text(
        s, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.size(10.dp))
}

@Composable
private fun PageSub(s: String) {
    Text(
        s, fontSize = 14.sp, textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

@Composable
private fun Bullet(s: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        Spacer(Modifier.size(8.dp))
        Text(
            s, fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
    }
}

/** Status-bar chip via the production renderer (colour-true path). */
private fun barChipImage(style: IconStyle, unit: UnitStyle) =
    IconRenderer(sizePx = 120).apply {
        colorTrue = true
        bgColorArgb = 0xE6101218.toInt()
        fgColorArgb = android.graphics.Color.WHITE
        unitStyle = unit
    }.render(style, DEMO_DOWN, DEMO_UP, showCombined = false).asImageBitmap()

/** Floating-bubble face via the production chip renderer. */
private fun bubbleBadgeImage(tf: Typeface, bold: Boolean) =
    IconRenderer(sizePx = 120).apply {
        colorTrue = true
        bgColorArgb = 0xE6101218.toInt()
        glyphTypeface = if (bold) Typeface.create(tf, Typeface.BOLD) else tf
    }.renderChip(IconStyle.AUTO, DEMO_DOWN, DEMO_UP, showCombined = false).asImageBitmap()
