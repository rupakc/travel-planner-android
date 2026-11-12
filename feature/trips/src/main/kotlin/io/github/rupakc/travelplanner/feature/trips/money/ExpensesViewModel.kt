package io.github.rupakc.travelplanner.feature.trips.money

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.repository.ExpensesRepository
import io.github.rupakc.travelplanner.core.data.repository.Outbox
import io.github.rupakc.travelplanner.core.data.repository.OutboxRequest
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.trips.CreateExpenseRequest
import io.github.rupakc.travelplanner.core.model.trips.Expense
import io.github.rupakc.travelplanner.core.model.trips.ExpenseCategories
import io.github.rupakc.travelplanner.core.model.trips.ExpenseRate
import io.github.rupakc.travelplanner.core.model.trips.ExpenseTotals
import io.github.rupakc.travelplanner.core.model.trips.UpdateExpenseRequest
import io.github.rupakc.travelplanner.feature.trips.trip.TripViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add / edit form. [editingId] null means a new expense, whose [clientId] is fresh for this form. */
@Immutable
data class ExpenseForm(
    val editingId: Long?,
    val clientId: String,
    val amount: String,
    val currency: String,
    val category: String,
    val note: String,
    val spentOn: String,
    val saving: Boolean = false,
    val problem: ExpenseRules.Problem? = null,
    /** The backend's text, shown for [ExpenseRules.Problem.Other] and [ExpenseRules.Problem.Invalid] without our own copy. */
    val message: String? = null,
)

/** A short message above the list. */
sealed interface ExpenseNotice {
    data object DeleteFailed : ExpenseNotice

    data object QueueFailed : ExpenseNotice

    data class Backend(val message: String) : ExpenseNotice
}

@Immutable
data class ExpensesUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** The server could not be reached and nothing was saved for this trip: an empty list that still takes offline adds. */
    val offline: Boolean = false,
    val expenses: List<Expense> = emptyList(),
    /** Adds made offline and not yet synced. */
    val pending: List<Expense> = emptyList(),
    val totals: ExpenseTotals = ExpenseTotals(),
    val rate: ExpenseRate? = null,
    val form: ExpenseForm? = null,
    val notice: ExpenseNotice? = null,
) {
    val rows: List<ExpenseRow> get() = expenses.map { ExpenseRow(it) } + pending.map { ExpenseRow(it, pending = true) }
}

sealed interface ExpensesEvent {
    data object Retry : ExpensesEvent

    data object Add : ExpensesEvent

    data class Edit(val id: Long) : ExpensesEvent

    data class Delete(val id: Long) : ExpensesEvent

    data class Amount(val value: String) : ExpensesEvent

    data class Currency(val value: String) : ExpensesEvent

    data class Category(val value: String) : ExpensesEvent

    data class Note(val value: String) : ExpensesEvent

    data class SpentOn(val value: String) : ExpensesEvent

    data object Submit : ExpensesEvent

    data object DismissForm : ExpensesEvent

    data object DismissNotice : ExpensesEvent
}

/** Money tab: expenses of a trip, totals per currency, add / edit / delete; adds made offline wait in the outbox. */
@HiltViewModel
class ExpensesViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repo: ExpensesRepository,
        private val outbox: Outbox,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : ViewModel() {
        private val tripId: Long = savedStateHandle.get<Long>(TripViewModel.TRIP_ID_ARG) ?: -1L
        private val _state = MutableStateFlow(ExpensesUiState())
        val state: StateFlow<ExpensesUiState> = _state.asStateFlow()
        private var lastCurrency: String? = null
        private val seenPending = mutableSetOf<String>()

        init {
            load()
            viewModelScope.launch { outbox.pendingKeys().collect(::onPendingKeys) }
        }

        fun onEvent(event: ExpensesEvent) {
            when (event) {
                ExpensesEvent.Retry -> load()
                ExpensesEvent.Add -> openForm(null)
                is ExpensesEvent.Edit -> _state.value.expenses.firstOrNull { it.id == event.id }?.let { openForm(it) }
                is ExpensesEvent.Delete -> delete(event.id)
                is ExpensesEvent.Amount -> editForm { it.copy(amount = event.value, problem = null) }
                is ExpensesEvent.Currency -> editForm { it.copy(currency = event.value, problem = null) }
                is ExpensesEvent.Category -> editForm { it.copy(category = event.value, problem = null) }
                is ExpensesEvent.Note -> editForm { it.copy(note = event.value, problem = null) }
                is ExpensesEvent.SpentOn -> editForm { it.copy(spentOn = event.value, problem = null) }
                ExpensesEvent.Submit -> submit()
                ExpensesEvent.DismissForm -> _state.update { it.copy(form = null) }
                ExpensesEvent.DismissNotice -> _state.update { it.copy(notice = null) }
            }
        }

        private fun load() {
            _state.update { it.copy(loading = it.expenses.isEmpty(), failed = false, offline = false) }
            viewModelScope.launch {
                when (val result = repo.list(tripId)) {
                    is AppResult.Success -> {
                        val r = result.value
                        val synced = r.expenses.mapNotNull { it.clientId }.toSet()
                        _state.update {
                            it.copy(
                                loading = false,
                                failed = false,
                                offline = false,
                                expenses = r.expenses,
                                totals = r.totals,
                                rate = r.rate,
                                pending = it.pending.filterNot { p -> p.clientId in synced },
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        // No network and nothing saved: still an (empty) list, so the add form works and queues in the outbox.
                        val offline = result.error is AppError.Network
                        _state.update {
                            val empty = it.expenses.isEmpty() && it.pending.isEmpty()
                            it.copy(loading = false, failed = empty && !offline, offline = offline && empty)
                        }
                    }
                }
            }
        }

        /** The outbox sent a queued add: drop its marker and fetch the real row. */
        private fun onPendingKeys(keys: Set<String>) {
            val waiting = _state.value.pending.filter { ExpenseRules.pendingKey(tripId, it.clientId.orEmpty()) in keys }
            seenPending += waiting.mapNotNull { it.clientId }
            val sent =
                _state.value.pending.filter { p ->
                    val id = p.clientId.orEmpty()
                    id in seenPending && ExpenseRules.pendingKey(tripId, id) !in keys
                }
            if (sent.isEmpty()) return
            val sentIds = sent.mapNotNull { it.clientId }.toSet()
            seenPending -= sentIds
            _state.update { s -> s.copy(pending = s.pending.filterNot { it.clientId in sentIds }) }
            load()
        }

        private fun openForm(expense: Expense?) {
            val currency = expense?.currency ?: lastCurrency ?: _state.value.rate?.local ?: "USD"
            _state.update {
                it.copy(
                    form =
                        ExpenseForm(
                            editingId = expense?.id,
                            clientId = UUID.randomUUID().toString(),
                            amount = expense?.amount.orEmpty(),
                            currency = currency,
                            category = expense?.category ?: "other",
                            note = expense?.note.orEmpty(),
                            spentOn = expense?.spentOn ?: clock.today().toString(),
                        ),
                )
            }
        }

        private fun editForm(change: (ExpenseForm) -> ExpenseForm) = _state.update { s -> s.copy(form = s.form?.let(change)) }

        private fun submit() {
            val form = _state.value.form ?: return
            if (form.saving) return
            val problem = ExpenseRules.check(form.amount.trim(), form.currency, form.note, form.spentOn.trim(), form.category)
            if (problem != null) {
                editForm { it.copy(problem = problem, message = null) }
                return
            }
            editForm { it.copy(saving = true, problem = null, message = null) }
            viewModelScope.launch { if (form.editingId == null) add(form) else edit(form, form.editingId) }
        }

        private fun request(form: ExpenseForm) =
            CreateExpenseRequest(
                clientId = form.clientId,
                amount = form.amount.trim(),
                currency = form.currency,
                category = form.category,
                note = form.note.trim().ifEmpty { null },
                spentOn = form.spentOn.trim(),
            )

        private suspend fun add(form: ExpenseForm) {
            val body = request(form)
            when (val result = repo.create(tripId, body)) {
                is AppResult.Success -> {
                    lastCurrency = form.currency
                    emit("add", form.category, queued = false)
                    _state.update { it.copy(form = null) }
                    load()
                }

                is AppResult.Failure -> {
                    if (ExpenseRules.shouldQueue(result.error)) queue(form, body) else fail(result.error)
                }
            }
        }

        private suspend fun queue(form: ExpenseForm, body: CreateExpenseRequest) {
            val queued =
                outbox.enqueue(
                    OutboxRequest(
                        "POST",
                        ExpenseRules.queuePath(tripId),
                        TravelJson.encodeToString(CreateExpenseRequest.serializer(), body),
                        ExpenseRules.pendingKey(tripId, body.clientId),
                    ),
                )
            if (queued is AppResult.Failure) {
                editForm { it.copy(saving = false) }
                _state.update { it.copy(notice = ExpenseNotice.QueueFailed) }
                return
            }
            lastCurrency = form.currency
            emit("add", form.category, queued = true)
            val local = Expense(0, body.clientId, body.amount, body.currency, body.category, body.note, body.spentOn)
            _state.update { it.copy(form = null, pending = it.pending + local) }
        }

        private suspend fun edit(form: ExpenseForm, id: Long) {
            val old = _state.value.expenses.firstOrNull { it.id == id }
            val patch =
                UpdateExpenseRequest(
                    amount = form.amount.trim().takeIf { it != old?.amount },
                    currency = form.currency.takeIf { it != old?.currency },
                    category = form.category.takeIf { it != old?.category },
                    note = form.note.trim().takeIf { it != old?.note.orEmpty() },
                    spentOn = form.spentOn.trim().takeIf { it != old?.spentOn },
                )
            when (val result = repo.update(tripId, id, patch)) {
                is AppResult.Success -> {
                    lastCurrency = form.currency
                    emit("edit", form.category, queued = false)
                    _state.update { it.copy(form = null) }
                    load()
                }

                is AppResult.Failure -> {
                    fail(result.error)
                }
            }
        }

        private fun fail(error: AppError) {
            val problem = ExpenseRules.problemOf(error)
            val message = if (problem == ExpenseRules.Problem.Other || problem == ExpenseRules.Problem.Invalid) error.userFacingDetail() else null
            editForm { it.copy(saving = false, problem = problem, message = message) }
        }

        private fun delete(id: Long) {
            val before = _state.value
            val gone = before.expenses.firstOrNull { it.id == id } ?: return
            // Optimistic: the row disappears now and comes back in place when the server refuses.
            _state.update { it.copy(expenses = it.expenses.filterNot { e -> e.id == id }, notice = null) }
            viewModelScope.launch {
                when (val result = repo.delete(tripId, id)) {
                    is AppResult.Success -> {
                        emit("delete", gone.category, queued = false)
                        load()
                    }

                    is AppResult.Failure -> {
                        val restored = (_state.value.expenses + gone).sortedBy { e -> before.expenses.indexOfFirst { it.id == e.id } }
                        val detail = result.error.takeIf { it is AppError.Client }?.userFacingDetail()
                        _state.update { it.copy(expenses = restored, notice = detail?.let(ExpenseNotice::Backend) ?: ExpenseNotice.DeleteFailed) }
                    }
                }
            }
        }

        /** Telemetry carries the action, the category and whether it was queued; never an amount, a note or a place. */
        private fun emit(action: String, category: String, queued: Boolean) {
            val cat = category.takeIf { it in ExpenseCategories.ALL } ?: "other"
            telemetry.emit(TelemetryEvents.EXPENSE_ACTION, mapOf("action" to action, "category" to cat, "queued" to queued))
        }
    }
