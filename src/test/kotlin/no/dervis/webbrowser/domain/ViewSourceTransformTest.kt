package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewSourceTransformTest {

    @Test
    fun `blank input returns null`() {
        assertNull(ViewSourceTransform.targetFor(""))
        assertNull(ViewSourceTransform.targetFor("   "))
    }

    @Test
    fun `already viewing source returns null (no double-prefixing)`() {
        assertNull(ViewSourceTransform.targetFor("view-source:http://example.com"))
        assertNull(ViewSourceTransform.targetFor("view-source:https://example.com/path"))
    }

    @Test
    fun `http URL gets prefixed`() {
        assertEquals(
            "view-source:http://example.com",
            ViewSourceTransform.targetFor("http://example.com"),
        )
    }

    @Test
    fun `https URL gets prefixed`() {
        assertEquals(
            "view-source:https://example.com/x?q=1",
            ViewSourceTransform.targetFor("https://example.com/x?q=1"),
        )
    }

    @Test
    fun `arbitrary URL gets prefixed (no scheme restriction)`() {
        assertEquals(
            "view-source:data:text/html,Hi",
            ViewSourceTransform.targetFor("data:text/html,Hi"),
        )
    }
}
