package no.dervis.webbrowser.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

/**
 * The "open the browser when a run configuration starts" rule. Pure decision logic;
 * resolving the actual URL and performing the open are left to the caller.
 */
data class OpenOnRunPolicy(
    val enabled: Boolean,
    val runConfigName: String, // blank = any configuration
    val readiness: ReadinessMode,
    val waitSeconds: Int,
) {
    fun isTriggeredBy(startedConfigName: String): Boolean =
        enabled && (runConfigName.isBlank() || runConfigName == startedConfigName)

    /**
     * Resolve into a concrete [OpenRequest], or a [reason][OpenRunSkip] why nothing
     * should happen — combining the trigger check and URL resolution into a single
     * short-circuiting pipeline. [requestedUrl] falls back to [fallbackUrl] when blank.
     */
    fun resolve(
        startedConfigName: String,
        requestedUrl: String,
        fallbackUrl: String,
    ): Either<OpenRunSkip, OpenRequest> = either {
        ensure(isTriggeredBy(startedConfigName)) { OpenRunSkip.NotTriggered }
        val url = Url.parse(requestedUrl.ifBlank { fallbackUrl }).getOrNull()
            ?: raise(OpenRunSkip.NoValidUrl)
        OpenRequest(url, readiness, waitSeconds)
    }
}

/** A fully-resolved instruction to open the browser. */
data class OpenRequest(val url: Url, val readiness: ReadinessMode, val waitSeconds: Int)

/** Why an open-on-run trigger did not produce an [OpenRequest]. */
sealed interface OpenRunSkip {
    data object NotTriggered : OpenRunSkip
    data object NoValidUrl : OpenRunSkip
}
