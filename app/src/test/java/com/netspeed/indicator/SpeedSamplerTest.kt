package com.netspeed.indicator

import com.netspeed.indicator.service.SpeedSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedSamplerTest {

    @Test
    fun firstSample_returnsNull_becauseThereIsNoBaseline() {
        val sampler = SpeedSampler()
        assertNull(sampler.sample(rx = 1_000, tx = 500))
    }

    @Test
    fun secondSample_returnsPerSecondDelta() {
        val sampler = SpeedSampler()
        sampler.sample(rx = 1_000, tx = 500)                 // prime
        val s = sampler.sample(rx = 3_000, tx = 1_500, elapsedMillis = 1000)!!
        assertEquals(2_000, s.rxBytesPerSec)
        assertEquals(1_000, s.txBytesPerSec)
        assertEquals(3_000, s.combinedBytesPerSec)
    }

    @Test
    fun counterReset_clampsNegativeDeltaToZero() {
        val sampler = SpeedSampler()
        sampler.sample(rx = 10_000, tx = 10_000)             // prime high
        // Reboot / interface reset -> counters jump backwards.
        val s = sampler.sample(rx = 200, tx = 50)!!
        assertEquals(0, s.rxBytesPerSec)
        assertEquals(0, s.txBytesPerSec)
    }

    @Test
    fun unsupportedReading_isTreatedAsZero() {
        val sampler = SpeedSampler()
        sampler.sample(rx = 1_000, tx = 1_000)               // prime
        // TrafficStats.UNSUPPORTED == -1
        val s = sampler.sample(rx = -1, tx = -1)!!
        assertEquals(0, s.rxBytesPerSec)
        assertEquals(0, s.txBytesPerSec)
    }

    @Test
    fun elapsedScaling_normalisesToPerSecond() {
        val sampler = SpeedSampler()
        sampler.sample(rx = 0, tx = 0)                       // prime
        // 4000 bytes over 2s -> 2000 B/s
        val s = sampler.sample(rx = 4_000, tx = 0, elapsedMillis = 2000)!!
        assertEquals(2_000, s.rxBytesPerSec)
    }

    @Test
    fun reset_dropsBaseline_soNextSampleReprimes() {
        val sampler = SpeedSampler()
        sampler.sample(rx = 1_000, tx = 0)
        sampler.reset()
        assertNull(sampler.sample(rx = 5_000, tx = 0))       // re-primes, no garbage spike
    }
}
