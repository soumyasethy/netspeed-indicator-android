package com.netspeed.indicator.render.scenes

/**
 * Registry of all speed scenes. The single list every surface reads:
 *  - bubble FX picker keys are the raw [Entry.id] (disjoint from the legacy
 *    none/flame/glow/sparks/lottie keys),
 *  - hero/widget theme keys are "scene_<id>" via [THEME_PREFIX].
 */
object SceneRegistry {

    data class Entry(
        val id: String,
        val label: String,
        val emoji: String,
        val factory: () -> SpeedScene,
    )

    val ALL: List<Entry> = listOf(
        Entry("journey", "Journey", "🚀", ::JourneyScene),
        Entry("comet", "Comet", "🌠", ::CometScene),
        Entry("heartbeat", "Heartbeat", "🫀", ::HeartbeatScene),
        Entry("manga", "Manga", "💢", ::MangaScene),
        Entry("river", "Data river", "🌊", ::RiverScene),
        Entry("firefly", "Fireflies", "✨", ::FireflyScene),
        Entry("blob", "Blob", "🟣", ::BlobScene),
        Entry("turbine", "Turbine", "🌬️", ::TurbineScene),
        Entry("runner", "Runner", "🏃", ::RunnerScene),
        Entry("jar", "Lightning jar", "⚡", ::JarScene),
        Entry("tach", "Tachometer", "🏎️", ::TachScene),
    )

    const val THEME_PREFIX = "scene_"

    fun isScene(key: String): Boolean = ALL.any { it.id == key }

    fun create(key: String): SpeedScene? = ALL.firstOrNull { it.id == key }?.factory?.invoke()

    fun entry(key: String): Entry? = ALL.firstOrNull { it.id == key }

    fun fromThemeKey(themeKey: String): Entry? =
        if (themeKey.startsWith(THEME_PREFIX)) entry(themeKey.removePrefix(THEME_PREFIX)) else null
}
