package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class OpenOnRunPolicyTest {

    private val enabledAnyConfig = OpenOnRunPolicy(
        enabled = true,
        runConfigName = "",
        readiness = ReadinessMode.WHEN_REACHABLE,
        waitSeconds = 30,
    )

    /** The URL a schedule opens, or null for the one mode that opens whatever the server prints. */
    private fun urlOf(schedule: LaunchSchedule): String? = when (schedule) {
        is LaunchSchedule.Immediate -> schedule.url.value
        is LaunchSchedule.Delayed -> schedule.url.value
        is LaunchSchedule.WhenReachable -> schedule.url.value
        is LaunchSchedule.WhenAnnounced -> null
    }

    private fun OpenOnRunPolicy.scheduleFor(requested: String, fallback: String): LaunchSchedule =
        resolve("FooApp", requested, fallback).fold(ifLeft = { fail("expected a schedule, got $it") }, ifRight = { it })

    // ---- isTriggeredBy ----

    @Test
    fun `isTriggeredBy returns false when disabled`() {
        assertFalse(enabledAnyConfig.copy(enabled = false).isTriggeredBy("FooApp"))
    }

    @Test
    fun `isTriggeredBy returns true for any started name when runConfigName is blank`() {
        assertTrue(enabledAnyConfig.isTriggeredBy("FooApp"))
        assertTrue(enabledAnyConfig.isTriggeredBy(""))
    }

    @Test
    fun `isTriggeredBy returns true when the started name matches the configured name`() {
        val policy = enabledAnyConfig.copy(runConfigName = "DevServer")
        assertTrue(policy.isTriggeredBy("DevServer"))
    }

    @Test
    fun `isTriggeredBy returns false when the started name does not match`() {
        val policy = enabledAnyConfig.copy(runConfigName = "DevServer")
        assertFalse(policy.isTriggeredBy("OtherApp"))
    }

    // ---- resolve ----

    @Test
    fun `resolve returns NotTriggered when disabled`() {
        val policy = enabledAnyConfig.copy(enabled = false)
        policy.resolve("FooApp", "http://x", "http://y").fold(
            ifLeft = { assertEquals(OpenRunSkip.NotTriggered, it) },
            ifRight = { fail("expected Left, got $it") },
        )
    }

    @Test
    fun `resolve returns NotTriggered when the started name does not match`() {
        val policy = enabledAnyConfig.copy(runConfigName = "DevServer")
        policy.resolve("Other", "http://x", "http://y").fold(
            ifLeft = { assertEquals(OpenRunSkip.NotTriggered, it) },
            ifRight = { fail("expected Left, got $it") },
        )
    }

    @Test
    fun `resolve returns NoValidUrl for a non-web requested URL`() {
        enabledAnyConfig.resolve("FooApp", "file:///etc/hosts", "http://localhost:3000").fold(
            ifLeft = { assertEquals(OpenRunSkip.NoValidUrl, it) },
            ifRight = { fail("expected Left, got $it") },
        )
    }

    @Test
    fun `resolve returns NoValidUrl when both requested and fallback URLs are blank`() {
        enabledAnyConfig.resolve("FooApp", "", "").fold(
            ifLeft = { assertEquals(OpenRunSkip.NoValidUrl, it) },
            ifRight = { fail("expected Left, got $it") },
        )
    }

    @Test
    fun `resolve prefers requestedUrl when given`() {
        enabledAnyConfig.resolve("FooApp", "http://requested:8080", "http://fallback:3000").fold(
            ifLeft = { fail("expected Right, got $it") },
            ifRight = { schedule -> assertEquals("http://requested:8080", urlOf(schedule)) },
        )
    }

    @Test
    fun `resolve falls back to fallbackUrl when requestedUrl is blank`() {
        enabledAnyConfig.resolve("FooApp", "", "http://fallback:3000").fold(
            ifLeft = { fail("expected Right, got $it") },
            ifRight = { schedule -> assertEquals("http://fallback:3000", urlOf(schedule)) },
        )
    }

    // ---- readiness mode → launch schedule ----

    @Test
    fun `ON_LAUNCH opens the URL immediately`() {
        val schedule = enabledAnyConfig.copy(readiness = ReadinessMode.ON_LAUNCH).scheduleFor("http://x:1", "")
        assertEquals(LaunchSchedule.Immediate(Url.web("http://x:1").getOrNull()!!), schedule)
    }

    @Test
    fun `AFTER_DELAY opens the URL after the wait seconds`() {
        val schedule = enabledAnyConfig.copy(readiness = ReadinessMode.AFTER_DELAY, waitSeconds = 15)
            .scheduleFor("http://x:1", "")
        assertEquals(LaunchSchedule.Delayed(Url.web("http://x:1").getOrNull()!!, 15), schedule)
    }

    @Test
    fun `WHEN_REACHABLE polls the URL for up to the wait seconds`() {
        val schedule = enabledAnyConfig.scheduleFor("http://x:1", "")
        assertEquals(LaunchSchedule.WhenReachable(Url.web("http://x:1").getOrNull()!!, 30), schedule)
    }

    @Test
    fun `WHEN_ANNOUNCED watches the console for up to the wait seconds`() {
        val schedule = enabledAnyConfig.copy(readiness = ReadinessMode.WHEN_ANNOUNCED, waitSeconds = 45)
            .scheduleFor("http://x:1", "")
        assertEquals(LaunchSchedule.WhenAnnounced(45), schedule)
    }

    @Test
    fun `When the server prints its URL needs no open or home URL`() {
        val announced = enabledAnyConfig.copy(readiness = ReadinessMode.WHEN_ANNOUNCED)
        assertEquals(LaunchSchedule.WhenAnnounced(30), announced.scheduleFor("", ""))
        assertEquals(LaunchSchedule.WhenAnnounced(30), announced.scheduleFor("", "file:///etc/hosts"))
        assertEquals(LaunchSchedule.WhenAnnounced(30), announced.scheduleFor("ftp://x", "http://ok"))
    }

    @Test
    fun `every readiness mode resolves to a schedule`() {
        ReadinessMode.entries.forEach { mode ->
            enabledAnyConfig.copy(readiness = mode).scheduleFor("http://x:1", "")
        }
    }

    @Test
    fun `resolve normalizes the URL by adding the http scheme when missing`() {
        enabledAnyConfig.resolve("FooApp", "localhost:3000", "").fold(
            ifLeft = { fail("expected Right, got $it") },
            ifRight = { schedule -> assertEquals("http://localhost:3000", urlOf(schedule)) },
        )
    }

    // ---- data-class component accessors (sanity check + 100% method coverage) ----

    @Test
    fun `data class exposes its constructor fields via property accessors`() {
        val policy = OpenOnRunPolicy(
            enabled = true,
            runConfigName = "MyConfig",
            readiness = ReadinessMode.AFTER_DELAY,
            waitSeconds = 42,
        )
        assertTrue(policy.enabled)
        assertEquals("MyConfig", policy.runConfigName)
        assertEquals(ReadinessMode.AFTER_DELAY, policy.readiness)
        assertEquals(42, policy.waitSeconds)
    }

    @Test
    fun `data class equality is value-based`() {
        val a = OpenOnRunPolicy(true, "x", ReadinessMode.ON_LAUNCH, 5)
        val b = OpenOnRunPolicy(true, "x", ReadinessMode.ON_LAUNCH, 5)
        val c = a.copy(waitSeconds = 6)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }
}
