package com.netspeed.indicator.service

/**
 * Formats a raw bytes-per-second figure into the two pieces the status-bar icon
 * needs (a numeric line and a unit line) and into a single inline string for the
 * notification body / live preview.
 *
 * Units are binary (1 KB = 1024 B, 1 MB = 1024 KB) to match what every other
 * speed-meter app and the platform's own data-usage screen report.
 *
 * Rules (from the product spec):
 *  - < 1 KB/s            -> "0 KB/s"
 *  - < 1 MB/s            -> integer KB/s          e.g. "84 KB/s", "999 KB/s"
 *  - >= 1 MB/s, < 10     -> one-decimal MB/s       e.g. "1.4 MB/s"
 *  - >= 10 MB/s          -> integer MB/s           e.g. "23 MB/s"
 */
object SpeedFormatter {

    private const val KB = 1024L
    private const val MB = KB * 1024L

    /** Numeric value + unit, kept separate so the icon can stack them on two lines. */
    data class Parts(val value: String, val unit: String)

    fun parts(bytesPerSec: Long): Parts {
        val bps = if (bytesPerSec < 0) 0 else bytesPerSec
        return when {
            bps < KB -> Parts("0", "KB/s")
            bps < MB -> Parts((bps / KB).toString(), "KB/s")
            else -> {
                val mb = bps.toDouble() / MB
                val value = if (mb >= 10.0) {
                    mb.toLong().toString()                 // drop the decimal at >= 10 MB/s
                } else {
                    // one decimal, truncated (not rounded up) so we never overstate speed
                    val tenths = (mb * 10).toLong()
                    "${tenths / 10}.${tenths % 10}"
                }
                Parts(value, "MB/s")
            }
        }
    }

    /** "84 KB/s" — used in the notification body and the in-app live preview. */
    fun inline(bytesPerSec: Long): String {
        val p = parts(bytesPerSec)
        return "${p.value} ${p.unit}"
    }

    /**
     * Compact single-token form for the two-row up/down icon, where horizontal
     * space is scarce: "0", "84k", "1.4m", "23m". A lowercase k/m suffix stands
     * in for KB/s / MB/s (the per-second is implied by the meter context).
     */
    fun compact(bytesPerSec: Long): String {
        val bps = if (bytesPerSec < 0) 0 else bytesPerSec
        return when {
            bps < KB -> "0"
            bps < MB -> "${bps / KB}k"
            else -> {
                val mb = bps.toDouble() / MB
                if (mb >= 10.0) {
                    "${mb.toLong()}m"
                } else {
                    val tenths = (mb * 10).toLong()
                    "${tenths / 10}.${tenths % 10}m"
                }
            }
        }
    }

    /**
     * Down + up expressed in ONE shared unit (chosen from the larger value) so the
     * horizontal icon can show a single trailing unit, e.g. ("12", "0.2", "m") →
     * "↓12 ↑0.2 m". Keeps both readings comparable at a glance.
     */
    data class Pair3(val down: String, val up: String, val unit: String)

    fun compactPair(downBps: Long, upBps: Long): Pair3 {
        val maxV = maxOf(if (downBps < 0) 0 else downBps, if (upBps < 0) 0 else upBps)
        return if (maxV < MB) {
            // kilobytes: integer values, "k".
            Pair3((downBps.coerceAtLeast(0) / KB).toString(), (upBps.coerceAtLeast(0) / KB).toString(), "k")
        } else {
            // megabytes: one decimal under 10, integer above.
            Pair3(inUnit(downBps, MB), inUnit(upBps, MB), "m")
        }
    }

    private fun inUnit(bps: Long, divisor: Long): String {
        val v = (if (bps < 0) 0 else bps).toDouble() / divisor
        return if (v >= 10.0) v.toLong().toString() else {
            val tenths = (v * 10).toLong()
            "${tenths / 10}.${tenths % 10}"
        }
    }

    /** "1.2 GB" / "840 MB" / "12 KB" — cumulative total for the "today" line. */
    fun total(bytes: Long): String {
        val b = if (bytes < 0) 0 else bytes
        val gb = KB * MB
        return when {
            b < KB -> "$b B"
            b < MB -> "${b / KB} KB"
            b < gb -> {
                val tenths = (b.toDouble() / MB * 10).toLong()
                "${tenths / 10}.${tenths % 10} MB"
            }
            else -> {
                val tenths = (b.toDouble() / gb * 10).toLong()
                "${tenths / 10}.${tenths % 10} GB"
            }
        }
    }
}
