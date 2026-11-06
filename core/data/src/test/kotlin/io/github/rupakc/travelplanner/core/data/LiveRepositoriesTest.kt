package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.SystemAppClock
import io.github.rupakc.travelplanner.core.data.impl.LiveCheckRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.NearbyRepositoryImpl
import io.github.rupakc.travelplanner.core.domain.live.LiveFailure
import io.github.rupakc.travelplanner.core.domain.live.toLiveError
import io.github.rupakc.travelplanner.core.model.alerts.LiveCheckResult
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeLastLocationStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Near me v2 and "Search the web now" against MockWebServer: wire shape, lenient decoding, error mapping, no cache. */
class LiveRepositoriesTest {
    private val server = MockWebServer().apply { start() }
    private val net = TestNetwork(server.url("/").toString(), FakeSessionStore(Session("t", TestUser)), TestAppDispatchers(Dispatchers.IO))
    private val nearby = NearbyRepositoryImpl(net.api(), SystemAppClock(), FakeLastLocationStore())
    private val live = LiveCheckRepositoryImpl(net.api())

    @After fun tearDown() = server.close()

    private fun respond(code: Int, body: String, retryAfter: String? = null) =
        server.enqueue(
            MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json")
                .apply { retryAfter?.let { addHeader("Retry-After", it) } }.build(),
        )

    /** The next recorded request: its target and JSON body. */
    private fun request(): Pair<String?, JsonObject> {
        val recorded = server.takeRequest()
        return recorded.target to (Json.parseToJsonElement(recorded.body!!.utf8()) as JsonObject)
    }

    @Test
    fun `nearby sends sections true with the language and decodes the four sections`() =
        runTest {
            respond(200, V2_BODY)
            val result = nearby.nearby(38.723456, -9.139876, limit = 6, language = "es")
            val (target, body) = request()
            assertEquals("/api/nearby", target)
            assertTrue(body.getValue("sections").jsonPrimitive.boolean)
            // Rounded to 3 decimals (~100 m) before it leaves the phone.
            assertEquals(38.723, body.getValue("lat").jsonPrimitive.double, 0.0)
            assertEquals(-9.14, body.getValue("lng").jsonPrimitive.double, 0.0)
            assertEquals(6, body.getValue("limit").jsonPrimitive.int)
            assertEquals("es", body.getValue("language").jsonPrimitive.content)
            val value = (result as AppResult.Success).value
            assertEquals("Lisbon", value.place?.name)
            assertEquals(listOf("see", "eat", "shop", "stay"), value.sections.map { it.id })
            val market = value.sections[1].items.single()
            assertEquals("Time Out Market", market.name)
            assertEquals("https://maps.google.com/?cid=1", market.mapsUri)
            assertEquals("ChIJ1", market.placeId)
            assertEquals("Google Maps", value.attribution)
            assertEquals("https://maps.google.com/?cid=1", value.sources.single().uri)
        }

    @Test
    fun `nearby is never cached - every call reaches the server`() =
        runTest {
            respond(200, V2_BODY)
            respond(200, V2_BODY)
            nearby.nearby(38.72, -9.14)
            nearby.nearby(38.72, -9.14)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a v1-shaped or partial body decodes with empty sections, and malformed items are skipped`() {
        val json = io.github.rupakc.travelplanner.core.model.json.TravelJson
        val v1 = json.decodeFromString(NearbyResponse.serializer(), """{"place":{"name":"Kyoto"},"items":[{"name":"Hike","price_usd":"45"}]}""")
        assertEquals("Kyoto", v1.place?.name)
        assertTrue(v1.sections.isEmpty())
        assertTrue(v1.sources.isEmpty())
        val partial =
            json.decodeFromString(
                NearbyResponse.serializer(),
                """{"sections":[{"id":"eat","items":[{"name":"A","maps_uri":"https://maps.google.com/?cid=2","extra":1},"junk",{"name":["bad"]}]},7],
                   "sources":"nope","future":{"x":1}}""",
            )
        assertNull(partial.place)
        assertEquals(1, partial.sections.size)
        // "junk" is skipped; a wrong-typed field becomes null (the screen drops nameless items).
        assertEquals(listOf("A", null), partial.sections.single().items.map { it.name })
        assertTrue(partial.sources.isEmpty())
        val empty = json.decodeFromString(NearbyResponse.serializer(), "{}")
        assertTrue(empty.sections.isEmpty())
        val nulls = json.decodeFromString(NearbyResponse.serializer(), """{"place":null,"sections":null,"sources":null,"attribution":null}""")
        assertTrue(nulls.sections.isEmpty())
        assertTrue(nulls.sources.isEmpty())
        val nullItems = json.decodeFromString(NearbyResponse.serializer(), """{"sections":[{"id":"see","items":null},null]}""")
        assertEquals(listOf("see"), nullItems.sections.map { it.id })
        assertTrue(nullItems.sections.single().items.isEmpty())
    }

    @Test
    fun `nearby errors map to live failures with the backend words`() =
        runTest {
            respond(404, """{"detail":{"code":"no_place","message":"We don't know a town or city near you yet."}}""")
            respond(503, """{"detail":{"code":"ai_limit","message":"AI limit reached for this month — try again on the 1st."}}""")
            respond(503, """{"detail":{"code":"ai_billing","message":"AI is switched off on this server."}}""")
            respond(503, """{"detail":{"code":"grounding_unavailable","message":"Live web and map results need the Gemini provider."}}""")
            respond(502, """{"detail":{"code":"agent_error","message":"Couldn't get places right now."}}""")
            respond(429, """{"detail":"Too many requests — please wait 30 seconds and try again."}""", retryAfter = "30")
            val errors = List(6) { (nearby.nearby(1.0, 2.0) as AppResult.Failure).error.toLiveError() }
            assertEquals(
                listOf(
                    LiveFailure.NOT_FOUND,
                    LiveFailure.AI_LIMIT,
                    LiveFailure.AI_BILLING,
                    LiveFailure.GROUNDING_UNAVAILABLE,
                    LiveFailure.SERVER,
                    LiveFailure.RATE_LIMITED,
                ),
                errors.map { it.failure },
            )
            assertEquals("no_place", errors[0].code)
            assertEquals("AI limit reached for this month — try again on the 1st.", errors[1].message)
            assertEquals("Live web and map results need the Gemini provider.", errors[3].message)
            assertEquals("Too many requests — please wait 30 seconds and try again.", errors[5].message)
        }

    @Test
    fun `offline maps to OFFLINE without exception text`() =
        runTest {
            server.close()
            val error = (nearby.nearby(1.0, 2.0) as AppResult.Failure).error
            assertTrue(error is AppError.Network)
            assertEquals(LiveFailure.OFFLINE, error.toLiveError().failure)
            assertNull(error.toLiveError().message)
        }

    @Test
    fun `live check posts kind and watch_id and decodes the grounded answer`() =
        runTest {
            respond(200, LIVE_BODY)
            val result = live.check("hotel", 123)
            val (target, body) = request()
            assertEquals("/api/live-check", target)
            assertEquals("hotel", body.getValue("kind").jsonPrimitive.content)
            assertEquals(123L, body.getValue("watch_id").jsonPrimitive.long)
            assertEquals(setOf("kind", "watch_id"), body.keys)
            val value: LiveCheckResult = (result as AppResult.Success).value
            assertEquals("Rooms from €412 for 3 nights on Booking.com.", value.summary)
            assertEquals(412.0, value.quotes.single().amount!!, 0.0)
            assertEquals(0, value.quotes.single().sourceIndex)
            assertEquals("https://www.booking.com/hotel/pt/memmo", value.sources.single().uri)
            assertEquals(listOf("memmo alfama lisbon price 1-4 dec 2026"), value.searchedFor)
            assertTrue(value.disclaimer!!.startsWith("From a live web search"))
        }

    @Test
    fun `a partial live answer decodes with empty lists`() {
        val json = io.github.rupakc.travelplanner.core.model.json.TravelJson
        val r = json.decodeFromString(LiveCheckResult.serializer(), """{"summary":"Hi","quotes":[{"amount":"about 99"},"x"],"searched_for":"one query"}""")
        assertEquals("Hi", r.summary)
        assertEquals(99.0, r.quotes.single().amount!!, 0.0)
        assertTrue(r.sources.isEmpty())
        assertEquals(listOf("one query"), r.searchedFor)
        val nulls = json.decodeFromString(LiveCheckResult.serializer(), """{"summary":null,"quotes":null,"sources":null,"searched_for":null}""")
        assertNull(nulls.summary)
        assertTrue(nulls.quotes.isEmpty() && nulls.sources.isEmpty() && nulls.searchedFor.isEmpty())
    }

    @Test
    fun `live check errors map to live failures`() =
        runTest {
            respond(404, """{"detail":"Watch not found"}""")
            respond(502, """{"detail":{"code":"live_check_failed","message":"The web search returned no answer."}}""")
            respond(503, """{"detail":{"code":"ai_limit","message":"AI limit reached for this month — try again on the 1st."}}""")
            respond(409, """{"detail":{"code":"watch_expired","message":"This watch has expired."}}""")
            val errors = List(4) { (live.check("flight", 9) as AppResult.Failure).error.toLiveError() }
            assertEquals(listOf(LiveFailure.NOT_FOUND, LiveFailure.SERVER, LiveFailure.AI_LIMIT, LiveFailure.WATCH_EXPIRED), errors.map { it.failure })
            assertEquals("watch_expired", errors[3].code)
            assertEquals("Watch not found", errors[0].message)
            assertEquals("live_check_failed", errors[1].code)
        }

    private companion object {
        val V2_BODY =
            """
            {
              "place": {"name": "Lisbon", "region": null, "country": "Portugal", "country_code": "PT", "lat": 38.72, "lng": -9.14, "distance_km": 0.4},
              "sections": [
                {"id": "see", "title": "Places to see", "items": []},
                {"id": "eat", "title": "Places to eat", "items": [
                  {"name": "Time Out Market", "summary": "Food hall with local chefs", "maps_uri": "https://maps.google.com/?cid=1", "place_id": "ChIJ1", "category": "eat"}
                ]},
                {"id": "shop", "title": "Places to shop", "items": []},
                {"id": "stay", "title": "Places to stay", "items": []}
              ],
              "attribution": "Google Maps",
              "sources": [{"title": "Time Out Market", "uri": "https://maps.google.com/?cid=1"}]
            }
            """.trimIndent()

        val LIVE_BODY =
            """
            {
              "kind": "hotel", "watch_id": 123, "checked_at": "2026-11-06T18:00:00Z",
              "summary": "Rooms from €412 for 3 nights on Booking.com.",
              "quotes": [{"label": "Booking.com, 3 nights", "amount": 412.0, "currency": "EUR", "source_index": 0}],
              "sources": [{"title": "Booking.com", "uri": "https://www.booking.com/hotel/pt/memmo"}],
              "searched_for": ["memmo alfama lisbon price 1-4 dec 2026"],
              "disclaimer": "From a live web search just now. Prices change quickly — check the provider before booking. Not used for your price alerts."
            }
            """.trimIndent()
    }
}
