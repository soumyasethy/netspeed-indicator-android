package com.netspeed.indicator

import com.netspeed.indicator.core.BubbleDock
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleDockTest {

    @Test
    fun docksRightOfCentralPunchHole() {
        // 1440 wide, centred 100px cutout spanning 670..770, chip 160x90,
        // status bar 100, gap 16 → chip's LEFT edge lands just right of cutout.
        val (x, y) = BubbleDock.besideNotch(1440, 100, 160, 90, 670, 770, 16)
        assertEquals(770 + 16, x)         // 786 — just right of the punch-hole
        assertEquals((100 - 90) / 2, y)   // 5 — vertically centred in the bar
    }

    @Test
    fun docksLeftOfSystemIconsWhenNoCutout() {
        val (x, _) = BubbleDock.besideNotch(1440, 100, 160, 90, null, null, 16)
        val systemIconsLeft = 1440 - (1440 * 22 / 100)
        assertEquals(systemIconsLeft - 160 - 16, x)   // 947
    }

    @Test
    fun cornerCutoutIsNotTreatedAsCentral() {
        // A tiny top-left corner cutout (0..80) must NOT be tucked against.
        val (xCorner, _) = BubbleDock.besideNotch(1440, 100, 160, 90, 0, 80, 16)
        val (xNone, _) = BubbleDock.besideNotch(1440, 100, 160, 90, null, null, 16)
        assertEquals(xNone, xCorner)
    }

    @Test
    fun neverSlidesUnderTheSystemIconBand() {
        // A cutout sitting far right would push the chip into the icons — clamp it.
        val (x, _) = BubbleDock.besideNotch(1440, 100, 160, 90, 980, 1060, 16)
        val systemIconsLeft = 1440 - (1440 * 22 / 100)
        assertEquals(systemIconsLeft - 160 - 16, x)   // clamped to maxX
    }
}
