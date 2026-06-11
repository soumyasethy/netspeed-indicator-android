package com.netspeed.indicator.data

/**
 * How the unit is shown next to the number in the status-bar icon (for the
 * single-direction styles: Auto, side-by-side icons, Compact).
 */
enum class UnitStyle(val storageKey: String, val label: String) {
    /** "84k" — lowercase suffix, biggest digits. */
    SHORT("short", "Short — 84k"),

    /** "84 KB/s" — full unit inline at half height. */
    FULL("full", "Full — 84 KB/s"),

    /** "84" over "KB/s" — number on top, unit below. */
    BELOW("below", "Unit below");

    companion object {
        val DEFAULT = SHORT
        fun fromKey(key: String?): UnitStyle =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
