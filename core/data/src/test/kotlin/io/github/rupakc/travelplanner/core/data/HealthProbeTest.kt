package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.HealthRepositoryImpl
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Settings → Server "Test connection": a one-off, unauthenticated GET of a candidate server. */
class HealthProbeTest {
    private val configured = MockWebServer()
    private val candidate = MockWebServer()

    @Before fun setUp() {
        configured.start()
        candidate.start()
    }

    @After fun tearDown() {
        configured.close()
        candidate.close()
    }

    private suspend fun repository(): HealthRepositoryImpl {
        val sessions = FakeSessionStore().apply { save(Session("secret-token", User("alice"))) }
        val net = TestNetwork(configured.url("/").toString(), sessionStore = sessions, dispatchers = TestAppDispatchers(Dispatchers.IO))
        return HealthRepositoryImpl(
            net.api(),
            net.client,
            TestAppDispatchers(Dispatchers.IO),
            net.baseUrlProvider,
            MemoryHealthCache(),
            io.github.rupakc.travelplanner.core.common.SystemAppClock(),
        )
    }

    @Test
    fun `probe hits the candidate without the session token and leaves capabilities alone`() =
        runTest {
            candidate.enqueue(MockResponse.Builder().code(200).body("""{"status":"ok","serpapi_configured":true,"push_configured":true}""").build())
            val repo = repository()
            val result = repo.probe(candidate.url("/api-prefix/").toString())
            val health = (result as AppResult.Success).value
            assertTrue(health.serpapiConfigured && health.pushConfigured)
            val recorded = candidate.takeRequest()
            assertEquals("/api-prefix/health", recorded.url.encodedPath)
            assertNull(recorded.headers["Authorization"])
            assertEquals(0, configured.requestCount)
            assertNull(repo.capabilities.value)
        }

    @Test
    fun `probe maps http and connection failures`() =
        runTest {
            candidate.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            val repo = repository()
            assertEquals(AppError.Server(503, "down"), (repo.probe(candidate.url("/").toString()) as AppResult.Failure).error)
            val unreachable = repo.probe("http://127.0.0.1:1")
            assertTrue((unreachable as AppResult.Failure).error is AppError.Network)
            assertTrue((repo.probe("not a url") as AppResult.Failure).error is AppError.Client)
        }
}
