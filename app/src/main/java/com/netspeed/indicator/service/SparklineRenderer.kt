package com.netspeed.indicator.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Renders the last N download samples as a tiny bar sparkline Bitmap for the
 * expanded notification. Bars are normalized to a 48 MB/s ceiling and drawn in
 * translucent white so they read on the gradient strip. Cheap (~a few KB) and
 * redrawn once per second only while the screen is on.
 */
object SparklineRenderer {

    private const val CEILING_BYTES = 48L * 1024L * 1024L   // 48 MB/s full-scale
    private const val BARS = 22

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 255, 255)   // white 55%
        style = Paint.Style.FILL
    }

    /**
     * @param history newest-last list of download bytes/sec.
     * @param widthPx / heightPx target bitmap size in pixels.
     */
    fun render(history: List<Long>, widthPx: Int, heightPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val gap = widthPx * 0.018f
        val barW = (widthPx - gap * (BARS - 1)) / BARS

        // Right-align the most recent samples; pad the left with zero bars.
        val recent = history.takeLast(BARS)
        val pad = BARS - recent.size
        for (i in 0 until BARS) {
            val value = if (i < pad) 0L else recent[i - pad]
            val frac = (value.toFloat() / CEILING_BYTES).coerceIn(0f, 1f)
            val barH = (heightPx * 0.92f) * frac.coerceAtLeast(0.02f)
            val left = i * (barW + gap)
            val top = heightPx - barH
            canvas.drawRoundRect(
                RectF(left, top, left + barW, heightPx.toFloat()),
                barW * 0.4f, barW * 0.4f, barPaint,
            )
        }
        return bmp
    }
}
