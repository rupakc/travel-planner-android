package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.AlertsV2RepositoryImpl
import io.github.rupakc.travelplanner.core.model.alerts.AlertKind
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefsUpdate
import io.github.rupakc.travelplanner.core.model.alerts.AlertsAdviceCode
import io.github.rupakc.travelplanner.core.model.alerts.AlertsErrorCode
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.alertsErrorCode
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paths, bodies and error-code mapping of the v2 alerts routes against a MockWebServer. */
class AlertsV2RepositoryImplTest {
    private val server = MockWebServer().apply { start() }
    private val repo = AlertsV2RepositoryImpl(TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO)).api())

    @After fun tearDown() = server.close()

    private fun respond(code: Int, body: String) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    private suspend fun failure(code: Int, body: String, call: suspend () -> AppResult<*>): AppError {
        respond(code, body)
        return (call() as AppResult.Failure).error
    }

    @Test
    fun `summary story and preferences hit the contract paths`() =
        runTest {
            respond(200, """{"total_usd":43.0,"milestones":[50,200,1000],"next_milestone":50,"progress_pct":86}""")
            assertEquals(43.0, (repo.summary() as AppResult.Success).value.totalUsd!!, 0.0)
            assertEquals("/api/alerts/summary", server.takeRequest().target)

            respond(200, """{"kind":"hotel","watch_id":3,"advice":{"code":"steady","params":{"days":6}},"verdict":null}""")
            val story = (repo.story(AlertKind.HOTEL, 3) as AppResult.Success).value
            assertEquals(AlertsAdviceCode.STEADY, story.adviceCode)
            assertEquals("/api/alerts/watches/hotel/3/story", server.takeRequest().target)

            respond(200, """{"digest_weekly":true,"instant":"targets_only","quiet_hours_active":false}""")
            assertTrue((repo.preferences() as AppResult.Success).value.digestWeekly)
            assertEquals("/api/alerts/preferences", server.takeRequest().target)

            respond(200, """{"digest_weekly":true,"digest_day":2}""")
            repo.updatePreferences(AlertPrefsUpdate(digestDay = 2))
            val put = server.takeRequest()
            assertEquals("PUT", put.method)
            assertEquals("""{"digest_day":2}""", put.body?.utf8())
        }

    @Test
    fun `advice and suggestions`() =
        runTest {
            respond(200, """{"tip":"Book now","source":"ai","generated_at":"2026-11-10T00:00:00Z","cached":false}""")
            assertEquals("Book now", (repo.advice(AlertKind.FLIGHT, 7) as AppResult.Success).value.tip)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/alerts/watches/flight/7/advice", req.target)

            respond(200, """{"suggestions":[],"live_prices":false}""")
            repo.suggestions(12)
            assertEquals("/api/alerts/suggestions?plan_id=12", server.takeRequest().target)
            respond(200, """{"suggestions":[],"live_prices":false}""")
            repo.suggestions()
            assertEquals("/api/alerts/suggestions", server.takeRequest().target)
        }

    @Test
    fun `quick watch accepts 201 and 200 with existing`() =
        runTest {
            val request = QuickWatchRequest("hotel", "Memmo", null, JsonObject(mapOf("name" to JsonPrimitive("Memmo"))), null, "chat")
            respond(201, """{"kind":"hotel","watch":{"id":5,"pending_first_check":true},"existing":false}""")
            val created = (repo.quickWatch(request) as AppResult.Success).value
            assertEquals(false, created.existing)
            val sent = server.takeRequest()
            assertEquals("/api/alerts/quick-watch", sent.target)
            assertEquals("""{"kind":"hotel","title":"Memmo","spec":{"name":"Memmo"},"surface":"chat"}""", sent.body?.utf8())
            respond(200, """{"kind":"hotel","watch":{"id":5},"existing":true}""")
            assertEquals(true, (repo.quickWatch(request) as AppResult.Success).value.existing)
        }

    @Test
    fun `error codes reach the caller`() =
        runTest {
            val req = QuickWatchRequest("flight", "t", null, JsonObject(emptyMap()), null, "trip")

            fun env(code: String) = """{"detail":"msg","error_code":"$code"}"""
            assertEquals(AlertsErrorCode.WATCH_NOT_FOUND, failure(404, env("watch_not_found")) { repo.story(AlertKind.HOTEL, 1) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.KIND_INVALID, failure(422, env("kind_invalid")) { repo.story(AlertKind.TRIP, 1) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.ADVICE_UNSUPPORTED, failure(422, env("advice_unsupported")) { repo.advice(AlertKind.HOTEL, 1) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.ADVICE_NO_DATA, failure(422, env("advice_no_data")) { repo.advice(AlertKind.FLIGHT, 1) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.ADVICE_FAILED, failure(502, env("advice_failed")) { repo.advice(AlertKind.FLIGHT, 1) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.AI_LIMIT, failure(503, env("ai_limit")) { repo.advice(AlertKind.FLIGHT, 1) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.AI_BILLING, failure(503, env("ai_billing")) { repo.advice(AlertKind.FLIGHT, 1) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.LIVE_PRICES_OFF, failure(503, env("live_prices_off")) { repo.quickWatch(req) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.WATCH_LIMIT, failure(409, env("watch_limit")) { repo.quickWatch(req) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.SPEC_INVALID, failure(422, env("spec_invalid")) { repo.quickWatch(req) }.alertsErrorCode())
            assertEquals(AlertsErrorCode.TRIP_NOT_FOUND, failure(404, env("trip_not_found")) { repo.suggestions(9) }.alertsErrorCode())
            assertEquals(
                AlertsErrorCode.ALERT_PREFS_INVALID,
                failure(422, env("alert_prefs_invalid")) { repo.updatePreferences(AlertPrefsUpdate(groupWindowMin = 999)) }.alertsErrorCode(),
            )
            val limit = failure(503, env("ai_limit")) { repo.advice(AlertKind.FLIGHT, 1) }
            assertEquals("msg", limit.message)
        }
}
