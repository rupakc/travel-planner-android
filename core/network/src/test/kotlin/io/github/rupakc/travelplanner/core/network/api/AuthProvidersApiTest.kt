package io.github.rupakc.travelplanner.core.network.api

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

class AuthProvidersApiTest {
    private val server = MockWebServer().apply { start() }
    private val api: AuthProvidersApi =
        TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO)).api()

    @After fun tearDown() = server.close()

    @Test
    fun `GETs api auth providers and decodes the list`() =
        runTest {
            server.enqueue(
                MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                    .body("""{"providers":[{"id":"google","configured":true,"start_url":"/api/auth/oauth/google/start","id_token":true}]}""")
                    .build(),
            )
            val response = api.providers()
            val recorded = server.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/auth/providers", recorded.target)
            val provider = response.body()!!.providers.single()
            assertEquals("google", provider.id)
            assertTrue(provider.configured)
            assertEquals("/api/auth/oauth/google/start", provider.startUrl)
        }

    @Test
    fun `a 404 is a plain unsuccessful response`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(404).body("""{"detail":"Not Found"}""").build())
            assertEquals(404, api.providers().code())
        }
}
