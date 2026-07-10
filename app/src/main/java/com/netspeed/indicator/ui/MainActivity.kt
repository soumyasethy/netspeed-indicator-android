package com.netspeed.indicator.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.netspeed.indicator.BuildConfig
import com.netspeed.indicator.NetSpeedApp
import com.netspeed.indicator.billing.BillingManager
import com.netspeed.indicator.billing.EntitlementStore
import com.netspeed.indicator.core.BubbleDock
import com.netspeed.indicator.data.SettingsRepository
import com.netspeed.indicator.data.SpeedBus
import com.netspeed.indicator.data.UnitStyle
import com.netspeed.indicator.service.FloatingChip
import com.netspeed.indicator.service.SpeedMeterService
import com.netspeed.indicator.ui.theme.NetSpeedTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The app's single screen. Owns the notification-permission flow and wires the
 * settings UI to [SettingsRepository] (persistence) and [SpeedBus] (live preview).
 */
class MainActivity : ComponentActivity() {

    private lateinit var repo: SettingsRepository
    private lateinit var billing: BillingManager

    // Tracks the live POST_NOTIFICATIONS grant so the UI can show a banner / gate
    // the master switch. Recomputed in onResume (the user may toggle it in Settings).
    private var hasNotifPermission by mutableStateOf(true)

    // Drives the one-time "turn off the overlay disclosure" nudge (bubble mode only).
    private var hasOverlayPermission by mutableStateOf(false)

    // Shows the rationale dialog before we hand off to the bare system overlay
    // settings screen — the user reads what "display over other apps" unlocks first.
    private var showOverlayRationale by mutableStateOf(false)

    // Shows the notification rationale before the POST_NOTIFICATIONS system prompt
    // (Status-bar mode draws the speed as a silent notification icon).
    private var showNotifRationale by mutableStateOf(false)

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasNotifPermission = granted
            if (granted) enableIndicator()
        }

    /** Lottie scene file picker ("endless possibilities" mode for the bubble). */
    private val pickLottie =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                // Keep read access across reboots — the service re-reads the file.
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                persist { repo.setBubbleLottieUri(uri.toString()) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = SettingsRepository(applicationContext)
        billing = BillingManager(
            applicationContext,
            (application as NetSpeedApp).appScope,
            EntitlementStore(applicationContext),
        ).also { it.start() }
        hasNotifPermission = notificationsAllowed()

        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle(
                initialValue = com.netspeed.indicator.data.Settings(),
            )
            NetSpeedTheme(skin = settings.colorSkin) {
                val live by SpeedBus.state.collectAsStateWithLifecycle()
                val dailyHistory by repo.dailyHistory.collectAsStateWithLifecycle(
                    initialValue = emptyList(),
                )
                val entitlement by billing.entitlement.collectAsStateWithLifecycle()
                val priceSuite by billing.priceSuite.collectAsStateWithLifecycle()
                val priceTip by billing.priceTip.collectAsStateWithLifecycle()
                var showPaywall by remember { mutableStateOf(false) }
                var showStudio by rememberSaveable { mutableStateOf(false) }
                // adb replay hook: am start ... --ez debug_onboarding true
                var onbPreview by rememberSaveable {
                    mutableStateOf(intent?.getBooleanExtra("debug_onboarding", false) == true)
                }

                if (!settings.onboardingDone || onbPreview) {
                    OnboardingScreen(
                        live = live,
                        settings = settings,
                        suiteUnlocked = entitlement.suiteUnlocked || !BuildConfig.PAYWALL_ENABLED,
                        onStyleSelect = { v -> persist { repo.setIconStyle(v) } },
                        onUnitStyle = { v -> persist { repo.setIconUnitStyle(v) } },
                        onIconBgColor = { c -> persist { repo.setIconBgColor(c) } },
                        onIconFgColor = { c -> persist { repo.setIconFgColor(c) } },
                        onIconTextScale = { v -> persist { repo.setIconTextScale(v) } },
                        onIconBorderColor = { c -> persist { repo.setIconBorderColor(c) } },
                        onIconBorderWidth = { w -> persist { repo.setIconBorderWidth(w) } },
                        onLockedColor = { showPaywall = true },
                        onDone = {
                            // Real first-run completion (not the debug replay) auto-starts the
                            // indicator in Bubble mode so speed shows immediately — bubble docks
                            // beside the notch and the overlay permission is requested now.
                            val firstRun = !onbPreview
                            onbPreview = false
                            persist { repo.setOnboardingDone() }
                            if (firstRun) selectIndicatorMode(IndicatorMode.BUBBLE)
                        },
                    )
                } else if (showStudio) {
                    androidx.activity.compose.BackHandler { showStudio = false }
                    val suiteUnlockedNow = entitlement.suiteUnlocked ||
                        !com.netspeed.indicator.BuildConfig.PAYWALL_ENABLED
                    StudioScreen(
                        settings = settings,
                        live = live,
                        suiteUnlocked = suiteUnlockedNow,
                        onThemeSelect = { v -> persist { repo.setHeroTheme(v) } },
                        onSkinSelect = { v -> persist { repo.setColorSkin(v) } },
                        onBubbleFx = { v -> persist { repo.setBubbleFx(v) } },
                        onBubbleFont = { v -> persist { repo.setBubbleFont(v) } },
                        onStyleSelect = { v -> persist { repo.setIconStyle(v) } },
                        onIconUnitStyle = { v -> persist { repo.setIconUnitStyle(v) } },
                        onPinWidget = { kind -> pinWidget(kind) },
                        onLockedTap = { showPaywall = true },
                        onBack = { showStudio = false },
                    )
                } else SettingsScreen(
                    settings = settings,
                    live = live,
                    dailyHistory = dailyHistory,
                    hasNotificationPermission = hasNotifPermission,
                    isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
                    onMasterToggle = { wantOn ->
                        if (wantOn) requestEnable() else disableIndicator()
                    },
                    onCombinedToggle = { value -> persist { repo.setShowCombined(value) } },
                    onScreenOffToggle = { value -> persist { repo.setUpdateWhileScreenOff(value) } },
                    onAutoHideToggle = { value -> persist { repo.setAutoHideIdle(value) } },
                    onFloatingChipToggle = { value ->
                        persist { repo.setFloatingChip(value) }
                        if (value) {
                            // The bubble is served by the meter service — start it
                            // even when the status-bar toggle is off (icon stays
                            // transparent; only the bubble shows).
                            SpeedMeterService.start(this)
                        }
                        // The bubble needs the system overlay permission once.
                        if (value) requestOverlayPermission()
                    },
                    onFloatingChipScale = { v -> persist { repo.setFloatingChipScale(v) } },
                    onHideIconWhenBubble = { v -> persist { repo.setHideIconWhenBubble(v) } },
                    onBubbleFreePlacement = { v -> persist { repo.setBubbleFreePlacement(v) } },
                    onResetBubblePos = { persist { repo.resetFloatingChipPos() } },
                    onBubbleNudge = { dx, dy ->
                        persist {
                            val s = repo.settings.first()
                            repo.setFloatingChipPos(s.floatingChipX + dx, s.floatingChipY + dy)
                        }
                    },
                    onBubblePreset = { corner -> persist { applyBubblePreset(corner) } },
                    onFloatingChipPadScale = { v -> persist { repo.setFloatingChipPadScale(v) } },
                    onBubbleBold = { v -> persist { repo.setBubbleBold(v) } },
                    onBubbleFont = { v -> persist { repo.setBubbleFont(v) } },
                    onBubbleTracking = { v -> persist { repo.setBubbleTracking(v) } },
                    onBubbleFx = { v -> persist { repo.setBubbleFx(v) } },
                    onPickLottieFile = {
                        pickLottie.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                    },
                    onClearLottieFile = { persist { repo.setBubbleLottieUri("") } },
                    onBubbleFxPlacement = { v -> persist { repo.setBubbleFxPlacement(v) } },
                    onHeroTextPos = { v -> persist { repo.setHeroTextPos(v) } },
                    onHeroTextFormat = { v -> persist { repo.setHeroTextFormat(v) } },
                    onHeroTextNudge = { dx, dy -> persist {
                        repo.setHeroTextDX(settings.heroTextDX + dx)
                        repo.setHeroTextDY(settings.heroTextDY + dy)
                    } },
                    onBubbleLockSize = { v -> persist { repo.setBubbleLockSize(v) } },
                    onBubbleBoxW = { v -> persist { repo.setBubbleBoxW(v) } },
                    onBubbleBoxH = { v -> persist { repo.setBubbleBoxH(v) } },
                    onStyleSelect = { style -> persist { repo.setIconStyle(style) } },
                    onPanelToggle = { value -> persist { repo.setShowInPanel(value) } },
                    onThemeSelect = { theme -> persist { repo.setHeroTheme(theme) } },
                    onSkinSelect = { s -> persist { repo.setColorSkin(s) } },
                    onIconBgColor = { c -> persist { repo.setIconBgColor(c) } },
                    onIconFgColor = { c -> persist { repo.setIconFgColor(c) } },
                    onIconTextScale = { v -> persist { repo.setIconTextScale(v) } },
                    onIconUnitStyle = { v -> persist { repo.setIconUnitStyle(v) } },
                    onIconBorderColor = { c -> persist { repo.setIconBorderColor(c) } },
                    onIconBorderWidth = { w -> persist { repo.setIconBorderWidth(w) } },
                    onPinWidget = ::pinWidget,
                    onOpenStudio = { showStudio = true },
                    suiteUnlocked = entitlement.suiteUnlocked || !BuildConfig.PAYWALL_ENABLED,
                    onLockedTap = { showPaywall = true },
                    onThresholdsChange = { v -> persist { repo.setTierThresholds(v) } },
                    onNamesChange = { v -> persist { repo.setTierNames(v) } },
                    onQuotaChange = { v -> persist { repo.setDailyQuotaBytes(v) } },
                    onGrantNotifications = ::requestNotificationPermission,
                    onRequestIgnoreBattery = ::requestIgnoreBatteryOptimizations,
                    onOpenNotificationSettings = ::openAppNotificationSettings,
                    onIndicatorMode = { mode -> selectIndicatorMode(mode) },
                    onHideOverlayNotice = ::openOverlayDisclosureSettings,
                    hasOverlayPermission = hasOverlayPermission,
                    onAckOverlayNotice = { persist { repo.setOverlayNoticeAck(true) } },
                    onEnableBubble = { requestOverlayPermission() },
                )

                if (showOverlayRationale) {
                    OverlayPermissionSheet(
                        onAllow = { showOverlayRationale = false; openOverlaySettings() },
                        onUseStatusBar = {
                            showOverlayRationale = false
                            selectIndicatorMode(IndicatorMode.BAR)
                        },
                        onDismiss = { showOverlayRationale = false },
                    )
                }

                if (showNotifRationale) {
                    NotificationPermissionSheet(
                        onAllow = { launchNotifRequest() },
                        onDismiss = { showNotifRationale = false },
                    )
                }

                if (showPaywall) {
                    PaywallSheet(
                        priceSuite = priceSuite,
                        priceTip = priceTip,
                        onUnlock = { billing.buySuite(this@MainActivity) },
                        onTip = { billing.buyTip(this@MainActivity) },
                        onRestore = { billing.restore() },
                        onDismiss = { showPaywall = false },
                        debugUnlock = if (BuildConfig.DEBUG) {
                            { billing.debugSetUnlocked(true); showPaywall = false }
                        } else null,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasNotifPermission = notificationsAllowed()
        hasOverlayPermission = Settings.canDrawOverlays(this)
        // Returning here (e.g. after granting the overlay permission) must revive the
        // indicator: if a surface is now possible but the service died, restart it so
        // the bubble actually appears for first-time users.
        lifecycleScope.launch {
            val s = repo.settings.first()
            if (s.enabled || (s.floatingChip && hasOverlayPermission)) {
                SpeedMeterService.start(this@MainActivity)
            }
        }
    }

    override fun onDestroy() {
        billing.release()
        super.onDestroy()
    }

    // --- master switch flow ----------------------------------------------------

    private fun requestEnable() {
        if (notificationsAllowed()) {
            enableIndicator()
        } else {
            requestNotificationPermission()
        }
    }

    private fun enableIndicator() {
        persist { repo.setEnabled(true) }
        SpeedMeterService.start(this)
        // The bubble ships enabled by default, but the overlay permission can only
        // be granted by the user — ask at the natural moment (turning the meter on)
        // via the rationale dialog, instead of silently never showing the bubble.
        lifecycleScope.launch {
            if (repo.settings.first().floatingChip) requestOverlayPermission()
        }
    }

    private fun disableIndicator() {
        persist { repo.setEnabled(false) }
        // The service powers EVERY surface, not just the bar icon — keep it alive
        // when the floating bubble still needs it (the icon goes transparent; the
        // service stops itself when both surfaces are off).
        lifecycleScope.launch {
            val bubbleLive = repo.settings.first().floatingChip &&
                android.provider.Settings.canDrawOverlays(this@MainActivity)
            if (!bubbleLive) {
                SpeedMeterService.stop(this@MainActivity)
                SpeedBus.markStopped()
            }
        }
    }

    // --- permissions -----------------------------------------------------------

    /**
     * Ask for the overlay permission the friendly way: if it's missing, raise our
     * own rationale dialog first (explains + pictures what it unlocks). "Allow"
     * there calls [openOverlaySettings]. No-op when already granted. Single entry
     * point for every place that needs the bubble's overlay.
     */
    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) showOverlayRationale = true
    }

    /** Hands off to the system "Display over other apps" screen for this app. */
    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showNotifRationale = true
        } else {
            enableIndicator()
        }
    }

    /**
     * Sheet "Allow" → fire the system prompt. If it's already been permanently
     * denied (Android won't show the prompt again), route to the app's
     * notification settings instead so the user still has a way to turn it on.
     */
    private fun launchNotifRequest() {
        showNotifRationale = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            enableIndicator()
            return
        }
        lifecycleScope.launch {
            val asked = repo.settings.first().notifRequested
            val canPrompt = !asked ||
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            if (canPrompt) {
                repo.setNotifRequested(true)
                requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openAppNotificationSettings()
            }
        }
    }

    // --- battery optimisation --------------------------------------------------

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        // Direct per-app prompt. Requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }.onFailure {
            // Some OEMs hide the direct action; fall back to the battery settings list.
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    /**
     * Deep-links to this app's notification settings. The global "show icons vs
     * dot" status-bar toggle has no stable public deep-link, so we open the
     * closest stable screen and pair it with OEM-specific text directions in the
     * card. From here the user is one or two taps from the Status bar setting.
     */
    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(intent) }.onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName")),
                )
            }
        }
    }

    /**
     * Deep-links to the toggle that silences the OS "[app] is displaying over other
     * apps" notice. That notification is owned by the system ("android") package, in
     * a per-app channel — NetSpeed can't cancel it, but it can drop the user one tap
     * from turning it off. Falls back to the system app's notification list, then the
     * overlay-permission screen, if the exact channel deep-link is unsupported.
     */
    private fun openOverlayDisclosureSettings() {
        val channelId = "com.android.server.wm.AlertWindowNotification - $packageName"
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, "android")
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        runCatching { startActivity(intent) }.onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, "android"),
                )
            }.onFailure {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Quick-dock the bubble: corner presets target the screen extremes (the
     * service-side clamp pulls them to the legal edge for the current placement
     * mode), centre is computed from the display size.
     */
    private suspend fun applyBubblePreset(corner: BubbleCorner) {
        val dm = resources.displayMetrics
        val (x, y) = when (corner) {
            BubbleCorner.TOP_LEFT -> 0 to 0
            BubbleCorner.TOP_RIGHT -> dm.widthPixels to 0
            BubbleCorner.NOTCH -> repo.settings.first().let {
                besideNotchDock(it.floatingChipScale, it.bubbleBoxW, it.bubbleBoxH)
            }
            BubbleCorner.CENTRE -> dm.widthPixels / 2 - dm.widthPixels / 14 to dm.heightPixels / 2
            BubbleCorner.BOTTOM_LEFT -> 0 to dm.heightPixels
            BubbleCorner.BOTTOM_RIGHT -> dm.widthPixels to dm.heightPixels
        }
        repo.setFloatingChipPos(x, y)
    }

    /**
     * The default "clever spot": tucked into the status bar just RIGHT of a centred
     * punch-hole and left of the system icons (or left of those icons if there's no
     * central cutout). Reads the live [android.view.DisplayCutout] so it lands right
     * on any phone; geometry lives in the unit-tested [BubbleDock.besideNotch].
     */
    private fun besideNotchDock(scale: Float, boxWdp: Int, boxHdp: Int): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val d = dm.density
        val sbId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarPx = if (sbId > 0) resources.getDimensionPixelSize(sbId) else (28 * d).roundToInt()
        // A locked badge gives the real footprint → the dock centres it in the status
        // bar (≈20 px top margin for a 20 dp badge), level with the system icons.
        val chipH = if (boxHdp > 0) (boxHdp * d).roundToInt() else (FloatingChip.BASE_HEIGHT_DP * scale * d).roundToInt()
        val chipW = if (boxWdp > 0) (boxWdp * d).roundToInt() else (64 * scale * d).roundToInt()
        val gap = (8 * d).roundToInt()
        var cutLeft: Int? = null
        var cutRight: Int? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.decorView.rootWindowInsets?.displayCutout
                ?.boundingRects?.firstOrNull { it.top <= statusBarPx }
                ?.let { cutLeft = it.left; cutRight = it.right }
        }
        return BubbleDock.besideNotch(dm.widthPixels, statusBarPx, chipW, chipH, cutLeft, cutRight, gap)
    }

    /**
     * Headline indicator chooser (Off / Bubble / Status bar). Bubble and Bar are
     * mutually-exclusive presets — Bar uses no overlay, so the OS "displaying over
     * other apps" notice never appears. Bubble auto-docks to the clever spot the
     * first time and asks for the overlay permission.
     */
    private fun selectIndicatorMode(mode: IndicatorMode) {
        when (mode) {
            IndicatorMode.OFF -> {
                persist { repo.setFloatingChip(false); repo.setEnabled(false) }
                SpeedMeterService.stop(this)
                SpeedBus.markStopped()
            }
            IndicatorMode.BUBBLE -> {
                persist { repo.setIndicatorMode(bubble = true) }
                SpeedMeterService.start(this)
                // Ask for the overlay permission FIRST, synchronously, and never gate
                // it behind the auto-dock work below. It used to live at the tail of
                // that coroutine, so on first run any hiccup in applyBubblePreset()
                // swallowed the prompt entirely — the bubble silently never appeared
                // until the user toggled Off→Bubble again (which skipped the dock
                // block). Requesting it here, while the activity is unquestionably
                // resumed from the user's tap, makes the prompt reliable on first run.
                requestOverlayPermission()
                // Auto-dock to the clever spot only on a fresh bubble that's still at
                // the factory position — never clobber a user's drag. Isolated in its
                // own guarded coroutine so a failure here can't affect the prompt above.
                lifecycleScope.launch {
                    runCatching {
                        val s = repo.settings.first()
                        val untouched = s.floatingChipX == com.netspeed.indicator.data.DEFAULT_CHIP_X &&
                            s.floatingChipY == com.netspeed.indicator.data.DEFAULT_CHIP_Y
                        if (!s.chipAutoPlaced) {
                            if (untouched) applyBubblePreset(BubbleCorner.NOTCH)
                            repo.setChipAutoPlaced(true)
                        }
                    }
                }
            }
            IndicatorMode.BAR -> {
                persist {
                    repo.setIndicatorMode(bubble = false)
                    // One-time status-bar defaults: the tiny icon reads best with the
                    // unit stacked below the number and the text at max size. Applied
                    // once so it never clobbers a user's later tweaks.
                    if (!repo.settings.first().statusBarDefaultsApplied) {
                        repo.setIconUnitStyle(UnitStyle.BELOW)
                        repo.setIconTextScale(STATUS_BAR_MAX_TEXT_SCALE)
                        repo.setStatusBarDefaultsApplied(true)
                    }
                }
                if (notificationsAllowed()) SpeedMeterService.start(this)
                else requestNotificationPermission()
            }
        }
    }

    /** Asks the launcher to pin the chosen widget style to the home screen (API 26+). */
    private fun pinWidget(kind: com.netspeed.indicator.render.WidgetKind) {
        val cls = when (kind) {
            com.netspeed.indicator.render.WidgetKind.HERO -> com.netspeed.indicator.widget.HeroWidget::class.java
            com.netspeed.indicator.render.WidgetKind.DIAL -> com.netspeed.indicator.widget.DialWidget::class.java
            com.netspeed.indicator.render.WidgetKind.RINGS -> com.netspeed.indicator.widget.RingsWidget::class.java
            com.netspeed.indicator.render.WidgetKind.PILL -> com.netspeed.indicator.widget.PillWidget::class.java
            com.netspeed.indicator.render.WidgetKind.WEATHER -> com.netspeed.indicator.widget.WeatherWidget::class.java
        }
        val mgr = getSystemService(android.appwidget.AppWidgetManager::class.java)
        if (mgr != null && mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(android.content.ComponentName(this, cls), null, null)
        }
    }

    // Persist on the application scope, NOT lifecycleScope: a setting changed just
    // before the activity is destroyed must still finish writing to DataStore.
    private inline fun persist(crossinline block: suspend () -> Unit) {
        (application as NetSpeedApp).appScope.launch { block() }
    }

    private companion object {
        // Top of the "Icon text size" slider (valueRange 0f..1.4f) — the most
        // readable size for the tiny status-bar glyph.
        const val STATUS_BAR_MAX_TEXT_SCALE = 1.4f
    }
}
