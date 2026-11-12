package io.github.rupakc.travelplanner.feature.trips.money

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.domain.journey.Money
import io.github.rupakc.travelplanner.core.model.trips.Expense
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** An expense on screen: [pending] ones wait in the outbox ("waiting to sync") and are not in the totals. */
data class ExpenseRow(val expense: Expense, val pending: Boolean = false)

/** The expenses of one day ([day] null: no date). */
data class ExpenseDay(val day: String?, val rows: List<ExpenseRow>)

/** Pure rules of the Money tab (Trips-API, Expenses). */
object ExpenseRules {
    const val NOTE_MAX = 140

    /** Why an expense was refused: from the form's own checks or the server's code. */
    enum class Problem { Amount, Currency, Category, NoteTooLong, SpentOn, ClientId, Limit, Invalid, Other }

    /** Currencies offered by the picker (the trip's and the last used one are added in front). */
    val CURRENCIES: List<String> =
        listOf(
            "USD",
            "EUR",
            "GBP",
            "JPY",
            "CNY",
            "INR",
            "AUD",
            "CAD",
            "CHF",
            "SEK",
            "NOK",
            "DKK",
            "NZD",
            "SGD",
            "HKD",
            "KRW",
            "THB",
            "VND",
            "IDR",
            "MYR",
            "PHP",
            "AED",
            "SAR",
            "TRY",
            "MXN",
            "BRL",
            "ARS",
            "CLP",
            "ZAR",
            "EGP",
            "MAD",
            "KES",
            "CZK",
            "PLN",
            "HUF",
            "ISK",
            "BHD",
            "KWD",
            "OMR",
            "JOD",
            "TND",
        )

    fun problemOf(error: AppError): Problem {
        val code = (error as? AppError.Client)?.code
        return when {
            code == "amount_invalid" -> Problem.Amount
            code == "currency_invalid" -> Problem.Currency
            code == "category_invalid" -> Problem.Category
            code == "note_too_long" -> Problem.NoteTooLong
            code == "spent_on_invalid" -> Problem.SpentOn
            code == "client_id_invalid" -> Problem.ClientId
            code == "expense_limit" -> Problem.Limit
            code != null && code.endsWith("_invalid") -> Problem.Invalid
            else -> Problem.Other
        }
    }

    /** Checks the form fields before sending; null when they are fine. */
    fun check(amount: String, currency: String, note: String, spentOn: String, category: String): Problem? =
        when {
            Money.parse(amount, currency) == null -> Problem.Amount
            !Regex("^[A-Z]{3}$").matches(currency) -> Problem.Currency
            category !in io.github.rupakc.travelplanner.core.model.trips.ExpenseCategories.ALL -> Problem.Category
            note.trim().length > NOTE_MAX -> Problem.NoteTooLong
            !isDay(spentOn) -> Problem.SpentOn
            else -> null
        }

    fun isDay(value: String): Boolean =
        try {
            LocalDate.parse(value)
            true
        } catch (_: DateTimeParseException) {
            false
        }

    /** The outbox key of a queued add. */
    fun pendingKey(tripId: Long, clientId: String) = "expense:$tripId:$clientId"

    fun queuePath(tripId: Long) = "api/trips/$tripId/expenses"

    /** Only a lost connection is queued; everything else is the server's answer. */
    fun shouldQueue(error: AppError) = error is AppError.Network

    /** Rows grouped by day, newest day first, undated last; synced rows before waiting ones within a day. */
    fun groupByDay(rows: List<ExpenseRow>): List<ExpenseDay> =
        rows.groupBy { it.expense.spentOn }
            .map { (day, list) -> ExpenseDay(day, list.sortedWith(compareBy<ExpenseRow> { it.pending }.thenByDescending { it.expense.id })) }
            .sortedWith(compareByDescending<ExpenseDay> { it.day != null }.thenByDescending { it.day.orEmpty() })

    /** `EUR 12.50` for a decimal [amount] (already in the currency's exponent). */
    fun label(amount: String, currency: String) = "$currency $amount"

    /** The date part of an ISO timestamp. */
    fun dateOf(timestamp: String?): String? = timestamp?.take(DATE_LEN)?.takeIf { isDay(it) }

    /** Picker order: [preferred] currencies first, then the rest, no repeats. */
    fun currencyChoices(vararg preferred: String?): List<String> = (preferred.filterNotNull() + CURRENCIES).distinct()

    private const val DATE_LEN = 10
}
