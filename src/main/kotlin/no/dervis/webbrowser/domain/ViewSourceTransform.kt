package no.dervis.webbrowser.domain

/**
 * Produces the `view-source:<url>` navigation target for the "View Page Source"
 * context-menu action. Returns `null` when the input is blank or already a
 * view-source URL — so repeated clicks don't accumulate `view-source:` prefixes.
 */
object ViewSourceTransform {

    private const val PREFIX = "view-source:"

    fun targetFor(currentUrl: String): String? =
        if (currentUrl.isBlank() || currentUrl.startsWith(PREFIX)) null
        else "$PREFIX$currentUrl"
}
