package com.netspeed.indicator.data

/**
 * Live hero rendering modes. KINETIC is the default (cheapest — no canvas, the
 * number itself animates). All others draw on a single Canvas behind the
 * floating content; BENTO replaces the content with a tile grid.
 */
enum class HeroTheme(val storageKey: String, val label: String) {
    KINETIC("kinetic", "Kinetic"),
    TIER_FLOW("tier_flow", "Tier flow"),
    LIQUID("liquid", "Liquid"),
    ECG("ecg", "ECG"),
    DIAL("dial", "Dial"),
    RADAR("radar", "Radar"),
    PARTICLES("particles", "Particles"),
    CURTAINS("curtains", "Curtains"),
    MATERIAL_YOU("material_you", "Material You"),
    SKY("sky", "Sky"),
    BENTO("bento", "Bento"),
    TERMINAL("terminal", "Terminal"),
    BRUTALIST("brutalist", "Brutalist"),
    GLASS("glass", "Glass"),
    SPEEDTEST("speedtest", "Speedtest");

    companion object {
        val DEFAULT = KINETIC
        fun fromKey(key: String?): HeroTheme =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
