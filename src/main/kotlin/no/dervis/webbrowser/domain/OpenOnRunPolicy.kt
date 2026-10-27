package no.dervis.webbrowser.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

/**
 * The "open the browser when a run configuration starts" rule. Pure decision logic;
 * carrying out the resulting [LaunchSchedule] is left to the caller.
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
     * Resolve into the [LaunchSchedule] for the readiness mode, or a [reason][OpenRunSkip]
     * why nothing should happen. [requestedUrl] falls back to [fallbackUrl] when blank,
     * and must be an http or https URL — except in [ReadinessMode.WHEN_ANNOUNCED], which
     * opens whatever the server prints and so needs no configured URL at all.
     */
    fun resolve(
        startedConfigName: String,
        requestedUrl: String,
        fallbackUrl: String,
    ): Either<OpenRunSkip, LaunchSchedule> = either {
        ensure(isTriggeredBy(startedConfigName)) { OpenRunSkip.NotTriggered }
        fun configuredUrl(): Url =
            Url.web(requestedUrl.ifBlank { fallbackUrl }).getOrNull() ?: raise(OpenRunSkip.NoValidUrl)
        when (readiness) {
            ReadinessMode.ON_LAUNCH -> LaunchSchedule.Immediate(configuredUrl())
            ReadinessMode.AFTER_DELAY -> LaunchSchedule.Delayed(configuredUrl(), waitSeconds)
            ReadinessMode.WHEN_REACHABLE -> LaunchSchedule.WhenReachable(configuredUrl(), waitSeconds)
            ReadinessMode.WHEN_ANNOUNCED -> LaunchSchedule.WhenAnnounced(waitSeconds)
        }
    }
}

/** Why an open-on-run trigger did not produce a [LaunchSchedule]. */
sealed interface OpenRunSkip {
    data object NotTriggered : OpenRunSkip
    data object NoValidUrl : OpenRunSkip
}
