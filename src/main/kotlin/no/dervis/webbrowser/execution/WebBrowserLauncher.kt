package no.dervis.webbrowser.execution

import arrow.core.Either
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import no.dervis.webbrowser.WebBrowserController
import no.dervis.webbrowser.domain.OpenRequest
import no.dervis.webbrowser.domain.ReadinessMode
import no.dervis.webbrowser.domain.Url
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Carries out an [OpenRequest]: decides *when* to open (per the readiness mode),
 * then hands off to [WebBrowserController]. The only effectful piece in the flow.
 */
object WebBrowserLauncher {

    private val log = thisLogger()

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
        val host = url.host
        if (host == null) {
            log.warn("No host parsable from $url — opening immediately")
            openLater(project, url)
            return
        }
        val port = url.port
        ApplicationManager.getApplication().executeOnPooledThread {
            log.info("Polling $host:$port for up to ${timeoutSeconds}s")
            val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L
            while (System.currentTimeMillis() < deadline) {
                if (project.isDisposed || handler.isProcessTerminated) {
                    log.info("Polling $host:$port aborted (project disposed or process terminated)")
                    return@executeOnPooledThread
                }
                if (canConnect(host, port)) {
                    log.info("$host:$port is reachable — opening $url")
                    openLater(project, url)
                    return@executeOnPooledThread
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    return@executeOnPooledThread
                }
            }
            log.info("$host:$port never became reachable within ${timeoutSeconds}s — giving up")
        }
    }

    /**
     * Try to connect to [host]:[port], walking every resolved address. This matters
     * on macOS where `localhost` can resolve to `::1` first while many dev servers
     * (Astro, Vite, …) only bind to `127.0.0.1`.
     */
    private fun canConnect(host: String, port: Int): Boolean {
        val addresses = Either.catch { InetAddress.getAllByName(host) }.getOrNull() ?: return false
        return addresses.any { addr ->
            Either.catch {
                Socket().use { it.connect(InetSocketAddress(addr, port), CONNECT_TIMEOUT_MS) }
            }.isRight()
        }
    }

    private fun openLater(project: Project, url: Url) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) WebBrowserController.getInstance(project).open(url)
        }
    }
}
