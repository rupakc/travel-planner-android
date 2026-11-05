package io.github.rupakc.travelplanner.core.domain.format

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Port of web `formatPrice` (frontend/src/utils/priceWatch.js), same rule as the
 * backend `price_watch.format_price`: a price in its own currency — "$70",
 * "$9.90", "€24", "$1,234". Cents show only when the amount is under 100 AND not
 * whole (or always with [cents]); zero-decimal currencies (JPY) never show cents.
 * A code the platform doesn't know falls back to "CODE 5" / "CODE 5.50".
 * Returns "" for null/NaN/infinite.
 */
fun formatPrice(amount: Double?, currency: String? = "USD", cents: Boolean = false): String {
    if (amount == null || !amount.isFinite()) return ""
    val code = currency?.trim()?.takeIf(String::isNotEmpty)?.uppercase(Locale.ROOT) ?: "USD"
    val rounded = (amount * CENTS).roundToLong() / CENTS
    val fractional = abs(rounded) < CENTS_BELOW && rounded != rounded.roundToLong().toDouble()
    val platformCurrency = runCatching { Currency.getInstance(code) }.getOrNull()
    val currencyDigits = platformCurrency?.defaultFractionDigits?.takeIf { it >= 0 } ?: CENT_DIGITS
    val digits = if ((cents || fractional) && currencyDigits > 0) CENT_DIGITS else 0
    return if (platformCurrency != null) {
        NumberFormat.getCurrencyInstance(Locale.US).apply {
            this.currency = platformCurrency
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(amount)
    } else {
        val number =
            NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = digits
                maximumFractionDigits = digits
            }.format(amount)
        "$code $number"
    }
}

private const val CENTS = 100.0
private const val CENTS_BELOW = 100.0
private const val CENT_DIGITS = 2
