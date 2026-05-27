package no.dervis.webbrowser.execution

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings

/**
 * Fires for every run configuration that starts (run or debug, any type). Delegates
 * the decision to the pure [OpenOnRunPolicy][no.dervis.webbrowser.domain.OpenOnRunPolicy]
 * and only acts on a resolved request.
 */
class WebBrowserExecutionListener : ExecutionListener {

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val project = env.project
        val settings = WebBrowserProjectSettings.getInstance(project)

        settings.openOnRunPolicy()
            .resolve(
                startedConfigName = env.runProfile.name,
                requestedUrl = settings.openUrl,
                fallbackUrl = WebBrowserSettings.getInstance().homeUrl,
            )
            .onRight { request -> WebBrowserLauncher.scheduleOpen(project, request, handler) }
    }
}
