package com.netspeed.indicator.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * The notification's large icon: a rounded gradient square (#2563EB → #7C3AED →
 * #EC4899) with white up/down arrows. Static, so it's rendered once and cached.
 */
object LargeIconRenderer {

    private var cached: Bitmap? = null

    fun get(sizePx: Int = 96): Bitmap {
        cached?.let { if (it.width == sizePx) return it }
        val bmp = render(sizePx)
        cached = bmp
        return bmp
    }

    private fun render(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s = size.toFloat()
        val radius = s * 0.28f

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, s, s,
                intArrayOf(0xFF2563EB.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(RectF(0f, 0f, s, s), radius, radius, bg)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = s * 0.07f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        // Down arrow (left) + up arrow (right), the meter's signature mark.
        val down = Path().apply {
            moveTo(s * 0.34f, s * 0.30f); lineTo(s * 0.34f, s * 0.66f)
            moveTo(s * 0.24f, s * 0.55f); lineTo(s * 0.34f, s * 0.66f); lineTo(s * 0.44f, s * 0.55f)
        }
        val up = Path().apply {
            moveTo(s * 0.66f, s * 0.70f); lineTo(s * 0.66f, s * 0.34f)
            moveTo(s * 0.56f, s * 0.45f); lineTo(s * 0.66f, s * 0.34f); lineTo(s * 0.76f, s * 0.45f)
        }
        canvas.drawPath(down, stroke)
        canvas.drawPath(up, stroke)
        return bmp
    }
}
