package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.offline.currentUserKey
import io.github.rupakc.travelplanner.core.data.repository.ExpensesRepository
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import io.github.rupakc.travelplanner.core.database.CachedExpensesEntity
import io.github.rupakc.travelplanner.core.database.TripCacheDao
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.trips.CreateExpenseRequest
import io.github.rupakc.travelplanner.core.model.trips.Expense
import io.github.rupakc.travelplanner.core.model.trips.ExpensesResponse
import io.github.rupakc.travelplanner.core.model.trips.UpdateExpenseRequest
import io.github.rupakc.travelplanner.core.network.api.TripsApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Expenses over [TripsApi]. The last answer per user and trip is kept in Room (`cached_expenses`, wiped on sign-out), and
 * [list] falls back to it when the server cannot be reached (network or 5xx), so the Money tab opens after a cold start with
 * no network. Offline adds are queued by the caller through the outbox. Callers know `features.trips`.
 */
@Singleton
class ExpensesRepositoryImpl
    @Inject
    constructor(
        private val api: TripsApi,
        private val sessionStore: SessionStore,
        private val cacheDao: TripCacheDao,
        private val clock: AppClock,
    ) : ExpensesRepository,
        UserStateCleaner {
        override suspend fun list(tripId: Long): AppResult<ExpensesResponse> {
            val user = sessionStore.currentUserKey()
            val result = apiCall { api.expenses(tripId) }
            if (user == null) return result
            if (result is AppResult.Success) {
                write(key(user, tripId), user, tripId, result.value)
                return result
            }
            val error = (result as AppResult.Failure).error
            if (error is AppError.Network || error is AppError.Server) read(key(user, tripId))?.let { return AppResult.Success(it) }
            return result
        }

        override suspend fun create(tripId: Long, request: CreateExpenseRequest): AppResult<Expense> = apiCall { api.createExpense(tripId, request) }

        override suspend fun update(tripId: Long, expenseId: Long, request: UpdateExpenseRequest): AppResult<Expense> =
            apiCall { api.updateExpense(tripId, expenseId, request) }

        override suspend fun delete(tripId: Long, expenseId: Long): AppResult<Unit> = apiCall { api.deleteExpense(tripId, expenseId) }

        override suspend fun clear() = cacheDao.clearExpenses()

        private fun key(user: String, tripId: Long) = "$user:$tripId"

        @Suppress("TooGenericExceptionCaught")
        private suspend fun read(key: String): ExpensesResponse? =
            try {
                cacheDao.expenses(key)?.let { TravelJson.decodeFromString(ExpensesResponse.serializer(), it.json) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not read the cached expenses", e)
                null
            }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun write(key: String, user: String, tripId: Long, value: ExpensesResponse) {
            try {
                cacheDao.upsertExpenses(
                    CachedExpensesEntity(key, user, tripId, TravelJson.encodeToString(ExpensesResponse.serializer(), value), clock.now().toEpochMilli()),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not cache the expenses", e)
            }
        }

        private companion object {
            const val TAG = "ExpensesRepository"
        }
    }
