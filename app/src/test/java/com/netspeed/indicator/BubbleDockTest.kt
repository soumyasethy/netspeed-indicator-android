package com.netspeed.indicator

import com.netspeed.indicator.core.BubbleDock
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleDockTest {

    @Test
    fun docksLeftOfCentralPunchHole() {
        // 1440 wide, centred 100px cutout spanning 670..770, chip 160x90,
        // status bar 100, gap 16 → right edge of chip lands just left of cutout.
        val (x, y) = BubbleDock.notchLeft(1440, 100, 160, 90, 670, 770, 16)
        assertEquals(670 - 160 - 16, x)   // 494
        assertEquals((100 - 90) / 2, y)   // 5 — vertically centred in the bar
    }

    @Test
    fun docksLeftOfSystemIconsWhenNoCutout() {
        val (x, _) = BubbleDock.notchLeft(1440, 100, 160, 90, null, null, 16)
        assertEquals(1440 - 160 - 16 - (1440 * 22 / 100), x)   // 947
    }

    @Test
    fun cornerCutoutIsNotTreatedAsCentral() {
        // A tiny top-left corner cutout (0..80) must NOT be tucked against.
        val (xCorner, _) = BubbleDock.notchLeft(1440, 100, 160, 90, 0, 80, 16)
        val (xNone, _) = BubbleDock.notchLeft(1440, 100, 160, 90, null, null, 16)
        assertEquals(xNone, xCorner)
    }

    @Test
    fun xNeverGoesNegativeOnNarrowScreens() {
        val (x, _) = BubbleDock.notchLeft(300, 100, 280, 90, 150, 170, 16)
        assertEquals(16, x)   // coerced up to the gap
    }
}
