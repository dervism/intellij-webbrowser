package no.dervis.webbrowser.domain

/**
 * Pure validation over a [WebBrowserSettingsSnapshot] — used by the settings
 * Configurable's `apply()` to refuse obviously broken input rather than
 * silently round-tripping it to disk. Kept as a value-producing object (no
 * exceptions) so individual rules can be tested without the IntelliJ
 * platform fixture; the Configurable adapts the result into a
 * `ConfigurationException`.
 */
object SettingsValidation {

    /** A single problem the user needs to fix; [field] identifies the input. */
    data class Issue(val field: Field, val message: String)

    /** Which form input owns each issue — useful for focusing the field on apply failure. */
    enum class Field {
        HOME_URL,
        PROJECT_HOME_URL,
        RUN_CONFIG,
        OPEN_URL,
        WATCH_PATTERNS,
    }

    /**
     * Run every rule against [s] and return the consolidated list of issues.
     * An empty list means "ok to save".
     */
    fun validate(s: WebBrowserSettingsSnapshot): List<Issue> = buildList {
        addIfNotNull(checkUrl(s.homeUrl, Field.HOME_URL, required = true))
        addIfNotNull(checkUrl(s.projectHomeUrl, Field.PROJECT_HOME_URL, required = false))
        addIfNotNull(checkUrl(s.openUrl, Field.OPEN_URL, required = false))
        addIfNotNull(checkWatchPatterns(s.watchPatterns))
        // Run-config rule: if "open on run" is enabled and the user picked
        // a specific configuration, that name must be non-blank. Picking
        // "(Any configuration)" sets runConfigName to "" so we only flag
        // the wedged case where openOnRun is on but the form lost the name.
        if (s.openOnRun && s.runConfigName.isNotEmpty() && s.runConfigName.isBlank()) {
            add(Issue(Field.RUN_CONFIG, "Run configuration name can't be whitespace."))
        }
    }

    private fun checkUrl(value: String, field: Field, required: Boolean): Issue? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return if (required) Issue(field, "URL can't be empty.") else null
        }
        // Url.web adds http:// to a bare host, so "localhost:3000" is accepted
        // here as it is in the address bar. Any other scheme is refused.
        if (Url.web(trimmed).isNone()) {
            return Issue(
                field,
                if (Url.hasNonWebScheme(trimmed)) "Use an http:// or https:// URL."
                else "Not a valid URL: remove any spaces and keep it under ${Url.MAX_LENGTH} characters.",
            )
        }
        return null
    }

    private fun checkWatchPatterns(text: String): Issue? {
        if (text.isBlank()) return null
        // Anything non-blank that compiles to at least one valid matcher is
        // fine. If every line is unparseable we surface that — typical cause
        // is a stray backslash or unclosed bracket.
        val globs = WatchGlobs.parse(text)
        return if (globs.isEmpty)
            Issue(Field.WATCH_PATTERNS, "No watch patterns compiled — check for typos.")
        else null
    }

    private fun MutableList<Issue>.addIfNotNull(issue: Issue?) {
        if (issue != null) add(issue)
    }
}
