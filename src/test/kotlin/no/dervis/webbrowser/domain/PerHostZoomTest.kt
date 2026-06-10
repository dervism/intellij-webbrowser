package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerHostZoomTest {

    @Test
    fun `lookup returns DEFAULT for unknown host`() {
        assertEquals(ZoomLevel.DEFAULT, PerHostZoom.lookup(emptyMap(), "example.com"))
    }

    @Test
    fun `lookup returns DEFAULT for null host`() {
        assertEquals(ZoomLevel.DEFAULT, PerHostZoom.lookup(mapOf("example.com" to 1.0), null))
    }

    @Test
    fun `lookup returns DEFAULT for blank host`() {
        assertEquals(ZoomLevel.DEFAULT, PerHostZoom.lookup(mapOf("example.com" to 1.0), "   "))
    }

    @Test
    fun `lookup finds an entry case-insensitively`() {
        val map = mapOf("example.com" to 2.0)
        assertEquals(2.0, PerHostZoom.lookup(map, "Example.COM"))
    }

    @Test
    fun `lookup treats www-prefixed and bare hosts the same`() {
        val map = mapOf("example.com" to 1.5)
        assertEquals(1.5, PerHostZoom.lookup(map, "www.example.com"))
    }

    @Test
    fun `remember stores a non-default level under the normalized host`() {
        val map = PerHostZoom.remember(emptyMap(), "WWW.Example.COM", 2.0)
        assertEquals(mapOf("example.com" to 2.0), map)
    }

    @Test
    fun `remember replaces an existing entry`() {
        val map = PerHostZoom.remember(mapOf("example.com" to 1.0), "example.com", 3.0)
        assertEquals(mapOf("example.com" to 3.0), map)
    }

    @Test
    fun `remember removes the host when the level is DEFAULT`() {
        val map = PerHostZoom.remember(mapOf("example.com" to 2.0), "example.com", ZoomLevel.DEFAULT)
        assertTrue(map.isEmpty())
    }

    @Test
    fun `remember is a no-op when host is null or blank`() {
        val src = mapOf("example.com" to 1.0)
        assertEquals(src, PerHostZoom.remember(src, null, 2.0))
        assertEquals(src, PerHostZoom.remember(src, "  ", 2.0))
    }

    @Test
    fun `default value is not persisted on an empty map`() {
        val map = PerHostZoom.remember(emptyMap(), "example.com", ZoomLevel.DEFAULT)
        assertFalse(map.containsKey("example.com"))
    }
}
