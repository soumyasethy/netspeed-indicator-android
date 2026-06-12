package com.netspeed.indicator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single process-wide DataStore instance, scoped to the application context. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "netspeed_settings")

/** Out-of-box look: blue chip, white glyphs, white outline — legible on every
 * status bar and the bubble reads branded instead of bare. Users who pick their
 * own colours override these; explicit choices always win. */
const val DEFAULT_ICON_BG = 0xFF2563EB.toInt()      // "Blue" preset swatch
const val DEFAULT_ICON_BORDER = 0xFFFFFFFF.toInt()  // "White" preset swatch

/** Default bubble spot — also the target of "Reset bubble position". */
const val DEFAULT_CHIP_X = 24
const val DEFAULT_CHIP_Y = 240

/** User-facing toggles, read by both the UI and the service. */
data class Settings(
    val enabled: Boolean = false,
    val showCombined: Boolean = false,
    val updateWhileScreenOff: Boolean = false,
    val iconStyle: IconStyle = IconStyle.DEFAULT,
    val showInPanel: Boolean = false,
    val heroTheme: HeroTheme = HeroTheme.DEFAULT,
    /** The 4 tier boundaries in MB/s (Slow/Steady/Fast/Blazing onset). */
    val tierThresholds: List<Float> = com.netspeed.indicator.core.SpeedTiers.DEFAULT_THRESHOLDS.toList(),
    /** The 5 tier display names, in order. */
    val tierNames: List<String> = com.netspeed.indicator.core.SpeedTiers.ALL.map { it.defaultWord },
    /** Daily data cap in bytes (0 = unset); drives the Rings widget quota arc. */
    val dailyQuotaBytes: Long = 0L,
    /** Whole-app colour skin (palette + hero gradient + font). */
    val colorSkin: ColorSkin = ColorSkin.DEFAULT,
    /** Status-bar icon background ARGB (0 = transparent) and glyph colour. */
    val iconBgColor: Int = DEFAULT_ICON_BG,
    val iconFgColor: Int = 0xFFFFFFFF.toInt(),
    /** User icon text-size multiplier (0.8–1.4), on top of the system font scale. */
    val iconTextScale: Float = 1f,
    /** Hide the status-bar icon after ~30 s without traffic (panel row stays). */
    val autoHideIdle: Boolean = false,
    /** Unit treatment in single-direction icons (short suffix / full / below). */
    val iconUnitStyle: UnitStyle = UnitStyle.DEFAULT,
    /** Icon outline: ARGB colour (0 = none) and stroke width step (1–3). */
    val iconBorderColor: Int = DEFAULT_ICON_BORDER,
    val iconBorderWidth: Int = 1,
    /** Floating draggable speed bubble drawn over any app (needs overlay permission). */
    val floatingChip: Boolean = true,
    /** Hide the status-bar icon while the bubble is visible (panel row stays).
     *  Default OFF: combined with the default-on bubble, a true default suppressed
     *  the bar icon on fresh installs ("where is my speed?"). Opt-in dedupe. */
    val hideIconWhenBubble: Boolean = false,
    /** Bubble size multiplier (0.8–1.6). */
    val floatingChipScale: Float = 1f,
    /** Bubble width boost — multiplies the chip's horizontal padding (1.0–2.5). */
    val floatingChipPadScale: Float = 1f,
    /** Bubble glyph weight: heavy (default, matches the bar) or regular. */
    val bubbleBold: Boolean = true,
    /** Bubble font family key: sans / condensed / serif / mono (system faces). */
    val bubbleFont: String = "sans",
    /** Bubble letter-spacing in em (0–0.12) — relaxes clumpy horizontal text. */
    val bubbleTracking: Float = 0f,
    /** Bubble speed-reactive animation: none / flame / glow / sparks / lottie. */
    val bubbleFx: String = "none",
    /** Custom Lottie scene file (SAF uri); "" = the bundled paper-plane. */
    val bubbleLottieUri: String = "",
    /** Scene placement: behind the text, or beside it (left/right slot). */
    val bubbleFxPlacement: String = "behind",
    /** Fixed-size badge mode: lock the bubble box; content auto-fits inside. */
    val bubbleLockSize: Boolean = false,
    /** Locked box dimensions in dp. */
    /** Locked badge box in dp; 0 = capture the CURRENT size when lock turns on. */
    val bubbleBoxW: Int = 0,
    val bubbleBoxH: Int = 0,
    /** Persisted bubble position (window coordinates). */
    val floatingChipX: Int = DEFAULT_CHIP_X,
    val floatingChipY: Int = DEFAULT_CHIP_Y,
    /** Free placement: dock the bubble over the status bar / half-out screen
     *  edges (a touchable sliver always remains; Reset is the escape hatch). */
    val bubbleFreePlacement: Boolean = true,
    /** Set once we detect the OS force-grouping our notifications (One UI):
     *  the dual-icon attempt is then skipped forever on this device, so the OS
     *  never creates its orphan group summary (a blank icon) again. */
    val dualIconsBlocked: Boolean = false,
) {
    fun thresholdsArray(): FloatArray = tierThresholds.toFloatArray()
}

/** Cumulative traffic for one calendar day (epoch-day keyed so it self-resets). */
data class TodayUsage(
    val epochDay: Long = 0L,
    val totalBytes: Long = 0L,
    val peakBytesPerSec: Long = 0L,
)

/** Monotonic all-time counters that never reset. */
data class LifetimeUsage(
    val totalBytes: Long = 0L,
)

/**
 * Thin wrapper over DataStore Preferences. Everything is exposed as a [Flow] so
 * the service reacts to setting changes live, without a restart.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            enabled = p[KEY_ENABLED] ?: false,
            showCombined = p[KEY_COMBINED] ?: false,
            updateWhileScreenOff = p[KEY_SCREEN_OFF] ?: false,
            iconStyle = IconStyle.fromKey(p[KEY_ICON_STYLE]),
            showInPanel = p[KEY_SHOW_PANEL] ?: false,   // minimal card by default
            heroTheme = HeroTheme.fromKey(p[KEY_HERO_THEME]),
            tierThresholds = parseFloats(p[KEY_THRESHOLDS])
                ?: com.netspeed.indicator.core.SpeedTiers.DEFAULT_THRESHOLDS.toList(),
            tierNames = parseNames(p[KEY_TIER_NAMES])
                ?: com.netspeed.indicator.core.SpeedTiers.ALL.map { it.defaultWord },
            dailyQuotaBytes = p[KEY_QUOTA_BYTES] ?: 0L,
            colorSkin = ColorSkin.fromKey(p[KEY_COLOR_SKIN]),
            iconBgColor = p[KEY_ICON_BG] ?: DEFAULT_ICON_BG,
            iconFgColor = p[KEY_ICON_FG] ?: 0xFFFFFFFF.toInt(),
            iconTextScale = p[KEY_ICON_TEXT_SCALE] ?: 1f,
            autoHideIdle = p[KEY_AUTO_HIDE_IDLE] ?: false,
            iconUnitStyle = UnitStyle.fromKey(p[KEY_ICON_UNIT_STYLE]),
            iconBorderColor = p[KEY_ICON_BORDER_COLOR] ?: DEFAULT_ICON_BORDER,
            iconBorderWidth = (p[KEY_ICON_BORDER_WIDTH] ?: 1).coerceIn(1, 3),
            floatingChip = p[KEY_FLOAT_CHIP] ?: true,
            hideIconWhenBubble = p[KEY_HIDE_ICON_BUBBLE] ?: false,
            floatingChipScale = (p[KEY_FLOAT_SCALE] ?: 1f).coerceIn(0.5f, 1.6f),
            floatingChipPadScale = (p[KEY_FLOAT_PAD] ?: 1f).coerceIn(1f, 2.5f),
            bubbleBold = p[KEY_BUBBLE_BOLD] ?: true,
            bubbleFont = p[KEY_BUBBLE_FONT] ?: "sans",
            bubbleTracking = (p[KEY_BUBBLE_TRACKING] ?: 0f).coerceIn(0f, 0.12f),
            bubbleFx = p[KEY_BUBBLE_FX] ?: "none",
            bubbleLottieUri = p[KEY_BUBBLE_LOTTIE] ?: "",
            bubbleFxPlacement = p[KEY_BUBBLE_FX_PLACE] ?: "behind",
            bubbleLockSize = p[KEY_BUBBLE_LOCK] ?: false,
            bubbleBoxW = (p[KEY_BUBBLE_BOX_W] ?: 0).let { if (it == 0) 0 else it.coerceIn(32, 400) },
            bubbleBoxH = (p[KEY_BUBBLE_BOX_H] ?: 0).let { if (it == 0) 0 else it.coerceIn(20, 200) },
            floatingChipX = p[KEY_FLOAT_X] ?: DEFAULT_CHIP_X,
            floatingChipY = p[KEY_FLOAT_Y] ?: DEFAULT_CHIP_Y,
            bubbleFreePlacement = p[KEY_FLOAT_FREE] ?: true,
            dualIconsBlocked = p[KEY_DUAL_BLOCKED] ?: false,
        )
    }

    /** Finished days' totals, oldest first, max 30 (see [UsageHistory]). */
    val dailyHistory: Flow<List<DayUsage>> = context.dataStore.data.map { p ->
        UsageHistory.decode(p[KEY_DAILY_HISTORY])
    }

    val todayUsage: Flow<TodayUsage> = context.dataStore.data.map { p ->
        TodayUsage(
            epochDay = p[KEY_USAGE_DAY] ?: 0L,
            totalBytes = p[KEY_USAGE_BYTES] ?: 0L,
            peakBytesPerSec = p[KEY_USAGE_PEAK] ?: 0L,
        )
    }

    val lifetimeUsage: Flow<LifetimeUsage> = context.dataStore.data.map { p ->
        LifetimeUsage(totalBytes = p[KEY_LIFETIME_BYTES] ?: 0L)
    }

    suspend fun setEnabled(value: Boolean) = edit { it[KEY_ENABLED] = value }
    suspend fun setShowCombined(value: Boolean) = edit { it[KEY_COMBINED] = value }
    suspend fun setUpdateWhileScreenOff(value: Boolean) = edit { it[KEY_SCREEN_OFF] = value }
    suspend fun setIconStyle(value: IconStyle) = edit { it[KEY_ICON_STYLE] = value.storageKey }
    suspend fun setShowInPanel(value: Boolean) = edit { it[KEY_SHOW_PANEL] = value }
    suspend fun setHeroTheme(value: HeroTheme) = edit { it[KEY_HERO_THEME] = value.storageKey }
    suspend fun setTierThresholds(values: List<Float>) =
        edit { it[KEY_THRESHOLDS] = values.joinToString(",") }
    suspend fun setTierNames(values: List<String>) =
        edit { it[KEY_TIER_NAMES] = values.joinToString("|") }
    suspend fun setDailyQuotaBytes(bytes: Long) = edit { it[KEY_QUOTA_BYTES] = bytes }
    suspend fun setColorSkin(value: ColorSkin) = edit { it[KEY_COLOR_SKIN] = value.storageKey }
    suspend fun setIconBgColor(c: Int) = edit { it[KEY_ICON_BG] = c }
    suspend fun setIconFgColor(c: Int) = edit { it[KEY_ICON_FG] = c }
    suspend fun setIconTextScale(v: Float) = edit { it[KEY_ICON_TEXT_SCALE] = v }
    suspend fun setAutoHideIdle(value: Boolean) = edit { it[KEY_AUTO_HIDE_IDLE] = value }
    suspend fun setIconUnitStyle(value: UnitStyle) = edit { it[KEY_ICON_UNIT_STYLE] = value.storageKey }
    suspend fun setIconBorderColor(c: Int) = edit { it[KEY_ICON_BORDER_COLOR] = c }
    suspend fun setIconBorderWidth(w: Int) = edit { it[KEY_ICON_BORDER_WIDTH] = w.coerceIn(1, 3) }
    suspend fun setDualIconsBlocked(value: Boolean) = edit { it[KEY_DUAL_BLOCKED] = value }
    suspend fun setFloatingChip(value: Boolean) = edit { it[KEY_FLOAT_CHIP] = value }
    suspend fun setHideIconWhenBubble(value: Boolean) = edit { it[KEY_HIDE_ICON_BUBBLE] = value }
    suspend fun setFloatingChipScale(v: Float) = edit { it[KEY_FLOAT_SCALE] = v.coerceIn(0.5f, 1.6f) }
    suspend fun setBubbleBold(v: Boolean) = edit { it[KEY_BUBBLE_BOLD] = v }
    suspend fun setBubbleFont(v: String) = edit { it[KEY_BUBBLE_FONT] = v }
    suspend fun setBubbleTracking(v: Float) = edit { it[KEY_BUBBLE_TRACKING] = v.coerceIn(0f, 0.12f) }
    suspend fun setBubbleFx(v: String) = edit { it[KEY_BUBBLE_FX] = v }
    suspend fun setBubbleLottieUri(v: String) = edit { it[KEY_BUBBLE_LOTTIE] = v }
    suspend fun setBubbleFxPlacement(v: String) = edit { it[KEY_BUBBLE_FX_PLACE] = v }
    suspend fun setBubbleLockSize(v: Boolean) = edit {
        it[KEY_BUBBLE_LOCK] = v
        // Unlocking clears the stored box so the next lock re-captures the
        // bubble's then-current size (lock = "freeze what I see now").
        if (!v) {
            it.remove(KEY_BUBBLE_BOX_W)
            it.remove(KEY_BUBBLE_BOX_H)
        }
    }
    suspend fun setBubbleBoxW(v: Int) = edit { it[KEY_BUBBLE_BOX_W] = v.coerceIn(32, 400) }
    suspend fun setBubbleBoxH(v: Int) = edit { it[KEY_BUBBLE_BOX_H] = v.coerceIn(20, 200) }
    suspend fun setFloatingChipPadScale(v: Float) = edit { it[KEY_FLOAT_PAD] = v.coerceIn(1f, 2.5f) }
    suspend fun setFloatingChipPos(x: Int, y: Int) = edit { it[KEY_FLOAT_X] = x; it[KEY_FLOAT_Y] = y }
    suspend fun setBubbleFreePlacement(value: Boolean) = edit { it[KEY_FLOAT_FREE] = value }
    /** "Reset bubble position": back to the default spot (never-a-blocker net). */
    suspend fun resetFloatingChipPos() = setFloatingChipPos(DEFAULT_CHIP_X, DEFAULT_CHIP_Y)

    /** Records a finished day's total into the rolling 30-day history. */
    suspend fun appendDailyHistory(epochDay: Long, bytes: Long) = edit { p ->
        p[KEY_DAILY_HISTORY] =
            UsageHistory.encode(UsageHistory.append(UsageHistory.decode(p[KEY_DAILY_HISTORY]), epochDay, bytes))
    }

    suspend fun setTodayUsage(usage: TodayUsage) = edit {
        it[KEY_USAGE_DAY] = usage.epochDay
        it[KEY_USAGE_BYTES] = usage.totalBytes
        it[KEY_USAGE_PEAK] = usage.peakBytesPerSec
    }

    suspend fun setLifetimeUsage(usage: LifetimeUsage) = edit {
        it[KEY_LIFETIME_BYTES] = usage.totalBytes
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    /** Parse "1.0,5.0,15.0,30.0" → [1,5,15,30]; null/garbage → null (use default). */
    private fun parseFloats(raw: String?): List<Float>? {
        val parts = raw?.split(",")?.mapNotNull { it.trim().toFloatOrNull() } ?: return null
        return parts.takeIf { it.size == 4 && it.zipWithNext().all { (a, b) -> a < b } }
    }

    private fun parseNames(raw: String?): List<String>? {
        val parts = raw?.split("|")?.map { it.trim() } ?: return null
        return parts.takeIf { it.size == 5 && it.all { n -> n.isNotEmpty() } }
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("enabled")
        val KEY_COMBINED = booleanPreferencesKey("show_combined")
        val KEY_SCREEN_OFF = booleanPreferencesKey("update_screen_off")
        val KEY_ICON_STYLE = stringPreferencesKey("icon_style")
        val KEY_SHOW_PANEL = booleanPreferencesKey("show_in_panel")
        val KEY_HERO_THEME = stringPreferencesKey("hero_theme")
        val KEY_THRESHOLDS = stringPreferencesKey("tier_thresholds")
        val KEY_TIER_NAMES = stringPreferencesKey("tier_names")
        val KEY_QUOTA_BYTES = longPreferencesKey("daily_quota_bytes")
        val KEY_COLOR_SKIN = stringPreferencesKey("color_skin")
        val KEY_ICON_BG = intPreferencesKey("icon_bg_color")
        val KEY_ICON_FG = intPreferencesKey("icon_fg_color")
        val KEY_ICON_TEXT_SCALE = floatPreferencesKey("icon_text_scale")
        val KEY_AUTO_HIDE_IDLE = booleanPreferencesKey("auto_hide_idle")
        val KEY_DAILY_HISTORY = stringPreferencesKey("daily_history")
        val KEY_ICON_UNIT_STYLE = stringPreferencesKey("icon_unit_style")
        val KEY_ICON_BORDER_COLOR = intPreferencesKey("icon_border_color")
        val KEY_ICON_BORDER_WIDTH = intPreferencesKey("icon_border_width")
        val KEY_DUAL_BLOCKED = booleanPreferencesKey("dual_icons_blocked")
        val KEY_FLOAT_CHIP = booleanPreferencesKey("floating_chip")
        val KEY_HIDE_ICON_BUBBLE = booleanPreferencesKey("hide_icon_when_bubble")
        val KEY_FLOAT_FREE = booleanPreferencesKey("bubble_free_placement")
        val KEY_FLOAT_PAD = floatPreferencesKey("floating_chip_pad_scale")
        val KEY_BUBBLE_BOLD = booleanPreferencesKey("bubble_bold")
        val KEY_BUBBLE_FONT = stringPreferencesKey("bubble_font")
        val KEY_BUBBLE_TRACKING = floatPreferencesKey("bubble_tracking")
        val KEY_BUBBLE_FX = stringPreferencesKey("bubble_fx")
        val KEY_BUBBLE_LOTTIE = stringPreferencesKey("bubble_lottie_uri")
        val KEY_BUBBLE_FX_PLACE = stringPreferencesKey("bubble_fx_placement")
        val KEY_BUBBLE_LOCK = booleanPreferencesKey("bubble_lock_size")
        val KEY_BUBBLE_BOX_W = intPreferencesKey("bubble_box_w")
        val KEY_BUBBLE_BOX_H = intPreferencesKey("bubble_box_h")
        val KEY_FLOAT_SCALE = floatPreferencesKey("floating_chip_scale")
        val KEY_FLOAT_X = intPreferencesKey("floating_chip_x")
        val KEY_FLOAT_Y = intPreferencesKey("floating_chip_y")
        val KEY_USAGE_DAY = longPreferencesKey("usage_epoch_day")
        val KEY_USAGE_BYTES = longPreferencesKey("usage_total_bytes")
        val KEY_USAGE_PEAK = longPreferencesKey("usage_peak_bps")
        val KEY_LIFETIME_BYTES = longPreferencesKey("lifetime_total_bytes")
    }
}
