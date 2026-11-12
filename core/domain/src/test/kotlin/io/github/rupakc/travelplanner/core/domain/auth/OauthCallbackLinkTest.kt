package io.github.rupakc.travelplanner.core.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OauthCallbackLinkTest {
    @Test
    fun `a code is parsed`() {
        assertEquals(OauthCallback.Code("abc-123_x"), OauthCallbackLink.parse("voyager://auth/callback?code=abc-123_x"))
    }

    @Test
    fun `a known error is parsed and an unknown one becomes bad_request`() {
        assertEquals(OauthCallback.Error("cancelled"), OauthCallbackLink.parse("voyager://auth/callback?error=cancelled"))
        assertEquals(OauthCallback.Error("bad_request"), OauthCallbackLink.parse("voyager://auth/callback?error=whatever"))
    }

    @Test
    fun `only the callback link is accepted`() {
        listOf(
            "voyager://auth/other?code=a",
            "voyager://evil/callback?code=a",
            "voyager://auth/callback/x?code=a",
            "travelplanner://auth/callback?code=a",
            "https://auth/callback?code=a",
            "voyager://user@auth/callback?code=a",
            "voyager://auth/callback?code=a#frag",
            null,
            "",
            "not a uri",
        ).forEach { assertNull(it, OauthCallbackLink.parse(it)) }
    }

    @Test
    fun `missing, empty, odd or oversized params are rejected`() {
        assertNull(OauthCallbackLink.parse("voyager://auth/callback"))
        assertNull(OauthCallbackLink.parse("voyager://auth/callback?state=x"))
        assertNull(OauthCallbackLink.parse("voyager://auth/callback?code="))
        assertNull(OauthCallbackLink.parse("voyager://auth/callback?code=a%20b"))
        assertNull(OauthCallbackLink.parse("voyager://auth/callback?code=" + "a".repeat(OauthCallbackLink.MAX_PARAM_LENGTH + 1)))
        assertEquals(
            OauthCallback.Code("a".repeat(OauthCallbackLink.MAX_PARAM_LENGTH)),
            OauthCallbackLink.parse("voyager://auth/callback?code=" + "a".repeat(OauthCallbackLink.MAX_PARAM_LENGTH)),
        )
    }
}
