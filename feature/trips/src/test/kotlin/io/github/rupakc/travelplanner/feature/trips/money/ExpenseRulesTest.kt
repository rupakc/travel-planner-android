package io.github.rupakc.travelplanner.feature.trips.money

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.trips.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseRulesTest {
    private fun client(code: String?) = AppError.Client(422, "x", code)

    @Test
    fun `server codes map to problems and unknown invalid codes are generic`() {
        val table =
            mapOf(
                "amount_invalid" to ExpenseRules.Problem.Amount,
                "currency_invalid" to ExpenseRules.Problem.Currency,
                "category_invalid" to ExpenseRules.Problem.Category,
                "note_too_long" to ExpenseRules.Problem.NoteTooLong,
                "spent_on_invalid" to ExpenseRules.Problem.SpentOn,
                "client_id_invalid" to ExpenseRules.Problem.ClientId,
                "expense_limit" to ExpenseRules.Problem.Limit,
                "mood_invalid" to ExpenseRules.Problem.Invalid,
                "weird" to ExpenseRules.Problem.Other,
            )
        table.forEach { (code, problem) -> assertEquals(code, problem, ExpenseRules.problemOf(client(code))) }
        assertEquals(ExpenseRules.Problem.Other, ExpenseRules.problemOf(client(null)))
        assertEquals(ExpenseRules.Problem.Other, ExpenseRules.problemOf(AppError.Network()))
    }

    @Test
    fun `JPY rejects decimals and BHD allows three`() {
        assertEquals(ExpenseRules.Problem.Amount, ExpenseRules.check("12.5", "JPY", "", "2027-04-01", "food"))
        assertNull(ExpenseRules.check("1250", "JPY", "", "2027-04-01", "food"))
        assertNull(ExpenseRules.check("1.234", "BHD", "", "2027-04-01", "food"))
        assertEquals(ExpenseRules.Problem.Amount, ExpenseRules.check("1.234", "EUR", "", "2027-04-01", "food"))
        assertEquals(ExpenseRules.Problem.Amount, ExpenseRules.check("0", "EUR", "", "2027-04-01", "food"))
    }

    @Test
    fun `note, date and category are checked`() {
        assertEquals(ExpenseRules.Problem.NoteTooLong, ExpenseRules.check("1", "EUR", "x".repeat(141), "2027-04-01", "food"))
        assertNull(ExpenseRules.check("1", "EUR", "x".repeat(140), "2027-04-01", "food"))
        assertEquals(ExpenseRules.Problem.SpentOn, ExpenseRules.check("1", "EUR", "", "01/04/2027", "food"))
        assertEquals(ExpenseRules.Problem.Category, ExpenseRules.check("1", "EUR", "", "2027-04-01", "beer"))
    }

    @Test
    fun `only a lost connection is queued`() {
        assertTrue(ExpenseRules.shouldQueue(AppError.Network()))
        assertFalse(ExpenseRules.shouldQueue(client("amount_invalid")))
        assertFalse(ExpenseRules.shouldQueue(AppError.Server(503, null)))
    }

    @Test
    fun `days are newest first, undated last, waiting rows after synced ones`() {
        fun e(id: Long, day: String?) = Expense(id, "c$id", "1", "EUR", "food", null, day)
        val days =
            ExpenseRules.groupByDay(
                listOf(ExpenseRow(e(1, "2027-04-01")), ExpenseRow(e(2, null)), ExpenseRow(e(0, "2027-04-02"), pending = true), ExpenseRow(e(3, "2027-04-02"))),
            )
        assertEquals(listOf("2027-04-02", "2027-04-01", null), days.map { it.day })
        assertEquals(listOf(false, true), days[0].rows.map { it.pending })
    }

    @Test
    fun `picker puts the trip and last currencies first without repeats`() {
        val c = ExpenseRules.currencyChoices("JPY", null, "ZZZ")
        assertEquals(listOf("JPY", "ZZZ", "USD"), c.take(3))
        assertEquals(1, c.count { it == "JPY" })
    }
}
