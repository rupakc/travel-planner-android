package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLogger
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMappingTest {
    @Test
    fun `string detail is the user message`() {
        val error = HttpErrorMapper.map(404, null, """{"detail":"Plan not found"}""")
        assertEquals(AppError.NotFound("Plan not found", null), error)
    }

    @Test
    fun `validation list detail keeps field errors and uses the first msg`() {
        val body = """{"detail":[{"loc":["body","pace"],"msg":"String should match pattern","type":"string_pattern_mismatch"}]}"""
        val error = HttpErrorMapper.map(422, null, body) as AppError.Client
        assertEquals("String should match pattern", error.message)
        assertEquals(listOf("body", "pace"), error.fieldErrors.single().location)
    }

    @Test
    fun `object detail maps code and message`() {
        val error = HttpErrorMapper.map(422, null, """{"detail":{"code":"empty_page","message":"That page has no text."}}""") as AppError.Client
        assertEquals("empty_page", error.code)
        assertEquals("That page has no text.", error.message)
    }

    @Test
    fun `top level code with extras is kept for price watch 422s`() {
        val body = """{"detail":"Priced in EUR","code":"target_currency_mismatch","message":"Priced in EUR","currency":"EUR","price":120.0}"""
        val error = HttpErrorMapper.map(422, null, body) as AppError.Client
        assertEquals("target_currency_mismatch", error.code)
        assertEquals("Priced in EUR", error.message)
        assertEquals("EUR", error.extras["currency"])
        assertEquals("120.0", error.extras["price"])
    }

    @Test
    fun `429 keeps the backend string and Retry-After seconds`() {
        val error = HttpErrorMapper.map(429, "37", """{"detail":"Too many requests — please wait 37 seconds and try again."}""")
        assertEquals(AppError.RateLimited("Too many requests — please wait 37 seconds and try again.", 37), error)
    }

    @Test
    fun `5xx with structured detail and 401 and unparseable bodies`() {
        val server = HttpErrorMapper.map(502, null, """{"detail":{"code":"agent_error","message":"Try again"}}""") as AppError.Server
        assertEquals("agent_error", server.code)
        assertTrue(HttpErrorMapper.map(401, null, """{"detail":"Invalid or expired token"}""") is AppError.Unauthorized)
        val html = HttpErrorMapper.map(500, null, "<html>oops</html>") as AppError.Server
        assertNull(html.message)
        assertNull(HttpErrorMapper.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
    }

    @Test
    fun `decoding and unknown failures carry no exception text but are logged`() {
        val logged = mutableListOf<Throwable?>()
        val messages = mutableListOf<String>()
        val previous = AppLogger.sink
        AppLogger.sink =
            AppLogger.Sink { _, _, m, t ->
                logged += t
                messages += m
            }
        try {
            val html = SerializationException("Unexpected JSON token at offset 0: <!DOCTYPE html>")
            assertEquals(AppError.Decoding(null), html.toAppError())
            assertEquals(AppError.Decoding(null), IllegalArgumentException("Expected start of the object").toAppError())
            assertEquals(AppError.Unknown(null), IllegalStateException("java internals").toAppError())
            assertEquals(3, logged.size)
            // Decoding logs the exception class only: its message quotes the response body.
            assertNull(logged[0])
            assertNull(logged[1])
            assertEquals("Response could not be decoded: SerializationException", messages[0])
            assertTrue(messages.none { it.contains("DOCTYPE") || it.contains("Expected start") })
            assertTrue(logged[2] is IllegalStateException)
        } finally {
            AppLogger.sink = previous
        }
    }

    @Test
    fun `error_code is kept on 403, 429, 503 and 409, with the 403 extras`() {
        val pending = HttpErrorMapper.map(403, null, """{"detail":"Confirm your email","error_code":"account_pending","email_hint":"j•••@x.com"}""")
        assertEquals("account_pending", (pending as AppError.Forbidden).code)
        assertEquals("j•••@x.com", pending.extras["email_hint"])
        assertEquals("rate_limited", (HttpErrorMapper.map(429, "5", """{"detail":"Slow","error_code":"rate_limited"}""") as AppError.RateLimited).code)
        assertEquals("email_unavailable", (HttpErrorMapper.map(503, null, """{"error_code":"email_unavailable"}""") as AppError.Server).code)
        assertEquals("username_taken", (HttpErrorMapper.map(409, null, """{"error_code":"username_taken"}""") as AppError.Client).code)
        assertEquals("session_revoked", (HttpErrorMapper.map(401, null, """{"error_code":"session_revoked"}""") as AppError.Unauthorized).code)
    }
}
