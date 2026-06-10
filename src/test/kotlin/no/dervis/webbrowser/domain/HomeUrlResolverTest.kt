package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
