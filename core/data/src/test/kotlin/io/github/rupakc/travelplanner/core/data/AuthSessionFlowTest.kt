package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.SystemAppClock
import io.github.rupakc.travelplanner.core.data.impl.AuthRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.api.AccountApi
import io.github.rupakc.travelplanner.core.network.api.AuthApi
import io.github.rupakc.travelplanner.core.network.api.DevicesApi
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import java.util.Optional
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Login, sign-out and password change against MockWebServer (Accounts-API mobile flow). */
class AuthSessionFlowTest {
    private val server = MockWebServer()
    private var cleared = 0
    private val cleaner =
        object : SessionCleaner {
            override suspend fun clearUserData() {
                cleared++
            }
        }

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun json(code: Int, body: String = "") = MockResponse.Builder().code(code).body(body).build()

    private fun repo(store: FakeSessionStore): AuthRepositoryImpl {
        val net = TestNetwork(server.url("/").toString(), store, TestAppDispatchers(Dispatchers.IO))
        return AuthRepositoryImpl(
            net.api<AuthApi>(),
            io.mockk.mockk<DevicesApi>(relaxed = true),
            store,
            Optional.empty(),
            net.baseUrlProvider,
            cleaner,
            net.api<AccountApi>(),
            SystemAppClock(),
        )
    }

    @Test
    fun `login sends client android and stores the refresh token and expiry`() =
        runTest(timeout = 30.seconds) {
            server.enqueue(
                json(200, """{"access_token":"at","token_type":"bearer","expires_in":3600,"refresh_token":"rt_1","user":{"username":"jane","id":"u1"}}"""),
            )
            val store = FakeSessionStore()
            assertTrue(repo(store).login("jane", "pw") is AppResult.Success)
            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body, body.contains("\"client\":\"android\""))
            val s = store.current()
            assertEquals("rt_1", s?.refreshToken)
            assertEquals("u1", s?.userId)
            assertNotNull(s?.expiresAtEpochMs)
        }

    @Test
    fun `an old-backend login response without refresh_token still signs in`() =
        runTest(timeout = 30.seconds) {
            server.enqueue(json(200, """{"access_token":"at","token_type":"bearer","user":{"username":"jane"}}"""))
            val store = FakeSessionStore()
            assertTrue(repo(store).login("jane", "pw") is AppResult.Success)
            assertNull(store.current()?.refreshToken)
            assertNull(store.current()?.expiresAtEpochMs)
            assertEquals("at", store.current()?.token)
        }

    @Test
    fun `logout revokes the refresh family best effort then clears everything`() =
        runTest(timeout = 30.seconds) {
            // Real time: logout() bounds its network calls with withTimeoutOrNull, which virtual time would expire at once.
            withContext(Dispatchers.Default) {
                server.enqueue(json(204))
                val store = FakeSessionStore(Session("at", User("jane"), refreshToken = "rt_9"))
                repo(store).logout()
                val req = server.takeRequest()
                assertTrue(req.url.encodedPath.endsWith("/auth/logout"))
                assertTrue(req.body?.utf8().orEmpty().contains("rt_9"))
                assertNull(store.current())
                assertEquals(1, cleared)
            }
        }

    @Test
    fun `logout clears even when the server is unreachable`() =
        runTest(timeout = 30.seconds) {
            // Real time: logout() bounds its network calls with withTimeoutOrNull, which virtual time would expire at once.
            withContext(Dispatchers.Default) {
                server.close()
                val store = FakeSessionStore(Session("at", User("jane"), refreshToken = "rt_9"))
                repo(store).logout()
                assertNull(store.current())
                assertEquals(1, cleared)
            }
        }

    @Test
    fun `logoutAll unregisters the device before revoking, then clears locally`() =
        runTest(timeout = 30.seconds) {
            withContext(Dispatchers.Default) {
                server.enqueue(json(204))
                val store = FakeSessionStore(Session("at", User("jane"), refreshToken = "rt_9"))
                val devices = io.mockk.mockk<DevicesApi>()
                var requestsAtUnregister = -1
                io.mockk.coEvery { devices.unregister(any()) } coAnswers {
                    requestsAtUnregister = server.requestCount
                    retrofit2.Response.success(Unit)
                }
                val net = TestNetwork(server.url("/").toString(), store, TestAppDispatchers(Dispatchers.IO))
                val push = io.github.rupakc.travelplanner.core.data.repository.PushTokenSource { "fcm-1" }
                val repo =
                    AuthRepositoryImpl(
                        net.api<AuthApi>(),
                        devices,
                        store,
                        Optional.of(push),
                        net.baseUrlProvider,
                        cleaner,
                        net.api<AccountApi>(),
                        SystemAppClock(),
                    )
                assertTrue(repo.logoutAll() is AppResult.Success)
                assertEquals("unregistered while the token was still valid", 0, requestsAtUnregister)
                assertTrue(server.takeRequest().url.encodedPath.endsWith("/auth/logout-all"))
                assertNull(store.current())
                assertEquals(1, cleared)
            }
        }

    @Test
    fun `logoutAll clears on success and keeps the session on failure`() =
        runTest(timeout = 30.seconds) {
            server.enqueue(json(500, "{}"))
            server.enqueue(json(204))
            val store = FakeSessionStore(Session("at", User("jane"), refreshToken = "rt_9"))
            val repo = repo(store)
            assertTrue(repo.logoutAll() is AppResult.Failure)
            assertNotNull(store.current())
            assertTrue(repo.logoutAll() is AppResult.Success)
            assertNull(store.current())
            assertEquals(1, cleared)
        }

    @Test
    fun `changePassword stores the returned tokens`() =
        runTest(timeout = 30.seconds) {
            server.enqueue(json(200, """{"access_token":"at2","token_type":"bearer","expires_in":3600,"refresh_token":"rt_2"}"""))
            val store = FakeSessionStore(Session("at", User("jane", requiresPasswordChange = true), refreshToken = "rt_1"))
            assertTrue(repo(store).changePassword("old", "new-password") is AppResult.Success)
            val s = store.current()
            assertEquals("at2", s?.token)
            assertEquals("rt_2", s?.refreshToken)
            assertEquals(false, s?.user?.requiresPasswordChange)
        }
}
