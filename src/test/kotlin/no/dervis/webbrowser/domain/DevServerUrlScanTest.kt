package no.dervis.webbrowser.domain

import no.dervis.webbrowser.domain.ConsoleStream.STDERR
import no.dervis.webbrowser.domain.ConsoleStream.STDOUT
import no.dervis.webbrowser.domain.DevServerUrlScan.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class DevServerUrlScanTest {

    private val esc = "\u001B"

    /** Feeds the chunks in order and returns the URL found, or null if the scan never found one. */
    private fun scan(vararg chunks: Pair<ConsoleStream, String>, excludedPorts: Set<Int> = emptySet()): String? {
        var current = DevServerUrlScan.start(excludedPorts)
        for ((stream, text) in chunks) {
            when (val step = current.feed(stream, text)) {
                is Step.Found -> return step.url.url.value
                is Step.Continue -> current = step.scan
                Step.Exhausted -> return null
            }
        }
        return null
    }

    private fun stdout(text: String) = STDOUT to text

    @Test
    fun `finds the Vite local URL through its colour codes`() {
        val vite = "  $esc[32m➜$esc[39m  $esc[1mLocal$esc[22m:   " +
            "$esc[36mhttp://localhost:$esc[1m5173$esc[22m/$esc[39m\n"
        assertEquals("http://localhost:5173/", scan(stdout(vite)))
    }

    @Test
    fun `finds the Next js local URL and skips the telemetry link`() {
        val next = "Attention: Next.js now collects completely anonymous telemetry. " +
            "Learn more: https://nextjs.org/telemetry\n" +
            "   ▲ Next.js 15.0.0\n" +
            "   - Local:        http://localhost:3000\n" +
            "   - Network:      http://192.168.1.5:3000\n"
        assertEquals("http://localhost:3000/", scan(stdout(next)))
    }

    @Test
    fun `finds the URL in Angular, Storybook, Astro and Uvicorn banners`() {
        assertEquals(
            "http://localhost:4200/",
            scan(stdout("** Angular Live Development Server is listening on localhost:4200, open your browser on http://localhost:4200/ **\n")),
        )
        assertEquals(
            "http://localhost:6006/",
            scan(stdout("│   Local:            http://localhost:6006/              │\n")),
        )
        assertEquals("http://localhost:4321/", scan(stdout("┃ Local    http://localhost:4321/\n")))
        assertEquals(
            "http://localhost:8000/",
            scan(stdout("INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)\n")),
        )
    }

    @Test
    fun `ignores the Node inspector and its help link`() {
        val node = "Debugger listening on ws://127.0.0.1:9229/abc\n" +
            "For help, see: https://nodejs.org/en/docs/inspector\n"
        assertEquals(null, scan(stdout(node)))
    }

    @Test
    fun `waits past a Network line for the Local one`() {
        assertEquals(
            "http://localhost:5173/",
            scan(stdout("  Network: http://192.168.1.5:5173/\n"), stdout("  Local: http://localhost:5173/\n")),
        )
    }

    @Test
    fun `trims surrounding punctuation from the URL`() {
        assertEquals("http://localhost:3000/", scan(stdout("Server ready at (http://localhost:3000).\n")))
        assertEquals("http://localhost:3000/", scan(stdout("Open <http://localhost:3000>\n")))
    }

    @Test
    fun `only takes URLs that start a word`() {
        assertEquals(null, scan(stdout("view-source:http://localhost:5173/\n")))
        assertEquals(null, scan(stdout("xhttp://localhost:5173/\n")))
    }

    @Test
    fun `takes the first URL when a chunk has several`() {
        assertEquals("http://localhost:3000/", scan(stdout("http://localhost:3000/\nhttp://localhost:4000/\n")))
    }

    @Test
    fun `ignores the target of an OSC 8 hyperlink`() {
        val link = "$esc]8;; http://localhost:1/$esc\\ visible $esc]8;;$esc\\\n"
        assertEquals(null, scan(stdout(link)))
    }

    @Test
    fun `ignores DCS, SOS, PM and APC payloads`() {
        listOf('P', 'X', '^', '_').forEach { introducer ->
            assertEquals(
                null,
                scan(stdout("$esc$introducer http://localhost:1/$esc\\ visible\n")),
                "payload of ESC $introducer must be removed",
            )
        }
    }

    @Test
    fun `ignores an OSC payload ended by BEL`() {
        assertEquals(null, scan(stdout("$esc]0;dev server http://localhost:1/\u0007title\n")))
    }

    @Test
    fun `strips colour codes before checking the host`() {
        assertEquals(null, scan(stdout("http://localhost$esc[0m.evil.com:1/\n")))
    }

    @Test
    fun `joins chunks of the same stream`() {
        assertEquals(
            "http://localhost:5173/",
            scan(stdout("Local: http://localhost:"), stdout("5173/\n")),
        )
    }

    @Test
    fun `finds a URL printed only on stderr`() {
        assertEquals(
            "http://127.0.0.1:8000/",
            scan(STDERR to "Starting development server at http://127.0.0.1:8000/\n"),
        )
    }

    @Test
    fun `never joins stdout and stderr into one line`() {
        val result = scan(
            stdout("Local: http://local"),
            STDERR to "host:5173/\n",
            stdout("\n"),
        )
        assertEquals(null, result)
    }

    @Test
    fun `skips excluded ports`() {
        assertEquals(
            "http://localhost:5173/",
            scan(stdout("http://localhost:63342/api/x\nhttp://localhost:5173/\n"), excludedPorts = setOf(63342)),
        )
    }

    @Test
    fun `an overlong line does not hide the next line`() {
        assertEquals(
            "http://localhost:5173/",
            scan(stdout("a".repeat(100_000)), stdout("\nLocal: http://localhost:5173/\n")),
        )
    }

    @Test
    fun `stops once the output budget is used up`() {
        val step = DevServerUrlScan.start(emptySet())
            .feed(STDOUT, "noise\n".repeat((DevServerUrlScan.MAX_SCANNED_CHARS / 6 + 1).toInt()))
        assertTrue(step is Step.Exhausted, "expected Exhausted, got $step")
    }

    @Test
    fun `finds nothing in output after the budget is used up`() {
        val noise = "noise\n".repeat((DevServerUrlScan.MAX_SCANNED_CHARS / 6 + 1).toInt())
        assertEquals(null, scan(stdout(noise), stdout("http://localhost:5173/\n")))
    }

    @Test
    fun `many tiny chunks use up the budget`() {
        var current = DevServerUrlScan.start(emptySet())
        repeat(20_000) {
            when (val step = current.feed(STDOUT, "x")) {
                is Step.Continue -> current = step.scan
                Step.Exhausted -> return
                is Step.Found -> fail("found a URL in noise: ${step.url}")
            }
        }
        fail("20,000 one-character chunks should use up the budget")
    }

    // ---- IPv6 brackets ----------------------------------------------------------

    @Test
    fun `keeps the bracket that closes an IPv6 host`() {
        assertEquals("http://[::1]/", scan(stdout("Listening on http://[::1]\n")))
        assertEquals("http://[::1]/", scan(stdout("Listening (http://[::1])\n")))
        assertEquals("http://[::1]/", scan(stdout("Listening on http://[::1]]\n")))
    }

    @Test
    fun `never adds a bracket to a cut-off IPv6 host`() {
        assertEquals(null, scan(stdout("Listening on http://[::1\n")))
    }

    // ---- Unicode whitespace ------------------------------------------------------

    private fun char(code: Int) = Char(code).toString()

    @Test
    fun `a non-breaking space or line separator ends a word`() {
        listOf(0x00A0, 0x2028, 0x2029).forEach { code ->
            assertEquals(
                "http://localhost:5173/",
                scan(stdout("Local: http://localhost:5173/${char(code)}(ready)\n")),
                "U+%04X must separate words".format(code),
            )
        }
    }

    @Test
    fun `a line separator inside a hidden payload does not expose it`() {
        val link = "$esc]8;;${char(0x2028)} http://localhost:1/$esc\\ visible\n"
        assertEquals(null, scan(stdout(link)))
    }

    // ---- unfinished last line ------------------------------------------------------

    private fun fedWith(vararg chunks: Pair<ConsoleStream, String>): DevServerUrlScan =
        chunks.fold(DevServerUrlScan.start(emptySet())) { current, (stream, text) ->
            when (val step = current.feed(stream, text)) {
                is Step.Continue -> step.scan
                else -> fail("expected the scan to continue, got $step")
            }
        }

    @Test
    fun `scanTail finds a URL printed without a line end`() {
        assertEquals("http://localhost:5173/", fedWith(stdout("Local: http://localhost:5173/")).scanTail(STDOUT)?.url?.value)
    }

    @Test
    fun `scanTail ignores a last word that ends in a colon`() {
        assertEquals(null, fedWith(stdout("Local: http://localhost:")).scanTail(STDOUT))
    }

    @Test
    fun `scanTail ignores a tail that ends inside an escape sequence`() {
        assertEquals(null, fedWith(stdout("Local: http://localhost:51$esc[1")).scanTail(STDOUT))
        assertEquals(null, fedWith(stdout("Local: http://localhost:51$esc")).scanTail(STDOUT))
        assertEquals(null, fedWith(stdout("$esc]8;; http://localhost:1/")).scanTail(STDOUT))
        assertEquals(null, fedWith(stdout("Local: http://localhost:5173/ $esc]8;;")).scanTail(STDOUT))
    }

    @Test
    fun `scanTail reads a tail whose escape is already decided`() {
        assertEquals(
            "http://localhost:5173/",
            fedWith(stdout("Local: http://localhost:5173/ $esc${Char(0x2028)}")).scanTail(STDOUT)?.url?.value,
        )
    }

    @Test
    fun `scanTail reads a URL inside an opening bracket`() {
        assertEquals("http://localhost:5173/", fedWith(stdout("Local: [http://localhost:5173/")).scanTail(STDOUT)?.url?.value)
    }

    @Test
    fun `scanTail leaves the unfinished line for the rest to complete`() {
        val cut = fedWith(stdout("Local: http://localhost:"))
        assertEquals(null, cut.scanTail(STDOUT))
        val step = cut.feed(STDOUT, "5173/\n")
        assertTrue(step is Step.Found && step.url.url.value == "http://localhost:5173/", "got $step")
    }

    @Test
    fun `scanTail reads only the given stream`() {
        val split = fedWith(stdout("Local: http://local"), STDERR to "host:5173/")
        assertEquals(null, split.scanTail(STDOUT))
        assertEquals(null, split.scanTail(STDERR))
    }

    @Test
    fun `scanTail skips a line that is being dropped as too long`() {
        assertEquals(null, fedWith(stdout("x".repeat(5000)), stdout(" http://localhost:5173/")).scanTail(STDOUT))
    }

    @Test
    fun `scanTail of a stream with nothing unfinished is nothing`() {
        assertEquals(null, fedWith(stdout("compiling...\n")).scanTail(STDOUT))
        assertEquals(null, DevServerUrlScan.start(emptySet()).scanTail(STDERR))
    }

    @Test
    fun `keeps scanning while under budget`() {
        val step = DevServerUrlScan.start(emptySet()).feed(STDOUT, "compiling...\n")
        if (step !is Step.Continue) fail("expected Continue, got $step")
    }
}
