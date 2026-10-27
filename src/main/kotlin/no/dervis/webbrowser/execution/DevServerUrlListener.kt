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
 * A server can print its URL without a line end. [checkQuietTails] runs on a timer;
 * a stream's unfinished last line is read once that many checks in a row
 * ([QUIET_CHECKS]) have seen no new output from it, and then not again until it
 * changes. Counting checks, not elapsed time, means one late check after an IDE
 * stall can't read a line whose rest is about to arrive. A URL that arrives in
 * quick pieces is not read half-way; a process that stops mid-URL for longer than
 * the quiet period could still be read early.
 *
 * Output arrives on the process reader threads, one per stream, and the timer runs
 * on its own thread. Each chunk and each check runs under a lock, so they never race
 * on the scan and only one thread can end the watch. The work per chunk is bounded
 * by the scan's line and output limits.
 */
internal class DevServerUrlListener(
    excludedPorts: Set<Int>,
    private val onDone: (DevServerUrl?) -> Unit,
) : ProcessListener {

    /** The scan in progress, or null once the watch has ended. */
    private var scan: DevServerUrlScan? = DevServerUrlScan.start(excludedPorts)

    /** Checks in a row that each stream has gone without new output. */
    private val quietChecks = mutableMapOf<ConsoleStream, Int>()

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        val stream = when {
            ProcessOutputType.isStdout(outputType) -> ConsoleStream.STDOUT
            ProcessOutputType.isStderr(outputType) -> ConsoleStream.STDERR
            else -> return
        }
        val step = synchronized(this) {
            quietChecks[stream] = 0
            scan?.feed(stream, event.text)?.also { scan = (it as? DevServerUrlScan.Step.Continue)?.scan }
        }
        when (step) {
            is DevServerUrlScan.Step.Found -> onDone(step.url)
            DevServerUrlScan.Step.Exhausted -> onDone(null)
            is DevServerUrlScan.Step.Continue, null -> Unit
        }
    }

    /** One timer tick: counts a quiet check per stream and reads a tail that has just become quiet. */
    fun checkQuietTails() {
        val found = synchronized(this) {
            val current = scan ?: return
            quietChecks.replaceAll { _, checks -> checks + 1 }
            quietChecks
                .filterValues { it == QUIET_CHECKS }
                .keys
                .firstNotNullOfOrNull(current::scanTail)
                ?.also { scan = null }
        }
        if (found != null) onDone(found)
    }

    override fun processTerminated(event: ProcessEvent) = stop()

    /** Ends the watch without a URL. Does nothing if it has already ended. */
    fun stop() {
        val wasRunning = synchronized(this) { scan.also { scan = null } } != null
        if (wasRunning) onDone(null)
    }

    companion object {
        /**
         * Checks in a row without new output before a stream's unfinished line is read.
         * With checks every 250 ms that is at least two full quiet intervals, 500 ms.
         */
        const val QUIET_CHECKS = 3
    }
}
