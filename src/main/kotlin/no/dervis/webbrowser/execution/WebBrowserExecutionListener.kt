package no.dervis.webbrowser.execution

import arrow.core.Either
import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.thisLogger
import no.dervis.webbrowser.WebBrowserNotifications
import no.dervis.webbrowser.domain.LaunchSchedule
import no.dervis.webbrowser.domain.OpenRunSkip
import no.dervis.webbrowser.settings.WebBrowserProjectSettings
import no.dervis.webbrowser.settings.WebBrowserSettings

/**
 * Fires for every run configuration that starts (run or debug, any type). Delegates
 * the decision to the pure [OpenOnRunPolicy][no.dervis.webbrowser.domain.OpenOnRunPolicy]
 * and only acts on a resolved [LaunchSchedule].
 */
class WebBrowserExecutionListener : ExecutionListener {

    private val log = thisLogger()

    /**
     * The process starts writing between `processStarting` and `processStarted`,
     * so the console watch for "When the server prints its URL" is attached here,
     * before any output. The other modes wait for [processStarted].
     */
    override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        resolve(env).onRight { schedule ->
            if (schedule is LaunchSchedule.WhenAnnounced) {
                WebBrowserLauncher.scheduleOpen(env.project, schedule, handler)
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
            ifLeft = { skip ->
                log.info("Not opening browser for '$configName': $skip")
                if (skip == OpenRunSkip.NoValidUrl) notifyRejectedUrl(env)
            },
            ifRight = { schedule ->
                log.info("Scheduling browser open for '$configName': ${describe(schedule)}")
                WebBrowserLauncher.scheduleOpen(env.project, schedule, handler)
            },
        )
    }

    private fun resolve(env: ExecutionEnvironment): Either<OpenRunSkip, LaunchSchedule> {
        val settings = WebBrowserProjectSettings.getInstance(env.project)
        return settings.openOnRunPolicy().resolve(
            startedConfigName = env.runProfile.name,
            requestedUrl = settings.openUrl,
            fallbackUrl = WebBrowserSettings.getInstance().homeUrl,
        )
    }

    /** One log line's worth of what will open and when; URLs show only scheme, host and port. */
    private fun describe(schedule: LaunchSchedule): String = when (schedule) {
        is LaunchSchedule.Immediate -> "${schedule.url.redacted}, immediately"
        is LaunchSchedule.Delayed -> "${schedule.url.redacted}, after ${schedule.delaySeconds}s"
        is LaunchSchedule.WhenReachable -> "${schedule.url.redacted}, once reachable (up to ${schedule.timeoutSeconds}s)"
        is LaunchSchedule.WhenAnnounced -> "the URL the server prints (up to ${schedule.timeoutSeconds}s)"
    }

    /**
     * Open-on-run is on but its URL was refused, usually because the stored value isn't
     * http or https (for example, edited in a shared project file). Say so instead of
     * silently doing nothing. Only `processStarted` calls this, so it shows once per run.
     */
    private fun notifyRejectedUrl(env: ExecutionEnvironment) {
        WebBrowserNotifications.warn(
            env.project,
            "Open browser on run skipped",
            "The URL to open isn't an http or https URL. Check the open URL in Settings → Tools → " +
                "Web Browser Panel, or the home URL if the open URL is blank.",
        )
    }
}
