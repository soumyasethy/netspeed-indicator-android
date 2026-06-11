package com.netspeed.indicator

import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.core.TierTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTierTest {

    @Test
    fun rawIndex_mapsRangesToTiers() {
        assertEquals(0, SpeedTiers.rawIndex(0.4f))   // Crawling
        assertEquals(1, SpeedTiers.rawIndex(3f))     // Slow
        assertEquals(2, SpeedTiers.rawIndex(10f))    // Steady
        assertEquals(3, SpeedTiers.rawIndex(22f))    // Fast
        assertEquals(4, SpeedTiers.rawIndex(50f))    // Blazing
    }

    @Test
    fun hysteresis_doesNotStrobeAtBoundary() {
        val t = TierTracker()
        // Settle clearly in Steady (tier 2).
        t.update(10f, 1000)
        assertEquals(2, t.current.index)
        // Jitter across the 15 MB/s boundary by <10% margin, each tick <2.5s:
        // 14.9 ↔ 15.1 must NOT flip the committed tier.
        repeat(4) {
            t.update(15.1f, 500)
            t.update(14.9f, 500)
        }
        assertEquals(2, t.current.index)
    }

    @Test
    fun decisiveCross_flipsImmediately() {
        val t = TierTracker()
        t.update(10f, 1000)
        // 18 MB/s is >10% past the 15 boundary -> immediate flip to Fast (3).
        t.update(18f, 200)
        assertEquals(3, t.current.index)
    }

    @Test
    fun sustainedHold_flipsAfterDwell() {
        val t = TierTracker()
        t.update(10f, 1000)
        // 15.2 is within the 10% margin (boundary 15, margin 1.5 -> needs >=16.5),
        // so it should only flip after the candidate has held ≥2.5s.
        t.update(15.2f, 1000)   // establishes candidate, dwell starts at 0
        assertEquals(2, t.current.index)   // not yet
        t.update(15.2f, 3000)   // +3s dwell, past the 2.5s threshold
        assertEquals(3, t.current.index)   // now flipped
    }

    @Test
    fun customThresholds_shiftTierBoundaries() {
        // User sets aggressive thresholds: Blazing starts at 5 MB/s.
        val custom = floatArrayOf(0.2f, 1f, 3f, 5f)
        assertEquals(0, SpeedTiers.rawIndex(0.1f, custom))
        assertEquals(2, SpeedTiers.rawIndex(2f, custom))   // 2 MB/s = Steady (≥1, <3)
        assertEquals(4, SpeedTiers.rawIndex(6f, custom))   // 6 MB/s = Blazing under custom
        // Same 6 MB/s is only Steady under the defaults.
        assertEquals(2, SpeedTiers.rawIndex(6f))
    }

    // blendColors() relies on android.graphics.Color via ColorUtils.blendARGB,
    // which isn't available in pure-JVM tests — it's verified visually on device.
}
