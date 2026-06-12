package com.netspeed.indicator.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementTest {

    @Test fun locked_when_no_purchases() {
        assertFalse(Entitlement.from(emptySet()).suiteUnlocked)
        assertFalse(Entitlement.LOCKED.suiteUnlocked)
    }

    @Test fun unlocked_when_suite_owned_and_acked() {
        assertTrue(Entitlement.from(setOf(ProductIds.SUITE_UNLOCK)).suiteUnlocked)
    }

    @Test fun tip_alone_does_not_unlock() {
        assertFalse(Entitlement.from(setOf(ProductIds.TIP_SMALL)).suiteUnlocked)
    }

    @Test fun suite_plus_tip_unlocks() {
        val e = Entitlement.from(setOf(ProductIds.SUITE_UNLOCK, ProductIds.TIP_SMALL))
        assertTrue(e.suiteUnlocked)
    }

    @Test fun gate_blocks_premium_when_locked() {
        val e = Entitlement.LOCKED
        assertFalse(FeatureGate.floatingBubble(e))
        assertFalse(FeatureGate.widgets(e))
        assertFalse(FeatureGate.customColorPicker(e))
    }

    @Test fun gate_allows_everything_when_unlocked() {
        val e = Entitlement(suiteUnlocked = true)
        assertTrue(FeatureGate.floatingBubble(e))
        assertTrue(FeatureGate.widgets(e))
        assertTrue(FeatureGate.customColorPicker(e))
        assertTrue(FeatureGate.themeAllowed(13, e))
        assertTrue(FeatureGate.skinAllowed(5, e))
    }

    @Test fun free_tier_themes_and_skins_are_open_when_locked() {
        val e = Entitlement.LOCKED
        // First N free.
        assertTrue(FeatureGate.themeAllowed(0, e))
        assertTrue(FeatureGate.themeAllowed(FeatureGate.FREE_THEME_COUNT - 1, e))
        assertTrue(FeatureGate.skinAllowed(0, e))
        assertTrue(FeatureGate.skinAllowed(FeatureGate.FREE_SKIN_COUNT - 1, e))
        // Beyond the free band: locked.
        assertFalse(FeatureGate.themeAllowed(FeatureGate.FREE_THEME_COUNT, e))
        assertFalse(FeatureGate.skinAllowed(FeatureGate.FREE_SKIN_COUNT, e))
    }
}
