package io.github.rupakc.travelplanner.core.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Port of the web `safeStart` (SocialSignInButtons.jsx): only a same-origin `/api/auth/` path is ever opened. */
class SafeStartPathTest {
    private val base = "https://voyager.example/"

    @Test
    fun `relative good path resolves against the base and keeps the query`() {
        assertEquals(
            "https://voyager.example/api/auth/oauth/google/start?next=%2Fhome",
            SafeStartPath.resolve("/api/auth/oauth/google/start?next=%2Fhome", base),
        )
    }

    @Test
    fun `absolute same-origin good path is accepted`() {
        assertEquals(
            "https://voyager.example/api/auth/oauth/github/start",
            SafeStartPath.resolve("https://voyager.example/api/auth/oauth/github/start", base),
        )
        assertEquals(
            "https://voyager.example/api/auth/x",
            SafeStartPath.resolve("https://voyager.example:443/api/auth/x", base),
        )
        assertEquals("http://10.0.2.2:8001/api/auth/x", SafeStartPath.resolve("/api/auth/x", "http://10.0.2.2:8001"))
    }

    @Test
    fun `protocol-relative offsite url is rejected`() {
        assertNull(SafeStartPath.resolve("//evil.com/api/auth/x", base))
    }

    @Test
    fun `absolute offsite url is rejected`() {
        assertNull(SafeStartPath.resolve("https://evil.com/api/auth/x", base))
        assertNull(SafeStartPath.resolve("http://voyager.example/api/auth/x", base))
        assertNull(SafeStartPath.resolve("https://voyager.example:8443/api/auth/x", base))
        assertNull(SafeStartPath.resolve("https://user@voyager.example/api/auth/x", base))
        assertNull(SafeStartPath.resolve("javascript:alert(1)", base))
    }

    @Test
    fun `dot segments are rejected`() {
        assertNull(SafeStartPath.resolve("/api/auth/../admin", base))
    }

    @Test
    fun `encoded dot segments are rejected`() {
        assertNull(SafeStartPath.resolve("/api/%2e%2e/x", base))
        assertNull(SafeStartPath.resolve("/api/auth/%2E%2E/admin", base))
        assertNull(SafeStartPath.resolve("/api%2fauth/x", base))
    }

    @Test
    fun `backslash is rejected`() {
        assertNull(SafeStartPath.resolve("/api/auth\\x", base))
        assertNull(SafeStartPath.resolve("\\\\evil.com/api/auth/x", base))
        assertNull(SafeStartPath.resolve("/api/auth/%5cx", base))
    }

    @Test
    fun `a path outside api auth is rejected`() {
        assertNull(SafeStartPath.resolve("/api/other", base))
        assertNull(SafeStartPath.resolve("/api/authx/start", base))
        assertNull(SafeStartPath.resolve("api/auth/x", "https://voyager.example/app/"))
    }

    @Test
    fun `null or garbage is rejected`() {
        assertNull(SafeStartPath.resolve(null, base))
        assertNull(SafeStartPath.resolve("", base))
        assertNull(SafeStartPath.resolve("/api/auth/a b", base))
        assertNull(SafeStartPath.resolve("/api/auth/x", "not a url"))
    }
}
