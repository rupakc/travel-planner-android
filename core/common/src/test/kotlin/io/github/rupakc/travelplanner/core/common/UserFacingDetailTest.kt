package io.github.rupakc.travelplanner.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserFacingDetailTest {
    @Test
    fun `http errors pass the backend detail through`() {
        assertEquals("Too many searches", AppError.RateLimited(message = "Too many searches", retryAfterSeconds = 30).userFacingDetail())
        assertEquals("Bad input", AppError.Client(status = 400, message = "Bad input").userFacingDetail())
    }

    @Test
    fun `client-side failures and blank details never reach the user`() {
        assertNull(AppError.Unknown("java.lang.IllegalStateException: boom").userFacingDetail())
        assertNull(AppError.Client(status = 400, message = " ").userFacingDetail())
    }
}
