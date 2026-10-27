package no.dervis.webbrowser.execution

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import no.dervis.webbrowser.domain.ConsoleStream
import no.dervis.webbrowser.domain.DevServerUrl
import no.dervis.webbrowser.domain.DevServerUrlScan

/**
 * Feeds one run's stdout and stderr to a [DevServerUrlScan] and reports the
 * outcome to [onDone] exactly once: the URL when the scan finds one, or null when
 * the scan gives up, the process ends, or [stop] is called. The IDE's own system
 * messages (such as the echoed command line) are skipped.
 *
 * Output arrives on the process reader threads, one per stream. Each chunk is fed
 * to the scan under a lock, so the two streams never race on the scan and only
 * one thread can end the watch. The work per chunk is bounded by the scan's line
 * and output limits.
 */
internal class DevServerUrlListener(
    excludedPorts: Set<Int>,
    private val onDone: (DevServerUrl?) -> Unit,
) : ProcessListener {

    /** The scan in progress, or null once the watch has ended. */
    private var scan: DevServerUrlScan? = DevServerUrlScan.start(excludedPorts)

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        val stream = when {
            ProcessOutputType.isStdout(outputType) -> ConsoleStream.STDOUT
            ProcessOutputType.isStderr(outputType) -> ConsoleStream.STDERR
            else -> return
        }
        val step = synchronized(this) {
            scan?.feed(stream, event.text)?.also { scan = (it as? DevServerUrlScan.Step.Continue)?.scan }
        }
        when (step) {
            is DevServerUrlScan.Step.Found -> onDone(step.url)
            DevServerUrlScan.Step.Exhausted -> onDone(null)
            is DevServerUrlScan.Step.Continue, null -> Unit
        }
    }

    override fun processTerminated(event: ProcessEvent) = stop()

    /** Ends the watch without a URL. Does nothing if it has already ended. */
    fun stop() {
        val wasRunning = synchronized(this) { scan.also { scan = null } } != null
        if (wasRunning) onDone(null)
    }
}
