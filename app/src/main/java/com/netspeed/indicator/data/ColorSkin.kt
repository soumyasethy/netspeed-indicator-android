package com.netspeed.indicator.data

import androidx.compose.ui.graphics.Color

/**
 * Whole-app colour skins (palette + hero gradient + font feel), distinct from the
 * animation [HeroTheme]s. TIER is the default — it keeps the tier-driven gradient
 * and the system Material palette. Every other skin overrides the app background,
 * foreground, accent, and the hero gradient with a fixed identity.
 *
 * A skin is purely cosmetic: the tier engine, word, icon and all behaviour are
 * unchanged — only colours/typography differ.
 */
enum class ColorSkin(
    val storageKey: String,
    val label: String,
    val mono: Boolean,
    val bgDark: Color, val fgDark: Color,
    val bgLight: Color, val fgLight: Color,
    val accent: Color,
    val heroColors: List<Color>,
    val heroFg: Color,
) {
    TIER("tier", "Tier", false,
        Color(0xFF0C0F17), Color(0xFFEEF1F8), Color(0xFFF7F8FB), Color(0xFF171A23),
        Color(0xFF7C3AED), emptyList(), Color.White),

    AURORA("aurora", "Aurora", false,
        Color(0xFF0B0F1C), Color(0xFFEEF1F8), Color(0xFFF7F8FD), Color(0xFF1A1D2B),
        Color(0xFF7C3AED),
        listOf(Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFFEC4899), Color(0xFF2563EB)),
        Color.White),

    CARBON("carbon", "Carbon pulse", true,
        Color(0xFF101312), Color(0xFFE6EFE9), Color(0xFFF2F5F3), Color(0xFF10231B),
        Color(0xFF10B981),
        listOf(Color(0xFF0F172A), Color(0xFF064E3B), Color(0xFF10B981), Color(0xFF0F172A)),
        Color(0xFFD9FFEE)),

    GLASS("glass", "Glasswave", false,
        Color(0xFF141226), Color(0xFFF1ECFF), Color(0xFFFDF3F8), Color(0xFF3B1130),
        Color(0xFFDB2777),
        listOf(Color(0xFF6D28D9), Color(0xFFDB2777), Color(0xFFF59E0B), Color(0xFF6D28D9)),
        Color.White),

    BRUTAL("brutal", "Neo-brutal", false,
        Color(0xFF15151A), Color(0xFFFAFAFA), Color(0xFFFFFBEA), Color(0xFF1C1917),
        Color(0xFFFACC15),
        listOf(Color(0xFFFACC15), Color(0xFFFB923C), Color(0xFFFACC15)),
        Color(0xFF1A1304)),

    TERMINAL("term", "Terminal", true,
        Color(0xFF050A06), Color(0xFF7CFC9A), Color(0xFFF4FAF4), Color(0xFF0F3D1F),
        Color(0xFF22C55E),
        listOf(Color(0xFF04240F), Color(0xFF0A5226), Color(0xFF04240F)),
        Color(0xFF9DFFB8));

    fun bg(dark: Boolean) = if (dark) bgDark else bgLight
    fun fg(dark: Boolean) = if (dark) fgDark else fgLight

    companion object {
        val DEFAULT = TIER
        fun fromKey(key: String?): ColorSkin =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
