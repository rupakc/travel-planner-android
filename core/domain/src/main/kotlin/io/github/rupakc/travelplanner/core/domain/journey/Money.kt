package io.github.rupakc.travelplanner.core.domain.journey

import java.math.BigDecimal

/** Expense amounts (Trips-API, Expenses): decimal strings stored as integer minor units, never floats. */
object Money {
    private const val MAX_AMOUNT = 1_000_000
    private const val EXP_THREE = 3
    private const val EXP_DEFAULT = 2
    private val zero = setOf("JPY", "KRW", "VND", "CLP", "ISK", "UGX", "XAF", "XOF")
    private val three = setOf("BHD", "KWD", "OMR", "JOD", "TND")
    private val shape = Regex("^\\d+(\\.\\d+)?$")
    private val max = BigDecimal(MAX_AMOUNT)

    /** Decimal places of [currency]: 0, 3 or 2. */
    fun exponent(currency: String): Int {
        val c = currency.uppercase()
        return when (c) {
            in zero -> 0
            in three -> EXP_THREE
            else -> EXP_DEFAULT
        }
    }

    /** Minor units of [amount], or null unless `0 < amount <= 1,000,000` with no more decimals than the currency allows. */
    fun parse(amount: String, currency: String): Long? {
        val text = amount.trim()
        if (!shape.matches(text)) return null
        val exp = exponent(currency)
        val value = BigDecimal(text)
        if (value.signum() <= 0 || value > max) return null
        val decimals = text.substringAfter('.', "").length
        if (decimals > exp) return null
        return value.movePointRight(exp).longValueExact()
    }

    /** Decimal string for [minor] units, always with the currency's exponent (`1250` EUR is `12.50`). */
    fun format(minor: Long, currency: String): String = BigDecimal.valueOf(minor, exponent(currency)).toPlainString()
}
