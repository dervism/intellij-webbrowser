package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Path matching is delegated to Java NIO's default `FileSystem` PathMatcher, so
 * these tests rely on the build platform being Unix-flavoured (the macOS / Linux
 * dev + CI environment for this project). Patterns themselves are
 * platform-neutral — globs always use `/` per the NIO spec.
 */
class WatchGlobsTest {

    @Test
    fun `EMPTY matches nothing`() {
        assertTrue(WatchGlobs.EMPTY.isEmpty)
        assertFalse(WatchGlobs.EMPTY.matches("/project/src/foo.ts"))
    }

    @Test
    fun `blank input parses to EMPTY`() {
        assertTrue(WatchGlobs.parse("").isEmpty)
        assertTrue(WatchGlobs.parse("   \n  \n").isEmpty)
    }

    @Test
    fun `parse drops blank lines`() {
        val w = WatchGlobs.parse(
            """
            /project/src/**/*.ts


            /project/public/**/*.css
            """.trimIndent(),
        )
        assertEquals(2, w.size)
    }

    @Test
    fun `single pattern matches direct hit`() {
        val w = WatchGlobs.parse("/project/src/*.ts")
        assertTrue(w.matches("/project/src/foo.ts"))
        assertFalse(w.matches("/project/src/foo.tsx"))
    }

    @Test
    fun `double-star matches any depth`() {
        val w = WatchGlobs.parse("/project/src/**/*.ts")
        assertTrue(w.matches("/project/src/foo.ts"))
        assertTrue(w.matches("/project/src/deep/nested/foo.ts"))
        assertFalse(w.matches("/project/other/foo.ts"))
    }

    @Test
    fun `brace alternation matches either extension`() {
        val w = WatchGlobs.parse("/project/src/**/*.{ts,tsx}")
        assertTrue(w.matches("/project/src/foo.ts"))
        assertTrue(w.matches("/project/src/foo.tsx"))
        assertFalse(w.matches("/project/src/foo.js"))
    }

    @Test
    fun `multiple patterns OR-match`() {
        val w = WatchGlobs.parse(
            """
            /project/src/**/*.ts
            /project/public/**/*.css
            """.trimIndent(),
        )
        assertTrue(w.matches("/project/src/foo.ts"))
        assertTrue(w.matches("/project/public/styles.css"))
        assertFalse(w.matches("/project/src/foo.css"))
    }
}
