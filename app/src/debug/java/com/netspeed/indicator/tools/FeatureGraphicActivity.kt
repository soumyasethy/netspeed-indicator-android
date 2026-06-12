package com.netspeed.indicator.tools

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import com.netspeed.indicator.core.GradientFlow
import java.io.File
import java.io.FileOutputStream

/**
 * DEBUG-ONLY asset generator. Renders the 1024×500 Google Play feature graphic with
 * the real [GradientFlow] engine (Aurora palette) so the banner matches the in-app
 * hero, then writes it to the app's external files dir for `adb pull`. Not part of
 * the shipped app — lives in src/debug and is never merged into the release build.
 *
 * Run: adb shell am start -n com.netspeed.indicator/com.netspeed.indicator.tools.FeatureGraphicActivity
 * Pull: adb pull /sdcard/Android/data/com.netspeed.indicator/files/feature-graphic.png
 */
class FeatureGraphicActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = render()
        Log.i("FeatureGraphic", "wrote ${file.absolutePath} (${file.length()} bytes)")
        finish()
    }

    private fun render(): File {
        val w = 1024
        val h = 500
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Aurora gradient background (blue → purple → pink), via the shipped flow engine.
        val aurora = intArrayOf(
            0xFF2563EB.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt(), 0xFF2563EB.toInt(),
        )
        c.drawRect(
            0f, 0f, w.toFloat(), h.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = GradientFlow.shader(w.toFloat(), h.toFloat(), aurora, 0.22f)
            },
        )
        // Left-side darken so white text always has contrast over the bright gradient.
        c.drawRect(
            0f, 0f, w * 0.66f, h.toFloat(),
            Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, w * 0.66f, 0f,
                    0xB3000000.toInt(), 0x00000000, android.graphics.Shader.TileMode.CLAMP,
                )
            },
        )

        // --- product mock: a status-bar strip showing the real speed chip ---------
        val barL = 64f; val barT = 56f; val barR = 960f; val barB = 150f
        c.drawRoundRect(
            RectF(barL, barT, barR, barB), 26f, 26f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE60E1118.toInt() },
        )
        val barFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        c.drawText("9:41", barL + 36f, barB - 30f, barFg)
        // The blue speed chip (matches the in-app status-bar icon style).
        val chipL = barL + 360f
        c.drawRoundRect(
            RectF(chipL, barT + 18f, chipL + 250f, barB - 18f), 18f, 18f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2563EB.toInt() },
        )
        c.drawText(
            "↓84  ↑12", chipL + 30f, barB - 32f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 40f
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            },
        )
        barFg.textAlign = Paint.Align.RIGHT
        c.drawText("Wi-Fi 96%", barR - 36f, barB - 30f, barFg)

        // --- headline -------------------------------------------------------------
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textSize = 96f
        }
        c.drawText("Internet Speed", 64f, 296f, title)
        c.drawText("Meter", 64f, 392f, title)

        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE7ECF5.toInt()
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 36f
        }
        c.drawText("Live download & upload speed, every second.", 66f, 440f, sub)

        // --- privacy pills --------------------------------------------------------
        var px = 66f
        val py = 470f
        for (label in listOf("No ads", "No trackers", "No INTERNET")) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 28f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            val tw = tp.measureText(label)
            val pillW = tw + 44f
            c.drawRoundRect(
                RectF(px, py - 30f, px + pillW, py + 8f), 19f, 19f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF },
            )
            c.drawText(label, px + 22f, py - 4f, tp)
            px += pillW + 16f
        }

        // --- brand wordmark -------------------------------------------------------
        c.drawText(
            "lazycode.ai", barR - 4f, 300f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt(); textSize = 34f; textAlign = Paint.Align.RIGHT
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            },
        )

        val out = File(getExternalFilesDir(null), "feature-graphic.png")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }
}
