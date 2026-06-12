package com.netspeed.indicator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import com.netspeed.indicator.BuildConfig
import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.data.ColorSkin
import com.netspeed.indicator.data.HeroTheme
import com.netspeed.indicator.data.IconStyle
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.data.Settings
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.service.IconRenderer
import com.netspeed.indicator.service.SpeedFormatter
import com.netspeed.indicator.ui.hero.TierFlowHero

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    live: LiveSpeed,
    hasNotificationPermission: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onMasterToggle: (Boolean) -> Unit,
    onCombinedToggle: (Boolean) -> Unit,
    onScreenOffToggle: (Boolean) -> Unit,
    onAutoHideToggle: (Boolean) -> Unit,
    onFloatingChipToggle: (Boolean) -> Unit,
    onFloatingChipScale: (Float) -> Unit,
    onHideIconWhenBubble: (Boolean) -> Unit,
    onBubbleFreePlacement: (Boolean) -> Unit,
    onResetBubblePos: () -> Unit,
    onBubbleNudge: (dxPx: Int, dyPx: Int) -> Unit,
    onBubblePreset: (corner: BubbleCorner) -> Unit,
    onFloatingChipPadScale: (Float) -> Unit,
    onBubbleBold: (Boolean) -> Unit,
    onBubbleFont: (String) -> Unit,
    onBubbleTracking: (Float) -> Unit,
    onBubbleFx: (String) -> Unit,
    onPickLottieFile: () -> Unit,
    onBubbleFxPlacement: (String) -> Unit,
    onClearLottieFile: () -> Unit,
    onBubbleLockSize: (Boolean) -> Unit,
    onBubbleBoxW: (Int) -> Unit,
    onBubbleBoxH: (Int) -> Unit,
    dailyHistory: List<com.netspeed.indicator.data.DayUsage>,
    onStyleSelect: (IconStyle) -> Unit,
    onPanelToggle: (Boolean) -> Unit,
    onThemeSelect: (HeroTheme) -> Unit,
    onSkinSelect: (ColorSkin) -> Unit,
    onIconBgColor: (Int) -> Unit,
    onIconFgColor: (Int) -> Unit,
    onIconTextScale: (Float) -> Unit,
    onIconUnitStyle: (com.netspeed.indicator.data.UnitStyle) -> Unit,
    onIconBorderColor: (Int) -> Unit,
    onIconBorderWidth: (Int) -> Unit,
    onPinWidget: (com.netspeed.indicator.render.WidgetKind) -> Unit,
    suiteUnlocked: Boolean,
    onLockedTap: () -> Unit,
    onThresholdsChange: (List<Float>) -> Unit,
    onNamesChange: (List<String>) -> Unit,
    onQuotaChange: (Long) -> Unit,
    onGrantNotifications: () -> Unit,
    onRequestIgnoreBattery: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val skin = settings.colorSkin
    // Light haptic on every interaction — elevates the feel without being noisy.
    val view = LocalView.current
    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).dp

    // Subtle haptic "ticks" while scrolling — a textured, premium feel.
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState) {
        var lastTickAt = 0
        snapshotFlow { scrollState.value }.collect { v ->
            if (kotlin.math.abs(v - lastTickAt) >= 110) {
                lastTickAt = v
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    // Surface sets LocalContentColor = onSurface, so every Text WITHOUT an explicit
    // colour renders with proper contrast (light on dark, dark on light). Without
    // this, unstyled text defaults to black and vanishes on dark skins.
    Surface(
        color = skin.bg(dark),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        // Edge-to-edge animated hero — the speed number is the subject of the screen.
        TierFlowHero(
            live = live,
            theme = settings.heroTheme,
            skin = skin,
            thresholds = settings.thresholdsArray(),
            tierNames = settings.tierNames,
            modifier = Modifier.height(heroHeight),
        )
        TierScaleBar(
            live = live,
            thresholds = settings.thresholdsArray(),
            tierNames = settings.tierNames,
            skin = skin,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
        LiveThemeRow(
            selected = settings.heroTheme,
            unlocked = suiteUnlocked,
            onSelect = { tap(); onThemeSelect(it) },
            onLocked = onLockedTap,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        SkinRow(
            selected = settings.colorSkin,
            unlocked = suiteUnlocked,
            onSelect = { tap(); onSkinSelect(it) },
            onLocked = onLockedTap,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // Right under the live hero: add ANY of the 5 widget styles straight to the
        // home screen (one tap → launcher's pin prompt). No digging in menus.
        AddToHomeRow(
            locked = !suiteUnlocked,
            onPin = { tap(); onPinWidget(it) },
            onLocked = onLockedTap,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // Toggle thumbs adopt the accent: the live tier colour for the TIER skin,
        // or the fixed skin accent otherwise.
        val tierColor = when {
            skin != ColorSkin.TIER -> skin.accent
            live.running -> SpeedTiers.tierOf(live.downMBps).c2
            else -> MaterialTheme.colorScheme.primary
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // Persistent banner when notifications (our display surface) are off.
            AnimatedVisibility(visible = settings.enabled && !hasNotificationPermission) {
                PermissionBanner(onGrant = onGrantNotifications)
            }

            ToggleRow(
                title = "Show speed in status bar",
                subtitle = "Live download speed as a status-bar icon",
                checked = settings.enabled,
                onCheckedChange = { tap(); onMasterToggle(it) },
                tierColor = tierColor,
            )
            ToggleRow(
                title = "Show upload too",
                subtitle = if (settings.showCombined) "Icon shows download + upload combined"
                else "Icon shows download only",
                checked = settings.showCombined,
                onCheckedChange = { tap(); onCombinedToggle(it) },
                tierColor = tierColor,
            )

            Hairline()
            IconStyleCard(
                selected = settings.iconStyle,
                showCombined = settings.showCombined,
                live = live,
                iconBg = settings.iconBgColor,
                iconFg = settings.iconFgColor,
                iconTextScale = settings.iconTextScale,
                unitStyle = settings.iconUnitStyle,
                borderColor = settings.iconBorderColor,
                borderWidth = settings.iconBorderWidth,
                onSelect = { tap(); onStyleSelect(it) },
                onBgColor = { tap(); onIconBgColor(it) },
                onFgColor = { tap(); onIconFgColor(it) },
                onTextScale = onIconTextScale,
                onUnitStyle = onIconUnitStyle,
                onBorderColor = onIconBorderColor,
                onBorderWidth = onIconBorderWidth,
                customColorUnlocked = suiteUnlocked,
                onLockedColor = onLockedTap,
            )
            Hairline()
            ToggleRow(
                title = "Show details in notification panel",
                subtitle = if (settings.showInPanel) "Panel shows download, upload and today's total"
                else "Minimal: a thin silent row, just the status-bar icon",
                checked = settings.showInPanel,
                onCheckedChange = { tap(); onPanelToggle(it) },
                tierColor = tierColor,
            )
            if (!settings.showInPanel) {
                MinimalCardHelp(onOpenNotificationSettings = onOpenNotificationSettings)
            }
            ToggleRow(
                title = "Update while screen off",
                subtitle = "Keeps sampling with the screen off. Uses more battery.",
                checked = settings.updateWhileScreenOff,
                onCheckedChange = { tap(); onScreenOffToggle(it) },
                tierColor = tierColor,
            )
            ToggleRow(
                title = "Hide icon when idle",
                subtitle = "Icon disappears after 30 s without traffic; the panel row stays.",
                checked = settings.autoHideIdle,
                onCheckedChange = { tap(); onAutoHideToggle(it) },
                tierColor = tierColor,
            )
            ToggleRow(
                title = if (suiteUnlocked) "Floating speed bubble" else "Floating speed bubble 🔒",
                subtitle = if (suiteUnlocked)
                    "Draggable chip over any app — our own surface, always legible. Tap it to open NetSpeed."
                else "Premium — a draggable speed chip over any app. Tap to unlock the suite.",
                checked = settings.floatingChip && suiteUnlocked,
                onCheckedChange = { if (suiteUnlocked) { tap(); onFloatingChipToggle(it) } else onLockedTap() },
                tierColor = tierColor,
            )
            if (settings.floatingChip && suiteUnlocked) {
                ToggleRow(
                    title = "Hide status-bar icon while bubble is shown",
                    subtitle = "No double display. The notification row stays (Android requires it); only the icon disappears.",
                    checked = settings.hideIconWhenBubble,
                    onCheckedChange = { tap(); onHideIconWhenBubble(it) },
                    tierColor = tierColor,
                )
                ToggleRow(
                    title = "Place bubble anywhere",
                    subtitle = "Dock it over the status bar or half-off any edge. Heads-up: over the " +
                        "status bar Android steals the touches — use Reset below to recover it.",
                    checked = settings.bubbleFreePlacement,
                    onCheckedChange = { tap(); onBubbleFreePlacement(it) },
                    tierColor = tierColor,
                )
                BubblePositionPad(
                    posX = settings.floatingChipX,
                    posY = settings.floatingChipY,
                    onNudge = { dx, dy -> tap(); onBubbleNudge(dx, dy) },
                    onPreset = { tap(); onBubblePreset(it) },
                )
                OutlinedButton(
                    onClick = { tap(); onResetBubblePos() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Reset bubble position")
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Bubble size",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(settings.floatingChipScale * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                    Slider(
                        value = settings.floatingChipScale,
                        onValueChange = { onFloatingChipScale(((it * 20f).toInt() / 20f)) },
                        valueRange = 0.5f..1.6f,
                        steps = 21,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Bubble width",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(settings.floatingChipPadScale * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                    Slider(
                        value = settings.floatingChipPadScale,
                        onValueChange = { onFloatingChipPadScale(((it * 20f).toInt() / 20f)) },
                        valueRange = 1f..2.5f,
                        steps = 29,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Letter spacing",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%.2f".format(settings.bubbleTracking),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                    Slider(
                        value = settings.bubbleTracking,
                        onValueChange = { onBubbleTracking(((it * 100f).toInt() / 100f)) },
                        valueRange = 0f..0.12f,
                        steps = 11,
                    )
                }
                ToggleRow(
                    title = "Lock bubble size",
                    subtitle = "Freezes the badge at its CURRENT size — text auto-scales to fill it. Tune from tiny to full-width below.",
                    checked = settings.bubbleLockSize,
                    onCheckedChange = { tap(); onBubbleLockSize(it) },
                    tierColor = tierColor,
                )
                if (settings.bubbleLockSize) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Badge width", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                            Text(if (settings.bubbleBoxW == 0) "current" else "${settings.bubbleBoxW} dp", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                        Slider(
                            value = if (settings.bubbleBoxW == 0) 120f else settings.bubbleBoxW.toFloat(),
                            onValueChange = { onBubbleBoxW((it / 4f).toInt() * 4) },
                            valueRange = 32f..400f,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Badge height", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                            Text(if (settings.bubbleBoxH == 0) "current" else "${settings.bubbleBoxH} dp", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                        }
                        Slider(
                            value = if (settings.bubbleBoxH == 0) 40f else settings.bubbleBoxH.toFloat(),
                            onValueChange = { onBubbleBoxH((it / 2f).toInt() * 2) },
                            valueRange = 20f..200f,
                        )
                    }
                }
                ToggleRow(
                    title = "Bold bubble text",
                    subtitle = "Off = lighter, leaner glyphs for a smaller badge.",
                    checked = settings.bubbleBold,
                    onCheckedChange = { tap(); onBubbleBold(it) },
                    tierColor = tierColor,
                )
                ChipPickRow(
                    label = "Bubble font",
                    options = listOf("sans" to "Sans", "condensed" to "Condensed", "serif" to "Serif", "mono" to "Mono"),
                    selected = settings.bubbleFont,
                    onPick = { tap(); onBubbleFont(it) },
                )
                ScenePreviewRow(
                    label = "Bubble animation — reacts to your live speed",
                    selected = settings.bubbleFx,
                    live = live,
                    onPick = { tap(); onBubbleFx(it) },
                )
                val sceneActive = settings.bubbleFx == "lottie" ||
                    SceneRegistry.isScene(settings.bubbleFx)
                if (sceneActive) {
                    if (settings.bubbleFx == "lottie") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { tap(); onPickLottieFile() }, modifier = Modifier.weight(1f)) {
                                Text(if (settings.bubbleLottieUri.isEmpty()) "Animation file… (Lottie .json)" else "Change animation file…", fontSize = 12.sp)
                            }
                            if (settings.bubbleLottieUri.isNotEmpty()) {
                                TextButton(onClick = { tap(); onClearLottieFile() }) { Text("Built-in ✈️", fontSize = 12.sp) }
                            }
                        }
                        Text(
                            "Any Lottie animation plays inside the bubble, speed-mapped: it ambles when idle and races while you download. Thousands of free .json scenes online.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                    ChipPickRow(
                        label = "Scene placement",
                        options = listOf("left" to "⬅ Left of text", "behind" to "🎞 Background", "right" to "➡ Right of text"),
                        selected = settings.bubbleFxPlacement,
                        onPick = { tap(); onBubbleFxPlacement(it) },
                    )
                    Text(
                        "Background + transparent icon background = the animation IS the badge; the digits get a soft shadow so they stay readable.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    )
                }
            }

            Hairline()
            UsageHistorySection(history = dailyHistory)

            Hairline()
            TierEditor(
                settings = settings,
                onThresholdsChange = onThresholdsChange,
                onNamesChange = onNamesChange,
                onQuotaChange = onQuotaChange,
            )

            Hairline()
            VisibilityCard(onOpenNotificationSettings = onOpenNotificationSettings)

            Hairline()
            BatterySection(
                isIgnoring = isIgnoringBatteryOptimizations,
                onRequestIgnoreBattery = onRequestIgnoreBattery,
            )

            Hairline()
            AboutSection()

            Spacer(Modifier.size(28.dp))
        }
    }
    }
}

/**
 * Collapsible 30-day usage history (newest first): date · total · proportional
 * bar. Builds up one row per finished day; until then shows a one-line hint.
 */
@Composable
private fun UsageHistorySection(history: List<com.netspeed.indicator.data.DayUsage>) {
    var expanded by remember { mutableStateOf(false) }
    val view = LocalView.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = expanded, onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                expanded = !expanded
            })
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Usage history", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (history.isEmpty()) "History builds up day by day."
                    else "Last ${history.size} day${if (history.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Text(if (expanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        if (expanded && history.isNotEmpty()) {
            val maxBytes = history.maxOf { it.bytes }.coerceAtLeast(1L)
            val accent = MaterialTheme.colorScheme.primary
            history.sortedByDescending { it.epochDay }.forEach { day ->
                val date = java.time.LocalDate.ofEpochDay(day.epochDay)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")),
                        fontSize = 12.sp,
                        modifier = Modifier.width(92.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(day.bytes.toFloat() / maxBytes)
                                .background(accent, RoundedCornerShape(4.dp)),
                        )
                    }
                    Text(
                        SpeedFormatter.total(day.bytes),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 10.dp),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/** Single 1dp separator at 12% foreground alpha — the only divider in the design. */
@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    )
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Notifications are off",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = "The speed indicator is drawn as a notification icon, so it can't show until you allow notifications.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        TextButton(onClick = onGrant, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("Grant")
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tierColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = tierColor,
                checkedBorderColor = tierColor,
            ),
        )
    }
}

// LivePreviewCard removed — the edge-to-edge hero is now the live preview.

/**
 * Honest explainer shown when the user has chosen the minimal notification. A
 * foreground service is *required* to keep one notification, and its status-bar
 * icon and shade card are the same object — removing the card removes the icon.
 * So instead of faking a "hide" we explain it and point at the channel settings,
 * where One UI files our LOW/silent row into the collapsed "silent" section.
 */
@Composable
private fun MinimalCardHelp(onOpenNotificationSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        run {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Want the card fully gone?",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = "Android requires a foreground service to keep one notification — " +
                    "and its status-bar icon and this card are the same thing, so removing " +
                    "the card would remove your speed icon too. With details off it's already " +
                    "the thinnest possible row, and your phone tucks it into the silent section.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text("How to tuck it away")
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1. Pull down the notification panel.", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "2. Long-press the \"Network speed\" row and drag it into the " +
                            "Silent / minimised section, or open its settings below.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "3. Don't fully turn off the notification — that also hides the " +
                            "status-bar icon.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(2.dp))
                    OutlinedButton(onClick = onOpenNotificationSettings) {
                        Text("Open this app's notification settings")
                    }
                }
            }
        }
    }
}

/**
 * Helps when the OEM collapses status-bar icons to a dot. We can't override that
 * global setting from code, so we explain it honestly and deep-link the user to
 * notification settings, with the exact per-OEM path expandable below.
 */
@Composable
private fun VisibilityCard(onOpenNotificationSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val steps = remember { OemHints.statusBarIconStepsForCurrentDevice() }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        run {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Visibility, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Icon not showing? (status-bar dot)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = "Some phones (Samsung One UI, MIUI and others) collapse all " +
                    "notification icons into a single dot. No app can override that " +
                    "system display setting — flip it once and the speed icon shows for good.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenNotificationSettings) {
                Text("Open notification settings")
            }
            OutlinedButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text("Show the exact steps")
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    steps.forEachIndexed { i, step ->
                        Text(
                            text = "${i + 1}. $step",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatterySection(
    isIgnoring: Boolean,
    onRequestIgnoreBattery: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val guidance = remember { OemHints.forCurrentDevice() }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        run {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ShieldMoon, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(text = "Keep it running", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Aggressive battery managers can kill the indicator. Whitelist it to keep speed showing reliably.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isIgnoring) {
                Text(
                    text = "Battery optimisation already disabled ✓",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(onClick = onRequestIgnoreBattery) {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Ignore battery optimisations")
                }
            }

            if (guidance != null) {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Steps for ${guidance.vendor}")
                }
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        guidance.steps.forEachIndexed { i, step ->
                            Text(
                                text = "${i + 1}. $step",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Style picker. Each option shows a **real** icon bitmap produced by the same
 * [IconRenderer] the service uses, drawn with sample speeds on a dark pill (so
 * the white glyph is visible the way it is on a status bar). The preview honours
 * the user's current download-only / combined choice, so what they see is what
 * they'll get. Bitmaps are remembered keyed by (style, combined) to avoid
 * re-rendering on every recomposition.
 */
private val STYLE_DESCRIPTIONS = mapOf(
    IconStyle.ARROWS to "Direction + rate, most readable",
    IconStyle.STACKED to "Number on top, unit below",
    IconStyle.COMPACT to "Single bold number, least space",
    IconStyle.AUTO to "Busier direction's arrow + speed, one at a time",
)

@Composable
private fun IconStyleCard(
    selected: IconStyle,
    showCombined: Boolean,
    live: LiveSpeed,
    iconBg: Int,
    iconFg: Int,
    iconTextScale: Float,
    unitStyle: com.netspeed.indicator.data.UnitStyle,
    borderColor: Int,
    borderWidth: Int,
    onSelect: (IconStyle) -> Unit,
    onBgColor: (Int) -> Unit,
    onFgColor: (Int) -> Unit,
    onTextScale: (Float) -> Unit,
    onUnitStyle: (com.netspeed.indicator.data.UnitStyle) -> Unit,
    onBorderColor: (Int) -> Unit,
    onBorderWidth: (Int) -> Unit,
    customColorUnlocked: Boolean,
    onLockedColor: () -> Unit,
) {
    val down = if (live.running) live.downBytesPerSec else 1_258_291L
    val up = if (live.running) live.upBytesPerSec else 245_760L
    val renderer = remember { IconRenderer(sizePx = 144) }
    // Previews honour the chosen icon colours + size, so what you pick is what shows.
    // The side-by-side style ships as TWO status-bar icons (down + up) — unless the
    // OS force-groups them (One UI), in which case the service falls back to one
    // wide icon and the preview mirrors that.
    val dualOk = !live.dualIconsBlocked
    val previews = remember(
        down, up, showCombined, iconBg, iconFg, iconTextScale, dualOk,
        unitStyle, borderColor, borderWidth,
    ) {
        renderer.bgColorArgb = iconBg
        renderer.fgColorArgb = iconFg
        renderer.userScale = iconTextScale
        renderer.unitStyle = unitStyle
        renderer.borderColorArgb = borderColor
        renderer.borderWidth = borderWidth
        // Colour-true previews: glyphs painted in the chosen colour on top (like
        // the bubble), so every colour pick is visible here. The REAL status-bar
        // bitmap stays punch-out — the OS tints it monochrome regardless.
        renderer.colorTrue = true
        IconStyle.entries.associateWith { style ->
            if (style == IconStyle.ARROWS_H && showCombined && dualOk) {
                listOf(
                    renderer.renderSingle(down, down = true).asImageBitmap(),
                    renderer.renderSingle(up, down = false).asImageBitmap(),
                )
            } else {
                listOf(renderer.render(style, down, up, showCombined).asImageBitmap())
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        run {
            Text(text = "Icon style", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Pick how the speed looks in the status bar. Colours always show in full " +
                    "here and on the floating bubble. In the status bar it depends on the phone: " +
                    "some (e.g. Samsung) show your true colours; others (e.g. Pixel) repaint every " +
                    "app's icon in one colour — that's Android, not the app, and applies on every " +
                    "screen. On those phones, drag the floating bubble into the bar for full colour.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            IconStyle.entries.forEach { style ->
                StyleOptionRow(
                    style = style,
                    preview = previews[style]!!,
                    selected = style == selected,
                    onClick = { onSelect(style) },
                )
            }
            UnitStyleRow(selected = unitStyle, onPick = onUnitStyle)
            ColorSwatchRow("Icon background", BG_SWATCHES, iconBg, allowAlpha = true, customUnlocked = customColorUnlocked, onLockedCustom = onLockedColor, onPick = onBgColor)
            ColorSwatchRow("Text / icon colour", FG_SWATCHES, iconFg, allowAlpha = false, customUnlocked = customColorUnlocked, onLockedCustom = onLockedColor, onPick = onFgColor)
            ColorSwatchRow("Outline", BORDER_SWATCHES, borderColor, allowAlpha = false, customUnlocked = customColorUnlocked, onLockedCustom = onLockedColor, onPick = onBorderColor)
            if (android.graphics.Color.alpha(borderColor) != 0) {
                BorderWidthRow(selected = borderWidth, onPick = onBorderWidth)
            }
            IconTextSizeRow(value = iconTextScale, onChange = onTextScale)
        }
    }
}

/** "Unit display" — short 84k / full 84 KB/s / unit below the number. */
@Composable
private fun UnitStyleRow(
    selected: com.netspeed.indicator.data.UnitStyle,
    onPick: (com.netspeed.indicator.data.UnitStyle) -> Unit,
) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Unit display",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.netspeed.indicator.data.UnitStyle.entries.forEach { style ->
                val on = style == selected
                Text(
                    style.label,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                        .selectable(selected = on, onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onPick(style)
                        })
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Outline width chips — shown only while an outline colour is active. */
@Composable
private fun BorderWidthRow(selected: Int, onPick: (Int) -> Unit) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Outline width",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..3).forEach { w ->
                val on = w == selected
                Text(
                    "${w}px",
                    fontSize = 12.sp,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                        .selectable(selected = on, onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onPick(w)
                        })
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private val BORDER_SWATCHES = listOf(
    0 to "None",
    0xFFFFFFFF.toInt() to "White",
    0xFF111318.toInt() to "Black",
    0xFF2563EB.toInt() to "Blue",
    0xFF10B981.toInt() to "Green",
    0xFFEC4899.toInt() to "Pink",
)

/**
 * "Icon text size" slider. Lets the user scale the status-bar glyph above/below the
 * system default. 1.0 = matches the system font size; the renderer fills the icon
 * slot at 1.0 and the OS slot height is the hard ceiling beyond that.
 */
@Composable
private fun IconTextSizeRow(value: Float, onChange: (Float) -> Unit) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Icon text size",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(value * 100).toInt()}%",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
        Slider(
            value = value,
            onValueChange = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onChange((it * 20f).toInt() / 20f)   // snap to 5% steps
            },
            valueRange = 0.8f..1.4f,
            steps = 11,
        )
    }
}

private val BG_SWATCHES = listOf(
    0 to "None",                               // transparent
    0xFF101218.toInt() to "Dark",
    0xFFFFFFFF.toInt() to "White",
    0xCC000000.toInt() to "Black",
    0xFF2563EB.toInt() to "Blue",
    0xFF10B981.toInt() to "Green",
)
private val FG_SWATCHES = listOf(
    0xFFFFFFFF.toInt() to "White",
    0xFF111318.toInt() to "Black",
    0xFF2563EB.toInt() to "Blue",
    0xFF10B981.toInt() to "Green",
    0xFFEC4899.toInt() to "Pink",
)

/**
 * A labelled row of colour swatches plus a "custom" swatch (rainbow) that opens
 * the hex colour picker. The selected swatch — preset or custom — gets a ring.
 */
@Composable
private fun ColorSwatchRow(
    label: String,
    swatches: List<Pair<Int, String>>,
    selected: Int,
    allowAlpha: Boolean,
    customUnlocked: Boolean,
    onLockedCustom: () -> Unit,
    onPick: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val isPreset = swatches.any { it.first == selected }
    val view = LocalView.current
    val pick: (Int) -> Unit = { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onPick(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            swatches.forEach { (argb, _) ->
                Swatch(
                    fill = if (android.graphics.Color.alpha(argb) == 0) MaterialTheme.colorScheme.surface else Color(argb),
                    selected = argb == selected,
                    glyph = if (android.graphics.Color.alpha(argb) == 0) "∅" else null,
                    onClick = { pick(argb) },
                )
            }
            // Custom (rainbow) swatch — opens the picker; ringed when a non-preset is active.
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        if (!isPreset) 2.dp else 1.dp,
                        if (!isPreset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp),
                    )
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFF3B82F6), Color(0xFFEC4899)),
                        ),
                        RoundedCornerShape(8.dp),
                    )
                    .selectable(selected = !isPreset, onClick = { if (customUnlocked) showPicker = true else onLockedCustom() }),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (customUnlocked) "+" else "🔒", fontSize = 14.sp, color = Color.White)
            }
        }
    }
    if (showPicker) {
        ColorPickerDialog(
            initial = selected,
            allowAlpha = allowAlpha,
            onDismiss = { showPicker = false },
            onConfirm = { showPicker = false; pick(it) },
        )
    }
}

@Composable
private fun Swatch(fill: Color, selected: Boolean, glyph: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(8.dp),
            )
            .background(fill, RoundedCornerShape(8.dp))
            .selectable(selected = selected, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) Text(glyph, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

/**
 * Full-width option row with a mini status-bar mock that matches the system
 * dark/light mode (previewing a light-mode user on a fake dark pill would lie).
 * The glyph is tinted to the bar's content color exactly as the OS tints the
 * real status-bar icon.
 */
@Composable
private fun StyleOptionRow(
    style: IconStyle,
    preview: List<androidx.compose.ui.graphics.ImageBitmap>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val accent = MaterialTheme.colorScheme.primary
    val border =
        if (selected) BorderStroke(2.dp, accent)
        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStatusBar(preview = preview, styleLabel = style.label, dark = dark)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(style.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        STYLE_DESCRIPTIONS[style] ?: style.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                CtaChip(selected = selected, accent = accent)
            }
        }
    }
}

/** Mini status bar: clock · live glyph(s) · wifi+battery, in system theme. */
@Composable
private fun MiniStatusBar(
    preview: List<androidx.compose.ui.graphics.ImageBitmap>,
    styleLabel: String,
    dark: Boolean,
) {
    // Fixed dark "status bar" so the glyph's actual colours (incl. custom bg/fg)
    // read truthfully — no tint applied, since the user now controls the colours.
    val barBg = Color(0xFF101218)
    val barFg = Color.White
    // Emulate the OS icon slot so the preview is honest: an icon may be at most
    // SLOT_H tall AND SLOT_W wide (One UI width cap ≈ 1.7× the slot height). A wide
    // bitmap therefore previews shorter — exactly like the real status bar.
    val slotH = 15f
    val slotW = 26f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(barBg)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("4:38", color = barFg, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            preview.forEach { bmp ->
                val aspect = (bmp.width.toFloat() / bmp.height).coerceIn(0.5f, 6f)
                val h = minOf(slotH, slotW / aspect)
                Image(
                    bitmap = bmp,
                    contentDescription = "$styleLabel live preview",
                    modifier = Modifier.height(h.dp).aspectRatio(aspect),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Wifi,
                contentDescription = null,
                tint = barFg,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text("94%", color = barFg, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CtaChip(selected: Boolean, accent: Color) {
    if (selected) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text("Selected", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
        }
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("Use this", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
        }
    }
}

/**
 * Tier editor: rename the five tiers, set their four boundaries (MB/s), and a
 * daily data cap (GB) that drives the Rings widget quota arc. Collapsible to keep
 * the screen calm. Edits persist immediately when valid.
 */
@Composable
private fun TierEditor(
    settings: Settings,
    onThresholdsChange: (List<Float>) -> Unit,
    onNamesChange: (List<String>) -> Unit,
    onQuotaChange: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().selectable(selected = expanded, onClick = { expanded = !expanded }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Tiers & limits", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Rename tiers, set their speeds, daily data cap",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val names = settings.tierNames
                val ths = settings.tierThresholds
                SpeedTiers.ALL.forEach { tier ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = names.getOrElse(tier.index) { tier.defaultWord },
                            onValueChange = { v ->
                                if (v.isNotBlank()) {
                                    onNamesChange(names.toMutableList().also { it[tier.index] = v.take(14) })
                                }
                            },
                            label = { Text("Tier ${tier.index + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        // The boundary BELOW this tier (tiers 1..4 have an onset threshold).
                        if (tier.index in 1..4) {
                            OutlinedTextField(
                                value = ths.getOrElse(tier.index - 1) { 0f }.let {
                                    if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString()
                                },
                                onValueChange = { v ->
                                    val f = v.toFloatOrNull()
                                    if (f != null && f > 0f) {
                                        val updated = ths.toMutableList().also { it[tier.index - 1] = f }
                                        if (updated.zipWithNext().all { (a, b) -> a < b }) onThresholdsChange(updated)
                                    }
                                },
                                label = { Text("≥ MB/s") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(110.dp),
                            )
                        } else {
                            Spacer(Modifier.width(110.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = if (settings.dailyQuotaBytes > 0) {
                        (settings.dailyQuotaBytes / (1024.0 * 1024 * 1024)).let { gb ->
                            if (gb == gb.toLong().toDouble()) gb.toLong().toString() else "%.1f".format(gb)
                        }
                    } else "",
                    onValueChange = { v ->
                        val gb = v.toDoubleOrNull()
                        onQuotaChange(if (gb != null && gb > 0) (gb * 1024 * 1024 * 1024).toLong() else 0L)
                    },
                    label = { Text("Daily data cap (GB) — for Rings widget") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Horizontally-scrolling "Skin" chip row — each chip carries a colour swatch. */
@Composable
private fun SkinRow(
    selected: ColorSkin,
    unlocked: Boolean,
    onSelect: (ColorSkin) -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Skin",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 20.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorSkin.entries.forEach { s ->
                val active = s == selected
                val locked = !com.netspeed.indicator.billing.FeatureGate.skinAllowed(s.ordinal, com.netspeed.indicator.billing.Entitlement(unlocked))
                val swatch = if (s.heroColors.isNotEmpty()) s.heroColors.first() else s.accent
                val scale by animateFloatAsState(
                    targetValue = if (active) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f),
                    label = "skinPop",
                )
                Row(
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .selectable(selected = active, onClick = { if (locked) onLocked() else onSelect(s) })
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(swatch),
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        if (locked) "🔒 ${s.label}" else s.label,
                        fontSize = 13.sp,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/** Generic labelled chip row: pick one key from (key → label) options. */
@Composable
private fun ChipPickRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        // Scrolls on narrow screens — chip sets routinely outgrow 320 dp.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (key, text) ->
                val on = key == selected
                Text(
                    text,
                    fontSize = 12.sp,
                    maxLines = 1,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                        .selectable(selected = on, onClick = { onPick(key) })
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Quick-dock targets for the floating bubble. */
enum class BubbleCorner { TOP_LEFT, TOP_RIGHT, CENTRE, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Precise bubble placement without fighting a fingertip-sized drag target:
 * one-tap corner/centre presets (deep-docks land exactly, no drag needed) plus
 * a nudge pad that moves the live chip in small steps. Both write the stored
 * position; the service applies it instantly.
 */
@Composable
private fun BubblePositionPad(
    posX: Int,
    posY: Int,
    onNudge: (dxPx: Int, dyPx: Int) -> Unit,
    onPreset: (BubbleCorner) -> Unit,
) {
    // Pixel-perfect placement: selectable step, down to 1 px.
    var step by remember { androidx.compose.runtime.mutableIntStateOf(10) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Bubble position",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f),
            )
            Text(
                "X $posX · Y $posY px",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                BubbleCorner.TOP_LEFT to "◴ Top-L",
                BubbleCorner.TOP_RIGHT to "◷ Top-R",
                BubbleCorner.CENTRE to "◉ Centre",
                BubbleCorner.BOTTOM_LEFT to "◵ Bot-L",
                BubbleCorner.BOTTOM_RIGHT to "◶ Bot-R",
            ).forEach { (corner, label) ->
                Text(
                    label,
                    fontSize = 11.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .selectable(selected = false, onClick = { onPreset(corner) })
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        // Fine-tune, split across two rows so 320 dp screens fit everything:
        // step picker first, then the four big-target nudge arrows.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Fine-tune:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            listOf(1, 10, 50).forEach { px ->
                val on = step == px
                Text(
                    "${px}px",
                    fontSize = 11.sp,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        )
                        .selectable(selected = on, onClick = { step = px })
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                "◀" to Pair(-1, 0), "▶" to Pair(1, 0),
                "▲" to Pair(0, -1), "▼" to Pair(0, 1),
            ).forEach { (glyph, dir) ->
                val d = dir.first * step to dir.second * step
                Text(
                    glyph,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .selectable(selected = false, onClick = { onNudge(d.first, d.second) })
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Widget styles offered for one-tap pinning, in display order. */
private val WIDGET_STYLES = listOf(
    com.netspeed.indicator.render.WidgetKind.HERO to "Hero banner",
    com.netspeed.indicator.render.WidgetKind.DIAL to "Dial",
    com.netspeed.indicator.render.WidgetKind.RINGS to "Rings",
    com.netspeed.indicator.render.WidgetKind.PILL to "Pill",
    com.netspeed.indicator.render.WidgetKind.WEATHER to "Weather",
)

/**
 * "Add to Home screen" chip row, sitting directly under the live hero. Each chip
 * fires the launcher's pin-widget prompt for that widget style — so every banner
 * the user is looking at is one tap from the home screen, no menu digging. Falls
 * back gracefully: if the launcher doesn't support pinning, the long-press hint
 * below still applies.
 */
@Composable
private fun AddToHomeRow(
    locked: Boolean,
    onPin: (com.netspeed.indicator.render.WidgetKind) -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (locked) "Add to Home screen 🔒" else "Add to Home screen",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 20.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WIDGET_STYLES.forEach { (kind, label) ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .selectable(selected = false, onClick = { if (locked) onLocked() else onPin(kind) })
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (locked) Icons.Filled.Lock else Icons.Filled.Widgets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(
            if (locked) "Home widgets are part of the suite unlock — tap any style to unlock."
            else "Tap a style to drop it on your home screen, or long-press the home screen → Widgets → NetSpeed.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 20.dp, top = 2.dp, end = 20.dp),
        )
    }
}

/** Horizontally-scrolling "Live themes" chip row. */
@Composable
private fun LiveThemeRow(
    selected: HeroTheme,
    unlocked: Boolean,
    onSelect: (HeroTheme) -> Unit,
    onLocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Live themes",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 20.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroTheme.entries.forEach { theme ->
                val active = theme == selected
                val locked = !com.netspeed.indicator.billing.FeatureGate.themeAllowed(theme.ordinal, com.netspeed.indicator.billing.Entitlement(unlocked))
                val scale by animateFloatAsState(
                    targetValue = if (active) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = 520f),
                    label = "themePop",
                )
                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .selectable(selected = active, onClick = { if (locked) onLocked() else onSelect(theme) })
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    val emoji = SceneRegistry.fromThemeKey(theme.storageKey)?.emoji
                    val label = if (emoji != null) "$emoji ${theme.label}" else theme.label
                    Text(
                        if (locked) "🔒 $label" else label,
                        fontSize = 13.sp,
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

/**
 * Five-segment tier scale under the hero. Each segment is its tier's accent at
 * 25% alpha; the active tier (for the live speed) lights to 100%. Communicates
 * the tier by position + color + label — never color alone.
 */
@Composable
private fun TierScaleBar(
    live: LiveSpeed,
    thresholds: FloatArray,
    tierNames: List<String>,
    skin: ColorSkin,
    modifier: Modifier = Modifier,
) {
    val activeIndex = if (live.running) SpeedTiers.tierOf(live.downMBps, thresholds).index else -1
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SpeedTiers.ALL.forEach { tier ->
                val active = tier.index == activeIndex
                // Tier skin keeps the semantic rainbow; any other skin renders the
                // scale in its own accent so the screen stays one palette.
                val segment = if (skin == ColorSkin.TIER) tier.c2 else MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(segment.copy(alpha = if (active) 1f else 0.25f)),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SpeedTiers.ALL.forEach { tier ->
                Text(
                    text = tierNames.getOrElse(tier.index) { tier.defaultWord },
                    modifier = Modifier.weight(1f),
                    fontSize = 9.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        run {
            Text(text = "About", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No internet permission — your data never leaves the device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
