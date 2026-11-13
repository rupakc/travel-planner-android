package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.alerts.PlanWatchOffers
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.model.alerts.AlertSuggestion
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchResponse
import io.github.rupakc.travelplanner.core.model.alerts.SuggestionsResponse
import io.github.rupakc.travelplanner.core.testing.fake.FakeQuickWatch
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanWatchOffersTest {
    private val hotel = AlertSuggestion(kind = "hotel", title = "Lumen", spec = JsonObject(mapOf("name" to JsonPrimitive("Lumen"))))
    private val done = AlertSuggestion(kind = "flight", title = "NYC to LIS", alreadyWatching = true)

    private fun offers(q: FakeQuickWatch) = PlanWatchOffers(q.watcher, q.alerts, q.telemetry)

    @Test fun `nothing is offered, or called, while one-tap is off`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch()
            q.alerts.suggestionsResult = AppResult.Success(SuggestionsResponse(listOf(hotel), livePrices = true))
            assertTrue(offers(q).load(3).isEmpty())
            assertTrue(q.alerts.suggestionCalls.isEmpty())
        }

    @Test fun `already watched suggestions and a server without live prices are not offered`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.suggestionsResult = AppResult.Success(SuggestionsResponse(listOf(hotel, done), livePrices = true))
            assertEquals(listOf(hotel), offers(q).load(3))
            q.alerts.suggestionsResult = AppResult.Success(SuggestionsResponse(listOf(hotel), livePrices = false))
            assertTrue(offers(q).load(3).isEmpty())
        }

    @Test fun `accepting watches with the plan id and the suggest surface, dismissing stops further offers`() =
        runTest(timeout = 30.seconds) {
            val q = FakeQuickWatch.on()
            q.alerts.suggestionsResult = AppResult.Success(SuggestionsResponse(listOf(hotel), livePrices = true))
            q.alerts.quickWatchResult = AppResult.Success(QuickWatchResponse("hotel", JsonObject(mapOf("id" to JsonPrimitive(5))), existing = false))
            val o = offers(q)
            assertEquals(1, o.load(3).size)
            assertEquals(QuickWatchOutcome.Added("hotel", 5), o.accept(3, hotel))
            val sent = q.alerts.quickWatchCalls.single()
            assertEquals(3L, sent.planId)
            assertEquals("suggest", sent.surface)
            o.dismiss(3)
            assertTrue(o.load(3).isEmpty())
            assertEquals(1, q.alerts.suggestionCalls.size)
        }
}
