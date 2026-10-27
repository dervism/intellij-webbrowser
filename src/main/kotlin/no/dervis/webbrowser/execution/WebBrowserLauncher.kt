package no.dervis.webbrowser.execution

import arrow.core.Either
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.AppExecutorUtil
import no.dervis.webbrowser.WebBrowserController
import no.dervis.webbrowser.domain.LaunchSchedule
import no.dervis.webbrowser.domain.Url
import org.jetbrains.ide.BuiltInServerManager
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Carries out a [LaunchSchedule]: waits as the schedule says, then hands off to
 * [WebBrowserController]. The only effectful piece in the flow.
 */
object WebBrowserLauncher {

    private val log = thisLogger()

    private const val POLL_INTERVAL_MS = 500L
    private const val CONNECT_TIMEOUT_MS = 400
    private const val TAIL_CHECK_MILLIS = 250L
    private const val JCEF_DEBUG_PORT_KEY = "ide.browser.jcef.debug.port"
    private val BUILT_IN_SERVER_PORTS = 63342..63361
    private val WATCHED = Key.create<Boolean>("no.dervis.webbrowser.devServerUrlWatch")

    fun scheduleOpen(project: Project, schedule: LaunchSchedule, handler: ProcessHandler) {
        when (schedule) {
            is LaunchSchedule.Immediate ->
                openLater(project, schedule.url)

            is LaunchSchedule.Delayed ->
                AppExecutorUtil.getAppScheduledExecutorService().schedule(
                    { openLater(project, schedule.url) },
                    schedule.delaySeconds.toLong(),
                    TimeUnit.SECONDS,
                )

            is LaunchSchedule.WhenReachable ->
                pollThenOpen(project, schedule.url, schedule.timeoutSeconds, handler)

            is LaunchSchedule.WhenAnnounced ->
                watchConsoleThenOpen(project, schedule.timeoutSeconds, handler)
        }
    }

    /** Poll the URL's host:port until it accepts a connection, then open. */
    private fun pollThenOpen(project: Project, url: Url, timeoutSeconds: Int, handler: ProcessHandler) {
        val host = url.host
        if (host == null) {
            log.warn("No host parsable from ${url.redacted} — opening immediately")
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
                    log.info("$host:$port is reachable — opening it")
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
     * Read the run console until the dev server prints a loopback URL, then open
     * it. A URL on an unfinished last line counts once its stream has gone quiet.
     * The watch ends on the first URL, when the process ends, when the scan's
     * output budget runs out, or after [timeoutSeconds]. It is registered under
     * the project's [WebBrowserController], so closing the project ends it too.
     * Only `host:port` is logged; console lines never are.
     *
     * Both `processStarting` and `processStarted` can ask for a watch on the same
     * process; only the first one starts it.
     */
    private fun watchConsoleThenOpen(project: Project, timeoutSeconds: Int, handler: ProcessHandler) {
        if (!handler.replace(WATCHED, null, true)) return
        val watch = Disposer.newDisposable("Web Browser dev-server URL watch")
        if (!Disposer.tryRegister(WebBrowserController.getInstance(project), watch)) return

        val listener = DevServerUrlListener(excludedPorts()) { found ->
            Disposer.dispose(watch)
            if (found != null) {
                log.info("Dev server printed ${found.authority} — opening it")
                openLater(project, found.url)
            } else {
                log.info("No dev-server URL found in the run console — not opening")
            }
        }
        // The listener can finish, and dispose the watch, on a reader thread as soon
        // as it is added. So its removal is registered first, and the timers are
        // cancelled through cancelWith in case the watch has already ended.
        Disposer.register(watch, Disposable { handler.removeProcessListener(listener) })
        handler.addProcessListener(listener)

        val scheduler = AppExecutorUtil.getAppScheduledExecutorService()
        cancelWith(watch, scheduler.schedule({ listener.stop() }, timeoutSeconds.toLong(), TimeUnit.SECONDS))
        // A server can print its URL without a line end; look for that a few times a second.
        cancelWith(
            watch,
            scheduler.scheduleWithFixedDelay(
                { listener.checkQuietTails() },
                TAIL_CHECK_MILLIS,
                TAIL_CHECK_MILLIS,
                TimeUnit.MILLISECONDS,
            ),
        )

        if (handler.isProcessTerminated) listener.stop()
    }

    /** Cancels [task] when [watch] ends, or right away if it already has. */
    private fun cancelWith(watch: Disposable, task: Future<*>) {
        if (!Disposer.tryRegister(watch, Disposable { task.cancel(false) })) task.cancel(false)
    }

    /**
     * Local ports a printed URL may never point at: the ports IntelliJ's built-in
     * web server uses (in this IDE and any other running JetBrains IDE), a custom
     * built-in server port, and the JCEF remote-debugging port when it is set.
     */
    private fun excludedPorts(): Set<Int> =
        BUILT_IN_SERVER_PORTS.toSet() + setOfNotNull(
            BuiltInServerManager.getInstance().port,
            Registry.intValue(JCEF_DEBUG_PORT_KEY, -1).takeIf { it > 0 },
        )

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

    /**
     * Open-on-run fires while you may be typing elsewhere, so the panel is shown
     * without taking keyboard focus.
     */
    private fun openLater(project: Project, url: Url) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) WebBrowserController.getInstance(project).open(url, focus = false)
        }
    }
}
