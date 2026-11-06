package io.github.rupakc.travelplanner.core.domain.live

import io.github.rupakc.travelplanner.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveRulesTest {
    @Test
    fun `only absolute http(s) links with a host are shown`() {
        assertEquals("https://maps.google.com/?cid=1", safeWebUri(" https://maps.google.com/?cid=1 ")?.trim())
        assertEquals("http://example.com/a", safeWebUri("http://example.com/a"))
        assertNull(safeWebUri("intent://maps#Intent;end"))
        assertNull(safeWebUri("javascript:alert(1)"))
        assertNull(safeWebUri("file:///etc/hosts"))
        assertNull(safeWebUri("/relative/path"))
        assertNull(safeWebUri("https://"))
        assertNull(safeWebUri("not a uri"))
        assertNull(safeWebUri(null))
        assertNull(safeWebUri(""))
    }

    @Test
    fun `searched-for queries link to Google Search`() {
        assertEquals("https://www.google.com/search?q=memmo+alfama+lisbon+%26+price", googleSearchUrl(" memmo alfama lisbon & price "))
    }

    @Test
    fun `errors classify by status and code, client failures carry no text`() {
        assertEquals(LiveError(LiveFailure.OFFLINE, null), AppError.Network("Unable to resolve host").toLiveError())
        assertEquals(LiveError(LiveFailure.OTHER, null), AppError.Decoding("Unexpected JSON token").toLiveError())
        assertEquals(LiveFailure.AI_LIMIT, AppError.Server(503, "AI limit", "ai_limit").toLiveError().failure)
        assertEquals(LiveFailure.AI_BILLING, AppError.Server(503, "Billing", "ai_billing").toLiveError().failure)
        assertEquals(LiveFailure.GROUNDING_UNAVAILABLE, AppError.Server(503, "No", "grounding_unavailable").toLiveError().failure)
        assertEquals(LiveFailure.SERVER, AppError.Server(502, null, "live_check_failed").toLiveError().failure)
        assertEquals(LiveError(LiveFailure.RATE_LIMITED, "Wait 30 s"), AppError.RateLimited("Wait 30 s", 30).toLiveError())
        assertEquals(LiveError(LiveFailure.NOT_FOUND, "Watch not found"), AppError.NotFound("Watch not found").toLiveError())
        assertEquals(LiveFailure.WATCH_EXPIRED, AppError.Client(409, "Expired", "watch_expired").toLiveError().failure)
        assertEquals(LiveFailure.OTHER, AppError.Client(409, "Busy", "other").toLiveError().failure)
    }
}
