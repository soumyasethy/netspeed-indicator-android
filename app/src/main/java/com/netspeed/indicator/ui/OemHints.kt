package com.netspeed.indicator.ui

import android.os.Build

/**
 * Manufacturer-specific guidance. Several OEMs — especially common on devices
 * sold in India — kill foreground services aggressively unless the app is
 * granted "autostart"/"protected app" status, something Android has no standard
 * API to request. We detect the maker and surface the exact menu path.
 */
data class OemGuidance(val vendor: String, val steps: List<String>)

object OemHints {

    fun forCurrentDevice(): OemGuidance? = forManufacturer(Build.MANUFACTURER)

    /**
     * Where the "show notification icons in the status bar" / "dot vs icons"
     * toggle lives per OEM. This is a global SystemUI display setting; no app can
     * override it, so the best we can do is point the user at it precisely.
     */
    fun statusBarIconStepsForCurrentDevice(): List<String> =
        when (Build.MANUFACTURER?.lowercase()?.trim()) {
            "samsung" -> listOf(
                "Settings → Notifications → Status bar.",
                "Set \"Show notification icons\" to \"All notifications\" (not a dot/number).",
            )
            "xiaomi", "redmi", "poco" -> listOf(
                "Settings → Notifications & Control Center → Status bar.",
                "Enable \"Show notification icons\".",
            )
            "oneplus", "oppo", "realme" -> listOf(
                "Settings → Notifications & status bar → Status bar.",
                "Enable showing notification icons (not just a count/dot).",
            )
            "vivo", "iqoo" -> listOf(
                "Settings → Status bar & notifications.",
                "Enable showing notification icons in the status bar.",
            )
            else -> listOf(
                "Open your phone's Status bar / Notifications settings.",
                "Choose to show notification icons (not a dot or count).",
            )
        }

    fun forManufacturer(raw: String?): OemGuidance? {
        return when (raw?.lowercase()?.trim()) {
            "xiaomi", "redmi", "poco" -> OemGuidance(
                vendor = "Xiaomi / Redmi / POCO (MIUI/HyperOS)",
                steps = listOf(
                    "Open Security app → Permissions → Autostart, enable NetSpeed Indicator.",
                    "Settings → Apps → NetSpeed Indicator → Battery saver → No restrictions.",
                    "In Recents, pull the app card down and tap the lock icon to keep it.",
                ),
            )
            "oppo", "realme" -> OemGuidance(
                vendor = "Oppo / Realme (ColorOS/Realme UI)",
                steps = listOf(
                    "Settings → Battery → App Battery Management → NetSpeed Indicator → allow background + auto-launch.",
                    "Settings → Apps → Startup Manager → enable NetSpeed Indicator.",
                    "Lock the app in Recents.",
                ),
            )
            "vivo", "iqoo" -> OemGuidance(
                vendor = "Vivo / iQOO (Funtouch/OriginOS)",
                steps = listOf(
                    "Settings → Battery → Background power consumption management → allow NetSpeed Indicator.",
                    "Settings → More settings → Permission manager → Autostart → enable NetSpeed Indicator.",
                    "Lock the app in Recents.",
                ),
            )
            "samsung" -> OemGuidance(
                vendor = "Samsung (One UI)",
                steps = listOf(
                    "Settings → Battery → Background usage limits → Never sleeping apps → add NetSpeed Indicator.",
                    "Settings → Apps → NetSpeed Indicator → Battery → Unrestricted.",
                    "Turn off \"Put unused apps to sleep\" in Battery → Background usage limits.",
                ),
            )
            "huawei", "honor" -> OemGuidance(
                vendor = "Huawei / Honor (EMUI/MagicOS)",
                steps = listOf(
                    "Settings → Apps → NetSpeed Indicator → Battery → set Launch to Manage manually, enable all three.",
                    "Phone Manager → Protected apps → enable NetSpeed Indicator.",
                ),
            )
            "oneplus" -> OemGuidance(
                vendor = "OnePlus (OxygenOS)",
                steps = listOf(
                    "Settings → Battery → Battery optimization → NetSpeed Indicator → Don't optimize.",
                    "Settings → Apps → NetSpeed Indicator → Battery → Allow background activity.",
                    "Disable \"Advanced optimization\" / \"Deep optimization\" if present.",
                ),
            )
            else -> null
        }
    }
}
