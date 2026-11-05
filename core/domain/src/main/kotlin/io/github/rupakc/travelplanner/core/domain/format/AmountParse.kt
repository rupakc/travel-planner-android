package io.github.rupakc.travelplanner.core.domain.format

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Reads an amount the user typed, in their language: "1,500.50" and "1.500,50" are both 1500.5.
 * The one parser behind every numeric input (budget, fare and price targets, edits).
 *
 * - Currency symbols, currency codes, spaces and apostrophes are ignored ("$1,200", "1 200 US$", "EUR 24"); any other
 *   letter makes the text not a number ("1o0", "2k" → null), so a typo is never read as a smaller amount.
 * - Both `.` and `,` present: the last one is the decimal mark, the other groups thousands.
 * - Only one kind, used more than once: grouping ("1.500.000").
 * - Only one kind, used once: a decimal mark when it is [locale]'s decimal separator, or when 1–2
 *   digits follow (nobody groups by two); grouping when exactly 3 digits follow ("1.500" in Spanish).
 *
 * Returns null for anything that isn't a number (never a guess): "abc", "1.2.3", "1,50,0".
 */
fun parseLocalizedAmount(text: String?, locale: Locale): Double? {
    if (text == null || LETTERS.findAll(text).any { it.value.uppercase(Locale.ROOT) !in CURRENCY_WORDS }) return null
    val raw = text.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
    val negative = raw.startsWith('-')
    val body = raw.removePrefix("-")
    if (body.none(Char::isDigit) || '-' in body) return null
    val decimalMark = decimalMarkOf(body, locale)
    val grouping =
        if (decimalMark == '.') {
            ','
        } else if (decimalMark == ',') {
            '.'
        } else {
            body.firstOrNull { it == '.' || it == ',' }
        }
    val whole = if (decimalMark != null) body.substringBeforeLast(decimalMark) else body
    val fraction = if (decimalMark != null) body.substringAfterLast(decimalMark) else ""
    if ((decimalMark != null && decimalMark in whole) || !validGrouping(whole, grouping)) return null
    val canonical = whole.filter(Char::isDigit).ifEmpty { "0" } + if (fraction.isEmpty()) "" else ".$fraction"
    return canonical.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { if (negative) -it else it }
}

/** Which of `.`/`,` is the decimal mark in [body], or null when there is none. */
private fun decimalMarkOf(body: String, locale: Locale): Char? {
    val lastDot = body.lastIndexOf('.')
    val lastComma = body.lastIndexOf(',')
    return when {
        lastDot >= 0 && lastComma >= 0 -> if (lastDot > lastComma) '.' else ','
        lastDot < 0 && lastComma < 0 -> null
        else -> singleKindDecimal(body, if (lastDot >= 0) '.' else ',', locale)
    }
}

/** One separator kind only: is [mark] the decimal point here? null = it groups thousands. */
private fun singleKindDecimal(body: String, mark: Char, locale: Locale): Char? {
    if (body.count { it == mark } > 1) return null
    val digitsAfter = body.length - body.indexOf(mark) - 1
    val localeDecimal = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return when {
        mark == localeDecimal -> mark
        digitsAfter == GROUP_SIZE -> null
        else -> mark
    }
}

/** Groups after the first must be exactly three digits ("1.500.000" yes, "1,50,0" no). */
private fun validGrouping(whole: String, grouping: Char?): Boolean {
    if (grouping == null || grouping !in whole) return whole.all(Char::isDigit)
    val groups = whole.split(grouping)
    return groups.first().length in 1..GROUP_SIZE && groups.first().all(Char::isDigit) &&
        groups.drop(1).all { it.length == GROUP_SIZE && it.all(Char::isDigit) }
}

private const val GROUP_SIZE = 3

private val LETTERS = Regex("\\p{L}+")

/** ISO 4217 codes plus "US" (as in "US$"): the only words allowed around a number. */
private val CURRENCY_WORDS: Set<String> = java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.toSet() + "US"
