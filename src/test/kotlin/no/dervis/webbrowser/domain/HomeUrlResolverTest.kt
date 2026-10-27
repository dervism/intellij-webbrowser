package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeUrlResolverTest {

    @Test
    fun `project override wins when set`() {
        assertEquals(
            "http://localhost:5173",
            HomeUrlResolver.effective("http://localhost:5173", "http://localhost:3000"),
        )
    }

    @Test
    fun `blank project falls back to the application default`() {
        assertEquals(
            "http://localhost:3000",
            HomeUrlResolver.effective("", "http://localhost:3000"),
        )
    }

    @Test
    fun `whitespace-only project value is treated as blank`() {
        assertEquals(
            "http://localhost:3000",
            HomeUrlResolver.effective("   ", "http://localhost:3000"),
        )
    }

    @Test
    fun `project override is trimmed before being returned`() {
        assertEquals(
            "http://localhost:5173",
            HomeUrlResolver.effective("  http://localhost:5173  ", "http://localhost:3000"),
        )
    }

    @Test
    fun `both blank returns the application default verbatim`() {
        assertEquals("", HomeUrlResolver.effective("", ""))
    }

    @Test
    fun `web gives the http URL for a bare-host home`() {
        assertEquals("http://localhost:3000", HomeUrlResolver.web("", "localhost:3000").getOrNull()?.value)
    }

    @Test
    fun `web prefers the project home`() {
        assertEquals("http://p.example", HomeUrlResolver.web("http://p.example", "http://a.example").getOrNull()?.value)
    }

    @Test
    fun `web refuses a non-web project home from the shared file`() {
        assertTrue(HomeUrlResolver.web("file://attacker.example/share/x.html", "http://localhost:3000").isNone())
    }

    @Test
    fun `web of two blank homes is nothing`() {
        assertTrue(HomeUrlResolver.web("", "").isNone())
    }
}
