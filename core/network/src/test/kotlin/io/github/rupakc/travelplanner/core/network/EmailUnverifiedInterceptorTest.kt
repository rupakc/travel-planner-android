package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsStore
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmailUnverifiedInterceptorTest {
    private val server = MockWebServer()
    private val gate = EmailVerificationGate()

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun call(path: String, status: Int, body: String): String {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
        val info = AppBuildInfo(defaultBaseUrl = server.url("/").toString(), flavor = "local", versionName = "t", debug = false, cleartextAllowed = true)
        val client =
            OkHttpClient.Builder()
                .addInterceptor(EmailUnverifiedInterceptor(gate))
                .addInterceptor(BaseUrlInterceptor(BaseUrlProvider(FakeSettingsStore(AppSettings()), info)))
                .build()
        return client.newCall(Request.Builder().url("http://travelplanner.invalid$path").build()).execute().use { it.body.string() }
    }

    @Test
    fun `403 email_unverified raises the gate and keeps the body readable`() {
        val body = """{"detail":"Confirm your email","error_code":"email_unverified"}"""
        assertEquals(body, call("/api/search", 403, body))
        assertTrue(gate.blocked.value)
    }

    @Test
    fun `other 403 codes and social routes leave the gate alone`() {
        call("/api/search", 403, """{"detail":"x","error_code":"account_pending"}""")
        assertFalse(gate.blocked.value)
        call("/api/social/ideas", 403, """{"detail":"x","error_code":"email_unverified"}""")
        assertFalse(gate.blocked.value)
        call("/api/search", 200, "{}")
        assertFalse(gate.blocked.value)
    }

    @Test
    fun `clear lowers the gate`() {
        gate.markBlocked()
        gate.clear()
        assertFalse(gate.blocked.value)
    }
}
