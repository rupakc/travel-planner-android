package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.api.AuthApi
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Review I3: the bearer token goes only to the configured backend it was issued by. */
class AuthScopeTest {
    private val server = MockWebServer()
    private val other = MockWebServer()

    @Before
    fun setUp() {
        server.start()
        other.start()
    }

    @After
    fun tearDown() {
        server.close()
        other.close()
    }

    private fun network(session: Session) =
        TestNetwork(server.url("/").toString(), FakeSessionStore(session), TestAppDispatchers(Dispatchers.IO))

    @Test
    fun `requests to any other host never carry the token`() =
        runTest {
            other.enqueue(MockResponse.Builder().code(200).body("img").build())
            val net = network(Session("tok", User("alice"), baseUrl = server.url("/").toString().trimEnd('/')))
            net.client.newCall(Request.Builder().url(other.url("/img.jpg")).build()).execute().close()
            assertNull(other.takeRequest().headers["Authorization"])
        }

    @Test
    fun `a 401 from another host does not sign the user out`() =
        runTest {
            other.enqueue(MockResponse.Builder().code(401).build())
            val net = network(Session("tok", User("alice")))
            net.client.newCall(Request.Builder().url(other.url("/x")).build()).execute().close()
            assertNotNull(net.sessionStore.current())
        }

    @Test
    fun `a token issued by another server is not sent after the server changes`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(200).body("""{"username":"alice"}""").build())
            val net = network(Session("tok", User("alice"), baseUrl = "https://old.example.com"))
            net.api<AuthApi>().me()
            assertNull(server.takeRequest().headers["Authorization"])
        }

    @Test
    fun `the token is sent to the server that issued it`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(200).body("""{"username":"alice"}""").build())
            val net = network(Session("tok", User("alice"), baseUrl = server.url("/").toString().trimEnd('/')))
            net.api<AuthApi>().me()
            assertEquals("Bearer tok", server.takeRequest().headers["Authorization"])
        }
}
