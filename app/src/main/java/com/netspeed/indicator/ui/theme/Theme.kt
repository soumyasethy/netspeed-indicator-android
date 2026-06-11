package com.netspeed.indicator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.netspeed.indicator.data.ColorSkin

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5FFF),
    secondary = Color(0xFF335CFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FC0FF),
    secondary = Color(0xFFB5C4FF),
)

/** Readable "on" colour for an arbitrary accent — dark ink on light, white on dark. */
private fun onColorFor(c: Color): Color =
    if (c.luminance() > 0.5f) Color(0xFF14161C) else Color.White

/**
 * Material 3 theme. Uses Android 12+ dynamic colour (wallpaper-derived) when
 * available for the Tier skin, and otherwise derives the FULL design-system
 * trio — primary / secondary / tertiary plus their "on" colours — from the
 * active skin, so every button, chip and accent across the app speaks one
 * palette instead of ad-hoc colours.
 */
@Composable
fun NetSpeedTheme(
    skin: ColorSkin = ColorSkin.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val base = when {
        // TIER skin keeps wallpaper-derived dynamic colour on Android 12+.
        skin == ColorSkin.TIER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val colorScheme = if (skin == ColorSkin.TIER) base else {
        val bg = skin.bg(darkTheme); val fg = skin.fg(darkTheme)
        val stops = skin.heroColors.distinct()           // hero palette wraps (first repeated)
        val secondary = stops.getOrElse(1) { skin.accent }
        val tertiary = stops.getOrElse(2) { secondary }
        base.copy(
            primary = skin.accent,
            onPrimary = onColorFor(skin.accent),
            primaryContainer = skin.accent.copy(alpha = 0.20f).compositeOver(bg),
            onPrimaryContainer = fg,
            secondary = secondary,
            onSecondary = onColorFor(secondary),
            tertiary = tertiary,
            onTertiary = onColorFor(tertiary),
            background = bg,
            surface = bg,
            surfaceVariant = bg,
            onBackground = fg,
            onSurface = fg,
            onSurfaceVariant = fg.copy(alpha = 0.7f),
            outlineVariant = fg.copy(alpha = 0.18f),
            outline = fg.copy(alpha = 0.3f),
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
