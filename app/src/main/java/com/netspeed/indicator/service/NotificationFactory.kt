package com.netspeed.indicator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.netspeed.indicator.R
import com.netspeed.indicator.ui.MainActivity

/**
 * Builds the ongoing notification. Its **small icon is the live speed bitmap**;
 * its **title is the live speed string** (useful even when One UI tucks it into
 * the silent section); its shade accent follows the current tier via setColor;
 * and — when expanded — it shows a gradient strip with a 24sp number, a bitmap
 * sparkline of recent samples, and Pause / Icon-style / Hide-1h action chips.
 *
 * Re-issuing this every second with the same id refreshes the status-bar icon
 * without any sound (LOW channel + silent + alert-once).
 */
class NotificationFactory(private val context: Context) {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel() {
        val existing = manager.getNotificationChannel(ServiceConstants.CHANNEL_ID)
        if (existing != null) return
        // IMPORTANCE_LOW is the lowest level that still draws the status-bar icon
        // (MIN hides it; DEFAULT/HIGH would sound/peek on every 1 Hz post). No
        // importance can override an OEM "collapse icons to a dot" status-bar
        // setting — that is a global user preference with no app-facing override.
        val channel = NotificationChannel(
            ServiceConstants.CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /** All the live data the notification needs for one frame. */
    data class Content(
        val iconBitmap: Bitmap?,
        val downBps: Long,
        val upBps: Long,
        val todayBytes: Long,
        val tierColorArgb: Int,
        val connLabel: String,
        val history: List<Long>,
        val paused: Boolean,
        val showDetails: Boolean,
        /** Expanded-card gradient (skin colours, or brand trio) + flow phase. */
        val gradientArgb: List<Int> = emptyList(),
        val flowPhase: Float = 0f,
        /** Hero theme key — scene themes paint the expanded card with the scene. */
        val themeKey: String = "",
        val tierThresholds: List<Float> = listOf(1f, 5f, 15f, 30f),
    )

    fun build(content: Content): Notification {
        val contentIntent = activityIntent(0, null)

        val titleText = if (content.paused) {
            context.getString(R.string.notif_minimal)
        } else {
            // "▼ 12.4 MB/s · ▲ 1.8" — the speed IS the title.
            "▼ ${SpeedFormatter.inline(content.downBps)} · ▲ ${SpeedFormatter.compact(content.upBps)}"
        }
        val subText = context.getString(
            R.string.notif_today, SpeedFormatter.total(content.todayBytes),
        ) + " · " + content.connLabel

        val builder = NotificationCompat.Builder(context, ServiceConstants.CHANNEL_ID)
            .setContentTitle(titleText)
            .setContentText(subText)
            .setLargeIcon(LargeIconRenderer.get())
            .setColor(content.tierColorArgb)
            .setColorized(false)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            // Distinct explicit group per notification: stops the OS auto-bundling
            // this row with the upload companion (the autogroup summary would replace
            // both live bitmap icons in the status bar with one static fallback).
            .setGroup("netspeed.down")

        builder.setSmallIcon(
            content.iconBitmap?.let { IconCompat.createWithBitmap(it) }
                ?: IconCompat.createWithResource(context, R.drawable.ic_stat_speed),
        )

        // Expanded custom view with sparkline + actions, unless the user chose
        // the minimal-panel mode (then it stays a plain collapsed row).
        if (content.showDetails && !content.paused) {
            val expanded = buildExpandedView(content)
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(expanded)
        }
        return builder.build()
    }

    private fun buildExpandedView(content: Content): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.notification_expanded)

        // Card background, re-rendered each second: scene themes play their
        // diorama right in the panel (1 fps via the notify tick); classic
        // themes keep the flowing gemini gradient.
        val r = 14f * context.resources.displayMetrics.density * (600f / 600f)
        val sceneBg = com.netspeed.indicator.render.WidgetPainters.sceneCard(
            600, 220, content.themeKey, content.downBps, content.tierThresholds, r,
        )
        rv.setImageViewBitmap(R.id.notif_bg, sceneBg ?: gradientCard(content))

        rv.setTextViewText(R.id.notif_number, SpeedFormatter.inline(content.downBps))

        // Sparkline bitmap, redrawn each second (~few KB, negligible).
        val sparkline = SparklineRenderer.render(content.history, widthPx = 480, heightPx = 96)
        rv.setImageViewBitmap(R.id.notif_sparkline, sparkline)

        rv.setTextViewText(
            R.id.action_pause,
            context.getString(
                if (content.paused) R.string.notif_action_resume else R.string.notif_action_pause,
            ),
        )
        rv.setOnClickPendingIntent(
            R.id.action_pause,
            servicePendingIntent(
                1,
                if (content.paused) ServiceConstants.ACTION_RESUME else ServiceConstants.ACTION_PAUSE,
            ),
        )
        rv.setOnClickPendingIntent(R.id.action_style, activityIntent(2, "icon_style"))
        rv.setOnClickPendingIntent(
            R.id.action_hide,
            servicePendingIntent(3, ServiceConstants.ACTION_HIDE_1H),
        )
        return rv
    }

    /** Rounded gemini-flow gradient bitmap backing the expanded card. */
    private fun gradientCard(content: Content): Bitmap {
        val w = 600
        val h = 220
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val colors = if (content.gradientArgb.size >= 2) content.gradientArgb.toIntArray()
        else intArrayOf(0xFF2563EB.toInt(), 0xFF7C3AED.toInt(), 0xFFEC4899.toInt())
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = com.netspeed.indicator.core.GradientFlow.shader(
                w.toFloat(), h.toFloat(), colors, content.flowPhase,
            )
        }
        val r = 14f * context.resources.displayMetrics.density * (w / 600f)
        canvas.drawRoundRect(android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)
        return bmp
    }

    private fun activityIntent(requestCode: Int, extra: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (extra != null) intent.putExtra("nav", extra)
        return PendingIntent.getActivity(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, SpeedMeterService::class.java).setAction(action)
        return PendingIntent.getService(
            context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notify(notification: Notification) {
        manager.notify(ServiceConstants.NOTIFICATION_ID, notification)
    }

    /**
     * Companion notification whose only job is to add a SECOND status-bar icon —
     * the upload half of the side-by-side style. One UI caps a single icon's width,
     * so one wide "↓d ↑u" bitmap gets scaled down; two near-square icons (one per
     * direction) each fit by height and render near clock size. Silent, ongoing,
     * minimal row in the panel.
     */
    fun notifyUpload(iconBitmap: Bitmap, upBps: Long) {
        val n = NotificationCompat.Builder(context, ServiceConstants.CHANNEL_ID)
            .setContentTitle("▲ ${SpeedFormatter.inline(upBps)}")
            .setSmallIcon(IconCompat.createWithBitmap(iconBitmap))
            .setLargeIcon(LargeIconRenderer.get())
            .setContentIntent(activityIntent(0, null))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup("netspeed.up")     // distinct group: see build() — avoids autogroup
            .build()
        manager.notify(ServiceConstants.UPLOAD_NOTIFICATION_ID, n)
    }

    fun cancelUpload() {
        manager.cancel(ServiceConstants.UPLOAD_NOTIFICATION_ID)
        cancelAutogroupSummary()
    }

    /**
     * Removes the OS-created autogroup summary left behind after a dual-icon
     * attempt on One UI. The summary carries a static resource icon and renders
     * as a blank box in the status bar next to the live icon — it belongs to our
     * package, so we can cancel it like any of our notifications.
     */
    private fun cancelAutogroupSummary() {
        runCatching {
            manager.activeNotifications
                .filter { it.id == 0 && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }

    /**
     * True when the OS has force-bundled our notifications under its own autogroup
     * summary (One UI does this for ALL of an app's notifications and then shows
     * only the summary's static icon in the status bar — killing the dual-icon
     * trick). Detected via the system's group-key override / its id-0 summary.
     */
    fun isAutoGrouped(): Boolean = manager.activeNotifications.any { sbn ->
        sbn.id == 0 || sbn.overrideGroupKey != null
    }
}
