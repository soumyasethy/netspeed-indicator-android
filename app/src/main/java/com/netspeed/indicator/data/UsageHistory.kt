package com.netspeed.indicator.data

/** One finished day's total traffic (down+up), keyed by epoch day. */
data class DayUsage(val epochDay: Long, val bytes: Long)

/**
 * Codec for the 30-day usage history persisted as one DataStore string:
 * `epochDay:bytes|epochDay:bytes|…`, oldest first / newest last. Pure Kotlin so
 * it unit-tests without Android.
 */
object UsageHistory {

    const val MAX_DAYS = 30

    fun decode(raw: String?): List<DayUsage> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split("|").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val day = parts[0].toLongOrNull() ?: return@mapNotNull null
            val bytes = parts[1].toLongOrNull() ?: return@mapNotNull null
            DayUsage(day, bytes)
        }
    }

    fun encode(list: List<DayUsage>): String =
        list.joinToString("|") { "${it.epochDay}:${it.bytes}" }

    /** Appends (or replaces) [day], keeping only the newest [max] entries. */
    fun append(list: List<DayUsage>, day: Long, bytes: Long, max: Int = MAX_DAYS): List<DayUsage> =
        (list.filterNot { it.epochDay == day } + DayUsage(day, bytes))
            .sortedBy { it.epochDay }
            .takeLast(max)
}
