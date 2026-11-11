package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.testing.fake.FakeAppLanguage
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsStore
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiLocaleInterceptorTest {
    private val server = MockWebServer()
    private val json = "application/json".toMediaType()

    @Before fun setUp() {
        server.start()
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
    }

    @After fun tearDown() = server.close()

    private fun client(): OkHttpClient {
        val info = AppBuildInfo(defaultBaseUrl = server.url("/").toString(), flavor = "local", versionName = "t", debug = false, cleartextAllowed = true)
        val provider = BaseUrlProvider(FakeSettingsStore(AppSettings()), info)
        return OkHttpClient.Builder()
            .addInterceptor(AiLocaleInterceptor(FakeAppLanguage("de")))
            .addInterceptor(BaseUrlInterceptor(provider))
            .build()
    }

    private fun send(request: Request.Builder): String {
        client().newCall(request.build()).execute().close()
        return server.takeRequest().body?.utf8().orEmpty()
    }

    private fun post(path: String, body: String) =
        Request.Builder().url("http://travelplanner.invalid$path").post(body.toRequestBody(json))

    @Test
    fun `search body gains locale de`() {
        assertEquals("""{"origin":"NYC","locale":"de"}""", send(post("/api/search", """{"origin":"NYC"}""")))
    }

    @Test
    fun `keeps an existing locale`() {
        val body = """{"locale":"fr","a":1}"""
        assertEquals(body, send(post("/api/chat", body)))
    }

    @Test
    fun `plans POST is untouched`() {
        val body = """{"name":"x"}"""
        assertEquals(body, send(post("/api/plans", body)))
    }

    @Test
    fun `GET is untouched`() {
        assertEquals("", send(Request.Builder().url("http://travelplanner.invalid/api/search").get()))
    }

    @Test
    fun `multipart import is untouched`() {
        val multi = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("url", "https://x").build()
        val sent = send(Request.Builder().url("http://travelplanner.invalid/api/import/extract").post(multi))
        assertTrue(sent.contains("""name="url""""))
        assertTrue(!sent.contains("locale"))
    }

    @Test
    fun `foreign host is untouched`() {
        // Not the placeholder host: the request goes straight to the server with the body unchanged.
        val body = """{"origin":"NYC"}"""
        val req = Request.Builder().url(server.url("/api/search")).post(body.toRequestBody(json)).build()
        client().newCall(req).execute().close()
        assertEquals(body, server.takeRequest().body?.utf8())
    }

    @Test
    fun `non-object JSON is untouched`() {
        assertEquals("[1,2]", send(post("/api/search", "[1,2]")))
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        assertEquals("not json", send(post("/api/search", "not json")))
    }
}
