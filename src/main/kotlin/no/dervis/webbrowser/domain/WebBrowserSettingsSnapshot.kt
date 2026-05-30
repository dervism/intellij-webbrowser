package no.dervis.webbrowser.domain

/**
 * Immutable, IntelliJ-free value object that mirrors every field exposed by the
 * Settings UI. The configurable uses it to collapse three concerns into trivial
 * equality / projection operations:
 *
 *  - `isModified` → compare current form snapshot to stored snapshot
 *  - `apply`      → write the current form snapshot back to the services
 *  - `reset`      → paint the stored snapshot into the form
 *
 * Pure data — no IntelliJ types referenced — so it can be unit-tested without
 * the platform fixture and used safely off the EDT.
 */
data class WebBrowserSettingsSnapshot(
    val homeUrl: String,
    val watchExtensions: String,
    val watchPath: String,
    val openOnRun: Boolean,
    val runConfigName: String,
    val openUrl: String,
    val readiness: ReadinessMode,
    val readinessSeconds: Int,
)
