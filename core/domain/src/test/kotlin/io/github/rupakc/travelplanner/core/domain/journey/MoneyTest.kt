package io.github.rupakc.travelplanner.core.domain.journey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun `parses to minor units by currency exponent`() {
        assertEquals(1250L, Money.parse("12.50", "EUR"))
        assertEquals(1200L, Money.parse("12", "EUR"))
        assertEquals(1250L, Money.parse("12.5", "EUR"))
        assertEquals(1500L, Money.parse("1500", "JPY"))
        assertEquals(12500L, Money.parse("12.500", "KWD"))
        assertEquals(100_000_000L, Money.parse("1000000", "EUR"))
    }

    @Test
    fun `rejects invalid amounts`() {
        assertNull(Money.parse("12.505", "EUR"))
        assertNull(Money.parse("0", "EUR"))
        assertNull(Money.parse("0.00", "EUR"))
        assertNull(Money.parse("1000000.01", "EUR"))
        assertNull(Money.parse("1500.5", "JPY"))
        assertNull(Money.parse("-3", "EUR"))
        assertNull(Money.parse("abc", "EUR"))
        assertNull(Money.parse("", "EUR"))
        assertNull(Money.parse("1,5", "EUR"))
        assertNull(Money.parse("12.5000", "KWD"))
    }

    @Test
    fun `exponent table`() {
        listOf("JPY", "KRW", "VND", "CLP", "ISK", "UGX", "XAF", "XOF").forEach { assertEquals(it, 0, Money.exponent(it)) }
        listOf("BHD", "KWD", "OMR", "JOD", "TND").forEach { assertEquals(it, 3, Money.exponent(it)) }
        assertEquals(2, Money.exponent("usd"))
        assertEquals(0, Money.exponent("jpy"))
    }

    @Test
    fun `formats minor units as a decimal string`() {
        assertEquals("12.50", Money.format(1250L, "EUR"))
        assertEquals("0.05", Money.format(5L, "EUR"))
        assertEquals("1500", Money.format(1500L, "JPY"))
        assertEquals("12.500", Money.format(12500L, "KWD"))
        assertEquals("0.001", Money.format(1L, "KWD"))
    }
}
