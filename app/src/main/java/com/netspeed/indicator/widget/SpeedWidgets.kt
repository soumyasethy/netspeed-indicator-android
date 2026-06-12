package com.netspeed.indicator.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.netspeed.indicator.R
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.data.SpeedBus
import com.netspeed.indicator.render.WidgetData
import com.netspeed.indicator.render.WidgetKind
import com.netspeed.indicator.render.WidgetPainters
import com.netspeed.indicator.ui.MainActivity

/**
 * Base widget provider. Renders its [kind] via the shared [WidgetPainters] into a
 * Bitmap and pushes it with setImageViewBitmap — the same painter the in-app
 * preview uses, so widget and preview are pixel-identical.
 *
 * Live refresh is driven by the foreground service (1 Hz, screen-on only); the
 * framework's own onUpdate just paints the latest known snapshot so a freshly
 * added or resized widget isn't blank.
 */
abstract class SpeedWidgetProvider : AppWidgetProvider() {

    protected abstract val kind: WidgetKind
    protected abstract val widthPx: Int
    protected abstract val heightPx: Int

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Prefer the last frame the service pushed — it carries the skin gradient,
        // accent, theme key, history and quota. The bus fallback (bare speeds) only
        // happens if a widget is added before the service has ever ticked.
        val data = lastData ?: SpeedBus.state.value.toWidgetData()
        ids.forEach { id -> push(context, manager, id, data) }
    }

    private fun push(context: Context, manager: AppWidgetManager, id: Int, data: WidgetData) {
        val click = PendingIntent.getActivity(
            context, kind.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        // Scene-theme hero widgets animate via a launcher-side flip-book:
        // 24 half-resolution scene frames 42 ms apart (~24 fps), cycled locally
        // by the ViewFlipper under a crisp full-resolution text overlay —
        // smooth motion from the same 1 Hz push.
        val frames = if (kind == WidgetKind.HERO) {
            WidgetPainters.renderHeroSceneFrames(widthPx, heightPx, data, FRAME_IDS.size, 0.042f)
        } else null
        val views = if (frames != null) {
            RemoteViews(context.packageName, R.layout.widget_flipbook).apply {
                FRAME_IDS.forEachIndexed { i, viewId -> setImageViewBitmap(viewId, frames[i]) }
                setImageViewBitmap(R.id.widget_overlay, WidgetPainters.renderHeroOverlay(widthPx, heightPx, data))
                setOnClickPendingIntent(R.id.widget_flipbook_root, click)
            }
        } else {
            RemoteViews(context.packageName, R.layout.widget_image).apply {
                setImageViewBitmap(R.id.widget_image, WidgetPainters.render(kind, widthPx, heightPx, data))
                setOnClickPendingIntent(R.id.widget_image, click)
            }
        }
        manager.updateAppWidget(id, views)
    }

    companion object {
        /** Flip-book frame slots in widget_flipbook.xml, in play order. */
        private val FRAME_IDS = listOf(
            R.id.frame_0, R.id.frame_1, R.id.frame_2, R.id.frame_3,
            R.id.frame_4, R.id.frame_5, R.id.frame_6, R.id.frame_7,
            R.id.frame_8, R.id.frame_9, R.id.frame_10, R.id.frame_11,
            R.id.frame_12, R.id.frame_13, R.id.frame_14, R.id.frame_15,
            R.id.frame_16, R.id.frame_17, R.id.frame_18, R.id.frame_19,
            R.id.frame_20, R.id.frame_21, R.id.frame_22, R.id.frame_23,
        )

        /** Last frame pushed by the service — used so a freshly pinned widget
         *  renders with the right skin/theme instantly instead of default blue. */
        @Volatile private var lastData: WidgetData? = null

        /**
         * Pushes a fresh frame to every live widget of all kinds. Called by the
         * service once per second while the screen is on. Skips a kind entirely
         * when no widget of that kind exists, so idle widgets cost nothing.
         */
        fun pushAll(context: Context, data: WidgetData) {
            lastData = data
            val manager = AppWidgetManager.getInstance(context)
            providers.forEach { (cls, _) ->
                val ids = manager.getAppWidgetIds(ComponentName(context, cls))
                if (ids.isNotEmpty()) {
                    val provider = cls.getDeclaredConstructor().newInstance()
                    ids.forEach { id -> provider.push(context, manager, id, data) }
                }
            }
        }

        /** True if at least one widget of any kind is on a home screen. */
        fun anyPresent(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return providers.any { (cls, _) ->
                manager.getAppWidgetIds(ComponentName(context, cls)).isNotEmpty()
            }
        }

        private val providers: List<Pair<Class<out SpeedWidgetProvider>, WidgetKind>> = listOf(
            HeroWidget::class.java to WidgetKind.HERO,
            DialWidget::class.java to WidgetKind.DIAL,
            RingsWidget::class.java to WidgetKind.RINGS,
            PillWidget::class.java to WidgetKind.PILL,
            WeatherWidget::class.java to WidgetKind.WEATHER,
        )
    }
}

private fun LiveSpeed.toWidgetData() = WidgetData(
    downBps = downBytesPerSec,
    upBps = upBytesPerSec,
    todayBytes = todayBytes,
    peakBps = peakBytesPerSec,
)

class HeroWidget : SpeedWidgetProvider() {
    override val kind = WidgetKind.HERO
    override val widthPx = 520
    override val heightPx = 240
}

class DialWidget : SpeedWidgetProvider() {
    override val kind = WidgetKind.DIAL
    override val widthPx = 300
    override val heightPx = 300
}

class RingsWidget : SpeedWidgetProvider() {
    override val kind = WidgetKind.RINGS
    override val widthPx = 300
    override val heightPx = 300
}

class PillWidget : SpeedWidgetProvider() {
    override val kind = WidgetKind.PILL
    override val widthPx = 360
    override val heightPx = 170
}

class WeatherWidget : SpeedWidgetProvider() {
    override val kind = WidgetKind.WEATHER
    override val widthPx = 420
    override val heightPx = 280
}
