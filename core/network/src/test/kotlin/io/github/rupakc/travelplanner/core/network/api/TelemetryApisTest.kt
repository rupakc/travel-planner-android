package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class TelemetryApisTest {
    private lateinit var server: MockWebServer
    private lateinit var retrofit: Retrofit

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        retrofit =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(TravelJson.asConverterFactory("application/json".toMediaType()))
                .build()
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `config 404 is a Response with code 404, not an exception`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(404).body("{\"detail\":\"Not Found\"}").build())
            val r = retrofit.create(ClientConfigApi::class.java).config()
            assertEquals(404, r.code())
            assertFalse(r.isSuccessful)
            assertEquals("/api/config", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `config decodes enabled and max_batch`() =
        runBlocking {
            server.enqueue(
                MockResponse.Builder().body("""{"telemetry":{"enabled":true,"flush_interval_s":20,"max_batch":40},"extra":1}""").build(),
            )
            val t = retrofit.create(ClientConfigApi::class.java).config().body()!!.telemetry!!
            assertTrue(t.enabled)
            assertEquals(40, t.maxBatch)
            assertEquals(20, t.flushIntervalS)
        }

    @Test
    fun `send posts to api telemetry with Content-Encoding gzip and the raw body bytes`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().code(204).build())
            val bytes = byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 1, 2, 3)
            val r = retrofit.create(TelemetryApi::class.java).send("gzip", bytes.toRequestBody("application/json".toMediaType()))
            assertTrue(r.isSuccessful)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/telemetry", req.url.encodedPath)
            assertEquals("gzip", req.headers["Content-Encoding"])
            assertArrayEquals(bytes, req.body!!.toByteArray())
        }
}
