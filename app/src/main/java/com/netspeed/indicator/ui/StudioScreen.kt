package com.netspeed.indicator.ui

import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.netspeed.indicator.billing.Entitlement
import com.netspeed.indicator.billing.FeatureGate
import com.netspeed.indicator.data.ColorSkin
import com.netspeed.indicator.data.HeroTheme
import com.netspeed.indicator.data.IconStyle
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.data.Settings
import com.netspeed.indicator.data.UnitStyle
import com.netspeed.indicator.render.WidgetData
import com.netspeed.indicator.render.WidgetKind
import com.netspeed.indicator.render.WidgetPainters
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.service.IconRenderer
import com.netspeed.indicator.ui.hero.rememberReducedMotion

/**
 * Style Studio — the marketplace: every customization option as a LIVE preview
 * card in category grids. Scenes/themes/skins play their real animations;
 * fonts/units/icon styles are real renderer output; widget cards are
 * pixel-identical [WidgetPainters] frames. See a card, tap it, own it.
 */
@Composable
fun StudioScreen(
    settings: Settings,
    live: LiveSpeed,
    suiteUnlocked: Boolean,
    onThemeSelect: (HeroTheme) -> Unit,
    onSkinSelect: (ColorSkin) -> Unit,
    onBubbleFx: (String) -> Unit,
    onBubbleFont: (String) -> Unit,
    onStyleSelect: (IconStyle) -> Unit,
    onIconUnitStyle: (UnitStyle) -> Unit,
    onPinWidget: (WidgetKind) -> Unit,
    onLockedTap: () -> Unit,
    onBack: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    fun tap() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)

    // One shared ~30 fps clock for every visible animated cell.
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
    val clockFn = remember { { clock } }
    val liveMbps = if (live.running) live.downMBps else 0f
    val ent = Entitlement(suiteUnlocked)
    // Fallback sample speeds so renderer cards always show something readable.
    val downBps = if (live.running && live.downBytesPerSec > 0) live.downBytesPerSec else 1_258_291L
    val upBps = if (live.running && live.upBytesPerSec > 0) live.upBytesPerSec else 245_760L

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Style Studio", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Every card is live — what you see is what you get",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1 · Speed scenes -------------------------------------------------
            header("🚀 Speed scenes", "Living dioramas driven by your real speed — hero, widgets, notification & bubble")
            items(SCENE_THEMES, key = { "t-${it.storageKey}" }) { theme ->
                val locked = !FeatureGate.themeAllowed(theme.ordinal, ent)
                val entry = SceneRegistry.fromThemeKey(theme.storageKey)
                StudioCardFrame(
                    selected = settings.heroTheme == theme,
                    locked = locked,
                    label = "${entry?.emoji ?: ""} ${theme.label}",
                    previewHeight = 86.dp,
                    onClick = { tap(); if (locked) onLockedTap() else onThemeSelect(theme) },
                ) { ThemeCanvas(theme, settings.colorSkin, clockFn, liveMbps) }
            }

            // 2 · Classic themes ----------------------------------------------
            header("🎬 Classic themes", "Gradients, gauges, particles — the motion library")
            items(CLASSIC_THEMES, key = { "t-${it.storageKey}" }) { theme ->
                val locked = !FeatureGate.themeAllowed(theme.ordinal, ent)
                StudioCardFrame(
                    selected = settings.heroTheme == theme,
                    locked = locked,
                    label = theme.label,
                    previewHeight = 86.dp,
                    onClick = { tap(); if (locked) onLockedTap() else onThemeSelect(theme) },
                ) { ThemeCanvas(theme, settings.colorSkin, clockFn, liveMbps) }
            }

            // 3 · Skins --------------------------------------------------------
            header("🎨 Skins", "One palette repaints the whole app — every card is your hero in it")
            items(ColorSkin.entries, key = { "s-${it.storageKey}" }) { skin ->
                val locked = !FeatureGate.skinAllowed(skin.ordinal, ent)
                val previewTheme = if (settings.heroTheme.isScene) HeroTheme.TIER_FLOW else settings.heroTheme
                StudioCardFrame(
                    selected = settings.colorSkin == skin,
                    locked = locked,
                    label = skin.label,
                    previewHeight = 86.dp,
                    onClick = { tap(); if (locked) onLockedTap() else onSkinSelect(skin) },
                ) {
                    SkinCanvas(skin, previewTheme, clockFn, liveMbps)
                    Text(
                        "8.4",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = if (skin.mono) androidx.compose.ui.text.font.FontFamily.Monospace
                        else androidx.compose.ui.text.font.FontFamily.Default,
                        color = skin.heroFg,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // 4 · Bubble animation ----------------------------------------------
            header("🫧 Bubble animation", "Plays beside the digits or as the bubble's living background")
            items(BUBBLE_BUILTINS, key = { "b-${it.first}" }) { (key, label) ->
                StudioCardFrame(
                    selected = settings.bubbleFx == key,
                    locked = !suiteUnlocked,
                    label = label,
                    previewHeight = 64.dp,
                    onClick = { tap(); if (!suiteUnlocked) onLockedTap() else onBubbleFx(key) },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) { Text(label.take(2), fontSize = 18.sp) }
                }
            }
            items(SceneRegistry.ALL, key = { "b-${it.id}" }) { entry ->
                StudioCardFrame(
                    selected = settings.bubbleFx == entry.id,
                    locked = !suiteUnlocked,
                    label = "${entry.emoji} ${entry.label}",
                    previewHeight = 64.dp,
                    onClick = { tap(); if (!suiteUnlocked) onLockedTap() else onBubbleFx(entry.id) },
                ) { SceneCanvas(entry, clockFn, liveMbps) }
            }

            // 5 · Bubble font ----------------------------------------------------
            header("🔤 Bubble font", "The real badge, rendered in each face")
            items(BUBBLE_FONTS, key = { "f-${it.first}" }) { (key, label) ->
                val bmp = remember(key, downBps, upBps, settings.bubbleBold) {
                    fontPreview(key, settings.bubbleBold, downBps, upBps)
                }
                StudioCardFrame(
                    selected = settings.bubbleFont == key,
                    locked = !suiteUnlocked,
                    label = label,
                    previewHeight = 56.dp,
                    onClick = { tap(); if (!suiteUnlocked) onLockedTap() else onBubbleFont(key) },
                ) { BitmapCard(bmp) }
            }

            // 6 · Status-bar icon style -----------------------------------------
            header("📟 Status-bar icon", "Exactly how the bar chip reads, in your colours")
            items(IconStyle.entries, key = { "i-${it.name}" }) { style ->
                val bmp = remember(style, downBps, upBps, settings.iconBgColor, settings.iconFgColor, settings.iconUnitStyle) {
                    iconPreview(style, settings, downBps, upBps)
                }
                StudioCardFrame(
                    selected = settings.iconStyle == style,
                    locked = false,
                    label = style.label,
                    previewHeight = 56.dp,
                    onClick = { tap(); onStyleSelect(style) },
                ) { BitmapCard(bmp, dark = true) }
            }

            // 7 · Unit display ---------------------------------------------------
            header("🔢 Unit display", "Short, full, or number-over-unit")
            items(UnitStyle.entries, key = { "u-${it.name}" }) { unit ->
                val bmp = remember(unit, downBps, settings.iconStyle, settings.iconBgColor, settings.iconFgColor) {
                    unitPreview(unit, settings, downBps, upBps)
                }
                StudioCardFrame(
                    selected = settings.iconUnitStyle == unit,
                    locked = false,
                    label = unit.label,
                    previewHeight = 56.dp,
                    onClick = { tap(); onIconUnitStyle(unit) },
                ) { BitmapCard(bmp, dark = true) }
            }

            // 8 · Widgets --------------------------------------------------------
            header("🏠 Widgets", "Pixel-identical to what lands on your home screen — tap to pin")
            items(WidgetKind.entries, key = { "w-${it.name}" }) { kind ->
                val data = widgetDataFrom(settings, live)
                val bmp = remember(kind, live.downBytesPerSec, settings.heroTheme, settings.colorSkin) {
                    WidgetPainters.render(kind, 480, if (kind == WidgetKind.HERO) 222 else 300, data)
                        .asImageBitmap()
                }
                StudioCardFrame(
                    selected = false,
                    locked = !suiteUnlocked,
                    label = WIDGET_LABELS[kind] ?: kind.name,
                    previewHeight = 86.dp,
                    onClick = { tap(); if (!suiteUnlocked) onLockedTap() else onPinWidget(kind) },
                ) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                    )
                }
            }
        }
    }
}

/** Full-span category header. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.header(title: String, sub: String) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "h-$title") {
        Column(Modifier.padding(top = 18.dp, bottom = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        }
    }
}

/** Renderer bitmap centered on a slot-like backdrop. */
@Composable
private fun BitmapCard(bmp: androidx.compose.ui.graphics.ImageBitmap, dark: Boolean = false) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (dark) androidx.compose.ui.graphics.Color(0xFF101218)
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.7f),
            contentScale = ContentScale.Fit,
        )
    }
}

/** The service's bubble typeface mapping, mirrored for previews. */
private fun bubbleTypeface(fontKey: String, bold: Boolean): Typeface {
    val family = when (fontKey) {
        "condensed" -> "sans-serif-condensed"
        "serif" -> "serif"
        "mono" -> "monospace"
        else -> if (bold) "sans-serif-black" else "sans-serif"
    }
    return Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
}

private fun fontPreview(fontKey: String, bold: Boolean, down: Long, up: Long) =
    IconRenderer(sizePx = 120).apply {
        colorTrue = true
        bgColorArgb = 0xE6101218.toInt()
        glyphTypeface = bubbleTypeface(fontKey, bold)
    }.renderChip(IconStyle.AUTO, down, up, showCombined = false).asImageBitmap()

private fun iconPreview(style: IconStyle, s: Settings, down: Long, up: Long) =
    IconRenderer(sizePx = 144).apply {
        colorTrue = true
        bgColorArgb = s.iconBgColor
        fgColorArgb = if (android.graphics.Color.alpha(s.iconFgColor) != 0) s.iconFgColor
        else android.graphics.Color.WHITE
        unitStyle = s.iconUnitStyle
        borderColorArgb = s.iconBorderColor
        borderWidth = s.iconBorderWidth
        userScale = s.iconTextScale
    }.render(style, down, up, s.showCombined).asImageBitmap()

private fun unitPreview(unit: UnitStyle, s: Settings, down: Long, up: Long) =
    IconRenderer(sizePx = 144).apply {
        colorTrue = true
        bgColorArgb = s.iconBgColor
        fgColorArgb = if (android.graphics.Color.alpha(s.iconFgColor) != 0) s.iconFgColor
        else android.graphics.Color.WHITE
        unitStyle = unit
        userScale = s.iconTextScale
    }.render(IconStyle.AUTO, down, up, showCombined = false).asImageBitmap()

/** Mirrors the service's WidgetData build so cards match the real widgets. */
private fun widgetDataFrom(s: Settings, live: LiveSpeed): WidgetData = WidgetData(
    downBps = if (live.running && live.downBytesPerSec > 0) live.downBytesPerSec else 1_258_291L,
    upBps = if (live.running && live.upBytesPerSec > 0) live.upBytesPerSec else 245_760L,
    todayBytes = live.todayBytes,
    peakBps = live.peakBytesPerSec,
    dailyQuotaBytes = s.dailyQuotaBytes,
    accentArgb = if (s.colorSkin != ColorSkin.TIER) s.colorSkin.accent.toArgb() else 0,
    gradientArgb = if (s.colorSkin != ColorSkin.TIER) s.colorSkin.heroColors.map { it.toArgb() } else emptyList(),
    themeKey = s.heroTheme.storageKey,
    heroFgArgb = s.colorSkin.heroFg.toArgb(),
    tierThresholds = s.tierThresholds,
)

private val SCENE_THEMES = HeroTheme.entries.filter { it.isScene }
private val CLASSIC_THEMES = HeroTheme.entries.filter { !it.isScene }
private val BUBBLE_BUILTINS = listOf(
    "none" to "— None",
    "theme" to "🎨 Match theme",
    "flame" to "🔥 Flame",
    "glow" to "✨ Glow",
    "sparks" to "⚡ Sparks",
    "lottie" to "✈️ Lottie file",
)
private val BUBBLE_FONTS = listOf(
    "sans" to "Sans",
    "condensed" to "Condensed",
    "serif" to "Serif",
    "mono" to "Mono",
)
private val WIDGET_LABELS = mapOf(
    WidgetKind.HERO to "Hero banner",
    WidgetKind.DIAL to "Dial",
    WidgetKind.RINGS to "Rings",
    WidgetKind.PILL to "Pill",
    WidgetKind.WEATHER to "Weather",
)
