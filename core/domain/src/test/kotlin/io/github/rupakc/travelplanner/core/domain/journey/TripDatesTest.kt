package io.github.rupakc.travelplanner.core.domain.journey

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDatesTest {
    @Test
    fun `local today follows the device zone not utc`() {
        val clock = Clock.fixed(Instant.parse("2026-11-12T16:30:00Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.parse("2026-11-12"), TripDates.localToday(clock, ZoneId.of("UTC")))
        assertEquals(LocalDate.parse("2026-11-13"), TripDates.localToday(clock, ZoneId.of("Asia/Tokyo")))
        assertEquals(LocalDate.parse("2026-11-12"), TripDates.localToday(clock, ZoneId.of("America/Los_Angeles")))
    }
}
