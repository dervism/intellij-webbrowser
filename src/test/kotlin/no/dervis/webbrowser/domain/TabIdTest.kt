package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TabIdTest {

    @Test
    fun `two TabIds with the same value are equal`() {
        assertEquals(TabId(1), TabId(1))
        assertEquals(TabId(1).hashCode(), TabId(1).hashCode())
    }

    @Test
    fun `TabIds with different values are not equal`() {
        assertNotEquals(TabId(1), TabId(2))
    }

    @Test
    fun `toString includes the numeric value`() {
        assertEquals("tab:0", TabId(0).toString())
        assertEquals("tab:42", TabId(42).toString())
    }
}
