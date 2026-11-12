package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.ExpensesRepository
import io.github.rupakc.travelplanner.core.model.trips.CreateExpenseRequest
import io.github.rupakc.travelplanner.core.model.trips.Expense
import io.github.rupakc.travelplanner.core.model.trips.ExpensesResponse
import io.github.rupakc.travelplanner.core.model.trips.UpdateExpenseRequest
import io.github.rupakc.travelplanner.core.network.api.TripsApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Expenses over [TripsApi]. Offline adds are queued by the caller through the outbox. Callers know `features.trips`. */
@Singleton
class ExpensesRepositoryImpl
    @Inject
    constructor(private val api: TripsApi) : ExpensesRepository {
        override suspend fun list(tripId: Long): AppResult<ExpensesResponse> = apiCall { api.expenses(tripId) }

        override suspend fun create(tripId: Long, request: CreateExpenseRequest): AppResult<Expense> = apiCall { api.createExpense(tripId, request) }

        override suspend fun update(tripId: Long, expenseId: Long, request: UpdateExpenseRequest): AppResult<Expense> =
            apiCall { api.updateExpense(tripId, expenseId, request) }

        override suspend fun delete(tripId: Long, expenseId: Long): AppResult<Unit> = apiCall { api.deleteExpense(tripId, expenseId) }
    }
