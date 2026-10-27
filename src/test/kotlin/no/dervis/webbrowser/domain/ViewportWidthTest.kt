package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewportWidthTest {

    private fun parsed(text: String): Int? = ViewportWidth.parse(text).getOrNull()?.pixels

    @Test
    fun `parse accepts a width in range`() {
        assertEquals(375, parsed("375"))
        assertEquals(375, parsed(" 375 "))
        assertEquals(240, parsed("240"))
        assertEquals(3840, parsed("3840"))
    }

    @Test
    fun `parse rejects widths outside 240 to 3840`() {
        listOf("239", "3841", "0", "99999").forEach { assertNull(parsed(it), "must reject $it") }
    }

    @Test
    fun `parse rejects anything but ASCII digits`() {
        listOf("", " ", "-1", "+375", "1e3", "37 5", "375px", "375.5", "３７５").forEach {
            assertNull(parsed(it), "must reject '$it'")
        }
    }

    @Test
    fun `parse rejects digit strings too long to be a width`() {
        assertNull(parsed("99999999999"))
        assertNull(parsed("000375"))
    }

    @Test
    fun `of clamps stored values into range`() {
        assertEquals(240, ViewportWidth.of(0).pixels)
        assertEquals(240, ViewportWidth.of(-5).pixels)
        assertEquals(3840, ViewportWidth.of(Int.MAX_VALUE).pixels)
        assertEquals(500, ViewportWidth.of(500).pixels)
    }
}
