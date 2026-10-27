package no.dervis.webbrowser.execution

import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.util.Key
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Plain JUnit tests for the listener's "report exactly once" contract. They need
 * no IDE application, so they run even while the platform test fixture can't start.
 */
class DevServerUrlListenerTest {

    private val handler = NopProcessHandler()
    private val reported = mutableListOf<String?>()
    private val listener = DevServerUrlListener(emptySet()) { reported += it?.url?.value }

    private fun emit(text: String, type: Key<*> = ProcessOutputType.STDOUT) =
        listener.onTextAvailable(ProcessEvent(handler, text), type)

    @Test
    fun `reports the first URL once and ignores later output`() {
        emit("Local: http://localhost:5173/\n")
        emit("Local: http://localhost:4000/\n")
        assertEquals(listOf<String?>("http://localhost:5173/"), reported)
    }

    @Test
    fun `does not report again when stopped after a URL was found`() {
        emit("http://localhost:5173/\n")
        listener.stop()
        listener.processTerminated(ProcessEvent(handler, 0))
        assertEquals(listOf<String?>("http://localhost:5173/"), reported)
    }

    @Test
    fun `reports no URL exactly once when stopped twice`() {
        listener.stop()
        listener.stop()
        assertEquals(listOf<String?>(null), reported)
    }

    @Test
    fun `reports no URL when the process ends first and ignores later output`() {
        listener.processTerminated(ProcessEvent(handler, 0))
        emit("http://localhost:5173/\n")
        assertEquals(listOf<String?>(null), reported)
    }

    @Test
    fun `ignores the IDE's system output`() {
        emit("python3 -m http.server http://localhost:1/\n", ProcessOutputType.SYSTEM)
        emit("Serving on http://localhost:8000/\n")
        assertEquals(listOf<String?>("http://localhost:8000/"), reported)
    }

    @Test
    fun `reads colour-split stdout segments as one stream`() {
        val cyan = ProcessOutputType("\u001B[36m", ProcessOutputType.STDOUT)
        emit("Local: http://localhost:")
        emit("5173", cyan)
        emit("/\n")
        assertEquals(listOf<String?>("http://localhost:5173/"), reported)
    }

    @Test
    fun `reads stderr`() {
        emit("Listening on http://127.0.0.1:3000\n", ProcessOutputType.STDERR)
        assertEquals(listOf<String?>("http://127.0.0.1:3000/"), reported)
    }
}
