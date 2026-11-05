package io.github.rupakc.travelplanner.core.domain.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParseTest {
    private val en = Locale.US
    private val es = Locale.forLanguageTag("es-ES")

    private fun en(text: String) = parseLocalizedAmount(text, en)

    private fun es(text: String) = parseLocalizedAmount(text, es)

    @Test fun plainNumbers() {
        assertEquals(1500.0, en("1500")!!, 0.0)
        assertEquals(1500.0, es("1500")!!, 0.0)
        assertEquals(0.5, en(".5")!!, 0.0)
    }

    @Test fun bothSeparatorsLastOneIsDecimal() {
        assertEquals(1500.5, en("1,500.50")!!, 0.0)
        assertEquals(1500.5, es("1,500.50")!!, 0.0)
        assertEquals(1500.5, en("1.500,50")!!, 0.0)
        assertEquals(1500.5, es("1.500,50")!!, 0.0)
        assertEquals(1234567.89, es("1.234.567,89")!!, 0.0)
    }

    @Test fun commaDecimalWithTwoDigits() {
        assertEquals(450.5, es("450,50")!!, 0.0)
        // Nobody groups by two digits: a comma before 1-2 digits is a decimal mark even in English.
        assertEquals(450.5, en("450,50")!!, 0.0)
        assertEquals(450.5, es("450.50")!!, 0.0)
        assertEquals(450.5, en("450.50")!!, 0.0)
    }

    @Test fun threeDigitsAfterTheLocalesGroupingMarkIsGrouping() {
        assertEquals(1500.0, es("1.500")!!, 0.0)
        assertEquals(1500.0, en("1,500")!!, 0.0)
        assertEquals(1500000.0, es("1.500.000")!!, 0.0)
        assertEquals(1500000.0, en("1,500,000")!!, 0.0)
    }

    @Test fun threeDigitsAfterTheLocalesDecimalMarkIsDecimal() {
        assertEquals(1.5, en("1.500")!!, 0.0)
        assertEquals(1.5, es("1,500")!!, 0.0)
    }

    @Test fun currencySymbolsAndSpacesAreIgnored() {
        assertEquals(1200.0, en("$1,200")!!, 0.0)
        assertEquals(1200.0, es("1.200 €")!!, 0.0)
        assertEquals(1200.0, es("1 200 US$")!!, 0.0)
        assertEquals(1200.0, es("1 200")!!, 0.0)
        assertEquals(1200.0, en("USD 1200")!!, 0.0)
    }

    @Test fun junkIsNull() {
        assertNull(en(""))
        assertNull(en("   "))
        assertNull(en("abc"))
        assertNull(en("$"))
        assertNull(es("1.2.3"))
        assertNull(en("1,50,0"))
        assertNull(en("1.5.0,0,0"))
        assertNull(en("12-3"))
        assertNull(parseLocalizedAmount(null, en))
    }

    @Test fun lettersOtherThanACurrencyAreNotANumber() {
        assertNull(en("1o0"))
        assertNull(en("2k"))
        assertNull(es("1.5OO"))
        assertNull(en("about 100"))
        assertEquals(24.0, es("EUR 24")!!, 0.0)
        assertEquals(24.0, es("24 eur")!!, 0.0)
        assertEquals(1200.0, es("1.200 US$")!!, 0.0)
    }

    @Test fun negativeKeepsItsSign() {
        assertEquals(-20.5, es("-20,5")!!, 0.0)
    }
}
