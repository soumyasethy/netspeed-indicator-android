package com.netspeed.indicator

import com.netspeed.indicator.core.GradientFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class GradientFlowTest {

    @Test
    fun `phase wraps within the period`() {
        val p = GradientFlow.STEPPED_PERIOD_MS
        assertEquals(0f, GradientFlow.phase(0L), 1e-4f)
        assertEquals(0.5f, GradientFlow.phase(p / 2), 1e-4f)
        assertEquals(0f, GradientFlow.phase(p), 1e-4f)
        assertEquals(0.25f, GradientFlow.phase(p * 7 + p / 4), 1e-4f)
    }

    @Test
    fun `phase advances at constant velocity - equal steps`() {
        val p = GradientFlow.STEPPED_PERIOD_MS
        val step1 = GradientFlow.phase(1_000L) - GradientFlow.phase(0L)
        val step2 = GradientFlow.phase(p / 2 + 1_000L) - GradientFlow.phase(p / 2)
        assertEquals(step1, step2, 1e-5f)            // no mid-cycle surge
        assertEquals(1_000f / p, step1, 1e-5f)       // ~2.8% per second at 36 s
    }

    @Test
    fun `wrapped appends the first colour for a seamless loop`() {
        val base = intArrayOf(1, 2, 3)
        val w = GradientFlow.wrapped(base)
        assertEquals(4, w.size)
        assertEquals(1, w.first())
        assertEquals(1, w.last())
        assertEquals(2, w[1])
        assertEquals(3, w[2])
    }
}
