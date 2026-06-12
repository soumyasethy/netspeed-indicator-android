package com.netspeed.indicator.core

import kotlin.math.sqrt

/**
 * Passive connection statistics derived from the per-second speed samples we
 * already collect. NOTE: true RTT latency/jitter would require actively
 * probing a server — impossible (and deliberately so) in an app that ships
 * without the INTERNET permission. "Jitter" here is therefore SPEED jitter:
 * how unsteady the throughput itself is.
 */
object SpeedStats {

    /** 90th-percentile of the samples (the Cloudflare-style consistency line). */
    fun p90(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val sorted = samples.sorted()
        val idx = ((sorted.size - 1) * 0.9f).toInt()
        return sorted[idx]
    }

    /** Population standard deviation — throughput unsteadiness ("speed jitter"). */
    fun jitter(samples: List<Float>): Float {
        if (samples.size < 2) return 0f
        val mean = samples.sum() / samples.size
        var acc = 0f
        for (s in samples) {
            val d = s - mean
            acc += d * d
        }
        return sqrt(acc / samples.size)
    }

    /**
     * Resolves a text-position preference into (horizontal, vertical) biases in
     * {-1,0,1}. Keys: "auto" (per-scene side, centered vertically), legacy
     * "left"/"center"/"right", or the 9-grid "tl|tc|tr|cl|cc|cr|bl|bc|br".
     */
    fun parseTextPos(pos: String, sceneDefaultH: Int): Pair<Int, Int> = when (pos) {
        "left" -> -1 to 0
        "center" -> 0 to 0
        "right" -> 1 to 0
        "tl" -> -1 to -1; "tc" -> 0 to -1; "tr" -> 1 to -1
        "cl" -> -1 to 0; "cc" -> 0 to 0; "cr" -> 1 to 0
        "bl" -> -1 to 1; "bc" -> 0 to 1; "br" -> 1 to 1
        else -> sceneDefaultH to 0          // "auto"
    }
}
