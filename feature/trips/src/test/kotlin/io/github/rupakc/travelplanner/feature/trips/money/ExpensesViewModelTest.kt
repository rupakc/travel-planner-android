package io.github.rupakc.travelplanner.feature.trips.money

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.trips.Expense
import io.github.rupakc.travelplanner.core.model.trips.ExpenseRate
import io.github.rupakc.travelplanner.core.model.trips.ExpenseTotals
import io.github.rupakc.travelplanner.core.model.trips.ExpensesResponse
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeExpensesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeOutbox
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpensesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeExpensesRepository()
    private val outbox = FakeOutbox()
    private val telemetry = FakeTelemetry()
    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.parse("2027-04-01T10:00:00Z")

            override fun zone() = ZoneOffset.UTC
        }

    private fun exp(id: Long, amount: String = "12.50", currency: String = "EUR", clientId: String? = "c$id", day: String = "2027-04-01") =
        Expense(id, clientId, amount, currency, "food", "SECRET NOTE", day)

    private fun vm(vararg list: Expense, totals: ExpenseTotals = ExpenseTotals(), rate: ExpenseRate? = null): ExpensesViewModel {
        repo.listResult = AppResult.Success(ExpensesResponse(list.toList(), totals, rate))
        return ExpensesViewModel(SavedStateHandle(mapOf("tripId" to 9L)), repo, outbox, telemetry, clock)
    }

    private fun fill(vm: ExpensesViewModel, amount: String, currency: String? = null) {
        vm.onEvent(ExpensesEvent.Add)
        vm.onEvent(ExpensesEvent.Amount(amount))
        currency?.let { vm.onEvent(ExpensesEvent.Currency(it)) }
    }

    @Test
    fun `totals stay per currency and are never mixed`() =
        runTest(timeout = 30.seconds) {
            val totals = ExpenseTotals(mapOf("EUR" to "12.50", "JPY" to "3000"), "35.00", unconverted = listOf("JPY"))
            val s = vm(exp(1), exp(2, "3000", "JPY"), totals = totals).state.value
            assertEquals(mapOf("EUR" to "12.50", "JPY" to "3000"), s.totals.byCurrency)
            assertEquals(listOf("JPY"), s.totals.unconverted)
        }

    @Test
    fun `the form starts on the trip currency, then the last one used`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(rate = ExpenseRate("EUR", 0.92, "2027-04-01T00:00:00Z"))
            vm.onEvent(ExpensesEvent.Add)
            assertEquals("EUR", vm.state.value.form?.currency)
            assertEquals("2027-04-01", vm.state.value.form?.spentOn)
            repo.createResult = AppResult.Success(exp(5, "5", "GBP"))
            fill(vm, "5", "GBP")
            vm.onEvent(ExpensesEvent.Submit)
            vm.onEvent(ExpensesEvent.Add)
            assertEquals("GBP", vm.state.value.form?.currency)
        }

    @Test
    fun `JPY with decimals is rejected before anything is sent`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            fill(vm, "12.5", "JPY")
            vm.onEvent(ExpensesEvent.Submit)
            assertEquals(ExpenseRules.Problem.Amount, vm.state.value.form?.problem)
            assertTrue(repo.created.isEmpty())
            assertTrue(outbox.queued.value.isEmpty())
        }

    @Test
    fun `every add carries a fresh uuid client id`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.createResult = AppResult.Success(exp(1))
            fill(vm, "5")
            vm.onEvent(ExpensesEvent.Submit)
            fill(vm, "6")
            vm.onEvent(ExpensesEvent.Submit)
            val ids = repo.created.map { it.clientId }
            assertEquals(2, ids.size)
            assertNotEquals(ids[0], ids[1])
            ids.forEach { assertEquals(4, java.util.UUID.fromString(it).version()) }
            assertNull(vm.state.value.form)
        }

    @Test
    fun `an offline add is queued, shows waiting to sync and reconciles after the flush`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.createResult = AppResult.Failure(AppError.Network())
            fill(vm, "7.25")
            vm.onEvent(ExpensesEvent.Submit)
            val queued = outbox.queued.value.single()
            assertEquals("POST", queued.method)
            assertEquals("api/trips/9/expenses", queued.path)
            assertTrue(queued.body!!.contains("\"amount\":\"7.25\""))
            assertEquals(1, vm.state.value.pending.size)
            assertTrue(vm.state.value.rows.single().pending)
            assertNull(vm.state.value.form)
            // The outbox sends it: the marker goes and the list is fetched again.
            val clientId = vm.state.value.pending.single().clientId!!
            repo.listResult = AppResult.Success(ExpensesResponse(listOf(exp(40, "7.25", clientId = clientId))))
            outbox.queued.value = emptyList()
            assertTrue(vm.state.value.pending.isEmpty())
            assertEquals(listOf(false), vm.state.value.rows.map { it.pending })
            assertEquals(40L, vm.state.value.expenses.single().id)
        }

    @Test
    fun `a rejected add shows the server code and is not queued`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.createResult = AppResult.Failure(AppError.Client(409, "Too many", "expense_limit"))
            fill(vm, "7")
            vm.onEvent(ExpensesEvent.Submit)
            assertEquals(ExpenseRules.Problem.Limit, vm.state.value.form?.problem)
            assertFalse(vm.state.value.form!!.saving)
            assertTrue(outbox.queued.value.isEmpty())
        }

    @Test
    fun `a failed delete puts the row back where it was`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(exp(1), exp(2), exp(3))
            repo.deleteResult = AppResult.Failure(AppError.Server(500, null))
            vm.onEvent(ExpensesEvent.Delete(2))
            assertEquals(listOf(1L, 2L, 3L), vm.state.value.expenses.map { it.id })
            assertEquals(ExpenseNotice.DeleteFailed, vm.state.value.notice)
            assertTrue(telemetry.events.none { it.second["action"] == "delete" })
        }

    @Test
    fun `a delete removes the row and emits telemetry`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(exp(1), exp(2))
            repo.deleteResult = AppResult.Success(Unit)
            repo.listResult = AppResult.Success(ExpensesResponse(listOf(exp(1))))
            vm.onEvent(ExpensesEvent.Delete(2))
            assertEquals(listOf(1L), vm.state.value.expenses.map { it.id })
            assertNull(vm.state.value.notice)
        }

    @Test
    fun `edit sends only the changed fields`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(exp(1))
            repo.updateResult = AppResult.Success(exp(1, "20"))
            vm.onEvent(ExpensesEvent.Edit(1))
            vm.onEvent(ExpensesEvent.Amount("20"))
            vm.onEvent(ExpensesEvent.Submit)
            assertTrue(repo.calls.contains("update:9:1"))
            assertNull(vm.state.value.form)
        }

    @Test
    fun `telemetry never carries an amount, a note or a currency`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            repo.createResult = AppResult.Success(exp(1))
            fill(vm, "99.99", "GBP")
            vm.onEvent(ExpensesEvent.Note("SECRET NOTE"))
            vm.onEvent(ExpensesEvent.Category("lodging"))
            vm.onEvent(ExpensesEvent.Submit)
            repo.createResult = AppResult.Failure(AppError.Network())
            fill(vm, "5")
            vm.onEvent(ExpensesEvent.Submit)
            val events = telemetry.events.filter { it.first == "expense_action" }
            assertEquals(2, events.size)
            assertEquals(mapOf("action" to "add", "category" to "lodging", "queued" to false), events[0].second)
            assertEquals(true, events[1].second["queued"])
            events.forEach { (_, props) ->
                assertEquals(setOf("action", "category", "queued"), props.keys)
                assertTrue(props.values.none { it.toString().contains("99") || it.toString().contains("SECRET") })
            }
        }

    @Test
    fun `a failed first load offers retry`() =
        runTest(timeout = 30.seconds) {
            repo.listResult = AppResult.Failure(AppError.Network())
            val vm = ExpensesViewModel(SavedStateHandle(mapOf("tripId" to 9L)), repo, outbox, telemetry, clock)
            assertTrue(vm.state.value.failed)
            repo.listResult = AppResult.Success(ExpensesResponse(listOf(exp(1))))
            vm.onEvent(ExpensesEvent.Retry)
            assertFalse(vm.state.value.failed)
            assertNotNull(vm.state.value.expenses.firstOrNull())
        }
}
