package io.github.rupakc.travelplanner.feature.auth.signup

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupSupportTest {
    @Test
    fun `token comes out of a pasted link`() {
        assertEquals("abc", LinkTokens.extract("https://h/verify?token=abc&x=1"))
        assertEquals("abc", LinkTokens.extract("  https://h/reset-password?x=1&token=abc#frag "))
        assertEquals("rt_abc-1.2", LinkTokens.extract("voyager://verify?token=rt_abc-1.2"))
    }

    @Test
    fun `a bare token is accepted, junk is not`() {
        assertEquals("Zm9vYmFy-_", LinkTokens.extract(" Zm9vYmFy-_ "))
        assertNull(LinkTokens.extract(""))
        assertNull(LinkTokens.extract(null))
        assertNull(LinkTokens.extract("two words"))
        assertNull(LinkTokens.extract("https://h/verify"))
        assertNull(LinkTokens.extract("https://h/verify?other=1"))
        assertNull(LinkTokens.extract("https://h/verify?token="))
        assertNull(LinkTokens.extract("https://h/verify?token=<script>"))
    }

    private val codes =
        mapOf(
            "username_taken" to R.string.auth_err_username_taken,
            "username_invalid" to R.string.auth_err_username_invalid,
            "username_reserved" to R.string.auth_err_username_reserved,
            "email_invalid" to R.string.auth_err_email_invalid,
            "password_too_short" to R.string.auth_err_password_too_short,
            "password_too_long" to R.string.auth_err_password_too_long,
            "password_common" to R.string.auth_err_password_common,
            "password_matches_identity" to R.string.auth_err_password_matches_identity,
            "display_name_invalid" to R.string.auth_err_display_name_invalid,
            "invite_required" to R.string.auth_err_invite_required,
            "invite_invalid" to R.string.auth_err_invite_invalid,
            "email_unavailable" to R.string.auth_err_email_unavailable,
            "token_invalid" to R.string.auth_err_token_invalid,
            "token_expired" to R.string.auth_err_token_expired,
            "token_used" to R.string.auth_err_token_used,
            "email_changed" to R.string.auth_err_email_changed,
            "rate_limited" to R.string.auth_err_rate_limited,
            "email_unverified" to R.string.auth_err_email_unverified,
        )

    @Test
    fun `every error code maps to its message whatever the status`() {
        for ((code, res) in codes) {
            for (error in listOf(
                AppError.Client(422, "detail", code),
                AppError.Forbidden("detail", code),
                AppError.RateLimited("detail", null, code),
                AppError.Server(503, "detail", code),
            )) {
                assertEquals("$code via $error", AuthMessage.Local(res), error.toAccountMessage())
            }
        }
    }

    @Test
    fun `unknown code falls back to the backend detail, then to our generic copy`() {
        assertEquals(AuthMessage.Server("Too many requests"), AppError.RateLimited("Too many requests", 5).toAccountMessage())
        assertEquals(AuthMessage.Server("Nope"), AppError.Client(400, "Nope", "something_new").toAccountMessage())
        assertEquals(AuthMessage.Local(R.string.auth_error_generic), AppError.Client(400, null, null).toAccountMessage())
        assertEquals(AuthMessage.Local(R.string.auth_error_network, suggestServer = true), AppError.Network().toAccountMessage())
    }

    @Test
    fun `telemetry results of verify and reset failures`() {
        assertEquals("expired", AppError.Client(410, null, "token_expired").verifyResult())
        assertEquals("used", AppError.Client(409, null, "token_used").verifyResult())
        assertEquals("email_changed", AppError.Client(409, null, "email_changed").verifyResult())
        assertEquals("invalid", AppError.Client(400, null, "token_invalid").verifyResult())
        assertEquals("error", AppError.Client(422, null, "password_common").resetResult())
        assertEquals("expired", AppError.Client(410, null, "token_expired").resetResult())
    }

    @Test
    fun `password rules use the configured minimum and the identity`() {
        assertFalse(PasswordRules.check("short", 10).longEnough)
        assertTrue(PasswordRules.check("0123456789", 10).longEnough)
        assertTrue(PasswordRules.check("abcdefgh", 8).ok)
        assertFalse(PasswordRules.check("jane_doe_1234", 10, username = "jane_doe").distinctFromIdentity)
        assertFalse(PasswordRules.check("jane@example.com", 10, email = "jane@example.com").distinctFromIdentity)
        assertTrue(PasswordRules.check("correct horse battery", 10, username = "jane_doe", email = "jane@x.com").ok)
    }
}
