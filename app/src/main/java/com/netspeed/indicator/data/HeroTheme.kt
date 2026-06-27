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
    SPEEDTEST("speedtest", "Speedtest"),

    // Speed scenes — procedural dioramas (render/scenes) shared verbatim by the
    // hero, widgets and the floating bubble. APPEND ONLY: premium gating is
    // ordinal-based (FeatureGate.themeAllowed), inserting would re-tier users.
    SCENE_COMET("scene_comet", "Comet"),
    SCENE_TACH("scene_tach", "Tachometer"),
    SCENE_JOURNEY("scene_journey", "Journey"),
    SCENE_HEARTBEAT("scene_heartbeat", "Heartbeat"),
    SCENE_MANGA("scene_manga", "Manga"),
    SCENE_RIVER("scene_river", "Data river"),
    SCENE_FIREFLY("scene_firefly", "Fireflies"),
    SCENE_BLOB("scene_blob", "Blob"),
    SCENE_TURBINE("scene_turbine", "Turbine"),
    SCENE_RUNNER("scene_runner", "Runner"),
    SCENE_JAR("scene_jar", "Lightning jar");

    /** True for themes backed by a [com.netspeed.indicator.render.scenes.SpeedScene]. */
    val isScene: Boolean get() = storageKey.startsWith("scene_")

    companion object {
        val DEFAULT = SCENE_JOURNEY
        fun fromKey(key: String?): HeroTheme =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
