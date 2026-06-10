package no.dervis.webbrowser.domain

/**
 * Heuristic "what dev server is in this project?" detector. Pure over a
 * [ProjectShape] description — the IntelliJ-side adapter walks the project
 * VFS to produce the shape, then this object decides what to suggest. That
 * split lets the detection rules be tested without any IDE fixture.
 *
 * The output is a list of [Suggestion]s in confidence order: the highest-
 * confidence guess goes first. The Configurable's "Detect from project"
 * button picks the top entry and pre-fills the per-project home URL, watch
 * patterns, and any obvious run-config name.
 */
object DevServerDetector {

    /**
     * What the IntelliJ-side adapter discovered about the project. Only the
     * facts the detector needs — file presence, parsed package.json scripts,
     * the project's absolute base path.
     */
    data class ProjectShape(
        val basePath: String,
        val files: Set<String>,
        /** `package.json` scripts, lowercased keys → command strings (verbatim). */
        val packageJsonScripts: Map<String, String> = emptyMap(),
    )

    data class Suggestion(
        val label: String,
        val homeUrl: String,
        val watchPatterns: List<String>,
        val runConfigHint: String?,
    ) {
        /** Glob patterns joined newline-separated for the watch-patterns textarea. */
        fun watchPatternsText(): String = watchPatterns.joinToString("\n")
    }

    /**
     * Run every detector in turn. Detectors are checked in increasing
     * specificity order (Storybook → Next → Vite → generic npm script) so the
     * most specific match ends up at the top.
     */
    fun detect(shape: ProjectShape): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        detectStorybook(shape)?.let(out::add)
        detectNext(shape)?.let(out::add)
        detectVite(shape)?.let(out::add)
        detectGenericNpmScript(shape)?.let(out::add)
        return out
    }

    private fun detectStorybook(shape: ProjectShape): Suggestion? {
        if (shape.files.none { it.startsWith(".storybook/") || it == ".storybook" }) return null
        return Suggestion(
            label = "Storybook (.storybook/)",
            homeUrl = "http://localhost:6006",
            watchPatterns = pathsOf(
                shape, "src", "stories",
                exts = "{ts,tsx,js,jsx,mdx,css,scss}",
                extraRoots = listOf(".storybook"),
            ),
            runConfigHint = pickScript(shape, listOf("storybook", "dev:storybook")),
        )
    }

    private fun detectNext(shape: ProjectShape): Suggestion? {
        if (shape.files.none { it.startsWith("next.config.") }) return null
        return Suggestion(
            label = "Next.js (next.config.*)",
            homeUrl = "http://localhost:3000",
            watchPatterns = pathsOf(
                shape, "pages", "app", "src", "components",
                exts = "{ts,tsx,js,jsx,css,scss,mdx}",
            ),
            runConfigHint = pickScript(shape, listOf("dev", "start")),
        )
    }

    private fun detectVite(shape: ProjectShape): Suggestion? {
        val hasViteConfig = shape.files.any { it.startsWith("vite.config.") }
        if (!hasViteConfig) return null
        // Default Vite port is 5173; we can't know overrides without parsing
        // the config, so we go with the default and let the user adjust.
        return Suggestion(
            label = "Vite (vite.config.*)",
            homeUrl = "http://localhost:5173",
            watchPatterns = pathsOf(
                shape, "src",
                exts = "{ts,tsx,js,jsx,vue,svelte,astro,css,scss,html}",
            ),
            runConfigHint = pickScript(shape, listOf("dev", "start")),
        )
    }

    private fun detectGenericNpmScript(shape: ProjectShape): Suggestion? {
        // Last-resort: a package.json with a `dev` / `start` / `serve` script.
        // We can't infer the port; assume the application default.
        val script = pickScript(shape, listOf("dev", "start", "serve")) ?: return null
        return Suggestion(
            label = "package.json (\"$script\")",
            homeUrl = "http://localhost:3000",
            watchPatterns = pathsOf(
                shape, "src",
                exts = "{ts,tsx,js,jsx,css,scss,html}",
            ),
            runConfigHint = script,
        )
    }

    /** First matching key in [shape.packageJsonScripts] (case-insensitive on the key). */
    private fun pickScript(shape: ProjectShape, candidates: List<String>): String? =
        candidates.firstOrNull { it in shape.packageJsonScripts }

    /**
     * Build absolute glob patterns under `shape.basePath`. Each entry in
     * [roots] (and [extraRoots]) becomes a deep-recursive pattern with the
     * extension union [exts]. Only roots that actually exist in
     * `shape.files` are included.
     */
    private fun pathsOf(
        shape: ProjectShape,
        vararg roots: String,
        exts: String,
        extraRoots: List<String> = emptyList(),
    ): List<String> {
        val base = shape.basePath.trimEnd('/')
        val combined = roots.toList() + extraRoots
        val present = combined.filter { root ->
            shape.files.any { it == root || it.startsWith("$root/") }
        }
        val out = LinkedHashSet<String>()
        present.forEach { root ->
            // Each present root contributes a deep-recursive pattern. We use a
            // helper to avoid spelling out the doubled-star sequence inline,
            // which keeps this comment block safe to write.
            out += glob(base, root, exts)
        }
        return out.toList()
    }

    private fun glob(base: String, root: String, exts: String): String {
        // Glob: <base>/<root>/<doubled-star>/<single-star>.<exts>
        val s = "*"
        val ss = s + s
        return "$base/$root/$ss/$s.$exts"
    }
}
