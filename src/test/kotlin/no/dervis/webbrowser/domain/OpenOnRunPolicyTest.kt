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
            ifRight = { request -> assertEquals("http://requested:8080", request.url.value) },
        )
    }

    @Test
    fun `resolve falls back to fallbackUrl when requestedUrl is blank`() {
        enabledAnyConfig.resolve("FooApp", "", "http://fallback:3000").fold(
            ifLeft = { fail("expected Right, got $it") },
            ifRight = { request -> assertEquals("http://fallback:3000", request.url.value) },
        )
    }

    @Test
    fun `resolve carries readiness and waitSeconds onto the OpenRequest`() {
        val policy = enabledAnyConfig.copy(readiness = ReadinessMode.AFTER_DELAY, waitSeconds = 15)
        policy.resolve("FooApp", "http://x:1", "").fold(
            ifLeft = { fail("expected Right, got $it") },
            ifRight = { request ->
                assertEquals(ReadinessMode.AFTER_DELAY, request.readiness)
                assertEquals(15, request.waitSeconds)
            },
        )
    }

    @Test
    fun `resolve normalizes the URL by adding the http scheme when missing`() {
        enabledAnyConfig.resolve("FooApp", "localhost:3000", "").fold(
            ifLeft = { fail("expected Right, got $it") },
            ifRight = { request -> assertEquals("http://localhost:3000", request.url.value) },
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
