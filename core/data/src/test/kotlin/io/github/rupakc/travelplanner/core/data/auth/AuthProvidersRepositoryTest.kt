package io.github.rupakc.travelplanner.core.data.auth

import io.github.rupakc.travelplanner.core.data.impl.AuthProvidersRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.AuthProviderInfo
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `GET /api/auth/providers` is feature-detected: anything but a good list is "no providers". */
class AuthProvidersRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private val base = server.url("/").toString()
    private val net = TestNetwork(base, dispatchers = TestAppDispatchers(Dispatchers.IO))
    private val repo = AuthProvidersRepositoryImpl(net.api(), net.baseUrlProvider)

    @After fun tearDown() = server.close()

    private fun respond(code: Int, body: String) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    @Test
    fun `maps providers by id with start urls resolved against the base url`() =
        runTest {
            respond(
                200,
                """{"providers":[
                  {"id":"google","configured":true,"start_url":"/api/auth/oauth/google/start","id_token":true},
                  {"id":"github","configured":true,"start_url":"https://evil.com/api/auth/oauth/github/start"},
                  {"id":"apple","configured":false,"start_url":null},
                  {"configured":true,"start_url":"/api/auth/x"},
                  "junk"]}""",
            )
            val providers = repo.providers()
            assertEquals("/api/auth/providers", server.takeRequest().target)
            assertEquals(
                mapOf(
                    "google" to AuthProviderInfo(configured = true, startUrl = "${base.trimEnd('/')}/api/auth/oauth/google/start"),
                    "github" to AuthProviderInfo(configured = true, startUrl = null),
                    "apple" to AuthProviderInfo(configured = false, startUrl = null),
                ),
                providers,
            )
        }

    @Test
    fun `404 is an empty map`() =
        runTest {
            respond(404, """{"detail":"Not Found"}""")
            assertTrue(repo.providers().isEmpty())
        }

    @Test
    fun `any http error is an empty map`() =
        runTest {
            respond(500, "oops")
            assertTrue(repo.providers().isEmpty())
        }

    @Test
    fun `a non-list or undecodable body is an empty map`() =
        runTest {
            respond(200, """{"providers":{"id":"google","configured":true}}""")
            assertTrue(repo.providers().isEmpty())
            respond(200, "not json")
            assertTrue(repo.providers().isEmpty())
        }

    @Test
    fun `a network failure is an empty map`() =
        runTest {
            server.close()
            assertTrue(repo.providers().isEmpty())
        }
}
