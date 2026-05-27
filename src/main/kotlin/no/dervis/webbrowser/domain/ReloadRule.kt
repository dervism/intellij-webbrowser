package no.dervis.webbrowser.domain

/**
 * Decides whether a saved file should trigger a browser reload: the file must live
 * under [rootPath] and — unless [extensions] is empty (meaning "any file") — match
 * one of the watched extensions. Pure value object: no I/O, no framework types.
 */
data class ReloadRule(
    val rootPath: String,
    val extensions: Set<String>,
) {
    fun matches(savedPath: String): Boolean = when {
        rootPath.isEmpty() -> false // no scope to watch
        !savedPath.startsWith(rootPath) -> false
        extensions.isEmpty() -> true
        else -> savedPath.fileExtension() in extensions
    }

    companion object {
        fun of(rootPath: String, extensionsCsv: String): ReloadRule =
            ReloadRule(rootPath, parseExtensions(extensionsCsv))

        /** Split a comma/space/semicolon list into a normalized, lowercase extension set. */
        fun parseExtensions(csv: String): Set<String> =
            csv.split(',', ' ', ';')
                .map { it.trim().removePrefix(".").lowercase() }
                .filter(String::isNotEmpty)
                .toSet()

        private fun String.fileExtension(): String =
            substringAfterLast('.', "").lowercase()
    }
}
