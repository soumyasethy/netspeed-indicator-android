package com.netspeed.indicator.service

/**
 * Turns successive cumulative TrafficStats counter readings into per-second
 * deltas. Pure and side-effect free so it can be unit-tested on the JVM with no
 * Android dependency.
 *
 * Two edge cases are handled here, both called out in the spec:
 *
 *  1. **No baseline yet** — the very first reading after start (or after a
 *     screen-on resume) has nothing to subtract against, so [sample] returns
 *     `null` and the caller must skip the notification update for that tick.
 *
 *  2. **Counter reset** — TrafficStats counters are monotonic *until* the device
 *     reboots or the interface resets, at which point they jump backwards. A
 *     negative delta is meaningless, so we clamp it to 0 rather than render a
 *     garbage spike.
 */
class SpeedSampler {

    private var lastRx = UNSET
    private var lastTx = UNSET

    /** True once a baseline reading has been taken and deltas can be produced. */
    val isPrimed: Boolean get() = lastRx != UNSET

    /**
     * Drops the baseline so the next [sample] re-primes and returns `null`. Used
     * on screen-on resume so the first post-resume delta isn't measured across
     * the whole paused interval.
     */
    fun reset() {
        lastRx = UNSET
        lastTx = UNSET
    }

    /**
     * @param rx cumulative received bytes (TrafficStats.getTotalRxBytes()).
     * @param tx cumulative transmitted bytes.
     * @param elapsedMillis wall-clock gap since the previous sample; used to
     *        normalise to bytes/second even if the ticker drifted from 1000 ms.
     * @return the per-second sample, or `null` on the priming tick.
     */
    fun sample(rx: Long, tx: Long, elapsedMillis: Long = 1000L): Sample? {
        // TrafficStats.UNSUPPORTED (-1) or any negative reading -> treat as 0.
        val safeRx = if (rx < 0) 0 else rx
        val safeTx = if (tx < 0) 0 else tx

        if (lastRx == UNSET) {
            lastRx = safeRx
            lastTx = safeTx
            return null
        }

        val dRx = delta(safeRx, lastRx)
        val dTx = delta(safeTx, lastTx)
        lastRx = safeRx
        lastTx = safeTx

        val divisor = if (elapsedMillis <= 0) 1000L else elapsedMillis
        return Sample(
            rxBytesPerSec = dRx * 1000L / divisor,
            txBytesPerSec = dTx * 1000L / divisor,
            rxBytesDelta = dRx,
            txBytesDelta = dTx,
        )
    }

    private fun delta(now: Long, last: Long): Long =
        if (now < last) 0L else now - last     // counter reset -> 0, never negative

    companion object {
        private const val UNSET = -1L
    }
}

/**
 * One second's worth of traffic. [*BytesPerSec] drive the display; [*BytesDelta]
 * feed the running "today" total.
 */
data class Sample(
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long,
    val rxBytesDelta: Long,
    val txBytesDelta: Long,
) {
    /** Combined down+up rate, for the "Download + Upload" icon mode. */
    val combinedBytesPerSec: Long get() = rxBytesPerSec + txBytesPerSec
}
