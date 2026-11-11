package io.github.rupakc.travelplanner.core.domain.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsentRulesTest {
    @Test fun `consent required zones`() {
        listOf(
            "Europe/Berlin",
            "Europe/London",
            "Europe/Zurich",
            "Atlantic/Canary",
            "Atlantic/Reykjavik",
            "Atlantic/Faroe",
            "Atlantic/Madeira",
            "Atlantic/Azores",
        ).forEach { assertEquals(it, true, ConsentRules.required(it)) }
    }

    @Test fun `consent not required zones`() {
        listOf(
            "Europe/Istanbul",
            "Europe/Moscow",
            "Europe/Minsk",
            "Europe/Kiev",
            "Europe/Kyiv",
            "America/New_York",
            "Asia/Kolkata",
            "Atlantic/Bermuda",
            "",
        ).forEach { assertEquals(it, false, ConsentRules.required(it)) }
        assertEquals(false, ConsentRules.required(null))
    }
}
