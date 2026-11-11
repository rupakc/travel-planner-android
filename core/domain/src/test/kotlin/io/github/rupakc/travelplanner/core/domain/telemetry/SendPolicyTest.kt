package io.github.rupakc.travelplanner.core.domain.telemetry

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendPolicyTest {
    private val now = 1_800_000_000_000L

    private fun c(status: Int?, ra: String? = null) = SendPolicy.classify(status, ra, now)

    @Test fun `204 sent`() = assertEquals(SendOutcome.Sent, c(204))

    @Test fun `404 disabled`() = assertEquals(SendOutcome.Disabled, c(404))

    @Test fun `413 and 422 dropped`() {
        assertEquals(SendOutcome.Dropped, c(413))
        assertEquals(SendOutcome.Dropped, c(422))
    }

    @Test fun `408 retry`() = assertEquals(SendOutcome.Retry, c(408))

    @Test fun `500 retry`() = assertEquals(SendOutcome.Retry, c(500))

    @Test fun `null retry`() = assertEquals(SendOutcome.Retry, c(null))

    @Test fun `429 with 30 is RetryAfter 30000`() = assertEquals(SendOutcome.RetryAfter(30_000), c(429, "30"))

    @Test fun `429 without a usable header is retry`() {
        assertEquals(SendOutcome.Retry, c(429, null))
        assertEquals(SendOutcome.Retry, c(429, "soon"))
    }

    @Test fun `429 with an HTTP date 10 s ahead is about 10000`() {
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now + 10_000).atZone(ZoneOffset.UTC))
        val out = c(429, date) as SendOutcome.RetryAfter
        assertTrue("was ${out.ms}", out.ms in 9_000..10_000)
    }

    @Test fun `Retry-After 7200 is capped at 3600000`() = assertEquals(3_600_000L, SendPolicy.parseRetryAfter("7200", now))

    @Test fun `backoff starts at the third failure`() {
        val b = Backoff()
        b.onFailure(15_000, now)
        b.onFailure(15_000, now)
        assertEquals(0L, b.blockedUntil)
        b.onFailure(15_000, now)
        assertEquals(now + 30_000, b.blockedUntil)
        b.onFailure(15_000, now)
        assertEquals(now + 60_000, b.blockedUntil)
        repeat(10) { b.onFailure(15_000, now) }
        assertEquals(now + 300_000, b.blockedUntil)
        b.block(now + 1)
        assertEquals(now + 300_000, b.blockedUntil)
        b.block(now + 400_000)
        assertEquals(now + 400_000, b.blockedUntil)
        b.onSuccess()
        assertEquals(0L, b.blockedUntil)
    }

    @Test fun `fromServer clamps`() {
        assertEquals(TelemetrySettings(1_000, 100), TelemetrySettings.fromServer(0, 500))
        assertEquals(TelemetrySettings(15_000, 50), TelemetrySettings.fromServer(null, null))
        assertEquals(TelemetrySettings(300_000, 1), TelemetrySettings.fromServer(999, 0))
    }
}
