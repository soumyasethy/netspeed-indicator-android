package com.netspeed.indicator

import com.netspeed.indicator.data.HeroTheme
import com.netspeed.indicator.render.scenes.SceneRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroThemeTest {

    @Test
    fun storageKeysUnique_andRoundtrip() {
        val keys = HeroTheme.entries.map { it.storageKey }
        assertEquals(keys.size, keys.toSet().size)
        for (t in HeroTheme.entries) assertEquals(t, HeroTheme.fromKey(t.storageKey))
        assertEquals(HeroTheme.DEFAULT, HeroTheme.fromKey("nope"))
        assertEquals(HeroTheme.DEFAULT, HeroTheme.fromKey(null))
    }

    @Test
    fun freeTierOrdinalsStable() {
        // Premium gating is ordinal-based: the first three themes are the free
        // tier and must NEVER move. Scene themes are append-only.
        assertEquals(0, HeroTheme.KINETIC.ordinal)
        assertEquals(1, HeroTheme.TIER_FLOW.ordinal)
        assertEquals(2, HeroTheme.LIQUID.ordinal)
    }

    @Test
    fun everySceneHasAThemeAndViceVersa() {
        assertEquals(11, SceneRegistry.ALL.size)
        for (e in SceneRegistry.ALL) {
            val theme = HeroTheme.entries.firstOrNull {
                it.storageKey == SceneRegistry.THEME_PREFIX + e.id
            }
            assertNotNull("missing HeroTheme for scene ${e.id}", theme)
            assertTrue(theme!!.isScene)
            assertEquals(e.id, SceneRegistry.fromThemeKey(theme.storageKey)?.id)
        }
        val sceneThemes = HeroTheme.entries.filter { it.isScene }
        assertEquals(SceneRegistry.ALL.size, sceneThemes.size)
    }
}
