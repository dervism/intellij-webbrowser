package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DevServerDetectorTest {

    private fun shape(
        basePath: String = "/p",
        vararg files: String,
        scripts: Map<String, String> = emptyMap(),
    ) = DevServerDetector.ProjectShape(basePath, files.toSet(), scripts)

    @Test
    fun `nothing matches an empty project`() {
        assertTrue(DevServerDetector.detect(shape()).isEmpty())
    }

    @Test
    fun `storybook directory yields the storybook suggestion at the top`() {
        val out = DevServerDetector.detect(
            shape("/p", ".storybook/main.ts", "src/Button.tsx", scripts = mapOf("storybook" to "start-storybook")),
        )
        assertTrue(out.isNotEmpty())
        assertEquals("Storybook (.storybook/)", out.first().label)
        assertEquals("http://localhost:6006", out.first().homeUrl)
        assertEquals("storybook", out.first().runConfigHint)
    }

    @Test
    fun `vite config drives the vite suggestion`() {
        val out = DevServerDetector.detect(
            shape("/p", "vite.config.ts", "src/main.ts", scripts = mapOf("dev" to "vite")),
        )
        assertEquals("Vite (vite.config.*)", out.first().label)
        assertEquals("http://localhost:5173", out.first().homeUrl)
        assertEquals("dev", out.first().runConfigHint)
        assertTrue(out.first().watchPatterns.any { it.contains("/src/") })
    }

    @Test
    fun `next config drives the next-js suggestion`() {
        val out = DevServerDetector.detect(
            shape("/p", "next.config.js", "pages/index.tsx", scripts = mapOf("dev" to "next dev")),
        )
        assertEquals("Next.js (next.config.*)", out.first().label)
        assertEquals("http://localhost:3000", out.first().homeUrl)
    }

    @Test
    fun `generic dev script is the last-resort fallback`() {
        val out = DevServerDetector.detect(
            shape("/p", "package.json", "src/main.js", scripts = mapOf("dev" to "node server.js")),
        )
        assertEquals(1, out.size)
        assertEquals("package.json (\"dev\")", out.first().label)
    }

    @Test
    fun `more specific match shadows the generic dev script`() {
        // A project with both Vite config and a dev script should report Vite
        // first; the generic fallback still appears but ranks lower.
        val out = DevServerDetector.detect(
            shape("/p", "vite.config.ts", "src/main.ts", scripts = mapOf("dev" to "vite")),
        )
        assertEquals("Vite (vite.config.*)", out.first().label)
        // The generic fallback also matches the "dev" script — verify it's behind.
        assertTrue(out.any { it.label.startsWith("package.json") })
    }

    @Test
    fun `watchPatternsText joins entries with newlines`() {
        val suggestion = DevServerDetector.Suggestion(
            "test", "http://x", listOf("a", "b", "c"), null,
        )
        assertEquals("a\nb\nc", suggestion.watchPatternsText())
    }

    @Test
    fun `only present source roots end up in the pattern list`() {
        // Vite project where only `src` exists — no `pages` / `app` / `components`.
        val out = DevServerDetector.detect(
            shape("/p", "vite.config.ts", "src/x.ts"),
        )
        val patterns = out.first().watchPatterns
        assertTrue(patterns.all { it.contains("/src/") })
    }
}
