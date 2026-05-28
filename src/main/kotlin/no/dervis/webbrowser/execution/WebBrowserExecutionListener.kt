package no.dervis.webbrowser.execution

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.thisLogger
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings

/**
 * Fires for every run configuration that starts (run or debug, any type). Delegates
 * the decision to the pure [OpenOnRunPolicy][no.dervis.webbrowser.domain.OpenOnRunPolicy]
 * and only acts on a resolved request.
 */
class WebBrowserExecutionListener : ExecutionListener {

    private val log = thisLogger()

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val project = env.project
        val settings = WebBrowserProjectSettings.getInstance(project)
        val configName = env.runProfile.name

        log.info("processStarted runProfile='$configName' executor='$executorId'")

        settings.openOnRunPolicy()
            .resolve(
                startedConfigName = configName,
                requestedUrl = settings.openUrl,
                fallbackUrl = WebBrowserSettings.getInstance().homeUrl,
            )
            .fold(
                ifLeft = { skip -> log.info("Not opening browser for '$configName': $skip") },
                ifRight = { request ->
                    log.info(
                        "Scheduling browser open for '$configName' → ${request.url}, " +
                            "${request.readiness} (wait ${request.waitSeconds}s)",
                    )
                    WebBrowserLauncher.scheduleOpen(project, request, handler)
                },
            )
    }
}
