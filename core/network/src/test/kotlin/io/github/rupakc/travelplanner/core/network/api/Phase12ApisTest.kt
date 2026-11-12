package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.auth.DeleteAccountRequest
import io.github.rupakc.travelplanner.core.model.auth.OauthExchangeRequest
import io.github.rupakc.travelplanner.core.model.auth.RefreshRequest
import io.github.rupakc.travelplanner.core.model.auth.SignupRequest
import io.github.rupakc.travelplanner.core.model.trips.CheckinRequest
import io.github.rupakc.travelplanner.core.model.trips.CreateExpenseRequest
import io.github.rupakc.travelplanner.core.model.trips.PrepToggleRequest
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceRequest
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wire shapes of the Phase 1/2 APIs: method, path, query and body. */
class Phase12ApisTest {
    private val server = MockWebServer().apply { start() }
    private val net = TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO))

    @After fun tearDown() = server.close()

    private fun ok(body: String = "{}") = server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build())

    private fun noContent() = server.enqueue(MockResponse.Builder().code(204).build())

    @Test
    fun `settings patch sends only the given keys and can send null`() =
        runTest {
            ok()
            net.api<AccountApi>().patchSettings(JsonObject(mapOf("theme" to JsonPrimitive("dark"), "language" to JsonNull)))
            val r = server.takeRequest()
            assertEquals("PATCH", r.method)
            assertEquals("/api/account/settings", r.target)
            assertEquals("""{"theme":"dark","language":null}""", r.body!!.utf8())
        }

    @Test
    fun `delete account is a DELETE with a body and omits the unused field`() =
        runTest {
            noContent()
            net.api<AccountApi>().deleteAccount(DeleteAccountRequest(password = "pw"))
            val r = server.takeRequest()
            assertEquals("DELETE", r.method)
            assertEquals("/api/account", r.target)
            assertEquals("""{"password":"pw"}""", r.body!!.utf8())
        }

    @Test
    fun `refresh and signup bodies`() =
        runTest {
            ok("""{"access_token":"a","refresh_token":"r2","expires_in":3600}""")
            val refreshed = net.api<AccountApi>().refresh(RefreshRequest("r1"))
            assertEquals("r2", refreshed.refreshToken)
            assertEquals("""{"refresh_token":"r1"}""", server.takeRequest().body!!.utf8())

            server.enqueue(
                MockResponse.Builder().code(
                    202,
                ).addHeader("Content-Type", "application/json").body("""{"status":"verification_sent","email":"a@b.c","expires_in":1800}""").build(),
            )
            val sent = net.api<SignupApi>().signup(SignupRequest("jane", "a@b.c", "long enough pw", client = "android"))
            assertEquals("verification_sent", sent.status)
            val r = server.takeRequest()
            assertEquals("/api/auth/signup", r.target)
            assertTrue(r.body!!.utf8().contains(""""client":"android""""))
        }

    @Test
    fun `oauth exchange carries the verifier`() =
        runTest {
            ok("""{"status":"linked","provider":"github"}""")
            val result = net.api<OauthApi>().exchange(OauthExchangeRequest("c", "v", "Pixel"))
            assertEquals("github", result.provider)
            assertEquals("""{"code":"c","verifier":"v","device_name":"Pixel"}""", server.takeRequest().body!!.utf8())
        }

    @Test
    fun `trips list sends today and a trip patch can clear a date`() =
        runTest {
            ok("""{"trips":[],"pinned_id":null,"today":"2026-11-12"}""")
            net.api<TripsApi>().list("2026-11-12")
            assertEquals("/api/trips?today=2026-11-12", server.takeRequest().target)

            ok("""{"id":1}""")
            net.api<TripsApi>().update(1, JsonObject(mapOf("end_date" to JsonNull, "phase" to JsonPrimitive("ready"))))
            val r = server.takeRequest()
            assertEquals("PATCH", r.method)
            assertEquals("/api/trips/1", r.target)
            assertEquals("""{"end_date":null,"phase":"ready"}""", r.body!!.utf8())
        }

    @Test
    fun `prep toggle, checkin, expense and place paths`() =
        runTest {
            val api = net.api<TripsApi>()
            ok("""{"items":[],"done":0,"total":0,"percent":0,"complete":false}""")
            api.togglePrep(4, "passport", PrepToggleRequest(true))
            assertEquals("PUT /api/trips/4/prep/passport", server.takeRequest().let { "${it.method} ${it.target}" })

            ok("""{"day":"2027-04-01"}""")
            api.checkin(4, CheckinRequest("2027-04-01"))
            assertEquals("POST /api/trips/4/checkin", server.takeRequest().let { "${it.method} ${it.target}" })

            ok("""{"id":9,"amount":"12.50","currency":"EUR"}""")
            api.createExpense(4, CreateExpenseRequest("cid", "12.50", "EUR", "food", null, "2027-04-02"))
            val e = server.takeRequest()
            assertEquals("/api/trips/4/expenses", e.target)
            assertEquals("""{"client_id":"cid","amount":"12.50","currency":"EUR","category":"food","spent_on":"2027-04-02"}""", e.body!!.utf8())

            ok("""{"place":{"place_key":"activity:x"}}""")
            api.putPlace(4, "activity:x", PutPlaceRequest("X", "activity", visited = true))
            val p = server.takeRequest()
            assertEquals("PUT", p.method)
            assertEquals("/api/trips/4/places/activity:x", p.target)
        }

    @Test
    fun `game and offline pack`() =
        runTest {
            ok("""{"xp":1}""")
            assertEquals(1, net.api<GameApi>().me().xp)
            assertEquals("/api/game/me", server.takeRequest().target)
            ok("""{"trip_id":4}""")
            assertEquals(4L, net.api<TripsApi>().offlinePack(4, "2026-11-12").tripId)
            assertEquals("/api/trips/4/offline-pack?today=2026-11-12", server.takeRequest().target)
        }
}
