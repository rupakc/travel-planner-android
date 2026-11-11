package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.data.impl.FeedbackRepositoryImpl
import io.github.rupakc.travelplanner.core.model.feedback.AnalyticsEvent
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.ClientConfigApi
import io.github.rupakc.travelplanner.core.network.api.FeedbackApi
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `/api/config` feature detection per server, and the legacy analytics gate. */
class ClientConfigRepositoryImplTest {
    private val server = MockWebServer().apply { start() }
    private val other = MockWebServer().apply { start() }
    private val settings = FakeSettingsStore(AppSettings())
    private val provider =
        BaseUrlProvider(
            settings,
            AppBuildInfo(defaultBaseUrl = server.url("/").toString(), flavor = "local", versionName = "t", debug = false, cleartextAllowed = true),
        )
    private val net = TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO))
    private val repo = ClientConfigRepositoryImpl(net.api(), provider)

    @After fun tearDown() {
        server.close()
        other.close()
    }

    private fun MockWebServer.config(body: String) = enqueue(MockResponse.Builder().code(200).body(body).addHeader("Content-Type", "application/json").build())

    @Test fun `404 is cached for the server`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(404).build())
            assertNull(repo.telemetrySettings())
            assertNull(repo.telemetrySettings())
            assertEquals(1, server.requestCount)
        }

    @Test fun `enabled false is null`() =
        runTest {
            server.config("""{"telemetry":{"enabled":false}}""")
            assertNull(repo.telemetrySettings())
        }

    @Test fun `error is null and cached`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(500).build())
            assertNull(repo.telemetrySettings())
            assertNull(repo.telemetrySettings())
            assertEquals(1, server.requestCount)
        }

    @Test fun `refetches after a base URL change`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(404).build())
            server.config("""{"telemetry":{"enabled":true}}""")
            assertNull(repo.telemetrySettings())
            settings.setBaseUrlOverride(other.url("/").toString())
            assertNotNull(repo.telemetrySettings())
            assertEquals(2, server.requestCount)
        }

    @Test fun `defaults when enabled with no values`() =
        runTest {
            server.config("""{"telemetry":{"enabled":true}}""")
            val s = repo.telemetrySettings()!!
            assertEquals(15_000L, s.flushIntervalMs)
            assertEquals(50, s.maxBatch)
        }

    @Test fun `clamps flush interval and max batch`() =
        runTest {
            server.config("""{"telemetry":{"enabled":true,"flush_interval_s":9999,"max_batch":0}}""")
            val s = repo.telemetrySettings()!!
            assertEquals(300_000L, s.flushIntervalMs)
            assertEquals(1, s.maxBatch)
        }

    @Test fun `sendAnalytics is skipped when not allowed`() =
        runTest {
            val consent = FakeConsentRepository(initiallyAllowed = false)
            val feedback = FeedbackRepositoryImpl(net.api<FeedbackApi>(), consent)
            val events = listOf(AnalyticsEvent(feature = "x", page = "p"))
            val result = feedback.sendAnalytics(events)
            assertTrue(result is io.github.rupakc.travelplanner.core.common.AppResult.Success)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse.Builder().code(200).build())
            consent.allowedValue = true
            feedback.sendAnalytics(events)
            assertEquals(1, server.requestCount)
        }
}
