package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.data.impl.CachedHealth
import io.github.rupakc.travelplanner.core.data.impl.HealthCacheStore
import io.github.rupakc.travelplanner.core.data.impl.HealthRepositoryImpl
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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

/** In-memory [HealthCacheStore]. */
class MemoryHealthCache(var value: CachedHealth? = null) : HealthCacheStore {
    override fun read(): CachedHealth? = value

    override fun write(value: CachedHealth) {
        this.value = value
    }
}

/** `/health` capabilities are persisted per server and refreshed only when older than the TTL (performance review I1). */
class HealthCapabilitiesTest {
    private val server = MockWebServer()
    private val cache = MemoryHealthCache()
    private val clock =
        object : AppClock {
            var instant: Instant = Instant.parse("2026-11-05T10:00:00Z")

            override fun now(): Instant = instant

            override fun zone(): ZoneId = ZoneId.of("UTC")
        }

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private val serverUrl get() = server.url("/").toString().trimEnd('/')

    private fun repository(): HealthRepositoryImpl {
        val net = TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO))
        return HealthRepositoryImpl(net.api(), net.client, TestAppDispatchers(Dispatchers.IO), net.baseUrlProvider, cache, clock)
    }

    private fun ok(push: Boolean) = MockResponse.Builder().code(200).body("""{"status":"ok","push_configured":$push}""").build()

    @Test
    fun `a fresh process restores the persisted capabilities without a call`() =
        runTest {
            cache.value = CachedHealth(serverUrl, Health(status = "ok", pushConfigured = true), clock.instant.toEpochMilli())
            val repo = repository()
            repo.restore()
            assertEquals(true, repo.capabilities.value?.pushConfigured)
            assertEquals(true, repo.refreshIfStale()?.pushConfigured)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `refresh persists, and a stale answer is fetched again`() =
        runTest {
            server.enqueue(ok(push = false))
            val repo = repository()
            assertEquals(false, repo.refreshIfStale()?.pushConfigured)
            assertEquals(1, server.requestCount)
            assertEquals(serverUrl, cache.value?.server)
            clock.instant = clock.instant.plus(Duration.ofHours(5))
            repo.refreshIfStale()
            assertEquals(1, server.requestCount)
            clock.instant = clock.instant.plus(Duration.ofHours(2))
            server.enqueue(ok(push = true))
            assertEquals(true, repo.refreshIfStale()?.pushConfigured)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a failed refresh keeps the last known answer`() =
        runTest {
            cache.value = CachedHealth(serverUrl, Health(pushConfigured = true), clock.instant.minus(Duration.ofDays(2)).toEpochMilli())
            server.enqueue(MockResponse.Builder().code(503).body("""{"detail":"down"}""").build())
            assertEquals(true, repository().refreshIfStale()?.pushConfigured)
        }

    @Test
    fun `another server's capabilities are never applied`() =
        runTest {
            cache.value = CachedHealth("https://old.example", Health(pushConfigured = true), clock.instant.toEpochMilli())
            val repo = repository()
            repo.restore()
            assertNull(repo.capabilities.value)
            server.enqueue(ok(push = false))
            assertEquals(false, repo.refreshIfStale()?.pushConfigured)
            assertTrue(server.requestCount == 1)
        }
}
