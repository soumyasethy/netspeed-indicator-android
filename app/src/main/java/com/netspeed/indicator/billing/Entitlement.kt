package com.netspeed.indicator.billing

/**
 * Play Billing product identifiers. These must match the in-app products created
 * in Play Console exactly.
 *
 * - [SUITE_UNLOCK]: a one-time **non-consumable** "lifetime suite unlock" — the
 *   single SKU that flips every premium feature on. Early-bird Rs.29, later Rs.49
 *   (the price is set/scheduled in Play Console, not in code).
 * - [TIP_SMALL]: an optional **consumable** Rs.5 "tip" — buyable repeatedly, grants
 *   nothing functional; pure goodwill.
 */
object ProductIds {
    const val SUITE_UNLOCK = "suite_unlock"
    const val TIP_SMALL = "tip_small"

    /** Non-consumable SKUs queried as one-time products. */
    val ONE_TIME = listOf(SUITE_UNLOCK, TIP_SMALL)
}

/**
 * What the user has unlocked. Pure value type derived from owned purchases — no
 * Android or Billing types — so the gating rule is trivially unit-testable and can
 * later move into a shared `:core` module unchanged.
 */
data class Entitlement(val suiteUnlocked: Boolean) {
    companion object {
        val LOCKED = Entitlement(suiteUnlocked = false)

        /**
         * Derives entitlement from the set of product IDs that are both OWNED and
         * ACKNOWLEDGED. The suite unlock is the only SKU that grants features; the
         * tip never unlocks anything.
         */
        fun from(ownedAndAcknowledged: Set<String>): Entitlement =
            Entitlement(suiteUnlocked = ProductIds.SUITE_UNLOCK in ownedAndAcknowledged)
    }
}

/**
 * The feature gate. One place that decides, given an [Entitlement], whether a given
 * premium surface is available — so the free/paid split lives in a single tested
 * function instead of scattered `if` checks across the UI.
 *
 * Free (top-of-funnel core): the status-bar speed icon and all its styles/units/
 * basic colours, the notification panel, usage history, and the first few themes/
 * skins. Paid (suite unlock): the floating bubble, home-screen widgets, the full
 * theme/skin catalogue, and the custom colour picker.
 */
object FeatureGate {
    /** Themes available for free, by enum ordinal (the rest need the unlock). */
    const val FREE_THEME_COUNT = 3

    /** Skins available for free, by enum ordinal (the rest need the unlock). */
    const val FREE_SKIN_COUNT = 2

    /**
     * Kill-switch: while false (the shipped default), every gate is open and the
     * paywall never shows — the billing code stays dormant until the Play Console
     * products exist. Defaults to [com.netspeed.indicator.BuildConfig.PAYWALL_ENABLED];
     * mutable so unit tests can exercise both modes.
     */
    var gatingActive: Boolean = com.netspeed.indicator.BuildConfig.PAYWALL_ENABLED

    fun floatingBubble(e: Entitlement) = !gatingActive || e.suiteUnlocked
    fun widgets(e: Entitlement) = !gatingActive || e.suiteUnlocked
    fun customColorPicker(e: Entitlement) = !gatingActive || e.suiteUnlocked

    /** Theme at [ordinal] is allowed if free-tier or the suite is unlocked. */
    fun themeAllowed(ordinal: Int, e: Entitlement) =
        !gatingActive || e.suiteUnlocked || ordinal < FREE_THEME_COUNT

    /** Skin at [ordinal] is allowed if free-tier or the suite is unlocked. */
    fun skinAllowed(ordinal: Int, e: Entitlement) =
        !gatingActive || e.suiteUnlocked || ordinal < FREE_SKIN_COUNT
}
