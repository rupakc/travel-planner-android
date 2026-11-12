package io.github.rupakc.travelplanner.feature.trips.money

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.trips.Expense
import io.github.rupakc.travelplanner.core.model.trips.ExpenseRate
import io.github.rupakc.travelplanner.core.model.trips.ExpenseTotals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2000dp")
class ExpensesScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<ExpensesEvent>()

    private fun show(state: ExpensesUiState) = compose.setContent { TravelTheme(ThemeMode.LIGHT) { ExpensesScreen(state, { events += it }) } }

    private fun e(id: Long, amount: String, currency: String) = Expense(id, "c$id", amount, currency, "food", "Lunch", "2027-04-01")

    @Test
    fun `totals list each currency, the estimate, unconverted and the dated rate`() {
        show(
            ExpensesUiState(
                loading = false,
                expenses = listOf(e(1, "12.50", "EUR"), e(2, "3000", "JPY")),
                totals = ExpenseTotals(mapOf("EUR" to "12.50", "JPY" to "3000"), "35.00", unconverted = listOf("JPY")),
                rate = ExpenseRate("EUR", 0.92, "2027-04-01T00:00:00Z"),
            ),
        )
        compose.onAllNodesWithText("EUR 12.50").assertCountEquals(2)
        compose.onNodeWithText("Estimate: about USD 35.00").assertIsDisplayed()
        compose.onNodeWithText("Not converted: JPY").assertIsDisplayed()
        compose.onNodeWithText("1 USD = 0.92 EUR (rate from 2027-04-01)").assertIsDisplayed()
        compose.onNodeWithText("2027-04-01").assertIsDisplayed()
    }

    @Test
    fun `a pending expense says waiting to sync and has no edit`() {
        show(ExpensesUiState(loading = false, pending = listOf(e(0, "7.25", "EUR"))))
        compose.onNodeWithText("Waiting to sync").assertIsDisplayed()
        compose.onNodeWithText("Edit").assertDoesNotExist()
    }

    @Test
    fun `add opens the form event and delete is sent`() {
        show(ExpensesUiState(loading = false, expenses = listOf(e(1, "12.50", "EUR"))))
        compose.onNodeWithText("Add expense").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf(ExpensesEvent.Add, ExpensesEvent.Delete(1)), events)
    }

    @Test
    fun `the form shows the amount error`() {
        show(
            ExpensesUiState(
                loading = false,
                form = ExpenseForm(null, "id", "12.5", "JPY", "food", "", "2027-04-01", problem = ExpenseRules.Problem.Amount),
            ),
        )
        compose.onNodeWithText("Enter an amount above 0 that JPY allows.").assertIsDisplayed()
    }
}
