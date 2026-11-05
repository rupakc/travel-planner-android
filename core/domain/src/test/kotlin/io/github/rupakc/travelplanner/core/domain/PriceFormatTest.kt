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
}
