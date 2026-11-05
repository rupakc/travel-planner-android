package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppException
import io.github.rupakc.travelplanner.core.model.chat.ChatMessage
import io.github.rupakc.travelplanner.core.model.chat.ChatRequest
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.network.sse.ChatStreamClient
import io.github.rupakc.travelplanner.core.network.sse.SseClient
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.TestNetwork
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Review I1/I2: cancelling a quiet stream must close the call at once; a dead connection must time out. */
class StreamCancellationTest {
    private val server = MockWebServer()
    private val raw = QuietServer()
    private val dispatchers = TestAppDispatchers(Dispatchers.IO)
    private val request = ChatRequest(messages = listOf(ChatMessage("user", "hi")))
    private val firstEvent = "data: {\"type\":\"delta\",\"text\":\"hi\"}\n\n"

    @Before fun setUp() = server.start()

    @After
    fun tearDown() {
        server.close()
        raw.close()
    }

    private fun client(baseUrl: String = server.url("/").toString(), configure: (OkHttpClient.Builder) -> Unit = {}): ChatStreamClient {
        val net = TestNetwork(baseUrl, dispatchers = dispatchers)
        val http = net.client.newBuilder().apply(configure).build()
        return ChatStreamClient(SseClient(http, dispatchers), dispatchers)
    }

    @Test
    fun `cancelling the collector closes a quiet stream within a second`() =
        runBlocking {
            val url = raw.start(firstEvent)
            val received = CompletableDeferred<ChatEvent>()
            val job = launch(Dispatchers.Default) { client(url).stream(request).collect { received.complete(it) } }
            assertEquals(ChatEvent.Delta("hi"), withTimeout(5_000) { received.await() })
            val start = System.nanoTime()
            job.cancel()
            withTimeout(5_000) { job.join() }
            val millis = (System.nanoTime() - start) / 1_000_000
            assertTrue("join took $millis ms", millis < 1_000)
            assertTrue("socket closed by client", raw.awaitClientClose(2_000))
        }

    @Test
    fun `a silent connection ends as Interrupted after the idle read timeout`() =
        runBlocking {
            val url = raw.start(firstEvent)
            val events = mutableListOf<ChatEvent>()
            val error =
                withTimeout(10_000) {
                    runCatching { client(url) { it.readTimeout(Duration.ofMillis(500)) }.stream(request).collect { events += it } }.exceptionOrNull()
                }
            assertEquals(listOf<ChatEvent>(ChatEvent.Delta("hi")), events)
            assertTrue((error as AppException).error is AppError.Interrupted)
        }

    @Test
    fun `a connection reset mid-stream ends as Interrupted, not a clean end`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().body(firstEvent + "data: {\"type\":\"del").onResponseBody(SocketEffect.ShutdownConnection).build())
            val error = withTimeout(10_000) { runCatching { client().stream(request).collect { } }.exceptionOrNull() }
            assertTrue("got $error", (error as? AppException)?.error is AppError.Interrupted)
        }

    @Test
    fun `streaming client idle timeout is 300 s with no call timeout`() {
        val base = OkHttpClient()
        val streaming = io.github.rupakc.travelplanner.core.network.di.NetworkModule.streamingOkHttp(base)
        assertEquals(300_000, streaming.readTimeoutMillis)
        assertEquals(0, streaming.callTimeoutMillis)
    }
}

/** A raw HTTP server that sends headers + [firstEvent] and then stays silent (no Content-Length) until closed. */
private class QuietServer : AutoCloseable {
    private val socket = java.net.ServerSocket(0)
    private val clientClosed = java.util.concurrent.CountDownLatch(1)

    fun start(firstEvent: String): String {
        Thread {
            runCatching {
                socket.accept().use { s ->
                    val input = s.getInputStream().bufferedReader()
                    var contentLength = 0
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:", ignoreCase = true)) contentLength = line.substringAfter(':').trim().toInt()
                    }
                    repeat(contentLength) { input.read() }
                    val out = s.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nConnection: close\r\n\r\n$firstEvent".toByteArray())
                    out.flush()
                    // Blocks until the client closes its side (read returns -1 or throws).
                    runCatching { while (s.getInputStream().read() != -1) Unit }
                    clientClosed.countDown()
                }
            }
        }.apply { isDaemon = true }.start()
        return "http://127.0.0.1:${socket.localPort}/"
    }

    fun awaitClientClose(millis: Long): Boolean = clientClosed.await(millis, java.util.concurrent.TimeUnit.MILLISECONDS)

    override fun close() = socket.close()
}
