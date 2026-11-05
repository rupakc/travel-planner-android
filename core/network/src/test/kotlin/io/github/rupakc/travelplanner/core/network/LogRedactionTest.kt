package io.github.rupakc.travelplanner.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Debug HTTP logs never contain share tokens (security review S-M3). */
class LogRedactionTest {
    @Test
    fun `share tokens and token-like segments are masked, ordinary paths are not`() {
        assertEquals(
            "--> GET http://10.0.2.2:8001/api/share/<redacted>",
            LogRedaction.redact("--> GET http://10.0.2.2:8001/api/share/Xk3_pQ9-aZ"),
        )
        assertEquals(
            "<-- 200 OK http://h/api/share/<redacted>?x=1 (12ms, 1.2 kB body)",
            LogRedaction.redact("<-- 200 OK http://h/api/share/abc123?x=1 (12ms, 1.2 kB body)"),
        )
        assertEquals("--> GET http://h/api/v/<redacted>/x", LogRedaction.redact("--> GET http://h/api/v/a1B2c3D4e5F6g7H8i9J0k1L2m3/x"))
        assertEquals("--> GET http://h/api/x?access_token=<redacted>&a=1", LogRedaction.redact("--> GET http://h/api/x?access_token=s3cr3t&a=1"))
        val plain = "--> POST http://h/api/plans/42/share"
        assertEquals(plain, LogRedaction.redact(plain))
        val words = "--> GET http://h/api/notifications/unread-count-and-more-words"
        assertEquals(words, LogRedaction.redact(words))
    }

    @Test
    fun `the logger passes only redacted lines on`() {
        val lines = mutableListOf<String>()
        RedactingHttpLogger { lines += it }.log("--> GET http://h/api/share/tok3n")
        assertFalse(lines.single().contains("tok3n"))
    }
}
