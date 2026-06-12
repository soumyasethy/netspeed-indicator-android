package com.netspeed.indicator.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.view.Choreographer
import android.view.View
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.core.TierTracker
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.render.scenes.SceneState
import com.netspeed.indicator.render.scenes.SpeedScene
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The floating bubble's canvas: draws the chip bitmap plus an optional
 * SPEED-REACTIVE procedural effect (flame / glow / sparks) behind it. Pure
 * Canvas maths — zero assets, ~kilobytes of code — so the APK budget holds.
 *
 * Battery discipline:
 *  - The [Choreographer] frame loop runs ONLY while an effect is selected,
 *    [intensity] > 0 and the view is attached. Idle (no traffic) = a static
 *    bitmap, zero frames.
 *  - Frames are capped (~30 fps) by skipping alternate vsyncs.
 *  - No allocations on the frame path: paints/paths/particle pool reused.
 *
 * The view is intentionally LARGER than the chip ([fxPad] margin all around):
 * flames need headroom, and the margin doubles as a bigger touch target.
 */
@SuppressLint("ViewConstructor")
class BubbleFxView(context: Context) : View(context) {

    enum class Fx { NONE, FLAME, GLOW, SPARKS, LOTTIE, SCENE }

    private var chip: Bitmap? = null
    private var fx: Fx = Fx.NONE
    private var accent: Int = 0xFF7C3AED.toInt()

    /** Procedural speed scene (comet, tach, journey…): same placement semantics
     *  as the Lottie scene, but pure Canvas and speed-DRIVEN, not just speed-paced. */
    private var scene: SpeedScene? = null
    private var sceneKey: String = ""
    private val sceneState = SceneState()
    private val sceneTier = TierTracker()
    private var thresholds: FloatArray = SpeedTiers.DEFAULT_THRESHOLDS
    private var sceneTargetMbps = 0f
    private var sceneShownMbps = 0f
    private val clipPath = Path()
    private val clipRect = RectF()

    /** Lottie scene (the "endless possibilities" mode): plays BEHIND the chip,
     *  playback speed mapped to [intensity] — a trickle ambles, a download flies. */
    private var lottie: LottieDrawable? = null
    private var lastFrameMs = 0L

    /** Scene placement: BEHIND the chip (full-view backdrop) or a slot LEFT /
     *  RIGHT of it (mascot-beside-the-number, Cloudflare-rabbit style). Applies
     *  to the Lottie scene; aura effects (flame/glow/sparks) are always behind. */
    var placement: String = "behind"
        set(value) { if (field != value) { field = value; requestLayout() } }

    /** Width of the side slot when placement is left/right. Scenes get a wider
     *  slot (they are landscape dioramas, 208:70 reference aspect). */
    private fun sideSlotW(c: Bitmap): Int = when {
        placement == "behind" -> 0
        fx == Fx.LOTTIE -> (c.height * 1.4f).roundToInt()
        fx == Fx.SCENE -> (c.height * 1.6f).roundToInt()
        else -> 0
    }

    /** 0..1 — how hard the network is working; drives every effect. */
    var intensity: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            syncLoop()
        }

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flamePath = Path()

    /** Recycled spark pool: x,y in fractions of chip box; life 0..1. */
    private val sparkX = FloatArray(SPARK_POOL)
    private val sparkY = FloatArray(SPARK_POOL)
    private val sparkLife = FloatArray(SPARK_POOL)
    private var sparkSeed = 7

    private var running = false
    private var vsync = 0
    private var timeMs = 0L

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            vsync++
            // ~30 fps on a 60 Hz panel; scenes idle at ~15 fps (their idle
            // states — sleeping runner, sputtering comet — ARE the content,
            // but they don't need full cadence to read as alive).
            val divider =
                if (fx == Fx.SCENE && sceneShownMbps < 1f && sceneTargetMbps < 1f) 4 else 2
            if (vsync % divider == 0) {
                val now = frameTimeNanos / 1_000_000
                val dt = if (lastFrameMs == 0L) 33L else (now - timeMs).coerceIn(1, 300)
                timeMs = now
                lastFrameMs = now
                stepSparks()
                stepLottie(dt)
                stepScene(dt)
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /** 1 Hz speed drive from the service; the view smooths it per frame. */
    fun setSceneSpeed(mbps: Float, tierThresholds: FloatArray, transparentBg: Boolean = false) {
        sceneTargetMbps = mbps
        sceneState.transparentBg = transparentBg
        if (!thresholds.contentEquals(tierThresholds)) {
            thresholds = tierThresholds
            sceneTier.setThresholds(tierThresholds)
        }
    }

    /** Advance the scene clock + smoothed speed and refill the shared state. */
    private fun stepScene(dtMs: Long) {
        if (fx != Fx.SCENE) return
        val dtS = dtMs / 1000f
        // Reference smoothing is 0.06/frame at 60 fps; we run at ~30 fps.
        sceneShownMbps += (sceneTargetMbps - sceneShownMbps) * 0.12f
        sceneState.dtS = dtS
        sceneState.timeS += dtS
        fillSceneState(dtMs)
    }

    private fun fillSceneState(dtMs: Long) {
        sceneState.mbps = sceneShownMbps
        sceneState.sc = SpeedTiers.norm(sceneShownMbps)
        sceneState.tier = sceneTier.update(sceneShownMbps, dtMs).index
        sceneState.tierFrac = SpeedTiers.tierFrac(sceneShownMbps, thresholds)
        sceneState.tierProgress = SpeedTiers.tierProgress(sceneShownMbps)
        sceneState.accentArgb = SpeedTiers.blendAccentArgb(sceneShownMbps)
        // Day/light variants in the bubble (day turbine, light manga paper) —
        // the iconic gallery looks; widgets keep the night variants.
        sceneState.dark = false
    }

    fun setChip(bitmap: Bitmap) {
        chip = bitmap
        requestLayout()
        invalidate()
    }

    fun setEffect(key: String, accentArgb: Int) {
        if (SceneRegistry.isScene(key)) {
            if (sceneKey != key) {
                scene = SceneRegistry.create(key)
                sceneKey = key
            }
            fx = Fx.SCENE
        } else {
            scene = null
            sceneKey = ""
            fx = when (key) {
                "flame" -> Fx.FLAME
                "glow" -> Fx.GLOW
                "sparks" -> Fx.SPARKS
                "lottie" -> Fx.LOTTIE
                else -> Fx.NONE
            }
        }
        accent = accentArgb
        syncLoop()
        invalidate()
    }

    /** Installs (or clears) the Lottie scene. The drawable is retained across
     *  frames; only the composition swap allocates. */
    fun setLottie(composition: LottieComposition?) {
        if (composition == null) { lottie = null; return }
        if (lottie?.composition === composition) return
        lottie = LottieDrawable().apply {
            this.composition = composition
            repeatCount = LottieDrawable.INFINITE
        }
    }

    /** Scene playback: slow ambient at a trickle, full tilt while downloading. */
    private fun stepLottie(dtMs: Long) {
        val d = lottie ?: return
        if (fx != Fx.LOTTIE) return
        val duration = d.composition?.duration ?: return
        if (duration <= 0f) return
        val speed = 0.25f + 1.75f * intensity
        var p = d.progress + (dtMs / duration) * speed
        if (p > 1f) p -= 1f
        d.progress = p
    }

    /** Margin around the chip reserved for the effect (and easier grabbing).
     *  Scenes get none: in behind mode the scene IS the background, so the
     *  bubble footprint stays identical to a plain chip. */
    private fun fxPad(): Int {
        val c = chip ?: return 0
        return if (fx == Fx.NONE || fx == Fx.SCENE) 0 else (c.height * 0.55f).roundToInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val c = chip
        if (c == null) { setMeasuredDimension(1, 1); return }
        val slot = sideSlotW(c)
        if (slot > 0) {
            // Side placement: [scene][chip] row, no aura margins needed.
            setMeasuredDimension(c.width + slot, c.height)
            return
        }
        val pad = fxPad()
        setMeasuredDimension(c.width + pad * 2, c.height + pad * 2)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncLoop()
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
        super.onDetachedFromWindow()
    }

    private fun syncLoop() {
        // Scenes animate regardless of traffic: the sleeping runner, sputtering
        // comet and gear-1 tach ARE the designed idle look. Battery holds via
        // vsync stopping at screen-off, detach on hide, the 30 fps cap and the
        // 15 fps idle divider. Aura effects keep the intensity gate.
        val want = isAttachedToWindow && when (fx) {
            Fx.NONE -> false
            Fx.SCENE -> true
            else -> intensity > 0.01f
        }
        if (want && !running) {
            running = true
            Choreographer.getInstance().postFrameCallback(frame)
        } else if (!want && running) {
            running = false
            Choreographer.getInstance().removeFrameCallback(frame)
            invalidate()                        // settle on a clean static frame
        }
    }

    override fun onDraw(canvas: Canvas) {
        val c = chip ?: return
        val slot = sideSlotW(c)
        if (slot > 0) {
            // Mascot-beside-the-number: scene in its slot, chip alongside —
            // text/animation overlap is impossible by construction.
            val sceneLeft = if (placement == "left") 0 else c.width
            val chipLeft = if (placement == "left") slot else 0
            if (fx == Fx.SCENE) scene?.let { scn ->
                if (!running) sceneState.dtS = 0f    // static frame when idle
                val save = canvas.save()
                clipRoundRect(canvas, sceneLeft.toFloat(), 0f, (sceneLeft + slot).toFloat(), height.toFloat(), c.height * 0.22f)
                canvas.translate(sceneLeft.toFloat(), 0f)
                scn.render(canvas, slot.toFloat(), height.toFloat(), sceneState)
                canvas.restoreToCount(save)
            }
            // Lottie scene stays visible when idle too — a STATIC frame (the
            // loop is stopped, so this costs nothing).
            if (fx == Fx.LOTTIE) lottie?.let { d ->
                d.setBounds(sceneLeft, 0, sceneLeft + slot, height)
                d.draw(canvas)
            }
            canvas.drawBitmap(c, chipLeft.toFloat(), 0f, bmpPaint)
            return
        }
        val pad = fxPad().toFloat()
        if (running) when (fx) {
            Fx.FLAME -> drawFlames(canvas, pad, c)
            Fx.GLOW -> drawGlow(canvas, pad, c)
            Fx.SPARKS -> drawSparks(canvas, pad, c)
            Fx.LOTTIE -> Unit   // drawn below, also when idle (static frame)
            Fx.SCENE -> Unit    // drawn below, also when idle (static frame)
            Fx.NONE -> Unit
        }
        if (fx == Fx.LOTTIE) lottie?.let { d ->
            d.setBounds(0, 0, width, height)
            d.draw(canvas)
        }
        if (fx == Fx.SCENE) scene?.let { scn ->
            // The scene IS the background; round-clip so the bubble keeps the
            // chip's pill silhouette instead of a hard rectangle.
            if (!running) sceneState.dtS = 0f
            val save = canvas.save()
            clipRoundRect(canvas, 0f, 0f, width.toFloat(), height.toFloat(), height * 0.22f)
            scn.render(canvas, width.toFloat(), height.toFloat(), sceneState)
            canvas.restoreToCount(save)
        }
        canvas.drawBitmap(c, pad, pad, bmpPaint)
    }

    private fun clipRoundRect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        clipRect.set(l, t, r, b)
        clipPath.rewind()
        clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
    }

    // --- flame: tongues licking up from the chip's top edge ---------------------

    private fun drawFlames(canvas: Canvas, pad: Float, c: Bitmap) {
        val t = timeMs / 1000f
        val baseY = pad + c.height * 0.25f          // flames root behind the chip top
        // Tips must CLEAR the chip's top edge to be seen (the chip is drawn over
        // the roots) — so the height budget includes the buried root segment and
        // keeps a visible floor even at modest intensity.
        val maxH = (pad + c.height * 0.25f) * (0.35f + 0.65f * intensity)
        if (intensity < 0.02f) return
        fxPaint.style = Paint.Style.FILL
        val tongues = 7
        val span = c.width.toFloat()
        for (i in 0 until tongues) {
            val cx = pad + span * (i + 0.5f) / tongues
            val wob = sin(t * (3.1f + i * 0.83f) + i * 1.7f)
            val h = maxH * (0.55f + 0.45f * wob)
            if (h < 2f) continue
            val w = span / tongues * (0.8f + 0.2f * sin(t * 2.3f + i))
            flamePath.reset()
            flamePath.moveTo(cx - w / 2f, baseY)
            flamePath.quadTo(cx - w * 0.18f, baseY - h * 0.55f, cx + w * 0.06f * wob, baseY - h)
            flamePath.quadTo(cx + w * 0.22f, baseY - h * 0.5f, cx + w / 2f, baseY)
            flamePath.close()
            fxPaint.shader = LinearGradient(
                cx, baseY, cx, baseY - h,
                intArrayOf(accent, blend(accent, 0xFFFFC533.toInt(), 0.6f), 0x00FFFFFF),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawPath(flamePath, fxPaint)
        }
        fxPaint.shader = null
    }

    // --- glow: breathing halo ----------------------------------------------------

    private fun drawGlow(canvas: Canvas, pad: Float, c: Bitmap) {
        val t = timeMs / 1000f
        val cx = pad + c.width / 2f
        val cy = pad + c.height / 2f
        val breathe = 0.5f + 0.5f * sin(t * (1f + 4f * intensity) * 2f)
        val radius = min(width, height) / 2f * (0.65f + 0.35f * breathe)
        val alpha = (0x38 + 0x58 * intensity * (0.4f + 0.6f * breathe)).toInt().coerceAtMost(0xB0)
        fxPaint.shader = RadialGradient(
            cx, cy, radius.coerceAtLeast(1f),
            intArrayOf((accent and 0x00FFFFFF) or (alpha shl 24), accent and 0x00FFFFFF),
            floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, fxPaint)
        fxPaint.shader = null
    }

    // --- sparks: particles streaming upward ---------------------------------------

    private fun stepSparks() {
        if (fx != Fx.SPARKS) return
        for (i in 0 until SPARK_POOL) {
            if (sparkLife[i] > 0f) {
                sparkLife[i] -= 0.045f
                sparkY[i] -= 0.035f * (0.5f + intensity)
                sparkX[i] += (nextRand(i) - 0.5f) * 0.02f
            } else if (nextRand(i + timeMs.toInt()) < 0.10f + 0.5f * intensity) {
                // respawn at the chip's rim
                sparkLife[i] = 1f
                sparkX[i] = nextRand(i * 31 + 7)
                sparkY[i] = 1f
            }
        }
    }

    private fun drawSparks(canvas: Canvas, pad: Float, c: Bitmap) {
        fxPaint.style = Paint.Style.FILL
        val r = c.height * 0.05f
        for (i in 0 until SPARK_POOL) {
            val life = sparkLife[i]
            if (life <= 0f) continue
            val x = pad + sparkX[i] * c.width
            val y = pad + sparkY[i] * c.height - (1f - life) * pad * 1.4f
            fxPaint.color = (accent and 0x00FFFFFF) or ((0xE0 * life).toInt() shl 24)
            canvas.drawCircle(x, y, r * (0.5f + life * 0.8f), fxPaint)
        }
    }

    /** Cheap deterministic pseudo-random in [0,1) — no allocations, stable. */
    private fun nextRand(salt: Int): Float {
        sparkSeed = sparkSeed * 1103515245 + 12345 + salt
        return ((sparkSeed ushr 16) and 0x7FFF) / 32768f
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.argb(
            ch(Color.alpha(a), Color.alpha(b)), ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)),
        )
    }

    private companion object {
        const val SPARK_POOL = 24
    }
}
