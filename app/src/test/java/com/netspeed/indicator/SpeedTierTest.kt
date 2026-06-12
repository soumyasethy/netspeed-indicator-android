package com.netspeed.indicator

import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.core.TierTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedTierTest {

    @Test
    fun rawIndex_mapsRangesToTiers() {
        // Real-world defaults: 0.3 / 1.2 / 3 / 8 MB/s.
        assertEquals(0, SpeedTiers.rawIndex(0.2f))   // Crawling
        assertEquals(1, SpeedTiers.rawIndex(0.7f))   // Slow
        assertEquals(2, SpeedTiers.rawIndex(2f))     // Steady
        assertEquals(3, SpeedTiers.rawIndex(5f))     // Fast
        assertEquals(4, SpeedTiers.rawIndex(10f))    // Blazing
    }

    @Test
    fun hysteresis_doesNotStrobeAtBoundary() {
        val t = TierTracker()
        // Settle clearly in Steady (tier 2: 1.2..3 MB/s).
        t.update(2f, 1000)
        assertEquals(2, t.current.index)
        // Jitter across the 3 MB/s boundary by <10% margin, each tick <2.5s:
        // 2.95 ↔ 3.05 must NOT flip the committed tier.
        repeat(4) {
            t.update(3.05f, 500)
            t.update(2.95f, 500)
        }
        assertEquals(2, t.current.index)
    }

    @Test
    fun decisiveCross_flipsImmediately() {
        val t = TierTracker()
        t.update(2f, 1000)
        // 4 MB/s is >10% past the 3 boundary -> immediate flip to Fast (3).
        t.update(4f, 200)
        assertEquals(3, t.current.index)
    }

    @Test
    fun sustainedHold_flipsAfterDwell() {
        val t = TierTracker()
        t.update(2f, 1000)
        // 3.1 is within the 10% margin (boundary 3, margin 0.3 -> needs >=3.3),
        // so it should only flip after the candidate has held ≥2.5s.
        t.update(3.1f, 1000)    // establishes candidate, dwell starts at 0
        assertEquals(2, t.current.index)   // not yet
        t.update(3.1f, 3000)    // +3s dwell, past the 2.5s threshold
        assertEquals(3, t.current.index)   // now flipped
    }

    @Test
    fun customThresholds_shiftTierBoundaries() {
        // User sets aggressive thresholds: Blazing starts at 5 MB/s.
        val custom = floatArrayOf(0.2f, 1f, 3f, 5f)
        assertEquals(0, SpeedTiers.rawIndex(0.1f, custom))
        assertEquals(2, SpeedTiers.rawIndex(2f, custom))   // 2 MB/s = Steady (≥1, <3)
        assertEquals(4, SpeedTiers.rawIndex(6f, custom))   // 6 MB/s = Blazing under custom
        // Same 6 MB/s is Fast (not yet Blazing) under the defaults (3..8).
        assertEquals(3, SpeedTiers.rawIndex(6f))
    }

    // blendColors() relies on android.graphics.Color via ColorUtils.blendARGB,
    // which isn't available in pure-JVM tests — it's verified visually on device.
}
