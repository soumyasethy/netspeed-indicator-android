package com.netspeed.indicator.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import com.netspeed.indicator.R
import com.netspeed.indicator.core.SpeedTiers
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.netspeed.indicator.data.IconStyle
import com.netspeed.indicator.data.LiveSpeed
import com.netspeed.indicator.data.Settings
import com.netspeed.indicator.data.SettingsRepository
import com.netspeed.indicator.data.LifetimeUsage
import com.netspeed.indicator.data.SpeedBus
import com.netspeed.indicator.data.TodayUsage
import com.netspeed.indicator.render.WidgetData
import com.netspeed.indicator.widget.SpeedWidgetProvider
import com.netspeed.indicator.receiver.ScreenStateReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Foreground service that samples [TrafficStats] once per second and republishes
 * the result as (a) the status-bar notification icon and (b) a [SpeedBus]
 * snapshot for the in-app preview.
 *
 * Lifecycle / robustness decisions:
 *  - **specialUse FGS**: a passive speed readout fits none of the predefined FGS
 *    types; the manifest declares the required justification property.
 *  - **START_STICKY + onTaskRemoved**: the OS recreates the service after a kill
 *    or a swipe-away so the indicator self-heals.
 *  - **Coroutine ticker** in [lifecycleScope] rather than Handler/Timer: it is
 *    cancelled automatically with the lifecycle and reads suspend Flows directly.
 *  - **Screen-off pause**: with the screen off there is no one to see the icon,
 *    so (unless the user opts in) we stop sampling and stop notifying to save
 *    battery, then re-baseline on screen-on so the first delta isn't measured
 *    across the entire idle gap.
 */
class SpeedMeterService : LifecycleService() {

    private lateinit var repo: SettingsRepository
    private lateinit var entitlements: com.netspeed.indicator.billing.EntitlementStore
    private lateinit var notifications: NotificationFactory
    private val iconRenderer = IconRenderer()
    private val sampler = SpeedSampler()

    @Volatile private var settings: Settings = Settings()
    @Volatile private var screenOn: Boolean = true
    @Volatile private var paused: Boolean = false

    /** Set when the OS force-groups our notifications (One UI) — the side-by-side
     *  style then falls back from two icons to the single wide icon for this run. */
    @Volatile private var dualIconsBlocked: Boolean = false

    /** Suite unlock — premium surfaces (the floating bubble) require it. Starts
     *  open while the paywall kill-switch is off so the first ticks aren't gated. */
    @Volatile private var suiteUnlocked: Boolean = !com.netspeed.indicator.BuildConfig.PAYWALL_ENABLED

    // Display smoothing + idle auto-hide state (see tick()).
    private var displayDownBps: Double = 0.0
    private var displayUpBps: Double = 0.0
    private var idleTicks: Int = 0
    private var transparentIconBitmap: Bitmap? = null

    /** Floating overlay bubble (lazily shown when enabled + permission granted). */
    private val floatingChip by lazy {
        FloatingChip(this) { x, y -> lifecycleScope.launch { repo.setFloatingChipPos(x, y) } }
    }

    /** Separate renderer for the bubble: full-colour chip (no OS-tint punch-out),
     *  higher-res so it stays crisp when scaled up by the bubble-size slider. */
    private val bubbleRenderer = IconRenderer(sizePx = 120)

    /** Last STORED bubble position seen — reconcile fires only when it changes. */
    private var lastStoredChipPos: Pair<Int, Int>? = null

    // Lottie scene cache: parsed once per (uri) and reused every tick. A failed
    // parse records the uri so we don't retry per second; the FX falls back.
    private var lottieLoadedFor: String? = null
    private var lottieComposition: com.airbnb.lottie.LottieComposition? = null

    /** Resolves the scene composition ("" = bundled plane). Null = parse failed. */
    private fun lottieFor(uriStr: String): com.airbnb.lottie.LottieComposition? {
        if (lottieLoadedFor == uriStr) return lottieComposition
        lottieLoadedFor = uriStr
        lottieComposition = runCatching {
            if (uriStr.isEmpty()) {
                com.airbnb.lottie.LottieCompositionFactory
                    .fromAssetSync(this, "lottie/plane.json").value
            } else {
                contentResolver.openInputStream(android.net.Uri.parse(uriStr))?.use {
                    com.airbnb.lottie.LottieCompositionFactory
                        .fromJsonInputStreamSync(it, uriStr).value
                }
            }
        }.getOrNull()
        return lottieComposition
    }

    private fun transparentIcon(): Bitmap =
        transparentIconBitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            .also { transparentIconBitmap = it }

    private var tickerJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null

    // Running "today" total, kept in memory and flushed to DataStore periodically.
    private var todayEpochDay: Long = 0L
    private var todayBytes: Long = 0L
    private var todayPeakBps: Long = 0L      // highest download rate today
    private var lifetimeBytes: Long = 0L     // monotonic all-time total
    private var lastPersistMs: Long = 0L
    private var lastSampleMs: Long = 0L

    // Notification redesign state.
    private val speedHistory = ArrayDeque<Long>()   // last N download bytes/sec
    @Volatile private var manualPaused = false       // "Pause" action
    @Volatile private var hiddenUntilMs = 0L         // "Hide 1h" action

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(applicationContext)
        entitlements = com.netspeed.indicator.billing.EntitlementStore(applicationContext)
        notifications = NotificationFactory(applicationContext)
        notifications.ensureChannel()
        registerScreenReceiver()
        observeSettings()
        observeEntitlement()
        restoreTodayUsage()
        restoreLifetimeUsage()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Promote to foreground within the allowed window (idempotent — safe to
        // call again when re-delivered for an action intent).
        startForegroundCompat()
        when (intent?.action) {
            ServiceConstants.ACTION_PAUSE -> { manualPaused = true; refreshPausedNotification() }
            ServiceConstants.ACTION_RESUME -> { manualPaused = false; hiddenUntilMs = 0L }
            ServiceConstants.ACTION_HIDE_1H -> {
                hiddenUntilMs = elapsedMs() + ServiceConstants.HIDE_DURATION_MS
                refreshPausedNotification()
            }
        }
        startTicker()
        return Service.START_STICKY
    }

    override fun onDestroy() {
        runCatching { floatingChip.hide() }
        tickerJob?.cancel()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        notifications.cancelUpload()
        persistTodayUsage(force = true)
        SpeedBus.markStopped()
        super.onDestroy()
    }

    /**
     * Swipe-away from recents must not kill the indicator. We simply persist and
     * let START_STICKY recreate us; the indicator reappears within ~1 s.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        persistTodayUsage(force = true)
        super.onTaskRemoved(rootIntent)
    }

    // --- foreground promotion -------------------------------------------------

    private fun startForegroundCompat() {
        val notification = notifications.build(currentContent(iconBitmap = null, downBps = 0, upBps = 0))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ServiceConstants.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(ServiceConstants.NOTIFICATION_ID, notification)
        }
    }

    // --- settings + screen state ----------------------------------------------

    private fun observeSettings() {
        lifecycleScope.launch {
            repo.settings.collect { s ->
                settings = s
                // Device verdict from a previous run: skip the dual-icon attempt.
                if (s.dualIconsBlocked) dualIconsBlocked = true
                // Bubble toggled off → remove the overlay NOW, not on the next tick
                // (with the screen off / service paused the chip would linger).
                if (!s.floatingChip && floatingChip.isShown) runCatching { floatingChip.hide() }
                // Stored-position reconcile lives HERE (not the 1 Hz tick) so the
                // in-app nudge pad / presets / Reset move the chip the instant the
                // write lands. Fires only when the STORED value changes — comparing
                // stored vs live every emission would race a drag's async persist.
                val want = s.floatingChipX to s.floatingChipY
                if (want != lastStoredChipPos) {
                    lastStoredChipPos = want
                    if (floatingChip.isShown &&
                        (floatingChip.posX != want.first || floatingChip.posY != want.second)
                    ) {
                        floatingChip.moveTo(want.first, want.second)
                    }
                }
                // No surface left (bar icon off AND no drawable bubble) → shut down.
                val bubbleLive = s.floatingChip &&
                    android.provider.Settings.canDrawOverlays(this@SpeedMeterService)
                if (!s.enabled && !bubbleLive) {
                    SpeedBus.markStopped()
                    stopSelf()
                    return@collect
                }
                // Recompute pause state when the screen-off preference flips.
                applyPauseState()
            }
        }
    }

    private fun observeEntitlement() {
        lifecycleScope.launch {
            entitlements.entitlement.collect {
                suiteUnlocked = it.suiteUnlocked || !com.netspeed.indicator.BuildConfig.PAYWALL_ENABLED
            }
        }
    }

    private fun registerScreenReceiver() {
        val receiver = ScreenStateReceiver { isScreenOn ->
            screenOn = isScreenOn
            if (isScreenOn) {
                // Fresh baseline so the first post-resume delta isn't garbage.
                sampler.reset()
            }
            applyPauseState()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(receiver, filter)
        screenReceiver = receiver
    }

    /** Paused when the screen is off and the user hasn't opted into screen-off updates. */
    private fun applyPauseState() {
        paused = !screenOn && !settings.updateWhileScreenOff
        if (paused) {
            // Stop publishing live values; keep the foreground notification as-is.
            SpeedBus.publish(
                LiveSpeed(running = true, downBytesPerSec = 0, upBytesPerSec = 0, todayBytes = todayBytes),
            )
        }
    }

    // --- the once-per-second ticker -------------------------------------------

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = lifecycleScope.launch {
            while (isActive) {
                if (!paused) tick()
                kotlinx.coroutines.delay(ServiceConstants.SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun tick() {
        val now = elapsedMs()
        val elapsed = if (lastSampleMs == 0L) ServiceConstants.SAMPLE_INTERVAL_MS else now - lastSampleMs
        lastSampleMs = now

        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()

        // Priming tick (start or post-resume): no previous reading -> skip notify.
        val sample = sampler.sample(rx, tx, elapsed) ?: return

        accumulateToday(sample.rxBytesDelta + sample.txBytesDelta)
        if (sample.rxBytesPerSec > todayPeakBps) todayPeakBps = sample.rxBytesPerSec
        lifetimeBytes += sample.rxBytesDelta + sample.txBytesDelta

        // Maintain the sparkline ring buffer.
        speedHistory.addLast(sample.rxBytesPerSec)
        while (speedHistory.size > ServiceConstants.SPARKLINE_SAMPLES) speedHistory.removeFirst()

        // "Pause" / "Hide 1h" suppress the live readout (but sampling continues so
        // the today/lifetime totals stay accurate). Show a quiet paused row.
        if (isSuppressed()) {
            notifications.notify(notifications.build(currentContent(null, 0, 0)))
            notifications.cancelUpload()
            SpeedBus.publish(LiveSpeed(running = true, todayBytes = todayBytes))
            persistTodayUsage(force = false)
            return
        }

        // Displayed rates are lightly smoothed (EMA, ~2–3 s settle) so the number
        // reads steady instead of jumping with every bursty second. Accounting,
        // peaks, tier colour and the live bus keep the raw values.
        displayDownBps += ServiceConstants.SMOOTHING_ALPHA * (sample.rxBytesPerSec - displayDownBps)
        displayUpBps += ServiceConstants.SMOOTHING_ALPHA * (sample.txBytesPerSec - displayUpBps)
        if (sample.rxBytesPerSec == 0L && displayDownBps < 1024) displayDownBps = 0.0
        if (sample.txBytesPerSec == 0L && displayUpBps < 1024) displayUpBps = 0.0
        val downShown = displayDownBps.toLong()
        val upShown = displayUpBps.toLong()

        // Auto-hide: after 30 s without meaningful traffic the status-bar icon goes
        // fully transparent (Android requires *an* icon; alpha-0 renders invisible).
        // Threshold is 4 KiB/s combined: real phones always have ambient keep-alive
        // pings of 1–3 KiB/s, which would otherwise reset the idle counter forever.
        val idleNow = downShown + upShown < 4096
        idleTicks = if (settings.autoHideIdle && idleNow) idleTicks + 1 else 0
        // The bubble already shows the speed — optionally drop the status-bar icon
        // too (the notification row must stay: FGS requirement) so it isn't shown
        // twice. Same transparent-icon trick as idle-hide.
        val bubbleShowing = settings.floatingChip && suiteUnlocked &&
            android.provider.Settings.canDrawOverlays(this)
        // Icon also hides when the status-bar toggle is OFF while the service runs
        // for the bubble alone (the FGS notification must exist; alpha-0 icon).
        val hideIcon = !settings.enabled ||
            (settings.autoHideIdle && idleTicks >= ServiceConstants.IDLE_HIDE_TICKS) ||
            (bubbleShowing && settings.hideIconWhenBubble)

        iconRenderer.fontScale = resources.configuration.fontScale   // honor system font size
        iconRenderer.userScale = settings.iconTextScale              // user size override
        // Colour-true bars (One UI) render bitmap small icons verbatim → the
        // full-colour chip applies; tinted bars get bare max-contrast glyphs.
        iconRenderer.colorTrue =
            android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        iconRenderer.bgColorArgb = settings.iconBgColor
        // Glyphs must never go transparent in the bar — fall back to white.
        iconRenderer.fgColorArgb =
            if (Color.alpha(settings.iconFgColor) != 0) settings.iconFgColor else Color.WHITE
        iconRenderer.unitStyle = settings.iconUnitStyle
        iconRenderer.borderColorArgb = settings.iconBorderColor
        iconRenderer.borderWidth = settings.iconBorderWidth

        // Side-by-side style ships as TWO notifications → two near-square status-bar
        // icons (down + up), each fitted by height so the digits land near clock
        // size. One wide combined bitmap would be width-capped and shrunk.
        // Some OEMs (One UI) force-bundle all of an app's notifications under one
        // autogroup summary, replacing both live icons with a static one — when we
        // detect that, fall back permanently (per run) to the single wide icon.
        val dualIcons = settings.iconStyle == IconStyle.ARROWS_H && settings.showCombined &&
            !dualIconsBlocked && !hideIcon
        val bitmap = when {
            hideIcon -> transparentIcon()
            dualIcons -> iconRenderer.renderSingle(downShown, down = true)
            else -> iconRenderer.render(
                style = settings.iconStyle,
                downBps = downShown,
                upBps = upShown,
                showCombined = settings.showCombined,
            )
        }

        notifications.notify(
            notifications.build(currentContent(bitmap, downShown, upShown)),
        )
        if (dualIcons) {
            notifications.notifyUpload(
                iconRenderer.renderSingle(upShown, down = false),
                upShown,
            )
            if (notifications.isAutoGrouped()) {
                dualIconsBlocked = true
                notifications.cancelUpload()
                // Remember the verdict: never re-attempt on this device, so the OS
                // never re-creates its orphan group summary (shows as a blank icon).
                lifecycleScope.launch { repo.setDualIconsBlocked(true) }
            }
        } else {
            notifications.cancelUpload()
        }

        syncFloatingChip(downShown, upShown)

        SpeedBus.publish(
            LiveSpeed(
                running = true,
                downBytesPerSec = sample.rxBytesPerSec,
                upBytesPerSec = sample.txBytesPerSec,
                todayBytes = todayBytes,
                peakBytesPerSec = todayPeakBps,
                lifetimeBytes = lifetimeBytes,
                dualIconsBlocked = dualIconsBlocked,
            ),
        )

        // Push home-screen widgets — only when at least one exists (screen is on
        // here by definition, since the ticker is paused while it's off).
        pushWidgets(downShown, upShown)

        persistTodayUsage(force = false)
    }

    /**
     * Shows/hides the overlay bubble per setting+permission and feeds it the SAME
     * icon bitmap the status bar uses — but full-colour ([IconRenderer.renderChip],
     * no OS-tint punch-out). So the bubble honours every Icon-Style choice: style,
     * unit display, background, text/icon colour, outline, font size AND the upload
     * value (when "show upload" / a combined style is on). A transparent background
     * or "no outline" falls back to a legible dark pill so the chip always has a
     * readable body to float against.
     */
    private fun syncFloatingChip(downShown: Long, upShown: Long) {
        val wanted = settings.floatingChip && suiteUnlocked &&
            android.provider.Settings.canDrawOverlays(this)
        if (!wanted) {
            if (floatingChip.isShown) floatingChip.hide()
            return
        }
        floatingChip.freePlacement = settings.bubbleFreePlacement
        floatingChip.fxPlacement = settings.bubbleFxPlacement
        if (!floatingChip.isShown) {
            floatingChip.show(settings.floatingChipX, settings.floatingChipY, settings.floatingChipScale)
        }
        floatingChip.applyScale(settings.floatingChipScale)   // bubble-size slider, live
        floatingChip.lockedBox = if (settings.bubbleLockSize) {
            val d = resources.displayMetrics.density
            var wDp = settings.bubbleBoxW
            var hDp = settings.bubbleBoxH
            if (wDp == 0 || hDp == 0) {
                // Lock just turned on with no stored box: capture the bubble's
                // CURRENT size (what the user sees is what gets locked) and
                // persist it once; the sliders then fine-tune from there.
                val probe = bubbleRenderer.renderChip(
                    style = settings.iconStyle,
                    downBps = downShown,
                    upBps = upShown,
                    showCombined = settings.showCombined,
                )
                val curH = FloatingChip.BASE_HEIGHT_DP * settings.floatingChipScale
                hDp = curH.toInt().coerceIn(20, 200)
                wDp = (probe.width * (curH / probe.height)).toInt().coerceIn(32, 400)
                val w0 = wDp
                val h0 = hDp
                lifecycleScope.launch {
                    repo.setBubbleBoxW(w0)
                    repo.setBubbleBoxH(h0)
                }
            }
            (wDp * d).toInt() to (hDp * d).toInt()
        } else null

        val accent = SpeedTiers.tierOf(downShown / 1_048_576f).c2.toArgb()
        bubbleRenderer.fontScale = resources.configuration.fontScale
        bubbleRenderer.userScale = settings.iconTextScale
        // The user's background is honored VERBATIM: "None" means truly
        // transparent — bare digits over the wallpaper (or over the scene).
        // The shadow halo keeps them readable on any backdrop.
        bubbleRenderer.bgColorArgb = settings.iconBgColor
        bubbleRenderer.glyphShadow = Color.alpha(settings.iconBgColor) == 0
        bubbleRenderer.fgColorArgb =
            if (Color.alpha(settings.iconFgColor) != 0) settings.iconFgColor else Color.WHITE
        bubbleRenderer.unitStyle = settings.iconUnitStyle
        bubbleRenderer.chipPadScale = settings.floatingChipPadScale
        bubbleRenderer.letterSpacingEm = settings.bubbleTracking
        bubbleRenderer.glyphTypeface = bubbleTypeface(settings.bubbleFont, settings.bubbleBold)
        bubbleRenderer.borderColorArgb = settings.iconBorderColor
        bubbleRenderer.borderWidth = settings.iconBorderWidth
        // Lottie scene: resolve the composition (cached); a failed parse falls
        // back to the flame so the toggle is never silently dead.
        var fxKey = settings.bubbleFx
        // "Match theme": the bubble plays whatever speed scene the hero theme
        // is; non-scene themes fall back to no effect.
        if (fxKey == "theme") {
            fxKey = com.netspeed.indicator.render.scenes.SceneRegistry
                .fromThemeKey(settings.heroTheme.storageKey)?.id ?: "none"
        }
        var lottie: com.airbnb.lottie.LottieComposition? = null
        if (fxKey == "lottie") {
            lottie = lottieFor(settings.bubbleLottieUri)
            if (lottie == null) fxKey = "flame"
        }
        floatingChip.update(
            bitmap = bubbleRenderer.renderChip(
                style = settings.iconStyle,
                downBps = downShown,
                upBps = upShown,
                showCombined = settings.showCombined,
            ),
            fxKey = fxKey,
            // Normalised "how hard is the network working" for the FX engine —
            // log-ish ramp so KB/s traffic already animates gently.
            intensity = ((downShown + upShown) / (8f * 1024 * 1024)).coerceIn(0f, 1f)
                .let { kotlin.math.sqrt(it) },
            accentArgb = accent,
            lottie = lottie,
            // Speed scenes are DRIVEN by speed (not just paced): smoothed MB/s
            // plus the user's tier thresholds for exact gear/journey mapping.
            sceneMbps = downShown / 1_048_576f,
            tierThresholds = settings.thresholdsArray(),
            // Background "None" = the scene drops its own backdrop too: only
            // the characters/elements float on the wallpaper.
            sceneTransparentBg = Color.alpha(settings.iconBgColor) == 0,
        )
    }

    /** Bubble glyph face from the user's font + weight choice (system faces only). */
    private fun bubbleTypeface(fontKey: String, bold: Boolean): android.graphics.Typeface {
        val family = when (fontKey) {
            "condensed" -> "sans-serif-condensed"
            "serif" -> "serif"
            "mono" -> "monospace"
            else -> if (bold) "sans-serif-black" else "sans-serif"
        }
        val style = if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        return android.graphics.Typeface.create(family, style)
    }

    private fun pushWidgets(downBps: Long, upBps: Long) {
        // pushAll also caches the frame as `lastData`, so a widget pinned later
        // renders with the right skin/theme instantly — keep building the frame
        // even while no widget exists yet (it's a tiny value object).
        SpeedWidgetProvider.pushAll(
            this,
            WidgetData(
                downBps = downBps,
                upBps = upBps,
                todayBytes = todayBytes,
                peakBps = todayPeakBps,
                dailyQuotaBytes = settings.dailyQuotaBytes,
                history = speedHistory.toList(),
                accentArgb = if (settings.colorSkin != com.netspeed.indicator.data.ColorSkin.TIER)
                    settings.colorSkin.accent.toArgb() else 0,
                gradientArgb = skinGradientArgb(),
                phase = com.netspeed.indicator.core.GradientFlow.phase(System.currentTimeMillis()),
                themeKey = settings.heroTheme.storageKey,
                heroFgArgb = settings.colorSkin.heroFg.toArgb(),
                tierThresholds = settings.tierThresholds,
            ),
        )
    }

    /** True while a Pause or an unexpired Hide-1h is in effect. */
    private fun isSuppressed(): Boolean = manualPaused || elapsedMs() < hiddenUntilMs

    /** Skin hero gradient for non-Tier skins; empty = surfaces use the brand trio. */
    private fun skinGradientArgb(): List<Int> =
        if (settings.colorSkin != com.netspeed.indicator.data.ColorSkin.TIER)
            settings.colorSkin.heroColors.map { it.toArgb() } else emptyList()

    /** Assembles the current [NotificationFactory.Content] frame. */
    private fun currentContent(iconBitmap: Bitmap?, downBps: Long, upBps: Long): NotificationFactory.Content {
        // Shade accent follows the user's chosen chip colour when set (the bar
        // icon itself is OS-tinted, but the shade tints OUR way) — else live tier.
        val tierColor = if (Color.alpha(settings.iconBgColor) != 0) settings.iconBgColor
        else SpeedTiers.tierOf(downBps / 1_048_576f).c2.toArgb()
        return NotificationFactory.Content(
            iconBitmap = iconBitmap,
            downBps = downBps,
            upBps = upBps,
            todayBytes = todayBytes,
            tierColorArgb = tierColor,
            connLabel = connectionLabel(),
            history = speedHistory.toList(),
            paused = isSuppressed(),
            showDetails = settings.showInPanel,
            gradientArgb = skinGradientArgb(),
            flowPhase = com.netspeed.indicator.core.GradientFlow.phase(System.currentTimeMillis()),
        )
    }

    private fun refreshPausedNotification() {
        notifications.notify(notifications.build(currentContent(null, 0, 0)))
    }

    /**
     * "Wi-Fi 78%" / "Mobile 50%" / "Ethernet" / "Offline" — connection type plus
     * live signal strength, read once per tick. Read-only APIs: ACCESS_NETWORK_STATE
     * + ACCESS_WIFI_STATE (both normal permissions; the no-INTERNET promise holds).
     */
    private fun connectionLabel(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            caps == null -> getString(R.string.conn_offline)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                getString(R.string.conn_wifi) + wifiSignalSuffix()
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                getString(R.string.conn_mobile) + mobileSignalSuffix()
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> getString(R.string.conn_ethernet)
            else -> getString(R.string.conn_wifi)
        }
    }

    /** " 78%" from the current Wi-Fi RSSI, or "" when unavailable. */
    private fun wifiSignalSuffix(): String = runCatching {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        @Suppress("DEPRECATION") val rssi = wm.connectionInfo?.rssi ?: return ""
        if (rssi >= 0 || rssi <= -127) return ""              // invalid / unknown
        @Suppress("DEPRECATION")
        " ${android.net.wifi.WifiManager.calculateSignalLevel(rssi, 101)}%"
    }.getOrDefault("")

    /** " 75%" from the cellular signal level (0–4 → ×25), or "" when unavailable. */
    private fun mobileSignalSuffix(): String = runCatching {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return ""
        val level = tm.signalStrength?.level ?: return ""
        " ${level * 25}%"
    }.getOrDefault("")

    // --- "today" accounting ----------------------------------------------------

    private fun accumulateToday(deltaBytes: Long) {
        val today = currentEpochDay()
        if (today != todayEpochDay) {
            // Day rolled over: file the finished day into the 30-day history
            // (service lifecycleScope is fine — the service runs across days).
            if (todayEpochDay != 0L && todayBytes > 0L) {
                val day = todayEpochDay
                val bytes = todayBytes
                lifecycleScope.launch { repo.appendDailyHistory(day, bytes) }
            }
            todayEpochDay = today
            todayBytes = 0L
            todayPeakBps = 0L           // peak is a per-day stat
        }
        todayBytes += deltaBytes
    }

    private fun restoreTodayUsage() {
        lifecycleScope.launch {
            val stored = repo.todayUsage.first()
            val today = currentEpochDay()
            if (stored.epochDay == today) {
                todayEpochDay = stored.epochDay
                todayBytes = stored.totalBytes
                todayPeakBps = stored.peakBytesPerSec
            } else {
                todayEpochDay = today
                todayBytes = 0L
                todayPeakBps = 0L
            }
        }
    }

    private fun restoreLifetimeUsage() {
        lifecycleScope.launch {
            lifetimeBytes = repo.lifetimeUsage.first().totalBytes
        }
    }

    private fun persistTodayUsage(force: Boolean) {
        val now = elapsedMs()
        if (!force && now - lastPersistMs < ServiceConstants.USAGE_PERSIST_INTERVAL_MS) return
        lastPersistMs = now
        val snapshotDay = todayEpochDay
        val snapshotBytes = todayBytes
        val snapshotPeak = todayPeakBps
        val snapshotLifetime = lifetimeBytes
        lifecycleScope.launch {
            repo.setTodayUsage(TodayUsage(snapshotDay, snapshotBytes, snapshotPeak))
            repo.setLifetimeUsage(LifetimeUsage(snapshotLifetime))
        }
    }

    // --- time helpers (wrapped for clarity; no Date.now in pure logic) ---------

    private fun elapsedMs(): Long = android.os.SystemClock.elapsedRealtime()

    private fun currentEpochDay(): Long =
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

    companion object {
        /** Starts the service as a foreground service (API-correct entry point). */
        fun start(context: Context) {
            val intent = Intent(context, SpeedMeterService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SpeedMeterService::class.java))
        }
    }
}
