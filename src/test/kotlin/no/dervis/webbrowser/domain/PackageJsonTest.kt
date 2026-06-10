package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageJsonTest {

    @Test
    fun `blank or empty input yields an empty map`() {
        assertTrue(PackageJson.parseScripts("").isEmpty())
        assertTrue(PackageJson.parseScripts("   ").isEmpty())
    }

    @Test
    fun `a package json with no scripts block yields an empty map`() {
        assertTrue(PackageJson.parseScripts("""{ "name": "x", "version": "1.0.0" }""").isEmpty())
    }

    @Test
    fun `parses script name to command pairs`() {
        val json = """
            {
              "name": "demo",
              "scripts": {
                "dev": "vite",
                "build": "vite build"
              }
            }
        """.trimIndent()
        assertEquals(mapOf("dev" to "vite", "build" to "vite build"), PackageJson.parseScripts(json))
    }

    @Test
    fun `lowercases script names`() {
        val json = """{ "scripts": { "Dev": "next dev", "START": "next start" } }"""
        val scripts = PackageJson.parseScripts(json)
        assertTrue("dev" in scripts)
        assertTrue("start" in scripts)
    }

    @Test
    fun `tolerates escaped quotes inside command values`() {
        val json = """{ "scripts": { "test": "echo \"hi\" && jest" } }"""
        val scripts = PackageJson.parseScripts(json)
        assertTrue("test" in scripts)
    }

    @Test
    fun `malformed input does not throw`() {
        assertTrue(PackageJson.parseScripts("""{ "scripts": { broken """).isEmpty())
    }
}
