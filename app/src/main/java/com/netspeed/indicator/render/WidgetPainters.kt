package com.netspeed.indicator.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.service.SpeedFormatter
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * THE shared widget render pipeline. Each `draw*` function is pure — it takes a
 * [WidgetData] snapshot and produces a Bitmap — so the exact same code backs the
 * in-app preview (drawn live) and the home-screen widget (drawn into a Bitmap and
 * pushed via setImageViewBitmap). Picker preview and widget are pixel-identical.
 *
 * Speed maps to a 0..1 fraction against a 48 MB/s ceiling, matching the hero and
 * notification sparkline.
 */
data class WidgetData(
    val downBps: Long = 0,
    val upBps: Long = 0,
    val todayBytes: Long = 0,
    val peakBps: Long = 0,
    val dailyQuotaBytes: Long = 0,
    val history: List<Long> = emptyList(),
    /** Skin accent (0 = use tier colour). */
    val accentArgb: Int = 0,
    /** Skin hero gradient (empty = default blue→purple→pink). */
    val gradientArgb: List<Int> = emptyList(),
    /** Gemini gradient-flow phase [0,1) from the service clock; 0 = still frame. */
    val phase: Float = 0f,
)

enum class WidgetKind { HERO, DIAL, RINGS, PILL, WEATHER }

object WidgetPainters {

    private const val CEIL = 48f * 1024f * 1024f          // 48 MB/s download full-scale
    private const val UP_CEIL = 8f * 1024f * 1024f        // 8 MB/s upload full-scale

    private val cardBg = Color.argb(235, 16, 18, 24)      // #101218 ~92%
    private val faint = Color.argb((0.14f * 255).toInt(), 255, 255, 255)
    private val white = Color.WHITE
    private val white60 = Color.argb((0.6f * 255).toInt(), 255, 255, 255)

    fun render(kind: WidgetKind, widthPx: Int, heightPx: Int, data: WidgetData): Bitmap = when (kind) {
        WidgetKind.HERO -> hero(widthPx, heightPx, data)
        WidgetKind.DIAL -> dial(widthPx, heightPx, data)
        WidgetKind.RINGS -> rings(widthPx, heightPx, data)
        WidgetKind.PILL -> pill(widthPx, heightPx, data)
        WidgetKind.WEATHER -> weather(widthPx, heightPx, data)
    }

    /**
     * 4×2 gradient hero: the flagship widget — gradient card, header with today's
     * total, a big tabular number, the upload line, and a live sparkline of recent
     * samples. Mirrors the in-app hero so the home screen feels of-a-piece.
     */
    fun hero(w: Int, h: Int, d: WidgetData): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = h * 0.16f
        // Skin gradient if set, else the default blue→purple→pink — rendered with
        // the gemini flow (the service advances d.phase once per second).
        val colors = if (d.gradientArgb.size >= 2) d.gradientArgb.toIntArray()
        else intArrayOf(0xFF2563EB.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt())
        val grad = com.netspeed.indicator.core.GradientFlow.shader(w.toFloat(), h.toFloat(), colors, d.phase)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad })

        val pad = w * 0.06f
        // Header row.
        c.drawText("NetSpeed", pad, h * 0.18f, text(h * 0.10f, white60, align = Paint.Align.LEFT))
        c.drawText(
            "${SpeedFormatter.total(d.todayBytes)} today", w - pad, h * 0.18f,
            text(h * 0.10f, white60, align = Paint.Align.RIGHT),
        )
        // Big number.
        val num = SpeedFormatter.parts(d.downBps)
        val numPaint = text(h * 0.34f, white, bold = true, align = Paint.Align.LEFT)
        c.drawText(num.value, pad, h * 0.56f, numPaint)
        val numW = numPaint.measureText(num.value)
        val unitPaint = text(h * 0.12f, white60, align = Paint.Align.LEFT)
        c.drawText(num.unit, pad + numW + w * 0.02f, h * 0.56f, unitPaint)
        // Upload line.
        c.drawText(
            "▲ ${SpeedFormatter.inline(d.upBps)}", pad, h * 0.80f,
            text(h * 0.11f, white60, align = Paint.Align.LEFT),
        )
        // Sparkline on the right — its left edge yields to the measured number+unit
        // so a 4-digit value ("1023 KB/s") can never run under the bars.
        val textRight = pad + numW + w * 0.02f + unitPaint.measureText(num.unit)
        drawSparkline(
            c, d.history,
            left = maxOf(w * 0.52f, textRight + w * 0.03f),
            top = h * 0.30f, right = w - pad, bottom = h * 0.78f,
        )
        return bmp
    }

    private fun drawSparkline(c: Canvas, history: List<Long>, left: Float, top: Float, right: Float, bottom: Float) {
        val bars = 20
        val w = right - left
        val gap = w * 0.02f
        val barW = (w - gap * (bars - 1)) / bars
        val recent = history.takeLast(bars)
        val pad = bars - recent.size
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) }
        for (i in 0 until bars) {
            val v = if (i < pad) 0L else recent[i - pad]
            val frac = (v.toFloat() / CEIL).coerceIn(0f, 1f).coerceAtLeast(0.04f)
            val barH = (bottom - top) * frac
            val x = left + i * (barW + gap)
            c.drawRoundRect(RectF(x, bottom - barH, x + barW, bottom), barW * 0.4f, barW * 0.4f, p)
        }
    }

    private fun newCard(w: Int, h: Int): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = min(w, h) * 0.14f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg })
        return bmp to c
    }

    private fun text(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = align
            typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    /** Small faded "NetSpeed" brand tag so each widget is identifiable. */
    private fun brand(c: Canvas, w: Int, h: Int, yFrac: Float = 0.15f, sizeFrac: Float = 0.085f) {
        c.drawText("NetSpeed", w / 2f, h * yFrac, text(min(w, h) * sizeFrac, white60))
    }

    private fun tierColor(downBps: Long): Int = SpeedTiers.tierOf(downBps / 1_048_576f).c2.toArgb()

    /** Per-channel ARGB blend of [a] toward [b] by [t]. */
    private fun blendArgb(a: Int, b: Int, t: Float): Int {
        fun ch(sa: Int, sb: Int) = (sa + (sb - sa) * t).toInt().coerceIn(0, 255)
        return Color.argb(
            ch(Color.alpha(a), Color.alpha(b)),
            ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)),
            ch(Color.blue(a), Color.blue(b)),
        )
    }

    /** Skin accent if set, otherwise the live tier colour. */
    private fun accentOf(d: WidgetData): Int = if (d.accentArgb != 0) d.accentArgb else tierColor(d.downBps)

    /** Flagship dial: 270° round-cap arc (never a full circle), endpoint dot, centred number. */
    fun dial(w: Int, h: Int, d: WidgetData): Bitmap {
        val (bmp, c) = newCard(w, h)
        // Raised + smaller so neither the arc track nor the mid-scale endpoint dot
        // can run through the tag.
        brand(c, w, h, yFrac = 0.105f, sizeFrac = 0.07f)
        val f = (d.downBps / CEIL).coerceIn(0f, 1f)
        val stroke = min(w, h) * 0.085f
        val pad = stroke + min(w, h) * 0.10f
        val rect = RectF(pad, pad, w - pad, h - pad)
        val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        arcPaint.color = faint
        c.drawArc(rect, 135f, 270f, false, arcPaint)
        arcPaint.color = accentOf(d)
        c.drawArc(rect, 135f, 270f * f, false, arcPaint)
        // Endpoint dot.
        val a = Math.toRadians((135f + 270f * f).toDouble())
        val cx = rect.centerX(); val cy = rect.centerY(); val rr = rect.width() / 2f
        c.drawCircle(cx + rr * cos(a).toFloat(), cy + rr * sin(a).toFloat(), stroke * 0.55f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white })
        // Centre number + live unit. Unit only (no "down") — the short label stays
        // clear of the arc's endpoint dots, which sit either side of the gap.
        val p = SpeedFormatter.parts(d.downBps)
        c.drawText(p.value, cx, cy + min(w, h) * 0.06f, text(min(w, h) * 0.22f, white, bold = true))
        c.drawText(p.unit, cx, cy + min(w, h) * 0.22f, text(min(w, h) * 0.085f, white60))
        return bmp
    }

    /** Three concentric arcs: download, upload, daily quota. */
    fun rings(w: Int, h: Int, d: WidgetData): Bitmap {
        val (bmp, c) = newCard(w, h)
        val cx = w / 2f; val cy = h / 2f
        // Brand sits in the free centre hole — the old top placement ran straight
        // through the outer ring and its round stroke caps.
        c.drawText("NetSpeed", cx, cy + min(w, h) * 0.02f, text(min(w, h) * 0.055f, white60))
        val stroke = min(w, h) * 0.07f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND
        }
        val radii = floatArrayOf(min(w, h) * 0.37f, min(w, h) * 0.27f, min(w, h) * 0.17f)
        // Skin-derived trio: accent, accent lightened, accent faded — keeps every
        // skin's identity instead of the old fixed blue/pink/yellow.
        val accent = accentOf(d)
        val colors = intArrayOf(accent, blendArgb(accent, white, 0.45f), blendArgb(accent, white, 0.75f))
        val fracs = floatArrayOf(
            (d.downBps / CEIL).coerceIn(0f, 1f),
            (d.upBps / UP_CEIL).coerceIn(0f, 1f),
            if (d.dailyQuotaBytes > 0) (d.todayBytes.toFloat() / d.dailyQuotaBytes).coerceIn(0f, 1f) else 0f,
        )
        for (i in 0..2) {
            val rect = RectF(cx - radii[i], cy - radii[i], cx + radii[i], cy + radii[i])
            ringPaint.color = faint; c.drawArc(rect, -90f, 360f, false, ringPaint)
            ringPaint.color = colors[i]; c.drawArc(rect, -90f, 360f * fracs[i], false, ringPaint)
        }
        return bmp
    }

    /** 2×1 pill: down arrow + live number. */
    fun pill(w: Int, h: Int, d: WidgetData): Bitmap {
        val (bmp, c) = newCard(w, h)
        val ax = h * 0.42f; val ay = h * 0.5f; val s = h * 0.18f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentOf(d); style = Paint.Style.STROKE; strokeWidth = h * 0.06f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val path = Path().apply {
            moveTo(ax, ay - s); lineTo(ax, ay + s)
            moveTo(ax - s * 0.7f, ay + s * 0.3f); lineTo(ax, ay + s); lineTo(ax + s * 0.7f, ay + s * 0.3f)
        }
        c.drawPath(path, p)
        // Live unit trails the measured number on a SHARED baseline — the old
        // right-anchored hardcoded "MB/s" was wrong below 1 MB/s and collided with
        // 4-digit values.
        val parts = SpeedFormatter.parts(d.downBps)
        val numPaint = text(h * 0.34f, white, bold = true, align = Paint.Align.LEFT)
        c.drawText(parts.value, h * 0.72f, h * 0.60f, numPaint)
        c.drawText(
            parts.unit, h * 0.72f + numPaint.measureText(parts.value) + h * 0.08f, h * 0.60f,
            text(h * 0.2f, white60, align = Paint.Align.LEFT),
        )
        c.drawText("NetSpeed", h * 0.42f, h * 0.18f, text(h * 0.12f, white60, align = Paint.Align.LEFT))
        return bmp
    }

    /** Network weather: tier word + number + subtitle, tier-tinted. */
    fun weather(w: Int, h: Int, d: WidgetData): Bitmap {
        val (bmp, c) = newCard(w, h)
        val tier = SpeedTiers.tierOf(d.downBps / 1_048_576f)
        val tc = accentOf(d)
        c.drawCircle(w * 0.14f, h * 0.26f, h * 0.06f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tc })
        c.drawText(tier.defaultWord, w * 0.22f, h * 0.30f, text(h * 0.12f, white, bold = true, align = Paint.Align.LEFT))
        c.drawText(SpeedFormatter.inline(d.downBps), w * 0.08f, h * 0.62f, text(h * 0.27f, white, bold = true, align = Paint.Align.LEFT))
        c.drawText(tier.defaultSubtitle, w * 0.08f, h * 0.84f, text(h * 0.10f, white60, align = Paint.Align.LEFT))
        c.drawText("NetSpeed", w - w * 0.04f, h * 0.16f, text(h * 0.10f, white60, align = Paint.Align.RIGHT))
        return bmp
    }
}
