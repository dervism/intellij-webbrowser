package no.dervis.webbrowser.domain

import arrow.core.Option

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

    /**
     * The effective home URL, but only as an http or https [Url]. The project home
     * comes from a shared project file, so anything that loads it, opens it
     * elsewhere or puts it in the address bar goes through here.
     */
    fun web(projectHomeUrl: String, applicationHomeUrl: String): Option<Url> =
        Url.web(effective(projectHomeUrl, applicationHomeUrl))
}
