package no.dervis.webbrowser.domain

/**
 * Picks the "home / dev-server" URL to use for a given project: the project-level
 * override when set, otherwise the application-wide default. Pure function over
 * strings so it's trivial to test and reuse from the Home button, the
 * placeholder, and the open-on-run policy.
 */
object HomeUrlResolver {

    /**
     * @param projectHomeUrl the per-project override (may be blank to mean "use default")
     * @param applicationHomeUrl the application-wide fallback
     * @return whichever URL the user effectively wants for this project, with the
     *         application URL serving as the absolute last resort when both inputs
     *         are blank.
     */
    fun effective(projectHomeUrl: String, applicationHomeUrl: String): String =
        projectHomeUrl.trim().ifBlank { applicationHomeUrl }
}
