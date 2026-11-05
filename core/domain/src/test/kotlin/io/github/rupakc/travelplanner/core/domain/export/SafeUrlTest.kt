package io.github.rupakc.travelplanner.core.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Port of the `safeUrl` cases in the web's `planPrint.test.js`. */
class SafeUrlTest {
    @Test
    fun `keeps http and https links`() {
        assertEquals("https://example.com/a?b=1", safeUrl("https://example.com/a?b=1"))
        assertEquals("http://example.com/", safeUrl("http://example.com"))
    }

    @Test
    fun `drops script and other non-web links`() {
        assertNull(safeUrl("javascript:alert(1)"))
        assertNull(safeUrl("data:text/html,hi"))
        assertNull(safeUrl("not a url"))
        assertNull(safeUrl(null))
        assertNull(safeUrl("https://"))
        assertNull(safeUrl("ftp://example.com/file"))
    }
}
