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
        /** Where the hero text sits so it never covers the scene's focal point:
         *  -1 left, 0 center, +1 right. User can override ("Hero text position"). */
        val heroTextSide: Int,
        val factory: () -> SpeedScene,
    )

    val ALL: List<Entry> = listOf(
        // Journey's world rides left-of-center → text right (the demo layout).
        Entry("journey", "Journey", "🚀", 1, ::JourneyScene),
        Entry("comet", "Comet", "🌠", 0, ::CometScene),
        // Orb sits dead-center → text right keeps the heartbeat visible.
        Entry("heartbeat", "Heartbeat", "🫀", 1, ::HeartbeatScene),
        // Focal dot at 62% width → text left of the burst.
        Entry("manga", "Manga", "💢", -1, ::MangaScene),
        Entry("river", "Data river", "🌊", 0, ::RiverScene),
        Entry("firefly", "Fireflies", "✨", -1, ::FireflyScene),
        // Blob at 45% width → text right.
        Entry("blob", "Blob", "🟣", 1, ::BlobScene),
        // Turbine at 60% width → text left.
        Entry("turbine", "Turbine", "🌬️", -1, ::TurbineScene),
        // Runner at 45% width → text right.
        Entry("runner", "Runner", "🏃", 1, ::RunnerScene),
        // Jar centered → text right.
        Entry("jar", "Lightning jar", "⚡", 1, ::JarScene),
        Entry("tach", "Tachometer", "🏎️", 0, ::TachScene),
    )

    const val THEME_PREFIX = "scene_"

    fun isScene(key: String): Boolean = ALL.any { it.id == key }

    fun create(key: String): SpeedScene? = ALL.firstOrNull { it.id == key }?.factory?.invoke()

    fun entry(key: String): Entry? = ALL.firstOrNull { it.id == key }

    fun fromThemeKey(themeKey: String): Entry? =
        if (themeKey.startsWith(THEME_PREFIX)) entry(themeKey.removePrefix(THEME_PREFIX)) else null
}
