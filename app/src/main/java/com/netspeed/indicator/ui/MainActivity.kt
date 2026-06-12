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
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.netspeed.indicator.BuildConfig
import com.netspeed.indicator.NetSpeedApp
import com.netspeed.indicator.billing.BillingManager
import com.netspeed.indicator.billing.EntitlementStore
import com.netspeed.indicator.data.SettingsRepository
import com.netspeed.indicator.data.SpeedBus
import com.netspeed.indicator.service.SpeedMeterService
import com.netspeed.indicator.ui.theme.NetSpeedTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasNotifPermission = granted
            if (granted) enableIndicator()
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

                SettingsScreen(
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
                        // The bubble needs the system overlay permission once.
                        if (value && !android.provider.Settings.canDrawOverlays(this)) {
                            startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:$packageName"),
                                ),
                            )
                        }
                    },
                    onFloatingChipScale = { v -> persist { repo.setFloatingChipScale(v) } },
                    onHideIconWhenBubble = { v -> persist { repo.setHideIconWhenBubble(v) } },
                    onBubbleFreePlacement = { v -> persist { repo.setBubbleFreePlacement(v) } },
                    onResetBubblePos = { persist { repo.resetFloatingChipPos() } },
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
                    suiteUnlocked = entitlement.suiteUnlocked || !BuildConfig.PAYWALL_ENABLED,
                    onLockedTap = { showPaywall = true },
                    onThresholdsChange = { v -> persist { repo.setTierThresholds(v) } },
                    onNamesChange = { v -> persist { repo.setTierNames(v) } },
                    onQuotaChange = { v -> persist { repo.setDailyQuotaBytes(v) } },
                    onGrantNotifications = ::requestNotificationPermission,
                    onRequestIgnoreBattery = ::requestIgnoreBatteryOptimizations,
                    onOpenNotificationSettings = ::openAppNotificationSettings,
                )

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
        // instead of silently never showing the bubble.
        lifecycleScope.launch {
            val wantsBubble = repo.settings.first().floatingChip
            if (wantsBubble && !android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
        }
    }

    private fun disableIndicator() {
        persist { repo.setEnabled(false) }
        SpeedMeterService.stop(this)
        SpeedBus.markStopped()
    }

    // --- permissions -----------------------------------------------------------

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enableIndicator()
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
}
