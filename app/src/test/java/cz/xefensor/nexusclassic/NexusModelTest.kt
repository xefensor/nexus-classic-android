// SPDX-License-Identifier: Apache-2.0
package cz.xefensor.nexusclassic

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class NexusModelTest {
    @Test fun originalAmbientRules() {
        val scene = NexusModel(960f, 1066.6666f, 42)
        assertEquals(20, scene.pulses.count { it.active })
        for (pulse in scene.pulses.take(20)) {
            val speed = hypot(pulse.dx, pulse.dy)
            assertTrue(speed in 140f..260f)
            assertTrue(pulse.dx == 0f || pulse.dy == 0f)
            assertTrue(pulse.start >= 0.0 && pulse.start < 2.0)
            assertTrue(pulse.color in 0..3)
        }
    }
    @Test fun tapSnapsAndSendsFourDifferentColorsAtOriginalSpeed() {
        val scene = NexusModel(960f, 1066f, 1)
        assertEquals(4, scene.tap(115f, 439f, 0.0))
        val burst = scene.pulses.drop(20).filter { it.active }
        assertEquals(4, burst.map { it.color }.toSet().size)
        for (p in burst) {
            assertEquals(119f, p.x, .001f)
            assertEquals(443f, p.y, .001f)
            assertEquals(300f, hypot(p.dx, p.dy), .001f)
        }
        assertEquals(listOf(300f, -300f, 0f, 0f), burst.map { it.dx })
        assertEquals(listOf(0f, 0f, -300f, 300f), burst.map { it.dy })
    }
    @Test fun fullPoolPreservesEarlierBurstsAndReusesOnlyExpiredSlots() {
        val scene = NexusModel(960f, 1066f, 2)
        repeat(10) { assertEquals(4, scene.tap(240f, 500f, 0.0)) }
        val first = scene.pulses[20]
        val originalX = first.x
        assertEquals(0, scene.tap(600f, 700f, .1))
        assertEquals(originalX, first.x, 0f)
        assertEquals(40, scene.pulses.drop(20).count { it.active })
        scene.advance(10.0)
        assertEquals(0, scene.pulses.drop(20).count { it.active })
        assertEquals(20, scene.pulses.take(20).count { it.active })
        assertEquals(4, scene.tap(40f, 80f, 10.0))
    }
    @Test fun trailIsNotRecycledUntilItsTailHasExited() {
        val scene = NexusModel(960f, 1066f, 3)
        scene.tap(240f, 500f, 0.0)
        val p = scene.pulses[20]
        val tailExit = (960f - p.x + 560f - 7f) / 300f
        scene.advance(tailExit - .01)
        assertTrue(p.active)
        scene.advance(tailExit + .01)
        assertFalse(p.active)
    }
    @Test fun speedIsIndependentOfFrameRate() {
        val a = NexusModel(960f, 1066f, 6)
        val b = NexusModel(960f, 1066f, 6)
        a.tap(240f, 500f, 0.0); b.tap(240f, 500f, 0.0)
        repeat(60) { a.advance((it + 1) / 60.0) }
        repeat(30) { b.advance((it + 1) / 30.0) }
        assertEquals(a.pulses[20].headX(1.0), b.pulses[20].headX(1.0), 0f)
    }
    @Test fun pixel8aUsesSquareTilesAndOriginalWidthProportions() {
        val g = SceneGeometry(1080, 2400)
        assertEquals(2.25f, g.scale, 0f)
        assertEquals(480f, g.viewWidth, .001f)
        assertEquals(1066.6666f, g.viewHeight, .001f)
        assertEquals(960f, g.width, .001f)
        assertEquals(480f, g.worldX(540f, .5f), .001f)
        assertEquals(240f, g.worldX(540f, 0f), .001f)
        assertEquals(720f, g.worldX(540f, 1f), .001f)
        val landscape = SceneGeometry(2400, 1080)
        assertEquals(g.scale, landscape.scale, 0f)
        assertEquals(0f, landscape.offset(1f), 0f)
    }
    @Test fun hiddenTimeDoesNotAdvanceAnimation() {
        val clock = AnimationClock()
        assertEquals(0.0, clock.frame(1_000_000_000), 0.0)
        assertEquals(1.0, clock.frame(2_000_000_000), 0.0)
        clock.pause()
        assertEquals(1.0, clock.frame(99_000_000_000), 0.0)
        assertEquals(2.0, clock.frame(100_000_000_000), 0.0)
    }
}
