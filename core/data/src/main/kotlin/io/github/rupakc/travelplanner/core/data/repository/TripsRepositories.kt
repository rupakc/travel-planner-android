package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.trips.CheckinResponse
import io.github.rupakc.travelplanner.core.model.trips.CheckinsResponse
import io.github.rupakc.travelplanner.core.model.trips.CreateEntryRequest
import io.github.rupakc.travelplanner.core.model.trips.CreateExpenseRequest
import io.github.rupakc.travelplanner.core.model.trips.CreateTripRequest
import io.github.rupakc.travelplanner.core.model.trips.Entry
import io.github.rupakc.travelplanner.core.model.trips.Expense
import io.github.rupakc.travelplanner.core.model.trips.ExpensesResponse
import io.github.rupakc.travelplanner.core.model.trips.OfflinePack
import io.github.rupakc.travelplanner.core.model.trips.PrepResponse
import io.github.rupakc.travelplanner.core.model.trips.PrepToggleResponse
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceRequest
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceResponse
import io.github.rupakc.travelplanner.core.model.trips.Recap
import io.github.rupakc.travelplanner.core.model.trips.RecapCompleteResponse
import io.github.rupakc.travelplanner.core.model.trips.Stats
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.model.trips.TripPlace
import io.github.rupakc.travelplanner.core.model.trips.TripsResponse
import io.github.rupakc.travelplanner.core.model.trips.UpdateEntryRequest
import io.github.rupakc.travelplanner.core.model.trips.UpdateExpenseRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * Trips (Trips-API). All [today] arguments are the device's local date `YYYY-MM-DD`.
 * Every call needs `features.trips`; a backend without it is the previous backend and is never called.
 */
interface TripsRepository {
    suspend fun list(today: String): AppResult<TripsResponse>

    /**
     * True when the last [list] could not reach the server and returned the signed-in user's cached list
     * (phases recomputed for the device date). Implementations without a cache keep the default.
     */
    fun isShowingCachedList(): Boolean = false

    suspend fun create(request: CreateTripRequest): AppResult<Trip>

    suspend fun get(id: Long, today: String): AppResult<Trip>

    /** [patch] holds any subset of `title`, `start_date`, `end_date`, `phase` (a JSON null clears). */
    suspend fun update(id: Long, patch: JsonObject): AppResult<Trip>

    suspend fun delete(id: Long): AppResult<Unit>

    suspend fun stats(): AppResult<Stats>

    /** `POST /trips/{id}/checkin` with the local date; forwards the game delta. */
    suspend fun checkin(id: Long, day: String): AppResult<CheckinResponse>

    suspend fun checkins(id: Long): AppResult<CheckinsResponse>
}

/** Prep checklist. */
interface PrepRepository {
    suspend fun prep(tripId: Long, today: String): AppResult<PrepResponse>

    /** Forwards the `trip_ready` game delta. */
    suspend fun toggle(tripId: Long, itemId: String, done: Boolean): AppResult<PrepToggleResponse>
}

/** Expenses. Offline adds go through the outbox (Task 11), not through this interface. */
interface ExpensesRepository {
    suspend fun list(tripId: Long): AppResult<ExpensesResponse>

    suspend fun create(tripId: Long, request: CreateExpenseRequest): AppResult<Expense>

    suspend fun update(tripId: Long, expenseId: Long, request: UpdateExpenseRequest): AppResult<Expense>

    suspend fun delete(tripId: Long, expenseId: Long): AppResult<Unit>
}

/** Journal entries and place ratings/visits. */
interface JournalRepository {
    suspend fun entries(tripId: Long): AppResult<List<Entry>>

    suspend fun createEntry(tripId: Long, request: CreateEntryRequest): AppResult<Entry>

    suspend fun updateEntry(tripId: Long, entryId: Long, request: UpdateEntryRequest): AppResult<Entry>

    suspend fun deleteEntry(tripId: Long, entryId: Long): AppResult<Unit>

    suspend fun places(tripId: Long): AppResult<List<TripPlace>>

    /** [placeKey] is `<kind>:<slug>` (`PlaceKey.of`). Forwards the game delta when `visited` first becomes true. */
    suspend fun putPlace(tripId: Long, placeKey: String, request: PutPlaceRequest): AppResult<PutPlaceResponse>

    suspend fun deletePlace(tripId: Long, placeKey: String): AppResult<Unit>
}

/** Recap. */
interface RecapRepository {
    suspend fun recap(tripId: Long, today: String): AppResult<Recap>

    /** Idempotent; forwards the game delta. */
    suspend fun complete(tripId: Long, today: String): AppResult<RecapCompleteResponse>
}

/**
 * The offline pack of a trip, stored per user (`userId:tripId`). Everything is purged on sign-out, a revoked
 * session, account deletion and `features.offline_pack == false` / `404 offline_pack_disabled`.
 */
interface OfflinePackRepository {
    /** The saved pack for [tripId], null when none (works without a network). */
    fun saved(tripId: Long): Flow<OfflinePack?>

    /** Downloads (`GET /offline-pack`, `no-store`) and stores it. A disabled feature purges and fails with `NotFound`. */
    suspend fun save(tripId: Long, today: String): AppResult<OfflinePack>

    suspend fun remove(tripId: Long): AppResult<Unit>

    /** `PUT /trips/{id}/snapshot`; [sections] holds only the allowed keys. `413`/`422` are failures callers swallow. */
    suspend fun putSnapshot(tripId: Long, sections: JsonObject): AppResult<Unit>

    /** Deletes every pack and the outbox of the current user. */
    suspend fun purgeAll()
}
