package no.dervis.webbrowser.domain

import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Paths

/**
 * Power-user alternative to [ReloadRule]'s folder + extension list: an explicit
 * set of glob patterns, one per line. Saving a file triggers reload when any
 * pattern matches its absolute path.
 *
 * Patterns use [Java NIO's `glob:` syntax](https://docs.oracle.com/javase/tutorial/essential/io/find.html)
 * — a doubled star matches any number of path segments, a single star matches
 * anything within one segment, and `{ts,tsx}` matches either alternative.
 *
 * Unparseable patterns are silently dropped so a single typo doesn't disable
 * the whole feature; [parse] is the single place that compiles them.
 */
class WatchGlobs private constructor(
    private val matchers: List<PathMatcher>,
    // The number of user-supplied source patterns, not the (potentially larger)
    // expanded matcher count. Driven by tests / UI summaries.
    val size: Int,
) {

    val isEmpty: Boolean get() = matchers.isEmpty()

    fun matches(savedPath: String): Boolean {
        if (matchers.isEmpty()) return false
        val path = runCatching { Paths.get(savedPath) }.getOrNull() ?: return false
        return matchers.any { it.safelyMatches(path) }
    }

    private fun PathMatcher.safelyMatches(path: Path): Boolean =
        runCatching { matches(path) }.getOrDefault(false)

    companion object {
        val EMPTY = WatchGlobs(emptyList(), 0)

        /**
         * Split [text] into one pattern per line and compile each. Blank lines
         * and bad globs are silently dropped. Each pattern is expanded via
         * [expandDoubleStar] so a doubled-star segment also matches the
         * zero-depth case — Java NIO's strict glob otherwise requires the
         * doubled-star to absorb at least one path segment, but every dev
         * tool the user is likely to be coming from treats it as "any number,
         * including zero".
         */
        fun parse(text: String): WatchGlobs {
            val sources = text.lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
            val matchers = sources
                .flatMap { expandDoubleStar(it) }
                .mapNotNull(::compile)
            return WatchGlobs(matchers, sources.size)
        }

        /**
         * Generate the variants the user expects from a single source pattern.
         * Each `(slash)(star)(star)(slash)` segment is also tried with the
         * doubled-star collapsed away, so a file directly under the parent
         * directory still matches (the standard glob meaning of "zero or more
         * segments here" — strict Java NIO requires at least one segment).
         */
        internal fun expandDoubleStar(pattern: String): List<String> {
            val variants = LinkedHashSet<String>()
            variants += pattern
            // Collapse each `/⁎⁎/` segment to a single `/` so the doubled-star
            // can stand in for "zero or more segments". Done left-to-right so a
            // pattern with multiple `⁎⁎`s gets every combination.
            var current = listOf(pattern)
            while (true) {
                val next = current.flatMap { p ->
                    val idx = p.indexOf("/**/")
                    if (idx < 0) listOf(p)
                    else listOf(p, p.substring(0, idx) + "/" + p.substring(idx + 4))
                }.toSet().toList()
                if (next == current) break
                current = next
            }
            variants += current
            // Patterns that start with `⁎⁎/` (no leading slash) also need a
            // zero-segment fallback.
            current.forEach { p ->
                if (p.startsWith("**/")) variants += p.removePrefix("**/")
            }
            return variants.toList()
        }

        private fun compile(pattern: String): PathMatcher? =
            runCatching { FileSystems.getDefault().getPathMatcher("glob:$pattern") }.getOrNull()
    }
}
