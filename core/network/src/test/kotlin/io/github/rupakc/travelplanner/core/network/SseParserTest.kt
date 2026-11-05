package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.network.sse.SseParser
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    private fun parse(text: String) = SseParser.payloads(text.lineSequence()).toList()

    @Test
    fun `data lines become payloads and keepalive comments are ignored`() {
        val text = "data: {\"type\":\"a\"}\n\n: keepalive\n\ndata: {\"type\":\"b\"}\n\n"
        assertEquals(listOf("{\"type\":\"a\"}", "{\"type\":\"b\"}"), parse(text))
    }

    @Test
    fun `consecutive data lines of one event are joined with newline`() {
        val text = "data: {\"type\":\ndata: \"multi\"}\n\n"
        assertEquals(listOf("{\"type\":\n\"multi\"}"), parse(text))
    }

    @Test
    fun `event and id fields and CRLF line endings are tolerated`() {
        val text = "event: message\r\nid: 7\r\ndata: {\"x\":1}\r\n\r\nretry: 10\r\n"
        assertEquals(listOf("{\"x\":1}"), parse(text))
    }

    @Test
    fun `a final event without trailing blank line is still delivered and nothing is invented without done`() {
        val text = "data: {\"type\":\"flights\"}\n\ndata: {\"type\":\"hotels\"}"
        assertEquals(listOf("{\"type\":\"flights\"}", "{\"type\":\"hotels\"}"), parse(text))
    }

    @Test
    fun `data without space after colon is accepted`() {
        assertEquals(listOf("{}"), parse("data:{}\n\n"))
    }

    @Test
    fun `partial chunks across reads are reassembled`() {
        val payload = "data: {\"type\":\"visa\",\"data\":{}}\n\n: keepalive\n\ndata: {\"type\":\"done\"}\n\n"
        val source = ChunkedSource(payload.encodeToByteArray(), chunk = 3).buffer()
        assertEquals(listOf("{\"type\":\"visa\",\"data\":{}}", "{\"type\":\"done\"}"), SseParser.payloads(source).toList())
    }

    /** Emits at most [chunk] bytes per read, like a slow network. */
    private class ChunkedSource(private val bytes: ByteArray, private val chunk: Int) : Source {
        private var position = 0

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (position >= bytes.size) return -1
            val n = minOf(chunk, bytes.size - position, byteCount.toInt())
            sink.write(bytes, position, n)
            position += n
            return n.toLong()
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }
}
