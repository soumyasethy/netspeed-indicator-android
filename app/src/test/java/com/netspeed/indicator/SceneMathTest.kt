package com.netspeed.indicator

import com.netspeed.indicator.core.SpeedTiers
import com.netspeed.indicator.render.scenes.SceneRegistry
import com.netspeed.indicator.render.scenes.SceneRng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneMathTest {

    @Test
    fun norm_clampsToCeiling() {
        assertEquals(0f, SpeedTiers.norm(0f), 1e-6f)
        assertEquals(0.5f, SpeedTiers.norm(24f), 1e-6f)
        assertEquals(1f, SpeedTiers.norm(48f), 1e-6f)
        assertEquals(1f, SpeedTiers.norm(500f), 1e-6f)
    }

    @Test
    fun tierFrac_exactAtBounds() {
        // Tier 0 spans 0..1 MB/s.
        assertEquals(0f, SpeedTiers.tierFrac(0f), 1e-4f)
        assertEquals(0.5f, SpeedTiers.tierFrac(0.5f), 1e-4f)
        // At exactly 1 MB/s we are IN tier 1 (rawIndex uses >=), frac restarts at 0.
        assertEquals(0f, SpeedTiers.tierFrac(1f), 1e-4f)
        // Tier 2 spans 5..15: 10 MB/s = halfway.
        assertEquals(0.5f, SpeedTiers.tierFrac(10f), 1e-4f)
        // Top tier spans 30..48 (ceiling); 39 = halfway; beyond ceiling clamps.
        assertEquals(0.5f, SpeedTiers.tierFrac(39f), 1e-4f)
        assertEquals(1f, SpeedTiers.tierFrac(60f), 1e-4f)
    }

    @Test
    fun tierFrac_respectsCustomThresholds() {
        val custom = floatArrayOf(2f, 4f, 8f, 16f)
        // Tier 1 spans 2..4: 3 MB/s = halfway.
        assertEquals(0.5f, SpeedTiers.tierFrac(3f, custom), 1e-4f)
        // Top tier spans 16..48: 32 = halfway.
        assertEquals(0.5f, SpeedTiers.tierFrac(32f, custom), 1e-4f)
    }

    @Test
    fun tierProgress_endpointsAndMonotonic() {
        assertEquals(0f, SpeedTiers.tierProgress(0f), 1e-4f)
        assertEquals(0f, SpeedTiers.tierProgress(0.5f), 1e-4f)
        assertEquals(4f, SpeedTiers.tierProgress(39f), 1e-4f)
        assertEquals(4f, SpeedTiers.tierProgress(100f), 1e-4f)
        // Center of tier 2 (10 MB/s) sits at exactly 2.0.
        assertEquals(2f, SpeedTiers.tierProgress(10f), 1e-4f)
        var prev = -1f
        var v = 0f
        while (v <= 48f) {
            val p = SpeedTiers.tierProgress(v)
            assertTrue("monotonic at $v", p >= prev)
            prev = p
            v += 0.25f
        }
    }

    @Test
    fun sceneRng_deterministicAndBounded() {
        val a = SceneRng(7)
        val b = SceneRng(7)
        repeat(100) {
            val x = a.next()
            assertEquals(x, b.next(), 0f)
            assertTrue(x >= 0f && x < 1f)
        }
        a.reset(7)
        val c = SceneRng(7)
        repeat(20) { assertEquals(c.next(), a.next(), 0f) }
    }

    @Test
    fun registry_idsUniqueAndDisjointFromLegacyFxKeys() {
        val ids = SceneRegistry.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (legacy in listOf("none", "flame", "glow", "sparks", "lottie")) {
            assertFalse(SceneRegistry.isScene(legacy))
        }
        for (e in SceneRegistry.ALL) {
            assertTrue(SceneRegistry.isScene(e.id))
            assertNotNull(SceneRegistry.fromThemeKey(SceneRegistry.THEME_PREFIX + e.id))
            assertEquals(e.id, SceneRegistry.fromThemeKey("scene_" + e.id)?.id)
        }
        assertNull(SceneRegistry.fromThemeKey("kinetic"))
        assertNull(SceneRegistry.fromThemeKey("scene_nope"))
    }
}
