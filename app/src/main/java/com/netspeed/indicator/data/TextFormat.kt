package com.netspeed.indicator.data

/**
 * Info layouts for the hero banner / hero widget text block: WHICH numbers
 * show and HOW they're arranged. Composes with the 3x3 text position and the
 * theme — layout, placement and motion are independent choices.
 */
enum class TextFormat(val storageKey: String, val label: String) {
    /** Tier pill + big number + subtitle + upload/today + stats strip. */
    CLASSIC("classic", "Classic"),

    /** Big number + unit. Nothing else. */
    MINIMAL("minimal", "Minimal"),

    /** Number only — not even a unit. The quietest layout. */
    ZEN("zen", "Zen"),

    /** Big download + small upload line. */
    NUMBER_UP("number_up", "Down + Up"),

    /** Download and upload as two stacked rows — speed-test style. */
    DUAL("dual", "Dual rows"),

    /** One inline line: ↓ x · ↑ y. */
    COMPACT("compact", "One-liner"),

    /** Big tier WORD, number beneath — vibe first. */
    TIER_WORD("tier_word", "Tier first"),

    /** Number + 2x2 grid: upload, peak, p90, jitter. */
    STATS("stats", "Stats grid"),

    /** Data-budget focus: number + today / quota. */
    DATA("data", "Data focus"),

    /** Mono table: DL / UL / PK / P90 / JIT — the pro readout. */
    PRO("pro", "Pro table");

    companion object {
        val DEFAULT = CLASSIC
        fun fromKey(key: String?): TextFormat =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
