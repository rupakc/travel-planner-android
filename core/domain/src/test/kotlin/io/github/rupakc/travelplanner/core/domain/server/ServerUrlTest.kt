package io.github.rupakc.travelplanner.core.domain.server

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {
    private fun valid(url: String) = ServerUrlResult.Valid(url)

    @Test
    fun `accepts http and https and normalises`() {
        assertEquals(valid("http://10.0.2.2:8001"), ServerUrl.validate("http://10.0.2.2:8001"))
        assertEquals(valid("https://travel.example.com"), ServerUrl.validate("  HTTPS://Travel.Example.com/ "))
        assertEquals(valid("http://192.168.1.20:8001/api-prefix"), ServerUrl.validate("http://192.168.1.20:8001/api-prefix//"))
        assertEquals(valid("http://[::1]:8001"), ServerUrl.validate("http://[::1]:8001"))
    }

    @Test
    fun `rejects blank, malformed and other schemes`() {
        assertEquals(ServerUrlResult.Blank, ServerUrl.validate("   "))
        assertEquals(ServerUrlResult.Malformed, ServerUrl.validate("192.168.1.20:8001"))
        assertEquals(ServerUrlResult.Malformed, ServerUrl.validate("http://exa mple.com"))
        assertEquals(ServerUrlResult.Malformed, ServerUrl.validate("http://example.com/?q=1"))
        assertEquals(ServerUrlResult.Malformed, ServerUrl.validate("http://example.com/#top"))
        assertEquals(ServerUrlResult.Malformed, ServerUrl.validate("http://user:pw@example.com"))
        assertEquals(ServerUrlResult.UnsupportedScheme, ServerUrl.validate("ftp://example.com"))
        assertEquals(ServerUrlResult.MissingHost, ServerUrl.validate("http://"))
    }

    @Test
    fun `https only when http is not allowed`() {
        assertEquals(ServerUrlResult.HttpsRequired, ServerUrl.validate("http://example.com", allowHttp = false))
        assertEquals(valid("https://example.com"), ServerUrl.validate("https://example.com", allowHttp = false))
    }
}
