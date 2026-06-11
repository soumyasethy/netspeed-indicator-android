package com.netspeed.indicator

import com.netspeed.indicator.service.SpeedFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedFormatterTest {

    private val kb = 1024L
    private val mb = kb * 1024L

    @Test
    fun belowOneKilobyte_isZeroKb() {
        val p = SpeedFormatter.parts(500)
        assertEquals("0", p.value)
        assertEquals("KB/s", p.unit)
    }

    @Test
    fun integerKilobytes_haveNoDecimal() {
        val p = SpeedFormatter.parts(84 * kb)
        assertEquals("84", p.value)
        assertEquals("KB/s", p.unit)
    }

    @Test
    fun megabytes_underTen_haveOneDecimal() {
        // 1.4 MB/s (truncated, not rounded)
        val p = SpeedFormatter.parts((1.45 * mb).toLong())
        assertEquals("1.4", p.value)
        assertEquals("MB/s", p.unit)
    }

    @Test
    fun megabytes_tenOrMore_dropTheDecimal() {
        val p = SpeedFormatter.parts((23.9 * mb).toLong())
        assertEquals("23", p.value)
        assertEquals("MB/s", p.unit)
    }

    @Test
    fun inlineCombinesValueAndUnit() {
        assertEquals("84 KB/s", SpeedFormatter.inline(84 * kb))
    }

    @Test
    fun compactPair_sharesOneUnitFromTheLargerValue() {
        // Down 15 MB/s, up 1.5 MB/s -> both in MB, single "m".
        val p = SpeedFormatter.compactPair(15 * mb, mb * 3 / 2)
        assertEquals("15", p.down)
        assertEquals("1.5", p.up)
        assertEquals("m", p.unit)
        // Both in KB range -> "k".
        val q = SpeedFormatter.compactPair(84 * kb, 3 * kb)
        assertEquals("84", q.down)
        assertEquals("3", q.up)
        assertEquals("k", q.unit)
    }

    @Test
    fun compact_usesShortKmSuffixes() {
        assertEquals("0", SpeedFormatter.compact(500))
        assertEquals("84k", SpeedFormatter.compact(84 * kb))
        assertEquals("1.4m", SpeedFormatter.compact((1.45 * mb).toLong()))
        assertEquals("23m", SpeedFormatter.compact((23.9 * mb).toLong()))
    }
}
