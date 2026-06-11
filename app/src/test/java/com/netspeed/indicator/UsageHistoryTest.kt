package com.netspeed.indicator

import com.netspeed.indicator.data.DayUsage
import com.netspeed.indicator.data.UsageHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageHistoryTest {

    @Test
    fun `decode of null or empty is empty`() {
        assertTrue(UsageHistory.decode(null).isEmpty())
        assertTrue(UsageHistory.decode("").isEmpty())
    }

    @Test
    fun `encode decode round trip`() {
        val list = listOf(DayUsage(20600, 123L), DayUsage(20601, 456L))
        assertEquals(list, UsageHistory.decode(UsageHistory.encode(list)))
    }

    @Test
    fun `garbage entries are skipped`() {
        val decoded = UsageHistory.decode("20600:1|junk|:5|20601:2")
        assertEquals(listOf(DayUsage(20600, 1), DayUsage(20601, 2)), decoded)
    }

    @Test
    fun `append keeps only the newest 30`() {
        var list = emptyList<DayUsage>()
        for (day in 1L..40L) list = UsageHistory.append(list, day, day * 10)
        assertEquals(30, list.size)
        assertEquals(11L, list.first().epochDay)   // oldest kept
        assertEquals(40L, list.last().epochDay)    // newest last
    }

    @Test
    fun `append replaces an existing day instead of duplicating`() {
        var list = listOf(DayUsage(100, 5))
        list = UsageHistory.append(list, 100, 9)
        assertEquals(listOf(DayUsage(100, 9)), list)
    }
}
