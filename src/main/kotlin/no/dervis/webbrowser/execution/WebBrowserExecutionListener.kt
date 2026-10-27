package no.dervis.webbrowser.execution

import arrow.core.Either
import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.thisLogger
import no.dervis.webbrowser.domain.OpenRequest
import no.dervis.webbrowser.domain.OpenRunSkip
import no.dervis.webbrowser.domain.ReadinessMode
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings

/**
 * Fires for every run configuration that starts (run or debug, any type). Delegates
 * the decision to the pure [OpenOnRunPolicy][no.dervis.webbrowser.domain.OpenOnRunPolicy]
 * and only acts on a resolved request.
 */
class WebBrowserExecutionListener : ExecutionListener {

    private val log = thisLogger()

    /**
     * The process starts writing between `processStarting` and `processStarted`,
     * so the console watch for "When the server prints its URL" is attached here,
     * before any output. The other modes wait for [processStarted].
     */
    override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        resolve(env).onRight { request ->
            if (request.readiness == ReadinessMode.WHEN_ANNOUNCED) {
                WebBrowserLauncher.scheduleOpen(env.project, request, handler)
            }
        }
    }

    /**
     * Schedules every mode. For "When the server prints its URL" this is a fallback
     * for runners that skip `processStarting`; the launcher starts one watch per process.
     */
    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val configName = env.runProfile.name

        log.info("processStarted runProfile='$configName' executor='$executorId'")

        resolve(env).fold(
            ifLeft = { skip -> log.info("Not opening browser for '$configName': $skip") },
            ifRight = { request ->
                log.info(
                    "Scheduling browser open for '$configName' → ${destination(request)}, " +
                        "${request.readiness} (wait ${request.waitSeconds}s)",
                )
                WebBrowserLauncher.scheduleOpen(env.project, request, handler)
            },
        )
    }

    private fun resolve(env: ExecutionEnvironment): Either<OpenRunSkip, OpenRequest> {
        val settings = WebBrowserProjectSettings.getInstance(env.project)
        return settings.openOnRunPolicy().resolve(
            startedConfigName = env.runProfile.name,
            requestedUrl = settings.openUrl,
            fallbackUrl = WebBrowserSettings.getInstance().homeUrl,
        )
    }

    /** The configured URL isn't used when the server's printed URL is opened instead. */
    private fun destination(request: OpenRequest): String =
        if (request.readiness == ReadinessMode.WHEN_ANNOUNCED) "the URL the server prints" else request.url.toString()
}
