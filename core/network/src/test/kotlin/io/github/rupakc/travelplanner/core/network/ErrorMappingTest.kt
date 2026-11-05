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
        val previous = AppLogger.sink
        AppLogger.sink = AppLogger.Sink { _, _, _, t -> logged += t }
        try {
            val html = SerializationException("Unexpected JSON token at offset 0: <!DOCTYPE html>")
            assertEquals(AppError.Decoding(null), html.toAppError())
            assertEquals(AppError.Decoding(null), IllegalArgumentException("Expected start of the object").toAppError())
            assertEquals(AppError.Unknown(null), IllegalStateException("java internals").toAppError())
            assertEquals(3, logged.size)
            assertTrue(logged.first() === html)
        } finally {
            AppLogger.sink = previous
        }
    }
}
