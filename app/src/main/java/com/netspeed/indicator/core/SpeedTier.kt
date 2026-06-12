package com.netspeed.indicator.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * THE single source of truth for speed tiers. Every surface — hero, widgets,
 * notification, picker, banner — reads tiers, words, colors and subtitles only
 * from here. Adding or renaming a tier is a one-file change.
 *
 * Tiers are discrete (the *word* snaps at a threshold) but [blendColors] is
 * continuous (the *background* morphs smoothly), so the screen feels alive
 * without strobing the label.
 */

/** Abstract icon id; each surface maps it to its own concrete glyph. */
enum class TierIcon { SNOWFLAKE, CLOUD, WIND, BOLT, FLAME }

/** Static identity of one tier. Colors are ARGB via Compose [Color]. */
data class SpeedTier(
    val index: Int,                 // 0..4
    val defaultWord: String,
    val icon: TierIcon,
    val c1: Color,                  // gradient start
    val c2: Color,                  // gradient end / accent
    val defaultSubtitle: String,
)

object SpeedTiers {

    /** The five tiers, in order. Edit/extend here only. */
    val ALL: List<SpeedTier> = listOf(
        SpeedTier(0, "Crawling", TierIcon.SNOWFLAKE, Color(0xFFB91C1C), Color(0xFFEF4444), "Barely moving"),
        SpeedTier(1, "Slow", TierIcon.CLOUD, Color(0xFFB45309), Color(0xFFF59E0B), "Okay for browsing"),
        SpeedTier(2, "Steady", TierIcon.WIND, Color(0xFF1D4ED8), Color(0xFF3B82F6), "Good for HD streaming"),
        SpeedTier(3, "Fast", TierIcon.BOLT, Color(0xFF047857), Color(0xFF10B981), "4K and big downloads"),
        SpeedTier(4, "Blazing", TierIcon.FLAME, Color(0xFF7C3AED), Color(0xFFEC4899), "Don't blink"),
    )

    /** Default tier boundaries in MB/s: [<1]=0, [1..5]=1, [5..15]=2, [15..30]=3, [>30]=4. */
    val DEFAULT_THRESHOLDS = floatArrayOf(1f, 5f, 15f, 30f)

    /** Tier center-points (MB/s) used to interpolate the gradient continuously. */
    private val CENTERS = floatArrayOf(0.5f, 3f, 10f, 22f, 39f)

    /** Raw, stateless tier index for a speed given the (4) boundary thresholds. */
    fun rawIndex(speedMBps: Float, thresholds: FloatArray = DEFAULT_THRESHOLDS): Int {
        var i = 0
        while (i < thresholds.size && speedMBps >= thresholds[i]) i++
        return i.coerceIn(0, ALL.lastIndex)
    }

    fun tierAt(index: Int): SpeedTier = ALL[index.coerceIn(0, ALL.lastIndex)]

    /** Stateless convenience used by previews/widgets where hysteresis is moot. */
    fun tierOf(speedMBps: Float, thresholds: FloatArray = DEFAULT_THRESHOLDS): SpeedTier =
        tierAt(rawIndex(speedMBps, thresholds))

    /**
     * Continuous gradient for a speed: piecewise-lerp c1 and c2 between the two
     * nearest tier centers. The background therefore glides between tier colors
     * while the word snaps.
     */
    fun blendColors(speedMBps: Float): Pair<Color, Color> {
        val s = speedMBps.coerceIn(CENTERS.first(), CENTERS.last())
        var hi = 1
        while (hi < CENTERS.size && s > CENTERS[hi]) hi++
        val lo = (hi - 1).coerceAtLeast(0)
        val span = (CENTERS[hi] - CENTERS[lo]).coerceAtLeast(0.0001f)
        val t = ((s - CENTERS[lo]) / span).coerceIn(0f, 1f)
        val a = ALL[lo]
        val b = ALL[hi]
        return lerp(a.c1, b.c1, t) to lerp(a.c2, b.c2, t)
    }

    private fun lerp(from: Color, to: Color, t: Double): Color =
        Color(ColorUtils.blendARGB(from.toArgb(), to.toArgb(), t.toFloat()))

    private fun lerp(from: Color, to: Color, t: Float): Color = lerp(from, to, t.toDouble())

    /** Shared speed ceiling (MB/s) used by hero fill, widgets and scenes. */
    const val CEILING_MBPS = 48f

    /**
     * 0..1 PERCEPTUAL speed for the scenes, against the shared 48 MB/s ceiling.
     * Cube-root, not linear: real-world traffic lives in 0.05–5 MB/s, where a
     * linear /48 mapping pins every scene at its idle state (a 59 KB/s download
     * was sc 0.001 — the runner slept through it). Anchors: 10 KB/s ≈ 0.06
     * (wake-up), 1 MB/s ≈ 0.28, 10 MB/s ≈ 0.6, 48 MB/s = 1.
     */
    fun norm(speedMBps: Float): Float =
        Math.cbrt(((speedMBps / CEILING_MBPS).coerceIn(0f, 1f)).toDouble()).toFloat()

    /**
     * Within-tier progress 0..1: how far the speed sits between its tier's lower
     * and upper bound (upper bound of the top tier = [CEILING_MBPS]). Drives the
     * tachometer scene's RPM bar so it is mathematically exact per tier bounds.
     */
    fun tierFrac(speedMBps: Float, thresholds: FloatArray = DEFAULT_THRESHOLDS): Float {
        val idx = rawIndex(speedMBps, thresholds)
        val lo = if (idx == 0) 0f else thresholds[idx - 1]
        val hi = if (idx >= thresholds.size) CEILING_MBPS else thresholds[idx]
        val span = (hi - lo).coerceAtLeast(0.0001f)
        return ((speedMBps - lo) / span).coerceIn(0f, 1f)
    }

    /**
     * Continuous 0..4 position between tier CENTERS — the piecewise-lerp the
     * blended gradient already uses, exposed as a scalar. Scene environments
     * (Journey's dawn→space blend) interpolate on this so nothing ever snaps.
     */
    fun tierProgress(speedMBps: Float): Float {
        if (speedMBps <= CENTERS.first()) return 0f
        if (speedMBps >= CENTERS.last()) return (CENTERS.size - 1).toFloat()
        var i = 0
        while (i < CENTERS.size - 2 && speedMBps > CENTERS[i + 1]) i++
        return i + (speedMBps - CENTERS[i]) / (CENTERS[i + 1] - CENTERS[i])
    }

    /** Continuous accent (c2 blend) as an ARGB int — allocation-free for render paths. */
    fun blendAccentArgb(speedMBps: Float): Int {
        val s = speedMBps.coerceIn(CENTERS.first(), CENTERS.last())
        var hi = 1
        while (hi < CENTERS.size && s > CENTERS[hi]) hi++
        val lo = (hi - 1).coerceAtLeast(0)
        val span = (CENTERS[hi] - CENTERS[lo]).coerceAtLeast(0.0001f)
        val t = ((s - CENTERS[lo]) / span).coerceIn(0f, 1f)
        return ColorUtils.blendARGB(ALL[lo].c2.toArgb(), ALL[hi].c2.toArgb(), t)
    }
}

/**
 * Stateful tier selector with **hysteresis**: the tier only flips when the speed
 * crosses a boundary by ≥10% OR has held past it for ≥2.5s. This kills the
 * strobing you'd otherwise get hovering at 14.9 ↔ 15.1 MB/s.
 *
 * Drive it from a single ticker; it is not thread-safe by itself.
 */
class TierTracker(
    private var thresholds: FloatArray = SpeedTiers.DEFAULT_THRESHOLDS,
) {
    private var committedIndex = 0
    private var candidateIndex = 0
    private var candidateHeldMs = 0L

    val current: SpeedTier get() = SpeedTiers.tierAt(committedIndex)

    fun setThresholds(newThresholds: FloatArray) {
        thresholds = newThresholds
    }

    /**
     * @param speedMBps latest (smoothed) speed.
     * @param elapsedMs time since the previous call.
     * @return the committed tier after applying hysteresis.
     */
    fun update(speedMBps: Float, elapsedMs: Long): SpeedTier {
        val raw = SpeedTiers.rawIndex(speedMBps, thresholds)
        if (raw == committedIndex) {
            candidateIndex = committedIndex
            candidateHeldMs = 0L
            return current
        }

        // A different raw tier wants to take over. Reset the dwell timer if the
        // candidate changed, otherwise accumulate how long it has persisted.
        if (raw != candidateIndex) {
            candidateIndex = raw
            candidateHeldMs = 0L
        } else {
            candidateHeldMs += elapsedMs
        }

        if (crossedDecisively(speedMBps, raw) || candidateHeldMs >= DWELL_MS) {
            committedIndex = raw
            candidateHeldMs = 0L
        }
        return current
    }

    /** True if the speed is ≥10% beyond the boundary between current and target. */
    private fun crossedDecisively(speedMBps: Float, target: Int): Boolean {
        // Boundary that separates the committed tier from an adjacent target.
        val boundaryIdx = if (target > committedIndex) committedIndex else target
        if (boundaryIdx !in thresholds.indices) return true   // edge tiers: no margin to enforce
        val boundary = thresholds[boundaryIdx]
        val margin = boundary * 0.10f
        return if (target > committedIndex) {
            speedMBps >= boundary + margin
        } else {
            speedMBps <= boundary - margin
        }
    }

    private companion object {
        const val DWELL_MS = 2500L
    }
}
