package io.github.rupakc.travelplanner.core.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    @Test
    fun `challenge matches the RFC 7636 appendix B vector`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `verifier is 64 url-safe characters and never repeats`() {
        val a = Pkce.newVerifier()
        val b = Pkce.newVerifier()
        assertEquals(Pkce.VERIFIER_LENGTH, a.length)
        assertTrue(a.length in 43..128)
        assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(a))
        assertNotEquals(a, b)
    }

    @Test
    fun `challenge is unpadded base64url`() {
        val c = Pkce.challenge(Pkce.newVerifier())
        assertEquals(43, c.length)
        assertTrue(Regex("^[A-Za-z0-9_-]+$").matches(c))
    }
}
