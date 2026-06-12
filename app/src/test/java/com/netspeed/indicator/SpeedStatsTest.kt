package com.netspeed.indicator

import com.netspeed.indicator.core.SpeedStats
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedStatsTest {

    @Test
    fun p90_basics() {
        assertEquals(0f, SpeedStats.p90(emptyList()), 1e-6f)
        assertEquals(5f, SpeedStats.p90(listOf(5f)), 1e-6f)
        // 1..10: p90 index = (9*0.9)=8 -> sorted[8] = 9
        assertEquals(9f, SpeedStats.p90((1..10).map { it.toFloat() }), 1e-6f)
    }

    @Test
    fun jitter_basics() {
        assertEquals(0f, SpeedStats.jitter(listOf(5f)), 1e-6f)
        assertEquals(0f, SpeedStats.jitter(listOf(3f, 3f, 3f)), 1e-6f)
        // [2,4]: mean 3, var 1 -> sd 1
        assertEquals(1f, SpeedStats.jitter(listOf(2f, 4f)), 1e-5f)
    }

    @Test
    fun parseTextPos_grid_legacy_auto() {
        assertEquals(-1 to 0, SpeedStats.parseTextPos("left", 1))
        assertEquals(1 to 0, SpeedStats.parseTextPos("right", -1))
        assertEquals(-1 to -1, SpeedStats.parseTextPos("tl", 0))
        assertEquals(1 to 1, SpeedStats.parseTextPos("br", 0))
        assertEquals(0 to 1, SpeedStats.parseTextPos("bc", 0))
        assertEquals(1 to 0, SpeedStats.parseTextPos("auto", 1))
        assertEquals(-1 to 0, SpeedStats.parseTextPos("auto", -1))
    }
}
