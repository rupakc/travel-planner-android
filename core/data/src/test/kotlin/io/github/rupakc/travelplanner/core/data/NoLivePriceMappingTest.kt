package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.PriceWatchesRepositoryImpl
import io.github.rupakc.travelplanner.core.domain.alerts.noLivePrice
import io.github.rupakc.travelplanner.core.model.alerts.CreatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.HttpErrorMapper
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The backend's real 422 `no_live_price` body (E2E flow 7) must reach the watch sheet with its suggestions. */
class NoLivePriceMappingTest {
    @Test
    fun `no_live_price body keeps its suggestions`() {
        val error = HttpErrorMapper.map(422, null, BODY)
        val noLive = requireNotNull(error.noLivePrice())
        assertEquals(2, noLive.suggestions.size)
        assertEquals("Tandem Palacio Alfama Suites", noLive.suggestions.first().title)
    }

    private val server = MockWebServer().apply { start() }

    @After fun tearDown() = server.close()

    @Test
    fun `repository create keeps the suggestions of a no_live_price 422`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(422).body(BODY).build())
            val net = TestNetwork(server.url("/").toString(), FakeSessionStore(Session("t", TestUser)), TestAppDispatchers(Dispatchers.IO))
            val repo = PriceWatchesRepositoryImpl(net.api())
            val result = repo.create(CreatePriceWatchRequest(kind = "hotel", spec = buildJsonObject { put("name", JsonPrimitive("Memmo")) }))
            val noLive = requireNotNull((result as AppResult.Failure).error.noLivePrice())
            assertEquals(2, noLive.suggestions.size)
        }

    private companion object {
        const val BODY = "{\"detail\": \"We couldn't find live prices for this hotel on Google Hotels.\", \"code\": \"no_live_price\", \"message\": \"We couldn't find live prices for this hotel on Google Hotels.\", \"suggestions\": [{\"title\": \"Tandem Palacio Alfama Suites\", \"subtitle\": \"4.6/5 (837 reviews) · ${"$"}131 a night · total for 3 nights\", \"price\": 522.0, \"nightly\": 131.0, \"currency\": \"USD\", \"link\": \"https://www.google.com/travel/search?q=hotels+in+Lisbon&hl=en&gl=us\", \"spec\": {\"name\": \"Tandem Palacio Alfama Suites\", \"city\": \"Lisbon\", \"check_in\": \"2026-12-01\", \"check_out\": \"2026-12-04\", \"adults\": 2, \"rooms\": 1, \"property_token\": \"ChkQxtHgp5Ga0L44Gg0vZy8xMXliN3NyZ3Z4EAI\"}}, {\"title\": \"Brown's | Avenue Hotel\", \"subtitle\": \"4.6/5 (284 reviews) · ${"$"}157 a night · total for 3 nights\", \"price\": 626.0, \"nightly\": 157.0, \"currency\": \"USD\", \"link\": \"https://www.brownshotelgroup.com/browns-avenue/\", \"spec\": {\"name\": \"Brown's | Avenue Hotel\", \"city\": \"Lisbon\", \"check_in\": \"2026-12-01\", \"check_out\": \"2026-12-04\", \"adults\": 2, \"rooms\": 1, \"property_token\": \"ChkIhLCQwvjO2IYWGg0vZy8xMXE0bTZieDkyEAE\"}}], \"estimate\": null}"
    }
}
