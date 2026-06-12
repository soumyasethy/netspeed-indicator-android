package com.netspeed.indicator.tools

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import com.netspeed.indicator.data.HeroTheme
import com.netspeed.indicator.render.WidgetData
import com.netspeed.indicator.render.WidgetKind
import com.netspeed.indicator.render.WidgetPainters
import java.io.File
import java.io.FileOutputStream

/**
 * DEBUG-ONLY design-QA tool: renders EVERY hero theme motif plus the other four
 * widget kinds with worst-case wide values ("1023 KB/s", "999.9 MB today") into
 * one labelled contact sheet, so text-overlap regressions are caught by looking
 * at a single image instead of re-pinning widgets 18 times.
 *
 * Run:  adb shell am start -n com.netspeed.indicator/com.netspeed.indicator.tools.WidgetGalleryActivity
 * Pull: adb pull /sdcard/Android/data/com.netspeed.indicator/files/widget-gallery.png
 */
class WidgetGalleryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = render()
        Log.i("WidgetGallery", "wrote ${file.absolutePath} (${file.length()} bytes)")
        finish()
    }

    private fun render(): File {
        // Worst-case data: 4-digit speeds, near-GB today, full history ramp.
        val data = WidgetData(
            downBps = 1023L * 1024L,
            upBps = 1023L * 1024L,
            todayBytes = 1_048_400_000L,           // "999.9 MB"
            peakBps = 1023L * 1024L,
            dailyQuotaBytes = 2_000_000_000L,
            history = List(24) { (it + 1) * 2L * 1024 * 1024 },
            accentArgb = 0xFF7C3AED.toInt(),
            gradientArgb = listOf(0xFF2563EB.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt(), 0xFF2563EB.toInt()),
            phase = 0.35f,
            heroFgArgb = Color.WHITE,
        )

        val cellW = 520; val cellH = 240; val label = 34; val gap = 10
        val themes = HeroTheme.entries
        val cols = 2
        val heroRows = (themes.size + cols - 1) / cols
        val kindRow = listOf(WidgetKind.DIAL, WidgetKind.RINGS, WidgetKind.PILL, WidgetKind.WEATHER)
        // Wide enough for BOTH the hero grid and the bottom kind-row (which is
        // wider: 240+240+300+320 + gaps) — otherwise the sheet clips the last card
        // and fakes a text-overflow bug.
        val kindRowW = 240 + 240 + 300 + 320 + gap * 2 * 4 + gap
        val sheetW = maxOf(cols * cellW + (cols + 1) * gap, kindRowW)
        val sheetH = (heroRows + 1) * (cellH + label + gap) + gap
        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        c.drawColor(0xFF202428.toInt())
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 24f }

        themes.forEachIndexed { i, theme ->
            val col = i % cols; val row = i / cols
            val x = gap + col * (cellW + gap)
            val y = gap + row * (cellH + label + gap)
            c.drawText(theme.label, x.toFloat(), y + 24f, labelPaint)
            val bmp = WidgetPainters.hero(cellW, cellH, data.copy(themeKey = theme.storageKey))
            c.drawBitmap(bmp, x.toFloat(), (y + label).toFloat(), null)
        }
        // Bottom row: the other four widget kinds at their real aspect ratios.
        val y = gap + heroRows * (cellH + label + gap)
        var x = gap
        kindRow.forEach { kind ->
            val (kw, kh) = when (kind) {
                WidgetKind.DIAL, WidgetKind.RINGS -> 240 to 240
                WidgetKind.PILL -> 300 to 142
                else -> 320 to 213
            }
            c.drawText(kind.name, x.toFloat(), y + 24f, labelPaint)
            c.drawBitmap(WidgetPainters.render(kind, kw, kh, data), x.toFloat(), (y + label).toFloat(), null)
            x += kw + gap * 2
        }

        val out = File(getExternalFilesDir(null), "widget-gallery.png")
        FileOutputStream(out).use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out
    }
}
