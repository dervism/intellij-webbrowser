package no.dervis.webbrowser.domain

/**
 * The "how to open" strategy derived from an [OpenRequest]. A pure sealed type
 * so the launcher's main dispatch (Immediate / Delayed / WhenReachable /
 * WhenAnnounced) is exhaustive, and the policy → strategy mapping can be tested
 * independently of the IntelliJ Application / process plumbing.
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
     * Give up after [timeoutSeconds]. The configured URL isn't used.
     */
    data class WhenAnnounced(val timeoutSeconds: Int) : LaunchSchedule

    companion object {
        fun from(request: OpenRequest): LaunchSchedule = when (request.readiness) {
            ReadinessMode.ON_LAUNCH -> Immediate(request.url)
            ReadinessMode.AFTER_DELAY -> Delayed(request.url, request.waitSeconds)
            ReadinessMode.WHEN_REACHABLE -> WhenReachable(request.url, request.waitSeconds)
            ReadinessMode.WHEN_ANNOUNCED -> WhenAnnounced(request.waitSeconds)
        }
    }
}
