package io.github.rupakc.travelplanner.core.network

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.api.AuthApi
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

/** Accounts-API "Mobile flow" 4: single-flight refresh, rotation, stale/invalid/429/offline handling. */
class SessionRefreshTest {
    private val server = MockWebServer()
    private val user = User("alice")
    private val now = 1_000_000_000_000L
    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.ofEpochMilli(now)

            override fun zone() = ZoneOffset.UTC
        }

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun session(access: String = "at-old", refresh: String? = "rt-old", expires: Long? = now + 3_600_000) =
        Session(access, user, baseUrl = null, refreshToken = refresh, expiresAtEpochMs = expires)

    private fun network(s: Session?, store: FakeSessionStore = FakeSessionStore(s)) =
        TestNetwork(server.url("/").toString(), store, TestAppDispatchers(Dispatchers.IO), clock)

    private fun json(code: Int, body: String, vararg headers: Pair<String, String>) =
        MockResponse.Builder().code(code).body(body).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()

    private val refreshOk = """{"access_token":"at-new","token_type":"bearer","expires_in":3600,"refresh_token":"rt-new"}"""

    private fun dispatcher(onRefresh: (RecordedRequest) -> MockResponse) =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when {
                    request.url.encodedPath.endsWith("/auth/refresh") -> onRefresh(request)
                    request.headers["Authorization"] == "Bearer at-new" -> json(200, """{"username":"alice"}""")
                    else -> json(401, """{"detail":"expired"}""")
                }
        }

    @Test
    fun `20 parallel 401s cause exactly one refresh call`() =
        runTest {
            val refreshes = AtomicInteger()
            server.dispatcher = dispatcher { refreshes.incrementAndGet().let { json(200, refreshOk) } }
            val net = network(session())
            withContext(Dispatchers.IO) { (1..20).map { async { net.api<AuthApi>().me() } }.awaitAll() }
            assertEquals(1, refreshes.get())
        }

    @Test
    fun `rotated tokens are persisted before the retry`() =
        runTest {
            var storedAtRetry: Session? = null
            val net = network(session())
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        when {
                            request.url.encodedPath.endsWith("/auth/refresh") -> {
                                json(200, refreshOk)
                            }

                            request.headers["Authorization"] == "Bearer at-new" -> {
                                storedAtRetry = (net.sessionStore as FakeSessionStore).state.value
                                json(200, """{"username":"alice"}""")
                            }

                            else -> {
                                json(401, "{}")
                            }
                        }
                }
            net.api<AuthApi>().me()
            assertEquals("rt-new", storedAtRetry?.refreshToken)
            assertEquals("at-new", storedAtRetry?.token)
            assertEquals(now + 3_600_000, storedAtRetry?.expiresAtEpochMs)
        }

    @Test
    fun `refresh_stale re-reads the newest stored token and retries once`() =
        runTest {
            val store = FakeSessionStore(session())
            val used = mutableListOf<String>()
            server.dispatcher =
                dispatcher { req ->
                    val body = req.body?.utf8().orEmpty()
                    used += body
                    if (used.size == 1) {
                        // Another process rotated meanwhile.
                        store.state.value = session(refresh = "rt-other")
                        json(401, """{"error_code":"refresh_stale"}""")
                    } else {
                        json(200, refreshOk)
                    }
                }
            network(null, store).api<AuthApi>().me()
            assertEquals(2, used.size)
            assertTrue(used[0].contains("rt-old"))
            assertTrue(used[1].contains("rt-other"))
        }

    @Test
    fun `refresh_stale twice is treated as invalid`() =
        runTest {
            server.dispatcher = dispatcher { json(401, """{"error_code":"refresh_stale"}""") }
            val net = network(session())
            net.authEvents.flow.test {
                runCatching { net.api<AuthApi>().me() }
                assertEquals(AuthEvent.SessionExpired, awaitItem())
            }
            assertNull(net.sessionStore.current())
        }

    @Test
    fun `refresh_invalid signs out and emits SessionExpired`() =
        runTest {
            server.dispatcher = dispatcher { json(401, """{"error_code":"refresh_invalid"}""") }
            val net = network(session())
            net.authEvents.flow.test {
                val failure = runCatching { net.api<AuthApi>().me() }.exceptionOrNull()
                assertEquals(401, (failure as HttpException).code())
                assertEquals(AuthEvent.SessionExpired, awaitItem())
            }
            assertNull(net.sessionStore.current())
        }

    @Test
    fun `429 on refresh keeps the session`() =
        runTest {
            server.dispatcher = dispatcher { json(429, """{"detail":"slow down"}""", "Retry-After" to "60") }
            val net = network(session())
            runCatching { net.api<AuthApi>().me() }
            assertEquals("rt-old", net.sessionStore.current()?.refreshToken)
        }

    @Test
    fun `short Retry-After is waited out then the refresh succeeds`() =
        runTest {
            val calls = AtomicInteger()
            server.dispatcher =
                dispatcher { if (calls.incrementAndGet() == 1) json(429, "{}", "Retry-After" to "0") else json(200, refreshOk) }
            val net = network(session())
            net.api<AuthApi>().me()
            assertEquals(2, calls.get())
            assertEquals("rt-new", net.sessionStore.current()?.refreshToken)
        }

    @Test
    fun `an offline refresh keeps the session`() =
        runTest {
            server.dispatcher =
                dispatcher { MockResponse.Builder().onRequestStart(mockwebserver3.SocketEffect.CloseSocket()).build() }
            val net = network(session())
            runCatching { net.api<AuthApi>().me() }
            assertEquals("rt-old", net.sessionStore.current()?.refreshToken)
            assertNotNull(net.sessionStore.current())
        }

    @Test
    fun `session_revoked refreshes once then signs out when still rejected`() =
        runTest {
            val refreshes = AtomicInteger()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.url.encodedPath.endsWith("/auth/refresh")) {
                            refreshes.incrementAndGet()
                            json(200, refreshOk)
                        } else {
                            json(401, """{"error_code":"session_revoked"}""")
                        }
                }
            val net = network(session())
            net.authEvents.flow.test {
                runCatching { net.api<AuthApi>().me() }
                assertEquals(AuthEvent.SessionExpired, awaitItem())
            }
            assertEquals(1, refreshes.get())
            assertNull(net.sessionStore.current())
        }

    @Test
    fun `an access token near expiry is refreshed before the call`() =
        runTest {
            server.dispatcher = dispatcher { json(200, refreshOk) }
            val net = network(session(expires = now + 30_000))
            net.api<AuthApi>().me()
            assertTrue(server.takeRequest().url.encodedPath.endsWith("/auth/refresh"))
            assertEquals("Bearer at-new", server.takeRequest().headers["Authorization"])
        }

    @Test
    fun `a token-only session keeps the old behaviour and never calls refresh`() =
        runTest {
            server.enqueue(json(401, """{"detail":"Invalid or expired token"}"""))
            val net = network(Session("tok", user))
            net.authEvents.flow.test {
                runCatching { net.api<AuthApi>().me() }
                assertEquals(AuthEvent.SessionExpired, awaitItem())
            }
            assertEquals(1, server.requestCount)
            assertNull(net.sessionStore.current())
        }

    @Test
    fun `the token is never sent to a foreign host, and a foreign 401 never refreshes`() =
        runTest {
            val other = MockWebServer().apply { start() }
            other.enqueue(json(401, "{}"))
            val net = network(session())
            net.client.newCall(Request.Builder().url(other.url("/x")).build()).execute().close()
            assertNull(other.takeRequest().headers["Authorization"])
            assertEquals(0, server.requestCount)
            assertEquals("at-old", net.sessionStore.current()?.token)
            other.close()
        }
}
