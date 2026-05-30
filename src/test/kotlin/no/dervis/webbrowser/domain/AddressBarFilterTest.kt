package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddressBarFilterTest {

    @Test
    fun `null is not displayable`() {
        assertNull(AddressBarFilter.displayable(null))
    }

    @Test
    fun `empty string is not displayable`() {
        assertNull(AddressBarFilter.displayable(""))
    }

    @Test
    fun `data URL is suppressed (used by the placeholder)`() {
        assertNull(AddressBarFilter.displayable("data:text/html,<html></html>"))
    }

    @Test
    fun `about URL is suppressed`() {
        assertNull(AddressBarFilter.displayable("about:blank"))
    }

    @Test
    fun `view-source URL is suppressed`() {
        // view-source: is not an http/https scheme, so we hide it.
        assertNull(AddressBarFilter.displayable("view-source:http://example.com"))
    }

    @Test
    fun `http URL is returned unchanged`() {
        val url = "http://example.com/path?q=1"
        assertEquals(url, AddressBarFilter.displayable(url))
    }

    @Test
    fun `https URL is returned unchanged`() {
        val url = "https://example.com:8443/x"
        assertEquals(url, AddressBarFilter.displayable(url))
    }
}
