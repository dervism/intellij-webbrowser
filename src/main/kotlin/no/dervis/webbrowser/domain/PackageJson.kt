package no.dervis.webbrowser.domain

/**
 * Minimal, dependency-free reader for the `scripts` block of a `package.json`.
 * Dev-server detection only needs the script *names* (to match `dev` / `start`
 * / `storybook`), so a surface-level regex over the scripts object is enough —
 * pulling in a full JSON parser for one lookup would be overkill.
 *
 * Tolerant by design: anything it can't make sense of yields an empty map
 * rather than throwing, so a malformed `package.json` never breaks detection.
 * Keys are lowercased to match case-insensitively.
 */
object PackageJson {

    private val SCRIPTS_BLOCK = Regex(""""scripts"\s*:\s*\{([^}]*)}""", RegexOption.DOT_MATCHES_ALL)
    private val KEY_VALUE = Regex(""""([^"\\]+)"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""")

    /** Parse the `scripts` object into a `name → command` map (names lowercased). */
    fun parseScripts(json: String): Map<String, String> {
        val block = SCRIPTS_BLOCK.find(json)?.groupValues?.getOrNull(1) ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        KEY_VALUE.findAll(block).forEach { out[it.groupValues[1].lowercase()] = it.groupValues[2] }
        return out
    }
}
