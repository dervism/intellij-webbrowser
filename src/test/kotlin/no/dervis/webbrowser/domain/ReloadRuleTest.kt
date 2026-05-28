package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReloadRuleTest {

    // ---- matches ----

    @Test
    fun `matches returns false when rootPath is blank`() {
        val rule = ReloadRule("", setOf("ts"))
        assertFalse(rule.matches("/anything/file.ts"))
    }

    @Test
    fun `matches returns false when path is outside the rootPath`() {
        val rule = ReloadRule("/project/src", setOf("ts"))
        assertFalse(rule.matches("/other/file.ts"))
    }

    @Test
    fun `matches returns true for any file when extensions is empty (watch-all)`() {
        val rule = ReloadRule("/project", emptySet())
        assertTrue(rule.matches("/project/file.bin"))
        assertTrue(rule.matches("/project/no-extension"))
    }

    @Test
    fun `matches checks extension case-insensitively`() {
        val rule = ReloadRule("/project", setOf("ts"))
        assertTrue(rule.matches("/project/foo.TS"))
        assertTrue(rule.matches("/project/foo.Ts"))
        assertTrue(rule.matches("/project/foo.ts"))
    }

    @Test
    fun `matches rejects unwatched extensions`() {
        val rule = ReloadRule("/project", setOf("ts", "css"))
        assertFalse(rule.matches("/project/foo.js"))
        assertFalse(rule.matches("/project/foo.bin"))
    }

    @Test
    fun `matches rejects files without an extension when extensions are configured`() {
        val rule = ReloadRule("/project", setOf("ts"))
        assertFalse(rule.matches("/project/Makefile"))
    }

    @Test
    fun `matches handles nested paths within the root`() {
        val rule = ReloadRule("/project", setOf("ts"))
        assertTrue(rule.matches("/project/src/deep/nested/foo.ts"))
    }

    // ---- parseExtensions ----

    @Test
    fun `parseExtensions splits on commas`() {
        assertEquals(setOf("html", "css", "js"), ReloadRule.parseExtensions("html,css,js"))
    }

    @Test
    fun `parseExtensions splits on spaces`() {
        assertEquals(setOf("html", "css"), ReloadRule.parseExtensions("html css"))
    }

    @Test
    fun `parseExtensions splits on semicolons`() {
        assertEquals(setOf("html", "css"), ReloadRule.parseExtensions("html;css"))
    }

    @Test
    fun `parseExtensions accepts mixed separators`() {
        assertEquals(setOf("html", "css", "js", "ts"), ReloadRule.parseExtensions("html, css; js  ts"))
    }

    @Test
    fun `parseExtensions strips a leading dot`() {
        assertEquals(setOf("html"), ReloadRule.parseExtensions(".html"))
        assertEquals(setOf("html", "css"), ReloadRule.parseExtensions(".html, .css"))
    }

    @Test
    fun `parseExtensions lowercases the entries`() {
        assertEquals(setOf("html"), ReloadRule.parseExtensions("HTML"))
        assertEquals(setOf("tsx", "vue"), ReloadRule.parseExtensions("TSX, Vue"))
    }

    @Test
    fun `parseExtensions returns empty set for blank or separator-only input`() {
        assertEquals(emptySet(), ReloadRule.parseExtensions(""))
        assertEquals(emptySet(), ReloadRule.parseExtensions("   "))
        assertEquals(emptySet(), ReloadRule.parseExtensions(",,,,"))
    }

    // ---- of (factory) ----

    @Test
    fun `of builds a rule with parsed extensions and given root`() {
        val rule = ReloadRule.of("/p", "html, css")
        assertEquals("/p", rule.rootPath)
        assertEquals(setOf("html", "css"), rule.extensions)
    }
}
