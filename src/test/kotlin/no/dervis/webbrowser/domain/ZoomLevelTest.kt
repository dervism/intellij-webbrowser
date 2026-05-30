package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomLevelTest {

    @Test
    fun `DEFAULT is zero (100 percent)`() {
        assertEquals(0.0, ZoomLevel.DEFAULT, 0.0)
    }

    @Test
    fun `next applies delta to current`() {
        assertEquals(1.0, ZoomLevel.next(0.0, 1.0), 1e-9)
        assertEquals(-1.0, ZoomLevel.next(0.0, -1.0), 1e-9)
        assertEquals(2.5, ZoomLevel.next(1.0, 1.5), 1e-9)
    }

    @Test
    fun `next clamps below MIN`() {
        assertEquals(ZoomLevel.MIN, ZoomLevel.next(-4.0, -10.0), 1e-9)
        assertEquals(ZoomLevel.MIN, ZoomLevel.next(ZoomLevel.MIN, -1.0), 1e-9)
    }

    @Test
    fun `next clamps above MAX`() {
        assertEquals(ZoomLevel.MAX, ZoomLevel.next(4.0, 10.0), 1e-9)
        assertEquals(ZoomLevel.MAX, ZoomLevel.next(ZoomLevel.MAX, 1.0), 1e-9)
    }

    @Test
    fun `zoomedIn adds exactly one STEP`() {
        assertEquals(ZoomLevel.STEP, ZoomLevel.zoomedIn(0.0), 1e-9)
        assertEquals(2.0, ZoomLevel.zoomedIn(1.0), 1e-9)
    }

    @Test
    fun `zoomedOut subtracts exactly one STEP`() {
        assertEquals(-ZoomLevel.STEP, ZoomLevel.zoomedOut(0.0), 1e-9)
        assertEquals(0.0, ZoomLevel.zoomedOut(1.0), 1e-9)
    }

    @Test
    fun `repeated zoomedIn from DEFAULT eventually reaches MAX`() {
        var level = ZoomLevel.DEFAULT
        repeat(20) { level = ZoomLevel.zoomedIn(level) }
        assertEquals(ZoomLevel.MAX, level, 1e-9)
    }

    @Test
    fun `repeated zoomedOut from DEFAULT eventually reaches MIN`() {
        var level = ZoomLevel.DEFAULT
        repeat(20) { level = ZoomLevel.zoomedOut(level) }
        assertEquals(ZoomLevel.MIN, level, 1e-9)
    }

    @Test
    fun `clamp range covers the documented practical bounds`() {
        // Roughly 40% (MIN) up to 250% (MAX) — sanity check the constants.
        assertEquals(-5.0, ZoomLevel.MIN, 0.0)
        assertEquals(5.0, ZoomLevel.MAX, 0.0)
        assertEquals(1.0, ZoomLevel.STEP, 0.0)
    }
}
