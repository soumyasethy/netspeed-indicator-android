package com.netspeed.indicator.service

/** Shared identifiers for the foreground service and its notification. */
object ServiceConstants {
    const val CHANNEL_ID = "netspeed_indicator"
    const val NOTIFICATION_ID = 1001

    /** Companion notification carrying the upload icon for the side-by-side style. */
    const val UPLOAD_NOTIFICATION_ID = 1002

    /** Sampling cadence. The whole design (notify-per-second) hinges on this. */
    const val SAMPLE_INTERVAL_MS = 1000L

    /** EMA factor for the DISPLAYED rate (≈2–3 s settle — steady, not laggy). */
    const val SMOOTHING_ALPHA = 0.45

    /** Idle seconds before the status-bar icon auto-hides (when enabled). */
    const val IDLE_HIDE_TICKS = 30

    /** Persist the running "today" total at most this often to spare DataStore. */
    const val USAGE_PERSIST_INTERVAL_MS = 30_000L

    /** Number of recent samples kept for the expanded-notification sparkline. */
    const val SPARKLINE_SAMPLES = 22

    /** How long "Hide 1h" suppresses the live readout. */
    const val HIDE_DURATION_MS = 60L * 60L * 1000L

    // Notification action intents handled in onStartCommand.
    const val ACTION_PAUSE = "com.netspeed.indicator.action.PAUSE"
    const val ACTION_RESUME = "com.netspeed.indicator.action.RESUME"
    const val ACTION_HIDE_1H = "com.netspeed.indicator.action.HIDE_1H"
}
