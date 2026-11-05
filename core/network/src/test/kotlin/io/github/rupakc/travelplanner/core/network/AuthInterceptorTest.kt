package io.github.rupakc.travelplanner.core.network

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.api.AuthApi
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class AuthInterceptorTest {
    private val server = MockWebServer()
    private val user = User("alice")

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun network(session: Session?) =
        TestNetwork(server.url("/").toString(), FakeSessionStore(session), TestAppDispatchers(StandardTestDispatcher()))

    @Test
    fun `adds bearer token when signed in`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"username":"alice"}""").build())
        val net = network(Session("tok-1", user))
        net.api<AuthApi>().me()
        assertEquals("Bearer tok-1", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `401 clears the session and emits SessionExpired`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"detail":"Invalid or expired token"}""").build())
        val net = network(Session("tok-1", user))
        net.authEvents.flow.test {
            val result = runCatching { net.api<AuthApi>().me() }
            assertEquals(401, (result.exceptionOrNull() as HttpException).code())
            assertEquals(AuthEvent.SessionExpired, awaitItem())
        }
        assertNull(net.sessionStore.current())
    }

    @Test
    fun `401 without a token leaves state alone`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"detail":"Not authenticated"}""").build())
        val net = network(null)
        runCatching { net.api<AuthApi>().me() }
        assertNull(server.takeRequest().headers["Authorization"])
        assertNull(net.sessionStore.current())
    }

    @Test
    fun `base url path prefix is kept`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"username":"alice"}""").build())
        val net = TestNetwork(server.url("/prefix").toString(), FakeSessionStore(), TestAppDispatchers(StandardTestDispatcher()))
        net.api<AuthApi>().me()
        assertEquals("/prefix/api/auth/me", server.takeRequest().target)
    }
}
