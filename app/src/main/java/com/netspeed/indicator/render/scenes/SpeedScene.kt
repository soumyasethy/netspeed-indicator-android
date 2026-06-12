package com.netspeed.indicator.render.scenes

import android.graphics.Canvas

/**
 * Speed scenes: tiny procedural dioramas driven by one normalized speed value.
 * Pure android.graphics — the SAME renderer instance draws the floating bubble
 * (View canvas), the in-app hero (Compose nativeCanvas), home-screen widgets
 * (static bitmap frame) and the settings preview row.
 *
 * Contract:
 *  - render() must not allocate. Pools, Paints, Paths, RectFs are instance
 *    fields created at construction; particle pools are fixed-size arrays.
 *  - All randomness flows through [SceneRng] (seeded), never Math.random(),
 *    so two devices at the same speed look statistically identical.
 *  - The reference design space is 208x70 (the HTML gallery). Scenes scale
 *    every literal by `h / 70f` so they stay proportionate at any size.
 *  - `dtS == 0f` means "draw a static frame": derive everything analytically
 *    from [SceneState.timeS] and do NOT step particle pools (widgets do this
 *    once per second; previews/bubble/hero animate with real deltas).
 */
interface SpeedScene {
    fun render(canvas: Canvas, w: Float, h: Float, s: SceneState)
}

/**
 * Mutable per-frame inputs. ONE instance per host surface, refilled in place —
 * zero allocation per frame.
 */
class SceneState {
    /** Normalized speed 0..1 (smoothed MB/s over the shared 48 MB/s ceiling). */
    var sc = 0f

    /** Smoothed speed in MB/s (hosts lerp the raw 1 Hz sample by ~0.06/frame). */
    var mbps = 0f

    /** Committed tier index 0..4 (with hysteresis where the host has it). */
    var tier = 0

    /** Within-tier progress 0..1 computed with the USER's thresholds. */
    var tierFrac = 0f

    /** Continuous 0..4 position between tier centers (environment blends). */
    var tierProgress = 0f

    /** Continuous blended accent color (SpeedTiers.blendAccentArgb). */
    var accentArgb = 0xFF3B82F6.toInt()

    /** Scene clock in seconds. Monotonic while animating; frozen when paused. */
    var timeS = 0f

    /** Delta seconds since last frame; 0 = static frame (no pool stepping). */
    var dtS = 0f

    /** Dark variant flag (manga paper, turbine night). */
    var dark = true
}

/**
 * The gallery's deterministic PRNG: s = (s*9301 + 49297) % 233280.
 * Reset-able so flicker effects can replay a fixed sequence per arc/frame.
 */
class SceneRng(seed: Int = 1) {
    private var s = seed.coerceAtLeast(1).toLong() % MOD

    fun reset(seed: Int) {
        s = seed.coerceAtLeast(1).toLong() % MOD
    }

    /** Next pseudo-random float in [0, 1). */
    fun next(): Float {
        s = (s * 9301L + 49297L) % MOD
        return s / MOD.toFloat()
    }

    private companion object {
        const val MOD = 233280L
    }
}

/** alpha 0..1 applied onto an opaque ARGB color — render-path helper, no alloc. */
internal fun argbWithAlpha(argb: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    return (argb and 0x00FFFFFF) or (a shl 24)
}

/** Linear blend between two ARGB colors, t 0..1. */
internal fun lerpArgb(from: Int, to: Int, t: Float): Int {
    val k = t.coerceIn(0f, 1f)
    val a = ((from ushr 24) + (((to ushr 24) - (from ushr 24)) * k)).toInt()
    val r = (((from shr 16) and 0xFF) + ((((to shr 16) and 0xFF) - ((from shr 16) and 0xFF)) * k)).toInt()
    val g = (((from shr 8) and 0xFF) + ((((to shr 8) and 0xFF) - ((from shr 8) and 0xFF)) * k)).toInt()
    val b = ((from and 0xFF) + (((to and 0xFF) - (from and 0xFF)) * k)).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}
