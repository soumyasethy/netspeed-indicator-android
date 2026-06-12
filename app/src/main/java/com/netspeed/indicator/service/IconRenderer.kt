package com.netspeed.indicator.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import com.netspeed.indicator.data.IconStyle
import com.netspeed.indicator.data.UnitStyle

/**
 * Renders a speed value into a small white-on-transparent [Bitmap] suitable for
 * use as a notification small-icon.
 *
 * Why a bitmap at all? Android does not let an app draw into the status bar.
 * The only sanctioned surface that *is* in the status bar is a notification's
 * small icon, and [androidx.core.graphics.drawable.IconCompat.createWithBitmap]
 * lets that icon be an arbitrary bitmap. So we draw the speed onto a canvas and
 * hand it over as the icon, refreshing it once per second.
 *
 * [render] is the single entry point: it dispatches on [IconStyle] and the
 * download-only / combined toggle. The same renderer powers both the live
 * status-bar icon and the in-app style previews, so what the user picks in the
 * preview is pixel-identical to what they get.
 *
 * Everything is drawn in opaque white over an alpha background; the system tints
 * the icon to match the status bar, so shape comes from the alpha channel.
 */
class IconRenderer(private val sizePx: Int = 96) {

    /**
     * System font-scale factor (clamped) applied to the rendered text so the icon
     * grows/shrinks with the user's display font size, like everything else.
     */
    var fontScale: Float = 1f
        set(value) { field = value.coerceIn(0.85f, 1.35f) }

    /** User-chosen icon text-size multiplier, on top of [fontScale]. */
    var userScale: Float = 1f
        set(value) { field = value.coerceIn(0.7f, 1.4f) }

    /** Combined effective text scale (system font size × user choice). */
    private val eff: Float get() = fontScale * userScale

    /** Optional icon background (0 = transparent) and the glyph colour. */
    var bgColorArgb: Int = 0
    var fgColorArgb: Int = Color.WHITE

    /** Optional rounded outline: colour (0 = none) and width step (1–3). */
    var borderColorArgb: Int = 0
    var borderWidth: Int = 1
        set(value) { field = value.coerceIn(1, 3) }

    /** Unit treatment for the single-direction styles (Auto/side-by-side/Compact). */
    var unitStyle: UnitStyle = UnitStyle.DEFAULT

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val valuePaint = textPaint()
    private val unitPaint = textPaint()
    private val rowPaint = textPaint()
    // Stroked arrow (stem + chevron head) reads far better at status-bar size
    // than a solid triangle. Width is set per-call relative to the arrow height.
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** A clean directional arrow (stem + chevron) centred at (cx, cy), height [h]. */
    private fun drawArrowGlyph(canvas: Canvas, cx: Float, cy: Float, h: Float, down: Boolean) {
        arrowPaint.strokeWidth = h * 0.28f       // bolder so it reads at status-bar size
        val half = h / 2f
        val headW = h * 0.32f
        val headDepth = h * 0.34f
        val tipY = if (down) cy + half else cy - half
        val tailY = if (down) cy - half else cy + half
        val headBaseY = if (down) tipY - headDepth else tipY + headDepth
        canvas.drawLine(cx, tailY, cx, tipY, arrowPaint)              // stem
        canvas.drawLine(cx - headW, headBaseY, cx, tipY, arrowPaint)  // head left
        canvas.drawLine(cx + headW, headBaseY, cx, tipY, arrowPaint)  // head right
    }

    private fun textPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        // Heaviest system face: at status-bar size, stroke weight is what makes
        // digits readable (plain bold reads thin next to e.g. ISML's icons).
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        isSubpixelText = true
    }

    private val capProbe = Rect()

    /** Sets [paint]'s textSize so a digit's measured cap-height equals [capPx]. */
    private fun sizeForCapHeight(paint: Paint, capPx: Float) {
        paint.textSize = 100f
        paint.getTextBounds("8", 0, 1, capProbe)
        paint.textSize = 100f * (capPx / capProbe.height().coerceAtLeast(1).toFloat())
    }

    /**
     * @param style visual treatment.
     * @param downBps / upBps current per-second rates.
     * @param showCombined when true, "combined" styles fold up+down together and
     *        ARROWS shows both rows; otherwise download only.
     */
    fun render(style: IconStyle, downBps: Long, upBps: Long, showCombined: Boolean): Bitmap {
        applyColors()
        return frame(
            content = contentFor(style, downBps, upBps, showCombined),
            refContent = { contentFor(style, REF_BPS, REF_BPS, showCombined) },
        )
    }

    private fun applyColors() {
        valuePaint.color = fgColorArgb
        unitPaint.color = fgColorArgb
        rowPaint.color = fgColorArgb
        arrowPaint.color = fgColorArgb
        bgPaint.color = bgColorArgb
        borderPaint.color = borderColorArgb
    }

    private fun contentFor(style: IconStyle, downBps: Long, upBps: Long, showCombined: Boolean): Bitmap =
        when (style) {
            IconStyle.ARROWS ->
                if (showCombined) arrowsUpDown(upBps, downBps) else arrowsDownOnly(downBps)
            IconStyle.ARROWS_H ->
                // Download-only: single full-height row ("↓84k") — near-square, so
                // the OS fits it by height and it lands near clock size.
                if (showCombined) arrowsHorizontal(downBps, upBps) else singleContent(downBps, down = true)
            IconStyle.STACKED ->
                stacked(if (showCombined) downBps + upBps else downBps)
            IconStyle.COMPACT ->
                compact(if (showCombined) downBps + upBps else downBps)
            IconStyle.AUTO ->
                // The busier direction wins — one arrow + one speed at a time.
                if (upBps > downBps) singleContent(upBps, down = false)
                else singleContent(downBps, down = true)
        }

    /** True when a pill fill or outline is active — content is then framed in a
     *  fixed square chip ([frame]); the glyphs themselves stay full size. */
    private val isDecorated: Boolean
        get() = Color.alpha(bgColorArgb) != 0 || Color.alpha(borderColorArgb) != 0

    private val chipContentPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val punchPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
    }

    /**
     * Decorates glyph content with the optional background pill / outline.
     * The chip is one FIXED square badge — exactly [sizePx] × [sizePx] — so it
     * always fills the OS icon slot by height and never changes size with the
     * value. Content is fit-scaled into a thinly padded (6%) inner box: at slot
     * size the punched-out digits must DOMINATE the chip or they blur into the
     * fill, so the glyphs get ~80–94% of the chip, modulated by the user's
     * text-size slider (which therefore stays visibly effective inside the chip).
     *
     * LEGIBILITY FALLBACK: if the fitted digits would still land below
     * [MIN_LEGIBLE_FRACTION] of the chip (wide content squeezed into the square —
     * e.g. two-row styles or long "1023 KB/s" rows), the chip is skipped and the
     * plain undecorated glyphs are returned instead. Deterministic per
     * style/unit/value — never an intermittent blank box.
     *
     * Without bg and outline the tight content is returned as-is.
     */
    private fun frame(content: Bitmap, refContent: () -> Bitmap): Bitmap {
        if (!isDecorated) return content
        val h = sizePx
        val w = sizePx
        val pad = h * 0.06f
        val maxW = w - pad * 2f
        val maxH = h - pad * 2f
        // User slider maps 0.8..1.4 → 0.78..1.0 of the fitted size, so "Icon text
        // size" visibly changes the digits inside the chip too (pure fit-to-box
        // used to cancel the slider entirely).
        val userFill = (0.78f + (userScale - 0.8f) / 0.6f * 0.22f).coerceIn(0.78f, 1f)
        val fit = minOf(maxW / content.width, maxH / content.height)
        val s = fit * userFill
        val dh = content.height * s
        if (dh < h * MIN_LEGIBLE_FRACTION) return content   // legibility fallback
        val dw = content.width * s
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val r = h * 0.22f
        if (Color.alpha(bgColorArgb) != 0) {
            canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, bgPaint)
        }
        if (Color.alpha(borderColorArgb) != 0) {
            val stroke = borderWidth * 2.5f        // ≈1 screen px per step at slot scale
            borderPaint.strokeWidth = stroke
            val inset = stroke / 2f
            canvas.drawRoundRect(inset, inset, w - inset, h - inset, r, r, borderPaint)
        }
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        // With a FILLED chip the glyphs are PUNCHED OUT as transparent holes
        // (DST_OUT) instead of painted on top. Both Android renderers then work:
        // a tinting status bar (AOSP always, One UI sometimes) paints the chip's
        // alpha in one colour and the holes still read as digits; a colour-true
        // bar shows the fill with the bar showing through the cut-outs. An opaque
        // fill with opaque text would tint into a single blank box.
        canvas.drawBitmap(
            content, null,
            android.graphics.RectF(left, top, left + dw, top + dh),
            if (Color.alpha(bgColorArgb) != 0) punchPaint else chipContentPaint,
        )
        return out
    }

    /**
     * Full-colour chip for our OWN surfaces (the floating bubble) — NOT the status
     * bar. There is no OS tint to fight here, so glyphs are painted in their real
     * colour ON TOP of the background pill (no DST_OUT punch-out), and the chip is
     * sized to its content (not forced square), so wide styles like side-by-side
     * stay wide and legible. Honours icon style, unit style, colours, outline and
     * font size exactly like the status-bar icon — what you set is what floats.
     */
    fun renderChip(style: IconStyle, downBps: Long, upBps: Long, showCombined: Boolean): Bitmap {
        applyColors()
        val content = contentFor(style, downBps, upBps, showCombined)
        val padX = sizePx * 0.18f
        val padY = sizePx * 0.16f
        val w = (content.width + padX * 2f).toInt().coerceAtLeast(1)
        val h = (content.height + padY * 2f).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val r = h * 0.34f
        if (Color.alpha(bgColorArgb) != 0) {
            canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, bgPaint)
        }
        if (Color.alpha(borderColorArgb) != 0) {
            val stroke = borderWidth * 3f
            borderPaint.strokeWidth = stroke
            val inset = stroke / 2f
            canvas.drawRoundRect(inset, inset, w - inset, h - inset, r, r, borderPaint)
        }
        canvas.drawBitmap(content, padX, padY, chipContentPaint)
        return out
    }

    private companion object {
        /** Reference rate (888 KiB/s) for stable frame sizing — widest typical digits. */
        const val REF_BPS = 888L * 1024L

        /** Minimum digit height (fraction of the chip) for a legible punched chip. */
        const val MIN_LEGIBLE_FRACTION = 0.42f
    }

    // --- stacked styles --------------------------------------------------------
    // All four use tight cap-height rows flush to the bitmap edges (no floating
    // padding): the OS scales the bitmap into its icon slot, so any transparent
    // margin directly shrinks the visible digits.

    /** Download-only: big value on top, "▼ unit" on the bottom — tight rows. */
    private fun arrowsDownOnly(downBps: Long): Bitmap {
        val p = SpeedFormatter.parts(downBps)
        return stackedBitmap(p.value, p.unit, unitArrowDown = true)
    }

    /** Stacked: value over unit, no arrow — tight rows. */
    private fun stacked(bps: Long): Bitmap {
        val p = SpeedFormatter.parts(bps)
        return stackedBitmap(p.value, p.unit, unitArrowDown = null)
    }

    /** Value row + unit row, cap-height sized, zero outer padding. */
    private fun stackedBitmap(value: String, unit: String, unitArrowDown: Boolean?): Bitmap {
        val h = sizePx
        val valueCap = h * (0.54f * eff).coerceIn(0.30f, 0.60f)
        val unitCap = h * (0.30f * eff).coerceIn(0.17f, 0.34f)
        val rowGap = h * 0.08f
        val padY = (h - valueCap - unitCap - rowGap) / 2f

        valuePaint.textAlign = Paint.Align.CENTER
        valuePaint.textScaleX = 1f
        sizeForCapHeight(valuePaint, valueCap)
        val wValue = valuePaint.measureText(value)

        unitPaint.textAlign = Paint.Align.CENTER
        unitPaint.textScaleX = 1f
        sizeForCapHeight(unitPaint, unitCap)
        val arrowH = if (unitArrowDown != null) unitCap else 0f
        val arrowW = arrowH * 0.55f
        val arrowGap = if (unitArrowDown != null) unitCap * 0.18f else 0f
        val wUnitRow = arrowW + arrowGap + unitPaint.measureText(unit)

        val padX = h * 0.03f
        val w = (maxOf(wValue, wUnitRow) + padX * 2f).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = w / 2f
        valuePaint.textScaleX = squash(value, valuePaint, w - padX * 2f)
        canvas.drawText(value, cx, padY + valueCap, valuePaint)

        val unitBaseline = padY + valueCap + rowGap + unitCap
        if (unitArrowDown != null) {
            val rowLeft = cx - wUnitRow / 2f
            drawArrowGlyph(canvas, rowLeft + arrowW / 2f, unitBaseline - unitCap / 2f, arrowH, down = unitArrowDown)
            unitPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(unit, rowLeft + arrowW + arrowGap, unitBaseline, unitPaint)
        } else {
            unitPaint.textScaleX = squash(unit, unitPaint, w - padX * 2f)
            canvas.drawText(unit, cx, unitBaseline, unitPaint)
        }
        return bitmap
    }

    /** "▲ upCompact" / "▼ downCompact" — two tight cap-height rows. */
    private fun arrowsUpDown(upBps: Long, downBps: Long): Bitmap {
        val h = sizePx
        val rowCap = h * (0.40f * eff).coerceIn(0.23f, 0.44f)
        val rowGap = h * 0.10f
        val padY = (h - rowCap * 2f - rowGap) / 2f

        rowPaint.textAlign = Paint.Align.LEFT
        rowPaint.textScaleX = 1f
        sizeForCapHeight(rowPaint, rowCap)
        val up = SpeedFormatter.compact(upBps)
        val down = SpeedFormatter.compact(downBps)
        val arrowH = rowCap
        val arrowW = arrowH * 0.55f
        val gap = rowCap * 0.15f
        val wUp = arrowW + gap + rowPaint.measureText(up)
        val wDown = arrowW + gap + rowPaint.measureText(down)

        val padX = h * 0.03f
        val w = (maxOf(wUp, wDown) + padX * 2f).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        fun row(text: String, rowW: Float, baseline: Float, downArrow: Boolean) {
            val left = (w - rowW) / 2f
            drawArrowGlyph(canvas, left + arrowW / 2f, baseline - rowCap / 2f, arrowH, down = downArrow)
            canvas.drawText(text, left + arrowW + gap, baseline, rowPaint)
        }
        row(up, wUp, padY + rowCap, downArrow = false)
        row(down, wDown, padY + rowCap + rowGap + rowCap, downArrow = true)
        return bitmap
    }

    /** Compact: one big token — unit short / full / below per [unitStyle]. */
    private fun compact(bps: Long): Bitmap {
        if (unitStyle == UnitStyle.BELOW) {
            val p = SpeedFormatter.parts(bps)
            return stackedBitmap(p.value, p.unit, unitArrowDown = null)
        }
        val (digits, suffix) = when (unitStyle) {
            UnitStyle.FULL -> {
                val p = SpeedFormatter.parts(bps)
                p.value to p.unit
            }
            else -> {
                val token = SpeedFormatter.compact(bps)   // e.g. "65k", "1.4m", "0"
                if (token.last().isLetter()) token.dropLast(1) to token.takeLast(1)
                else token to ""
            }
        }

        val h = sizePx
        val capH = h * (0.92f * eff).coerceIn(0.6f, 1.0f)
        val padY = (h - capH) / 2f
        valuePaint.textAlign = Paint.Align.LEFT
        valuePaint.textScaleX = 1f
        sizeForCapHeight(valuePaint, capH)
        val wDigits = valuePaint.measureText(digits)
        unitPaint.textAlign = Paint.Align.LEFT
        unitPaint.textScaleX = 1f
        sizeForCapHeight(unitPaint, capH * 0.5f)
        val unitGap = if (suffix.isEmpty()) 0f else capH * 0.05f
        val wSuffix = if (suffix.isEmpty()) 0f else unitPaint.measureText(suffix)

        val padX = h * 0.04f
        val w = (wDigits + unitGap + wSuffix + padX * 2f).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val baseline = padY + capH
        canvas.drawText(digits, padX, baseline, valuePaint)
        if (suffix.isNotEmpty()) canvas.drawText(suffix, padX + wDigits + unitGap, baseline, unitPaint)
        return bitmap
    }

    // --- drawing helpers -------------------------------------------------------

    /** Rounded background fill, only when a non-transparent background is set. */
    private fun drawBg(canvas: Canvas, w: Int, h: Int) {
        if (Color.alpha(bgColorArgb) == 0) return
        val r = minOf(w, h) * 0.22f
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), r, r, bgPaint)
    }

    private fun squash(text: String, paint: Paint, maxWidth: Float): Float {
        paint.textScaleX = 1f
        val width = paint.measureText(text)
        return if (width <= maxWidth) 1f else maxWidth / width
    }

    /**
     * Horizontal combined: "↓down ↑up unit" in a single row. The visible *ink*
     * must fill the icon — earlier versions floated the text in a padded square,
     * so the digit cap-height was only ~57% of the bitmap and the OS shrank that
     * padded bitmap into the (already short) icon slot, making the digits tiny.
     *
     * Fix: size the digits by *measured* cap-height (getTextBounds) so the glyph
     * fills ~0.90 of the bitmap height, draw arrows at the same cap-height, and
     * crop the bitmap tightly to the content width. One UI honours the wide aspect
     * ratio (verified on device), so the row shows wide and large — near the clock
     * size, bounded only by the OS notification-icon slot height.
     */
    private fun arrowsHorizontal(downBps: Long, upBps: Long): Bitmap {
        val p = SpeedFormatter.compactPair(downBps, upBps)
        // The digits are the tallest element and fill ~0.92 of a bitmap cropped tight
        // top/bottom AND side-to-side. One UI fits a notification icon into its slot
        // preserving aspect ratio, so a *wide* bitmap is fit-by-width and ends up
        // shorter than the slot. We therefore keep the row as NARROW as legibility
        // allows (small arrows, tight gaps) so the bitmap fits closer to by-height
        // and the digits reach near the full slot height — close to the clock size.
        val fill = (0.92f * eff).coerceIn(0.45f, 1.0f)
        val capH = sizePx * fill                          // target digit cap-height

        // Size the font so a digit's measured cap-height equals capH.
        rowPaint.textAlign = Paint.Align.LEFT
        rowPaint.textScaleX = 1f
        rowPaint.textSize = 100f
        val probe = Rect()
        rowPaint.getTextBounds("8", 0, 1, probe)
        val capAt100 = probe.height().toFloat().coerceAtLeast(1f)
        val fullTextSize = 100f * (capH / capAt100)
        val unitTextSize = fullTextSize * 0.5f             // small suffix saves width

        // One UI caps a notification icon's WIDTH, so a wide row is scaled down and
        // ends up shorter than the slot. The digits are what the user reads, so we
        // keep them full height and make the arrows/unit small — that trims the row
        // width, letting the whole bitmap fit taller (digits closer to clock size).
        val arrowH = capH * 0.58f                          // small directional marks
        val arrowW = arrowH * 0.55f                        // narrow arrows save width
        val innerGap = capH * 0.03f                        // arrow ↔ its number
        val pairGap = capH * 0.11f                         // down-group ↔ up-group
        val unitGap = capH * 0.04f
        val padX = capH * 0.05f                            // minimal side margin
        val padY = capH * 0.05f                            // minimal top/bottom margin

        rowPaint.textSize = fullTextSize
        val wDown = rowPaint.measureText(p.down)
        val wUp = rowPaint.measureText(p.up)
        rowPaint.textSize = unitTextSize
        val wUnit = rowPaint.measureText(p.unit)
        val content = arrowW + innerGap + wDown + pairGap +
            arrowW + innerGap + wUp + unitGap + wUnit

        val w = (content + padX * 2f).toInt().coerceAtLeast(1)
        val h = (capH + padY * 2f).toInt().coerceAtLeast(1)   // tight to the digits
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val baseline = padY + capH                         // cap-top at padY; shared baseline
        val cyArrow = padY + capH / 2f                     // arrows centred on the caps
        var x = padX
        drawArrowGlyph(canvas, x + arrowW / 2f, cyArrow, arrowH, down = true)
        x += arrowW + innerGap
        rowPaint.textSize = fullTextSize
        canvas.drawText(p.down, x, baseline, rowPaint)
        x += wDown + pairGap
        drawArrowGlyph(canvas, x + arrowW / 2f, cyArrow, arrowH, down = false)
        x += arrowW + innerGap
        canvas.drawText(p.up, x, baseline, rowPaint)
        x += wUp + unitGap
        rowPaint.textSize = unitTextSize
        canvas.drawText(p.unit, x, baseline, rowPaint)
        return bitmap
    }

    /**
     * One-direction icon: "↓84k" / "↑1.4m" — a single tight row (or value-over-unit
     * when [unitStyle] is BELOW). Used by the Auto style, the side-by-side pair
     * (two notifications → two icons) and the download-only side-by-side mode:
     * near-square bitmaps are fitted by HEIGHT, landing close to the clock size.
     */
    fun renderSingle(bps: Long, down: Boolean): Bitmap {
        applyColors()
        return frame(
            content = singleContent(bps, down),
            refContent = { singleContent(REF_BPS, down) },
        )
    }

    private fun singleContent(bps: Long, down: Boolean): Bitmap {
        // BELOW: number on top, arrow+unit underneath — reuse the stacked layout.
        if (unitStyle == UnitStyle.BELOW) {
            val p = SpeedFormatter.parts(bps)
            return stackedBitmap(p.value, p.unit, unitArrowDown = down)
        }
        // SHORT: "84k" (suffix from the compact token); FULL: "84 KB/s".
        val (digits, suffix) = when (unitStyle) {
            UnitStyle.FULL -> {
                val p = SpeedFormatter.parts(bps)
                p.value to p.unit
            }
            else -> {
                val token = SpeedFormatter.compact(bps)    // "84k", "1.4m", "0"
                if (token.last().isLetter()) token.dropLast(1) to token.takeLast(1)
                else token to ""
            }
        }

        val fill = (0.92f * eff).coerceIn(0.45f, 1.0f)
        val capH = sizePx * fill

        rowPaint.textAlign = Paint.Align.LEFT
        rowPaint.textScaleX = 1f
        sizeForCapHeight(rowPaint, capH)
        val fullTextSize = rowPaint.textSize
        val unitTextSize = fullTextSize * 0.5f

        val arrowH = capH * 0.58f
        val arrowW = arrowH * 0.55f
        val innerGap = capH * 0.03f
        val unitGap = capH * 0.05f
        val padX = capH * 0.05f
        val padY = capH * 0.05f

        rowPaint.textSize = fullTextSize
        val wDigits = rowPaint.measureText(digits)
        rowPaint.textSize = unitTextSize
        val wUnit = if (suffix.isEmpty()) 0f else rowPaint.measureText(suffix)
        val content = arrowW + innerGap + wDigits + (if (suffix.isEmpty()) 0f else unitGap + wUnit)

        val w = (content + padX * 2f).toInt().coerceAtLeast(1)
        val h = (capH + padY * 2f).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val baseline = padY + capH
        val cyArrow = padY + capH / 2f
        var x = padX
        drawArrowGlyph(canvas, x + arrowW / 2f, cyArrow, arrowH, down = down)
        x += arrowW + innerGap
        rowPaint.textSize = fullTextSize
        canvas.drawText(digits, x, baseline, rowPaint)
        if (suffix.isNotEmpty()) {
            x += wDigits + unitGap
            rowPaint.textSize = unitTextSize
            canvas.drawText(suffix, x, baseline, rowPaint)
        }
        return bitmap
    }
}
