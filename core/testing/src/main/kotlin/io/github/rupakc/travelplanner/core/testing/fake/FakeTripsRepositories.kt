package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.ExpensesRepository
import io.github.rupakc.travelplanner.core.data.repository.FlushResult
import io.github.rupakc.travelplanner.core.data.repository.JournalRepository
import io.github.rupakc.travelplanner.core.data.repository.OfflinePackRepository
import io.github.rupakc.travelplanner.core.data.repository.Outbox
import io.github.rupakc.travelplanner.core.data.repository.OutboxRequest
import io.github.rupakc.travelplanner.core.data.repository.PrepRepository
import io.github.rupakc.travelplanner.core.data.repository.RecapRepository
import io.github.rupakc.travelplanner.core.data.repository.TripsRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

/** Settable results, call logs of method names with arguments. Unset results are [NotStubbed]. */
class FakeTripsRepository : TripsRepository {
    val calls = mutableListOf<String>()
    var listResult: AppResult<TripsResponse> = AppResult.Success(TripsResponse())
    var createResult: AppResult<Trip> = NotStubbed
    var getResult: AppResult<Trip> = NotStubbed
    var updateResult: AppResult<Trip> = NotStubbed
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit)
    var statsResult: AppResult<Stats> = AppResult.Success(Stats())
    var checkinResult: AppResult<CheckinResponse> = NotStubbed
    var checkinsResult: AppResult<CheckinsResponse> = AppResult.Success(CheckinsResponse())
    val createRequests = mutableListOf<CreateTripRequest>()

    /** What [isShowingCachedList] answers (the offline fallback of the real repository). */
    var showingCached = false
    val patches = mutableListOf<JsonObject>()

    override suspend fun list(today: String) = listResult.also { calls += "list:$today" }

    override fun isShowingCachedList() = showingCached

    override suspend fun create(request: CreateTripRequest) =
        createResult.also {
            createRequests += request
            calls += "create"
        }

    override suspend fun get(id: Long, today: String) = getResult.also { calls += "get:$id:$today" }

    override suspend fun update(id: Long, patch: JsonObject) =
        updateResult.also {
            patches += patch
            calls += "update:$id"
        }

    override suspend fun delete(id: Long) = deleteResult.also { calls += "delete:$id" }

    override suspend fun stats() = statsResult.also { calls += "stats" }

    override suspend fun checkin(id: Long, day: String) = checkinResult.also { calls += "checkin:$id:$day" }

    override suspend fun checkins(id: Long) = checkinsResult.also { calls += "checkins:$id" }
}

class FakePrepRepository : PrepRepository {
    val calls = mutableListOf<String>()
    var prepResult: AppResult<PrepResponse> = AppResult.Success(PrepResponse())
    var toggleResult: AppResult<PrepToggleResponse> = NotStubbed

    override suspend fun prep(tripId: Long, today: String) = prepResult.also { calls += "prep:$tripId:$today" }

    override suspend fun toggle(tripId: Long, itemId: String, done: Boolean) = toggleResult.also { calls += "toggle:$tripId:$itemId:$done" }
}

class FakeExpensesRepository : ExpensesRepository {
    val calls = mutableListOf<String>()
    val created = mutableListOf<CreateExpenseRequest>()
    var listResult: AppResult<ExpensesResponse> = AppResult.Success(ExpensesResponse())
    var createResult: AppResult<Expense> = NotStubbed
    var updateResult: AppResult<Expense> = NotStubbed
    var deleteResult: AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun list(tripId: Long) = listResult.also { calls += "list:$tripId" }

    override suspend fun create(tripId: Long, request: CreateExpenseRequest) =
        createResult.also {
            created += request
            calls += "create:$tripId"
        }

    override suspend fun update(tripId: Long, expenseId: Long, request: UpdateExpenseRequest) =
        updateResult.also { calls += "update:$tripId:$expenseId" }

    override suspend fun delete(tripId: Long, expenseId: Long) = deleteResult.also { calls += "delete:$tripId:$expenseId" }
}

class FakeJournalRepository : JournalRepository {
    val calls = mutableListOf<String>()
    val created = mutableListOf<CreateEntryRequest>()
    val placePuts = mutableListOf<Pair<String, PutPlaceRequest>>()
    var entriesResult: AppResult<List<Entry>> = AppResult.Success(emptyList())
    var createEntryResult: AppResult<Entry> = NotStubbed
    var updateEntryResult: AppResult<Entry> = NotStubbed
    var deleteEntryResult: AppResult<Unit> = AppResult.Success(Unit)
    var placesResult: AppResult<List<TripPlace>> = AppResult.Success(emptyList())
    var putPlaceResult: AppResult<PutPlaceResponse> = NotStubbed
    var deletePlaceResult: AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun entries(tripId: Long) = entriesResult.also { calls += "entries:$tripId" }

    override suspend fun createEntry(tripId: Long, request: CreateEntryRequest) =
        createEntryResult.also {
            created += request
            calls += "createEntry:$tripId"
        }

    override suspend fun updateEntry(tripId: Long, entryId: Long, request: UpdateEntryRequest) =
        updateEntryResult.also { calls += "updateEntry:$tripId:$entryId" }

    override suspend fun deleteEntry(tripId: Long, entryId: Long) = deleteEntryResult.also { calls += "deleteEntry:$tripId:$entryId" }

    override suspend fun places(tripId: Long) = placesResult.also { calls += "places:$tripId" }

    override suspend fun putPlace(tripId: Long, placeKey: String, request: PutPlaceRequest) =
        putPlaceResult.also {
            placePuts += placeKey to request
            calls += "putPlace:$tripId:$placeKey"
        }

    override suspend fun deletePlace(tripId: Long, placeKey: String) = deletePlaceResult.also { calls += "deletePlace:$tripId:$placeKey" }
}

class FakeRecapRepository : RecapRepository {
    val calls = mutableListOf<String>()
    var recapResult: AppResult<Recap> = NotStubbed
    var completeResult: AppResult<RecapCompleteResponse> = NotStubbed

    override suspend fun recap(tripId: Long, today: String) = recapResult.also { calls += "recap:$tripId:$today" }

    override suspend fun complete(tripId: Long, today: String) = completeResult.also { calls += "complete:$tripId:$today" }
}

/** In-memory packs keyed by trip id. */
class FakeOfflinePackRepository : OfflinePackRepository {
    val calls = mutableListOf<String>()
    val packs = MutableStateFlow<Map<Long, OfflinePack>>(emptyMap())
    var saveResult: AppResult<OfflinePack>? = null
    var snapshotResult: AppResult<Unit> = AppResult.Success(Unit)
    val snapshots = mutableListOf<Pair<Long, JsonObject>>()

    override fun saved(tripId: Long): Flow<OfflinePack?> = packs.map { it[tripId] }

    override suspend fun save(tripId: Long, today: String): AppResult<OfflinePack> {
        calls += "save:$tripId:$today"
        val result = saveResult ?: AppResult.Success(OfflinePack(tripId = tripId))
        if (result is AppResult.Success) packs.value = packs.value + (tripId to result.value)
        return result
    }

    override suspend fun remove(tripId: Long): AppResult<Unit> {
        calls += "remove:$tripId"
        packs.value = packs.value - tripId
        return AppResult.Success(Unit)
    }

    override suspend fun putSnapshot(tripId: Long, sections: JsonObject) =
        snapshotResult.also {
            snapshots += tripId to sections
            calls += "snapshot:$tripId"
        }

    override suspend fun purgeAll() {
        calls += "purgeAll"
        packs.value = emptyMap()
    }
}

/** In-memory [Outbox]: records queued requests (a repeated key replaces) and counts flushes. */
class FakeOutbox : Outbox {
    val queued = MutableStateFlow<List<OutboxRequest>>(emptyList())
    var enqueueResult: AppResult<Unit> = AppResult.Success(Unit)
    var flushes = 0

    override suspend fun enqueue(request: OutboxRequest): AppResult<Unit> {
        if (enqueueResult is AppResult.Success) queued.value = queued.value.filterNot { it.key == request.key } + request
        return enqueueResult
    }

    override fun pendingKeys(): Flow<Set<String>> = queued.map { list -> list.map { it.key }.toSet() }

    override suspend fun flush(userId: String?): FlushResult {
        flushes++
        return FlushResult()
    }
}

class FakeJourneyFlagsRepository(
    var flags: io.github.rupakc.travelplanner.core.domain.server.JourneyFlags =
        io.github.rupakc.travelplanner.core.domain.server.JourneyFlags(trips = true),
) : io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository {
    override suspend fun flags() = flags
}

/** A [io.github.rupakc.travelplanner.core.data.offline.PlanJourney] over fakes; [flags], [packs] and [telemetry] are exposed for assertions. */
class FakePlanJourney(
    val flags: FakeJourneyFlagsRepository = FakeJourneyFlagsRepository(),
    val packs: FakeOfflinePackRepository = FakeOfflinePackRepository(),
    val outbox: FakeOutbox = FakeOutbox(),
    val prep: FakePrepRepository = FakePrepRepository(),
    val telemetry: FakeTelemetry = FakeTelemetry(),
    clock: io.github.rupakc.travelplanner.core.common.AppClock = io.github.rupakc.travelplanner.core.common.SystemAppClock(),
) {
    val journey =
        io.github.rupakc.travelplanner.core.data.offline.PlanJourney(
            flags,
            io.github.rupakc.travelplanner.core.data.offline.TripSnapshotUploader(packs, outbox),
            prep,
            clock,
            telemetry,
        )
}
