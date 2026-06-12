package com.netspeed.indicator.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.render.scenes.SceneState
import com.netspeed.indicator.render.scenes.SpeedScene
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
    /** Selected hero theme ([com.netspeed.indicator.data.HeroTheme.storageKey];
     *  "" = default Tier-flow layout). Drives the Hero widget's theme motif. */
    val themeKey: String = "",
    /** Skin hero foreground (0 = white). */
    val heroFgArgb: Int = 0,
    /** User tier thresholds (MB/s) — speed scenes need exact tier bounds. */
    val tierThresholds: List<Float> = listOf(1f, 5f, 15f, 30f),
    /** Text block placement: horizontal -1/0/1, vertical -1/0/1 (resolved). */
    val textH: Int = -1,
    val textV: Int = 0,
    /** Info layout key ([com.netspeed.indicator.data.TextFormat]). */
    val textFormat: String = "classic",
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
     * 4×2 hero: the flagship widget. The background is a simplified static MOTIF
     * of the user's selected in-app hero theme ([WidgetData.themeKey]) painted in
     * the skin's colours, so the widget visibly matches what they picked. (Full
     * 60 fps theme animation can't run in RemoteViews — the service refreshes a
     * still frame once per second instead.) Default/unknown key = Tier-flow look.
     */
    /**
     * Flip-book frames for a scene-theme hero widget: [frames] SCENE-ONLY
     * frames at HALF resolution (the dioramas are soft — the launcher scales
     * them up invisibly), stepped [dtS] apart from "now" and cycled
     * launcher-side by a ViewFlipper at ~24 fps. The crisp scrim + text ride
     * in a single full-resolution overlay ([renderHeroOverlay]) so 24 frames
     * cost roughly what 8 full-resolution ones did. Null for non-scene themes.
     */
    fun renderHeroSceneFrames(w: Int, h: Int, d: WidgetData, frames: Int, dtS: Float): List<Bitmap>? {
        val entry = SceneRegistry.fromThemeKey(d.themeKey) ?: return null
        val base = (android.os.SystemClock.elapsedRealtime() % 3_600_000L) / 1000f
        val sw = (w / 2).coerceAtLeast(1)
        val sh = (h / 2).coerceAtLeast(1)
        return List(frames) { i ->
            val bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = sh * 0.16f
            val clip = Path().apply {
                addRoundRect(RectF(0f, 0f, sw.toFloat(), sh.toFloat()), r, r, Path.Direction.CW)
            }
            c.save()
            c.clipPath(clip)
            drawSceneMotif(c, sw.toFloat(), sh.toFloat(), d, entry, base + i * dtS)
            c.restore()
            bmp
        }
    }

    /**
     * Scene frame for the EXPANDED NOTIFICATION card (re-rendered each second by
     * the existing notify tick — the panel plays the scene at 1 fps like the
     * pre-flip-book widgets). Null when the theme isn't a scene → the gemini
     * gradient card applies as before.
     */
    fun sceneCard(
        w: Int, h: Int, themeKey: String, downBps: Long,
        thresholds: List<Float>, cornerPx: Float,
    ): Bitmap? {
        val entry = SceneRegistry.fromThemeKey(themeKey) ?: return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, wf, hf), cornerPx, cornerPx, Path.Direction.CW)
        }
        c.save()
        c.clipPath(clip)
        drawSceneMotif(c, wf, hf, WidgetData(downBps = downBps, tierThresholds = thresholds), entry)
        sceneScrim(c, wf, hf)
        c.restore()
        return bmp
    }

    /**
     * Flip-book frames for the EXPANDED NOTIFICATION: [frames] half-resolution
     * scene frames stepped [dtS] apart, cycled by the panel's ViewFlipper —
     * the diorama moves between the 1 Hz notify ticks. Null for non-scene themes.
     */
    fun sceneCardFrames(
        w: Int, h: Int, themeKey: String, downBps: Long,
        thresholds: List<Float>, cornerPx: Float, frames: Int, dtS: Float,
    ): List<Bitmap>? {
        val entry = SceneRegistry.fromThemeKey(themeKey) ?: return null
        val base = (android.os.SystemClock.elapsedRealtime() % 3_600_000L) / 1000f
        val sw = (w / 2).coerceAtLeast(1)
        val sh = (h / 2).coerceAtLeast(1)
        val d = WidgetData(downBps = downBps, tierThresholds = thresholds)
        return List(frames) { i ->
            val bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val clip = Path().apply {
                addRoundRect(RectF(0f, 0f, sw.toFloat(), sh.toFloat()), cornerPx / 2f, cornerPx / 2f, Path.Direction.CW)
            }
            c.save()
            c.clipPath(clip)
            drawSceneMotif(c, sw.toFloat(), sh.toFloat(), d, entry, base + i * dtS)
            sceneScrim(c, sw.toFloat(), sh.toFloat())
            c.restore()
            bmp
        }
    }

    /** Full-resolution transparent overlay for the flip-book: scrim + texts. */
    fun renderHeroOverlay(w: Int, h: Int, d: WidgetData): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = w.toFloat()
        val hf = h.toFloat()
        val r = h * 0.16f
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, wf, hf), r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        val fg = if (d.heroFgArgb != 0) d.heroFgArgb else white
        val fg60 = (fg and 0x00FFFFFF) or (0x99 shl 24)
        sceneScrim(c, wf, hf)
        heroTexts(c, w, h, d, fg, fg60, sparkline = false, hPos = d.textH, vPos = d.textV)
        c.restore()
        return bmp
    }

    fun hero(w: Int, h: Int, d: WidgetData, sceneTimeS: Float = -1f): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wf = w.toFloat(); val hf = h.toFloat()
        val r = h * 0.16f
        val card = RectF(0f, 0f, wf, hf)
        val accent = accentOf(d)
        val fg = if (d.heroFgArgb != 0) d.heroFgArgb else white
        val fg60 = (fg and 0x00FFFFFF) or (0x99 shl 24)

        // Clip to the rounded card so every motif gets the same silhouette.
        val clip = Path().apply { addRoundRect(card, r, r, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)

        val sceneEntry = SceneRegistry.fromThemeKey(d.themeKey)
        if (sceneEntry != null) {
            // Speed scene: a STATIC frame of the same renderer the hero and
            // bubble animate (RemoteViews can't animate; the flip-book layout
            // cycles 8 of these launcher-side). Scrim keeps text legible.
            drawSceneMotif(c, wf, hf, d, sceneEntry, sceneTimeS)
            sceneScrim(c, wf, hf)
            heroTexts(c, w, h, d, fg, fg60, sparkline = false, hPos = d.textH, vPos = d.textV)
        } else when (d.themeKey) {
            "kinetic" -> {                       // calm gradient, nothing but the number
                drawFlow(c, wf, hf, d)
                val num = SpeedFormatter.parts(d.downBps)
                val p = text(h * 0.46f, fg, bold = true)
                c.drawText(num.value, wf / 2f, hf * 0.58f, p)
                c.drawText(num.unit, wf / 2f, hf * 0.78f, text(h * 0.11f, fg60))
                brandTag(c, w, h, fg60)
            }
            "liquid" -> {                        // gradient + two translucent waves
                drawFlow(c, wf, hf, d)
                drawWave(c, wf, hf, yBase = hf * 0.74f, amp = hf * 0.08f, phase = d.phase, color = (fg and 0x00FFFFFF) or (0x2E shl 24))
                drawWave(c, wf, hf, yBase = hf * 0.82f, amp = hf * 0.06f, phase = d.phase + 0.33f, color = (fg and 0x00FFFFFF) or (0x4D shl 24))
                heroTexts(c, w, h, d, fg, fg60, sparkline = false)
            }
            "ecg" -> {                           // dark bg + accent heartbeat trace
                c.drawColor(0xFF0B0F14.toInt())
                drawEcg(c, wf, hf, accent, d)
                heroTexts(c, w, h, d, fg, fg60, sparkline = false)
            }
            "dial" -> {                          // left 270° arc, texts on the right
                c.drawColor(cardBg)
                val cx = hf * 0.52f; val cy = hf * 0.52f; val rad = hf * 0.34f
                val stroke = hf * 0.075f
                val rect = RectF(cx - rad, cy - rad, cx + rad, cy + rad)
                val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
                arc.color = faint; c.drawArc(rect, 135f, 270f, false, arc)
                arc.color = accent
                c.drawArc(rect, 135f, 270f * (d.downBps / CEIL).coerceIn(0f, 1f), false, arc)
                heroTexts(c, w, h, d, fg, fg60, sparkline = false, leftFrac = 0.42f)
            }
            "radar" -> {                         // rings + sweep wedge
                c.drawColor(0xFF081210.toInt())
                val cx = wf * 0.26f; val cy = hf * 0.52f
                val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = hf * 0.012f; color = blendArgb(accent, cardBg, 0.45f) }
                for (i in 1..3) c.drawCircle(cx, cy, hf * 0.14f * i, ring)
                val sweep = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.SweepGradient(cx, cy, intArrayOf(0, accent), floatArrayOf(0.7f, 1f))
                }
                c.save(); c.rotate(d.phase * 360f, cx, cy)
                c.drawCircle(cx, cy, hf * 0.42f, sweep); c.restore()
                heroTexts(c, w, h, d, fg, fg60, sparkline = false, leftFrac = 0.5f)
            }
            "particles" -> {                     // deterministic accent dots
                c.drawColor(0xFF0B0F17.toInt())
                val dot = Paint(Paint.ANTI_ALIAS_FLAG)
                for (i in 0 until 26) {          // pseudo-random but stable layout
                    val fx = ((i * 73) % 97) / 97f
                    val fy = ((i * 41) % 89) / 89f
                    dot.color = blendArgb(accent, white, (i % 5) * 0.12f)
                    dot.alpha = 60 + (i * 37) % 140
                    c.drawCircle(wf * fx, hf * fy, hf * (0.008f + ((i * 29) % 13) / 13f * 0.02f), dot)
                }
                heroTexts(c, w, h, d, fg, fg60, sparkline = false)
            }
            "curtains" -> {                      // vertical gradient bands
                val colors = flowColors(d)
                val bands = 7
                val bw = wf / bands
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                for (i in 0 until bands) {
                    p.color = colors[i % colors.size]
                    p.alpha = 200 - (i % 3) * 40
                    c.drawRect(i * bw, 0f, (i + 1) * bw, hf, p)
                }
                heroTexts(c, w, h, d, fg, fg60, sparkline = false)
            }
            "material_you" -> {                  // soft radial blobs
                c.drawColor(0xFF101418.toInt())
                drawBlobW(c, wf * 0.78f, hf * 0.22f, hf * 0.55f, blendArgb(accent, cardBg, 0.25f))
                drawBlobW(c, wf * 0.16f, hf * 0.85f, hf * 0.45f, blendArgb(accent, white, 0.35f))
                heroTexts(c, w, h, d, fg, fg60, sparkline = false)
            }
            "sky" -> {                           // vertical sky gradient + sun disc
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.LinearGradient(0f, 0f, 0f, hf,
                        0xFF1E3A8A.toInt(), 0xFF7DD3FC.toInt(), Shader.TileMode.CLAMP)
                }
                c.drawRect(card, p)
                // Sun sits mid-right, below the right-aligned "today" header text.
                c.drawCircle(wf * 0.84f, hf * 0.55f, hf * 0.12f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFDE68A.toInt() })
                heroTexts(c, w, h, d, white, Color.argb(0x99, 255, 255, 255), sparkline = false)
            }
            "bento" -> {                         // 2×2 stat tiles
                c.drawColor(0xFF0E1116.toInt())
                drawBento(c, wf, hf, d, accent, fg, fg60)
            }
            "speedtest" -> {                     // Cloudflare-style dual readout
                c.drawColor(0xFF0E1116.toInt())
                drawSpeedtestMotif(c, wf, hf, d, fg, fg60)
            }
            "terminal" -> {                      // green mono terminal lines
                c.drawColor(0xFF04130A.toInt())
                drawTerminal(c, wf, hf, d, accent)
            }
            "brutalist" -> {                     // flat yellow bg + hard-shadow card
                c.drawColor(0xFFFACC15.toInt())
                val cardR = RectF(wf * 0.06f, hf * 0.14f, wf * 0.94f, hf * 0.86f)
                val shadow = RectF(cardR).apply { offset(hf * 0.035f, hf * 0.035f) }
                c.drawRect(shadow, Paint().apply { color = Color.BLACK })
                c.drawRect(cardR, Paint().apply { color = 0xFF15151A.toInt() })
                val num = SpeedFormatter.parts(d.downBps)
                c.drawText(num.value, wf / 2f, hf * 0.56f, text(h * 0.30f, 0xFFFACC15.toInt(), bold = true))
                c.drawText("${num.unit} · ▲ ${SpeedFormatter.inline(d.upBps)}", wf / 2f, hf * 0.74f,
                    text(h * 0.09f, Color.argb(0xB3, 255, 255, 255)))
            }
            "glass" -> {                         // gradient + translucent panels
                drawFlow(c, wf, hf, d)
                val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(0x38, 255, 255, 255) }
                c.drawRoundRect(RectF(wf * 0.05f, hf * 0.18f, wf * 0.62f, hf * 0.84f), hf * 0.08f, hf * 0.08f, panel)
                panel.alpha = 0x24
                c.drawRoundRect(RectF(wf * 0.66f, hf * 0.30f, wf * 0.95f, hf * 0.84f), hf * 0.08f, hf * 0.08f, panel)
                heroTexts(c, w, h, d, fg, fg60, sparkline = false, leftFrac = 0.09f)
            }
            else -> {                            // "tier_flow" + default: gradient + sparkline
                drawFlow(c, wf, hf, d)
                heroTexts(c, w, h, d, fg, fg60, sparkline = true)
            }
        }
        c.restore()
        return bmp
    }

    // --- hero motif helpers -----------------------------------------------------

    /** The flowing skin gradient over the whole card. */
    private fun drawFlow(c: Canvas, w: Float, h: Float, d: WidgetData) {
        val grad = com.netspeed.indicator.core.GradientFlow.shader(w, h, flowColors(d), d.phase)
        c.drawRect(0f, 0f, w, h, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad })
    }

    private fun flowColors(d: WidgetData): IntArray =
        if (d.gradientArgb.size >= 2) d.gradientArgb.toIntArray()
        else intArrayOf(0xFF2563EB.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt())

    /**
     * Header + big number + upload line — shared by most motifs.
     *
     * Collision rules (every text is measured, nothing may overlap):
     *  - The "NetSpeed" brand tag is only drawn when the text block starts at the
     *    card edge (default [leftFrac]); motifs with art on the left (dial, radar)
     *    shift the block right, where the tag would run into the right-aligned
     *    "today" figure — so the tag is dropped there.
     *  - The number+unit row auto-shrinks to fit the available width (a 4-digit
     *    "1023 KB/s" used to overflow the card on the shifted motifs).
     */
    /** Scene instances persist across pushes so frames stay coherent at 1 fps. */
    private val sceneCache = HashMap<String, SpeedScene>()
    private val sceneState = SceneState()

    private fun drawSceneMotif(
        c: Canvas, wf: Float, hf: Float, d: WidgetData,
        entry: SceneRegistry.Entry, sceneTimeS: Float = -1f,
    ) {
        val scene = sceneCache.getOrPut(entry.id) { entry.factory() }
        val mbps = d.downBps / 1_048_576f
        val th = d.tierThresholds.toFloatArray()
        sceneState.apply {
            this.mbps = mbps
            sc = SpeedTiers.norm(mbps)
            tier = SpeedTiers.rawIndex(mbps, th)
            tierFrac = SpeedTiers.tierFrac(mbps, th)
            tierProgress = SpeedTiers.tierProgress(mbps)
            accentArgb = SpeedTiers.blendAccentArgb(mbps)
            // Wall-clock scene time (flip-book frames pass explicit offsets so
            // the launcher-side cycle continues the same timeline).
            timeS = if (sceneTimeS >= 0f) sceneTimeS
            else (android.os.SystemClock.elapsedRealtime() % 3_600_000L) / 1000f
            dtS = 0f                              // static frame, no pool stepping
            dark = true
        }
        scene.render(c, wf, hf, sceneState)
    }

    /** Left + top gradients under [heroTexts] so the number/today never fight the scene. */
    private fun sceneScrim(c: Canvas, wf: Float, hf: Float) {
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, wf * 0.65f, 0f, 0xA6000000.toInt(), 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf * 0.65f, hf, p)
        p.shader = LinearGradient(0f, 0f, 0f, hf * 0.25f, 0x73000000, 0, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, wf, hf * 0.25f, p)
    }

    private fun heroTexts(
        c: Canvas, w: Int, h: Int, d: WidgetData, fg: Int, fg60: Int,
        sparkline: Boolean, leftFrac: Float = 0.06f,
        hPos: Int = -1, vPos: Int = 0,
    ) {
        val fmt = com.netspeed.indicator.data.TextFormat.fromKey(d.textFormat)
        val pad = w * leftFrac
        val rightEdge = w - w * 0.06f
        val vOff = h * 0.15f * vPos          // -1 top / 0 centre / +1 bottom
        // Horizontal anchor for the whole block: left edge / centred / right edge.
        fun originX(width: Float): Float = when (hPos) {
            0 -> (w - width) / 2f
            1 -> rightEdge - width
            else -> pad
        }
        fun drawLine(s: String, sizeFrac: Float, y: Float, paintFg: Int, bold: Boolean = false, mono: Boolean = false): Float {
            val p = text(h * sizeFrac, paintFg, bold = bold, align = Paint.Align.LEFT)
            if (mono) p.typeface = Typeface.MONOSPACE
            c.drawText(s, originX(p.measureText(s)), y, p)
            return p.measureText(s)
        }

        if (fmt != com.netspeed.indicator.data.TextFormat.ZEN) {
            val todayPaint = text(h * 0.10f, fg60, align = Paint.Align.RIGHT)
            val todayText = "${SpeedFormatter.total(d.todayBytes)} today"
            c.drawText(todayText, rightEdge, h * 0.18f, todayPaint)
            if (leftFrac <= 0.06f && hPos == -1 && vPos != -1 &&
                fmt == com.netspeed.indicator.data.TextFormat.CLASSIC
            ) {
                val brand = text(h * 0.10f, fg60, align = Paint.Align.LEFT)
                val todayLeft = rightEdge - todayPaint.measureText(todayText)
                if (pad + brand.measureText("NetSpeed") < todayLeft - w * 0.03f) {
                    c.drawText("NetSpeed", pad, h * 0.18f, brand)
                }
            }
        }

        // Special whole-block formats first.
        when (fmt) {
            com.netspeed.indicator.data.TextFormat.COMPACT -> {
                drawLine(
                    "↓ ${SpeedFormatter.inline(d.downBps)}   ↑ ${SpeedFormatter.inline(d.upBps)}",
                    0.18f, (h * 0.56f + vOff).coerceIn(h * 0.30f, h * 0.86f), fg, bold = true,
                )
                return
            }
            com.netspeed.indicator.data.TextFormat.PRO -> {
                val th = d.tierThresholds.toFloatArray()
                val mb = d.history.map { it / 1_048_576f }
                val rows = listOf(
                    "DL  ${SpeedFormatter.inline(d.downBps)}",
                    "UL  ${SpeedFormatter.inline(d.upBps)}",
                    "PK  ${SpeedFormatter.inline(d.peakBps)}",
                    "P90 ${String.format("%.1f", com.netspeed.indicator.core.SpeedStats.p90(mb))} MB/s",
                    "JIT ±${String.format("%.1f", com.netspeed.indicator.core.SpeedStats.jitter(mb))}",
                )
                var y = (h * 0.32f + vOff).coerceAtLeast(h * 0.24f)
                rows.forEach { y += h * 0.135f; drawLine(it, 0.105f, y, fg60, mono = true) }
                return
            }
            com.netspeed.indicator.data.TextFormat.TIER_WORD -> {
                val word = SpeedTiers.tierOf(d.downBps / 1_048_576f, d.tierThresholds.toFloatArray()).defaultWord
                drawLine(word, 0.30f, (h * 0.50f + vOff).coerceIn(h * 0.34f, h * 0.66f), fg, bold = true)
                drawLine(
                    "↓ ${SpeedFormatter.inline(d.downBps)} · ↑ ${SpeedFormatter.inline(d.upBps)}",
                    0.11f, (h * 0.72f + vOff).coerceIn(h * 0.52f, h * 0.92f), fg60,
                )
                return
            }
            com.netspeed.indicator.data.TextFormat.DUAL -> {
                drawLine("↓ ${SpeedFormatter.inline(d.downBps)}", 0.26f, (h * 0.46f + vOff).coerceIn(h * 0.3f, h * 0.6f), fg, bold = true)
                drawLine("↑ ${SpeedFormatter.inline(d.upBps)}", 0.20f, (h * 0.78f + vOff).coerceIn(h * 0.56f, h * 0.92f), fg60, bold = true)
                return
            }
            else -> Unit
        }

        // Number-led formats share the auto-fit number row.
        val num = SpeedFormatter.parts(d.downBps)
        val zen = fmt == com.netspeed.indicator.data.TextFormat.ZEN
        var numPaint = text(h * 0.34f, fg, bold = true, align = Paint.Align.LEFT)
        var unitPaint = text(h * 0.12f, fg60, align = Paint.Align.LEFT)
        val avail = rightEdge - pad
        val unitW = if (zen) 0f else w * 0.02f + unitPaint.measureText(num.unit)
        var rowW = numPaint.measureText(num.value) + unitW
        if (rowW > avail) {
            val s = avail / rowW
            numPaint = text(h * 0.34f * s, fg, bold = true, align = Paint.Align.LEFT)
            unitPaint = text(h * 0.12f * s, fg60, align = Paint.Align.LEFT)
            rowW = avail
        }
        val numY = (h * 0.56f + vOff).coerceIn(h * 0.34f, h * 0.72f)
        val ox = originX(rowW)
        c.drawText(num.value, ox, numY, numPaint)
        val numW = numPaint.measureText(num.value)
        if (!zen) c.drawText(num.unit, ox + numW + w * 0.02f, numY, unitPaint)

        when (fmt) {
            com.netspeed.indicator.data.TextFormat.MINIMAL,
            com.netspeed.indicator.data.TextFormat.ZEN -> Unit
            com.netspeed.indicator.data.TextFormat.NUMBER_UP -> {
                drawLine("▲ ${SpeedFormatter.inline(d.upBps)}", 0.11f, (h * 0.80f + vOff).coerceIn(h * 0.58f, h * 0.94f), fg60)
            }
            com.netspeed.indicator.data.TextFormat.STATS -> {
                val mb = d.history.map { it / 1_048_576f }
                drawLine(
                    "▲ ${SpeedFormatter.inline(d.upBps)} · pk ${SpeedFormatter.inline(d.peakBps)}",
                    0.10f, (h * 0.74f + vOff).coerceIn(h * 0.56f, h * 0.88f), fg60,
                )
                drawLine(
                    "p90 ${String.format("%.1f", com.netspeed.indicator.core.SpeedStats.p90(mb))} · ±${String.format("%.1f", com.netspeed.indicator.core.SpeedStats.jitter(mb))} MB/s",
                    0.10f, (h * 0.88f + vOff).coerceIn(h * 0.66f, h * 0.96f), fg60,
                )
            }
            com.netspeed.indicator.data.TextFormat.DATA -> {
                drawLine(
                    "${SpeedFormatter.total(d.todayBytes)} today" +
                        if (d.dailyQuotaBytes > 0) " / ${SpeedFormatter.total(d.dailyQuotaBytes)}" else "",
                    0.11f, (h * 0.80f + vOff).coerceIn(h * 0.58f, h * 0.94f), fg60,
                )
            }
            else -> {   // CLASSIC
                drawLine(
                    "▲ ${SpeedFormatter.inline(d.upBps)}" +
                        if (d.peakBps > 0) "  ·  pk ${SpeedFormatter.inline(d.peakBps)}" else "",
                    0.11f, (h * 0.80f + vOff).coerceIn(h * 0.58f, h * 0.94f), fg60,
                )
                if (sparkline && hPos == -1 && vPos == 0) {
                    val textRight = pad + numW + w * 0.02f + unitPaint.measureText(num.unit)
                    drawSparkline(
                        c, d.history,
                        left = maxOf(w * 0.52f, textRight + w * 0.03f),
                        top = h * 0.30f, right = rightEdge, bottom = h * 0.78f,
                    )
                }
            }
        }
    }

    /** Small "NetSpeed" tag for motifs that draw their own content. */
    private fun brandTag(c: Canvas, w: Int, h: Int, fg60: Int) {
        c.drawText("NetSpeed", w * 0.06f, h * 0.18f, text(h * 0.10f, fg60, align = Paint.Align.LEFT))
    }

    /** One sine wave filled to the bottom edge (Liquid motif). */
    private fun drawWave(c: Canvas, w: Float, h: Float, yBase: Float, amp: Float, phase: Float, color: Int) {
        val path = Path().apply {
            moveTo(0f, yBase)
            var x = 0f
            while (x <= w) {
                val y = yBase + amp * sin(((x / w) * 2f * Math.PI) + phase * 2f * Math.PI).toFloat()
                lineTo(x, y)
                x += w / 48f
            }
            lineTo(w, h); lineTo(0f, h); close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    /** Heartbeat polyline scaled to recent history (ECG motif). */
    private fun drawEcg(c: Canvas, w: Float, h: Float, accent: Int, d: WidgetData) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent; style = Paint.Style.STROKE; strokeWidth = h * 0.018f
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        val mid = h * 0.62f
        val recent = d.history.takeLast(24)
        val path = Path().apply {
            moveTo(0f, mid)
            if (recent.isEmpty()) { lineTo(w, mid) } else {
                val step = w / recent.size
                recent.forEachIndexed { i, v ->
                    val frac = (v.toFloat() / CEIL).coerceIn(0f, 1f)
                    lineTo(i * step + step * 0.5f, mid - frac * h * 0.34f)
                }
                lineTo(w, mid)
            }
        }
        c.drawPath(path, p)
        // faint grid
        val grid = Paint().apply { color = Color.argb(0x14, 255, 255, 255); strokeWidth = 1f }
        var gx = 0f
        while (gx < w) { c.drawLine(gx, 0f, gx, h, grid); gx += h * 0.18f }
    }

    /** Soft radial blob (Material You motif). */
    private fun drawBlobW(c: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                cx, cy, radius, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(cx, cy, radius, p)
    }

    /** 2×2 stat tiles (Bento motif). */
    private fun drawBento(c: Canvas, w: Float, h: Float, d: WidgetData, accent: Int, fg: Int, fg60: Int) {
        val gap = h * 0.05f
        val tileW = (w - gap * 3) / 2f
        val tileH = (h - gap * 3) / 2f
        val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1E26.toInt() }
        val cells = listOf(
            "▼ down" to SpeedFormatter.inline(d.downBps),
            "▲ up" to SpeedFormatter.inline(d.upBps),
            "today" to SpeedFormatter.total(d.todayBytes),
            "peak" to SpeedFormatter.inline(d.peakBps),
        )
        cells.forEachIndexed { i, (label, value) ->
            val col = i % 2; val row = i / 2
            val left = gap + col * (tileW + gap)
            val top = gap + row * (tileH + gap)
            tile.color = if (i == 0) blendArgb(accent, 0xFF1A1E26.toInt(), 0.65f) else 0xFF1A1E26.toInt()
            c.drawRoundRect(RectF(left, top, left + tileW, top + tileH), h * 0.06f, h * 0.06f, tile)
            c.drawText(label, left + tileW * 0.08f, top + tileH * 0.34f, text(h * 0.085f, fg60, align = Paint.Align.LEFT))
            c.drawText(value, left + tileW * 0.08f, top + tileH * 0.78f, text(h * 0.13f, fg, bold = true, align = Paint.Align.LEFT))
        }
    }

    /** Dual down/up readout with a filled mini area chart (Speedtest motif).
     *  NOTE: WidgetData carries only the DOWN history — the chart shows it; the
     *  upload renders as a value row (honest simplification). */
    private fun drawSpeedtestMotif(c: Canvas, w: Float, h: Float, d: WidgetData, fg: Int, fg60: Int) {
        val orange = 0xFFF6821F.toInt()
        val purple = 0xFF9333EA.toInt()
        val pad = w * 0.06f
        c.drawText("Download", pad, h * 0.16f, text(h * 0.08f, fg60, align = Paint.Align.LEFT))
        c.drawText(
            SpeedFormatter.inline(d.downBps), pad, h * 0.40f,
            text(h * 0.20f, fg, bold = true, align = Paint.Align.LEFT),
        )
        // Filled area chart of the down history, right-aligned.
        val recent = d.history.takeLast(30)
        if (recent.size >= 2) {
            val maxV = recent.max().coerceAtLeast(1L).toFloat()
            val top = h * 0.18f; val bottom = h * 0.52f
            val left = w * 0.46f; val right = w - pad
            val stepX = (right - left) / 29f
            val x0 = right - stepX * (recent.size - 1)
            val path = Path()
            recent.forEachIndexed { i, v ->
                val x = x0 + stepX * i
                val y = bottom - (v / maxV) * (bottom - top)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fillPath = Path(path).apply {
                lineTo(right, bottom); lineTo(x0, bottom); close()
            }
            c.drawPath(fillPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(
                    0f, top, 0f, bottom,
                    (orange and 0x00FFFFFF) or (0x66 shl 24), orange and 0x00FFFFFF,
                    Shader.TileMode.CLAMP,
                )
            })
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = orange; style = Paint.Style.STROKE; strokeWidth = h * 0.012f
                strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
            })
        }
        c.drawText("Upload", pad, h * 0.66f, text(h * 0.08f, fg60, align = Paint.Align.LEFT))
        c.drawText(
            SpeedFormatter.inline(d.upBps), pad, h * 0.88f,
            text(h * 0.18f, purple, bold = true, align = Paint.Align.LEFT),
        )
        c.drawText("NetSpeed", w - pad, h * 0.16f, text(h * 0.08f, fg60, align = Paint.Align.RIGHT))
    }

    /** Green mono terminal lines + block sparkline (Terminal motif). */
    private fun drawTerminal(c: Canvas, w: Float, h: Float, d: WidgetData, accent: Int) {
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent; textSize = h * 0.105f
            typeface = Typeface.MONOSPACE; textAlign = Paint.Align.LEFT
        }
        val pad = w * 0.06f
        val blocks = " ▁▂▃▄▅▆▇█"
        val spark = d.history.takeLast(16).joinToString("") { v ->
            val i = ((v.toFloat() / CEIL).coerceIn(0f, 1f) * (blocks.length - 1)).toInt()
            blocks[i].toString()
        }.ifEmpty { "────────────────" }
        c.drawText("\$ netspeed --live", pad, h * 0.22f, mono)
        mono.textSize = h * 0.16f
        c.drawText("▼ ${SpeedFormatter.inline(d.downBps)}", pad, h * 0.46f, mono)
        mono.textSize = h * 0.105f
        c.drawText("▲ ${SpeedFormatter.inline(d.upBps)}  · ${SpeedFormatter.total(d.todayBytes)} today", pad, h * 0.64f, mono)
        c.drawText(spark, pad, h * 0.84f, mono)
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
        // Speed line auto-fits the card: a 4-digit "1023 KB/s" overflowed the right
        // edge at the fixed size, so measure and shrink when needed.
        val speedText = SpeedFormatter.inline(d.downBps)
        var speedPaint = text(h * 0.27f, white, bold = true, align = Paint.Align.LEFT)
        val availW = w * 0.84f
        val tw = speedPaint.measureText(speedText)
        if (tw > availW) speedPaint = text(h * 0.27f * (availW / tw), white, bold = true, align = Paint.Align.LEFT)
        c.drawText(speedText, w * 0.08f, h * 0.62f, speedPaint)
        c.drawText(tier.defaultSubtitle, w * 0.08f, h * 0.84f, text(h * 0.10f, white60, align = Paint.Align.LEFT))
        c.drawText("NetSpeed", w - w * 0.04f, h * 0.16f, text(h * 0.10f, white60, align = Paint.Align.RIGHT))
        return bmp
    }
}
