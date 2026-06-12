package com.netspeed.indicator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.ui.hero.rememberReducedMotion
import kotlinx.coroutines.launch

/**
 * Three-card first-launch story: your speed becomes a scene → it lives in the
 * status bar → float it anywhere. Live scene canvas on card one so the very
 * first thing a user sees is the product's magic, moving.
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
            .padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Skip") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (page) {
                    0 -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(20.dp)),
                        ) {
                            SceneCanvas(SceneRegistry.ALL.first(), clockFn, liveMbps, dark = true)
                        }
                        Spacer(Modifier.size(28.dp))
                        Text("Your speed becomes a story", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "A snail when it crawls, a rocket when it flies — 11 living scenes driven by your real connection, on the hero, widgets, notification and bubble.",
                            fontSize = 14.sp, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    1 -> {
                        Text("📶", fontSize = 64.sp)
                        Spacer(Modifier.size(28.dp))
                        Text("Live speed in your status bar", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "A crisp per-second readout beside the clock — styles, units and colours all yours. No INTERNET permission: your data never leaves the device.",
                            fontSize = 14.sp, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    else -> {
                        Text("🫧", fontSize = 64.sp)
                        Spacer(Modifier.size(28.dp))
                        Text("Float it anywhere", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "A draggable bubble over any app — transparent scenes on your wallpaper, lockable size, pixel-perfect placement. Pick everything by eye in the Style Studio.",
                            fontSize = 14.sp, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
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
