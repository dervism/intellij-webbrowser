package no.dervis.webbrowser.execution

import arrow.core.Either
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import no.dervis.webbrowser.WebBrowserController
import no.dervis.webbrowser.domain.OpenRequest
import no.dervis.webbrowser.domain.ReadinessMode
import no.dervis.webbrowser.domain.Url
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Carries out an [OpenRequest]: decides *when* to open (per the readiness mode),
 * then hands off to [WebBrowserController]. The only effectful piece in the flow.
 */
object WebBrowserLauncher {

    private const val POLL_INTERVAL_MS = 500L
    private const val CONNECT_TIMEOUT_MS = 400

    fun scheduleOpen(project: Project, request: OpenRequest, handler: ProcessHandler) {
        when (request.readiness) {
            ReadinessMode.ON_LAUNCH ->
                openLater(project, request.url)

            ReadinessMode.AFTER_DELAY ->
                AppExecutorUtil.getAppScheduledExecutorService().schedule(
                    { openLater(project, request.url) },
                    request.waitSeconds.toLong(),
                    TimeUnit.SECONDS,
                )

            ReadinessMode.WHEN_REACHABLE ->
                pollThenOpen(project, request.url, request.waitSeconds, handler)
        }
    }

    /** Poll the URL's host:port until it accepts a connection, then open. */
    private fun pollThenOpen(project: Project, url: Url, timeoutSeconds: Int, handler: ProcessHandler) {
        val host = url.host ?: return openLater(project, url)
        val port = url.port
        ApplicationManager.getApplication().executeOnPooledThread {
            val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
            while (System.currentTimeMillis() < deadline) {
                if (project.isDisposed || handler.isProcessTerminated) return@executeOnPooledThread
                if (canConnect(host, port)) {
                    openLater(project, url)
                    return@executeOnPooledThread
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    return@executeOnPooledThread
                }
            }
        }
    }

    private fun canConnect(host: String, port: Int): Boolean =
        Either.catch {
            Socket().use { it.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS) }
        }.isRight()

    private fun openLater(project: Project, url: Url) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) WebBrowserController.getInstance(project).open(url)
        }
    }
}
