package no.dervis.webbrowser.domain

/**
 * When to open the browser after a run configuration starts.
 *
 * [storageId] is the stable key persisted in settings (decoupled from the enum
 * name so renames are safe); [displayName] is what the settings UI shows.
 */
enum class ReadinessMode(val storageId: String, val displayName: String) {
    WHEN_REACHABLE("REACHABLE", "When the URL is reachable (recommended)"),
    AFTER_DELAY("DELAY", "After a fixed delay"),
    ON_LAUNCH("ONLAUNCH", "Immediately on launch"),
    ;

    companion object {
        val DEFAULT: ReadinessMode = WHEN_REACHABLE

        fun fromStorageId(id: String): ReadinessMode =
            entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}
