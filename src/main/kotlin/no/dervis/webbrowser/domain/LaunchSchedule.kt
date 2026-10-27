package no.dervis.webbrowser.domain

/**
 * How and when to open the browser after a run configuration starts, as decided
 * by [OpenOnRunPolicy.resolve]. A pure sealed type so the launcher's dispatch is
 * exhaustive. Each case carries a URL only when it needs one.
 */
sealed interface LaunchSchedule {

    /** Open the browser immediately on the EDT. */
    data class Immediate(val url: Url) : LaunchSchedule

    /** Open after [delaySeconds] from now. */
    data class Delayed(val url: Url, val delaySeconds: Int) : LaunchSchedule

    /** Poll the URL's host:port until reachable, then open. Give up after [timeoutSeconds]. */
    data class WhenReachable(val url: Url, val timeoutSeconds: Int) : LaunchSchedule

    /**
     * Read the run console and open the first [DevServerUrl] the server prints.
     * Give up after [timeoutSeconds]. No configured URL is involved.
     */
    data class WhenAnnounced(val timeoutSeconds: Int) : LaunchSchedule
}
