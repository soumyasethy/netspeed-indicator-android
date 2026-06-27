package com.netspeed.indicator.data

/**
 * Visual treatment of the status-bar speed glyph. The [storageKey] is what's
 * persisted (stable across reorderings); [label] / [description] drive the
 * picker UI. ARROWS is the default.
 */
enum class IconStyle(
    val storageKey: String,
    val label: String,
    val description: String,
) {
    /** Directional ▲/▼ triangles stacked vertically — the classic meter look. */
    ARROWS("arrows", "Arrows ↕", "Up over down, stacked"),

    /** Same arrows but down & up side-by-side in one row. */
    ARROWS_H("arrows_h", "Arrows ↔", "Down & up side by side"),

    /** Value over unit, bold, no arrow — clean and legible. */
    STACKED("stacked", "Stacked", "Number on top, unit below"),

    /** One big number with a tiny unit — smallest footprint. */
    COMPACT("compact", "Compact", "Single bold number"),

    /** Whichever direction is busier right now — its arrow + speed, one at a time. */
    AUTO("auto", "Auto ⇅", "Shows the busier direction");

    companion object {
        val DEFAULT = AUTO
        fun fromKey(key: String?): IconStyle =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
