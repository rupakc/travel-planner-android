package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.domain.telemetry.SendOutcome
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import io.github.rupakc.travelplanner.core.testing.fake.FakeAppLanguage
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TelemetryUploaderTest {
    private val server = MockWebServer()
    private val language = FakeAppLanguage("en")

    @Before fun setUp() = server.start()

    @After fun tearDown() = server.close()

    private fun uploader(): TelemetryUploader {
        val net = TestNetwork(server.url("/").toString(), dispatchers = TestAppDispatchers(Dispatchers.IO))
        val info = AppBuildInfo("http://x", "local", "1.2.3 beta", debug = false, cleartextAllowed = true)
        return TelemetryUploader(net.api(), FixedIdPrefs(), info, language, FixedClock())
    }

    @Test
    fun `body is gzip and decodes to platform android`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(204).build())
            val props = mapOf<String, Any>("target" to "save", "count" to 3L, "ratio" to 0.5, "on" to true)
            val outcome = uploader().send("sess-1", listOf(event(props = props)))
            assertEquals(SendOutcome.Sent, outcome)
            val request = server.takeRequest()
            assertEquals("gzip", request.headers["Content-Encoding"])
            assertEquals("/api/telemetry", request.url.encodedPath)
            val text = GZIPInputStream(request.body!!.toByteArray().inputStream()).readBytes().decodeToString()
            val root = TravelJson.parseToJsonElement(text).jsonObject
            val client = root["client"]!!.jsonObject
            assertEquals("android", client["platform"]!!.jsonPrimitive.content)
            assertEquals("sess-1", client["session_id"]!!.jsonPrimitive.content)
            assertEquals("11111111-1111-4111-8111-111111111111", client["anon_id"]!!.jsonPrimitive.content)
            assertEquals("1.2.3_beta", client["app_version"]!!.jsonPrimitive.content)
            val first = root["events"]!!.jsonArray.single().jsonObject
            assertEquals("ui_tap", first["event"]!!.jsonPrimitive.content)
            val sent = first["props"]!!.jsonObject
            assertEquals(JsonPrimitive(3L), sent["count"])
            assertEquals(0.5, sent["ratio"]!!.jsonPrimitive.content.toDouble(), 0.0)
            assertEquals(true, sent["on"]!!.jsonPrimitive.booleanOrNull)
        }

    @Test
    fun `locale is zh-CN for zh`() =
        runTest {
            language.code = "zh"
            server.enqueue(MockResponse.Builder().code(204).build())
            uploader().send("s", listOf(event()))
            val text = GZIPInputStream(server.takeRequest().body!!.toByteArray().inputStream()).readBytes().decodeToString()
            assertEquals("zh-CN", TravelJson.parseToJsonElement(text).jsonObject["client"]!!.jsonObject["locale"]!!.jsonPrimitive.content)
        }

    @Test
    fun `statuses map through the send policy`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(204).build())
            server.enqueue(MockResponse.Builder().code(404).build())
            server.enqueue(MockResponse.Builder().code(422).build())
            server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "30").build())
            val u = uploader()
            assertEquals(SendOutcome.Sent, u.send("s", listOf(event())))
            assertEquals(SendOutcome.Disabled, u.send("s", listOf(event())))
            assertEquals(SendOutcome.Dropped, u.send("s", listOf(event())))
            assertEquals(SendOutcome.RetryAfter(30_000), u.send("s", listOf(event())))
        }

    @Test
    fun `socket failure is Retry`() =
        runTest {
            server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
            assertEquals(SendOutcome.Retry, uploader().send("s", listOf(event())))
            assertTrue(server.requestCount >= 1)
        }
}
