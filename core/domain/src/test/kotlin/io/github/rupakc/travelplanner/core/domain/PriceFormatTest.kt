package io.github.rupakc.travelplanner.core.domain

import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as the web `formatPrice` docs. */
class PriceFormatTest {
    @Test
    fun `web examples`() {
        assertEquals("$70", formatPrice(70.0))
        assertEquals("$9.90", formatPrice(9.9))
        assertEquals("€24", formatPrice(24.0, "EUR"))
        assertEquals("$398", formatPrice(398.0))
        assertEquals("$1,234", formatPrice(1234.4))
        assertEquals("$5.00", formatPrice(5.0, cents = true))
        assertEquals("¥1,500", formatPrice(1500.0, "JPY", cents = true))
    }

    @Test
    fun `unknown code and invalid amounts`() {
        assertEquals("XYZ 5.50", formatPrice(5.5, "XYZ"))
        assertEquals("", formatPrice(null))
        assertEquals("", formatPrice(Double.NaN))
        assertEquals("$70", formatPrice(70.0, "  "))
    }

    private val es = java.util.Locale.forLanguageTag("es-ES")

    private fun String.spaces() = replace('\u00a0', ' ').replace('\u202f', ' ')

    @Test
    fun `prices follow the UI language, en-US stays the default`() {
        assertEquals("1.480 US$", formatPrice(1480.0, "USD", locale = es).spaces())
        assertEquals("9,90 US$", formatPrice(9.9, "USD", locale = es).spaces())
        assertEquals("24 €", formatPrice(24.0, "EUR", locale = es).spaces())
        assertEquals("$1,480", formatPrice(1480.0))
        assertEquals("1.480 US$", io.github.rupakc.travelplanner.core.domain.format.formatWholeUsd(1479.5, es).spaces())
        assertEquals("$1,234", io.github.rupakc.travelplanner.core.domain.alerts.formatUsd(1234.4))
        assertEquals("15 US$", io.github.rupakc.travelplanner.core.domain.alerts.formatUsd(15.0, es).spaces())
        assertEquals("−45 US$", io.github.rupakc.travelplanner.core.domain.results.FareCalendarRules.formatDelta(-45.0, es).spaces())
        assertEquals("1.234 US$", io.github.rupakc.travelplanner.core.domain.search.fmtUsd(1234.0, es).spaces())
        assertEquals("$1,235", io.github.rupakc.travelplanner.core.domain.results.Remix.formatMetricValue(1234.5, isMoney = true))
    }
}
