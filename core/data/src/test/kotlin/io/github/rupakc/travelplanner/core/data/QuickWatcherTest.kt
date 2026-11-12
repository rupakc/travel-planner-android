package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchResponse
import io.github.rupakc.travelplanner.core.testing.fake.FakeQuickWatch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickWatcherTest {
    private val spec = JsonObject(mapOf("name" to JsonPrimitive("Lumen")))

    @Test fun `availability needs live prices and the v2 API`() =
        runTest(timeout = 30.seconds) {
            assertFalse(FakeQuickWatch().watcher.available())
            assertTrue(FakeQuickWatch.on().watcher.available())
        }

    @Test fun `watch trims and caps the title, sends the surface and records telemetry`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.quickWatchResult =
                AppResult.Success(QuickWatchResponse("hotel", JsonObject(mapOf("id" to JsonPrimitive(8))), existing = false))
            val outcome = q.watcher.watch("hotel", "  " + "x".repeat(200), "  ", spec, planId = 3, surface = "trip")
            assertEquals(QuickWatchOutcome.Added("hotel", 8), outcome)
            val sent = q.alerts.quickWatchCalls.single()
            assertEquals(120, sent.title.length)
            assertEquals(null, sent.subtitle)
            assertEquals(3L, sent.planId)
            assertEquals("trip", sent.surface)
            assertEquals(listOf("alert_quick_watch" to mapOf("kind" to "hotel", "action" to "add", "surface" to "trip")), q.telemetry.events)
        }

    @Test fun `a blocked watch is reported as blocked, never with a title`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.quickWatchResult = AppResult.Failure(AppError.Server(503, "off", "live_prices_off"))
            assertEquals(QuickWatchOutcome.LivePricesOff, q.watcher.watch("flight", "A", null, spec, null, "results"))
            assertEquals("blocked", q.telemetry.events.single().second["action"])
            assertFalse(q.telemetry.events.single().second.values.contains("A"))
        }

    @Test fun `undo deletes through the kind's repository and only counts a success`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.fare.watches.value = listOf(FareWatch(id = 2))
            assertTrue(q.watcher.undo("flight", 2, "results"))
            assertTrue(q.fare.watches.value.isEmpty())
            assertEquals("undo", q.telemetry.events.single().second["action"])
        }
}
