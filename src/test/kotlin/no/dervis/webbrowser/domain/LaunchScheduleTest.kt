package no.dervis.webbrowser.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LaunchScheduleTest {

    private val url = Url.parse("http://localhost:4321").getOrNull()
        ?: fail("test setup: Url.parse should succeed for a normal URL")

    @Test
    fun `ON_LAUNCH maps to Immediate`() {
        val schedule = LaunchSchedule.from(OpenRequest(url, ReadinessMode.ON_LAUNCH, 30))
        assertTrue(schedule is LaunchSchedule.Immediate, "expected Immediate, got $schedule")
        assertEquals(url, schedule.url)
    }

    @Test
    fun `AFTER_DELAY maps to Delayed with the requested wait seconds`() {
        val schedule = LaunchSchedule.from(OpenRequest(url, ReadinessMode.AFTER_DELAY, 5))
        assertTrue(schedule is LaunchSchedule.Delayed, "expected Delayed, got $schedule")
        assertEquals(5, schedule.delaySeconds)
        assertEquals(url, schedule.url)
    }

    @Test
    fun `WHEN_REACHABLE maps to WhenReachable with the requested timeout`() {
        val schedule = LaunchSchedule.from(OpenRequest(url, ReadinessMode.WHEN_REACHABLE, 30))
        assertTrue(schedule is LaunchSchedule.WhenReachable, "expected WhenReachable, got $schedule")
        assertEquals(30, schedule.timeoutSeconds)
        assertEquals(url, schedule.url)
    }

    @Test
    fun `every ReadinessMode is mapped (exhaustiveness sanity)`() {
        ReadinessMode.entries.forEach { mode ->
            // must not throw — sealed mapping must cover all enum entries
            LaunchSchedule.from(OpenRequest(url, mode, 10))
        }
    }

    @Test
    fun `schedule carries the same Url as the request`() {
        ReadinessMode.entries.forEach { mode ->
            val schedule = LaunchSchedule.from(OpenRequest(url, mode, 7))
            assertEquals(url, schedule.url, "url must be preserved for $mode")
        }
    }
}
