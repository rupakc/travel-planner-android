package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.JournalRepository
import io.github.rupakc.travelplanner.core.model.trips.CreateEntryRequest
import io.github.rupakc.travelplanner.core.model.trips.Entry
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceRequest
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceResponse
import io.github.rupakc.travelplanner.core.model.trips.TripPlace
import io.github.rupakc.travelplanner.core.model.trips.UpdateEntryRequest
import io.github.rupakc.travelplanner.core.network.api.TripsApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Journal entries and place visits/ratings over [TripsApi]; the `place_visited` delta goes to [GameEvents]. Callers know `features.trips`. */
@Singleton
class JournalRepositoryImpl
    @Inject
    constructor(
        private val api: TripsApi,
        private val gameEvents: GameEvents,
    ) : JournalRepository {
        override suspend fun entries(tripId: Long): AppResult<List<Entry>> = apiCall { api.journal(tripId) }.map { it.entries }

        override suspend fun createEntry(tripId: Long, request: CreateEntryRequest): AppResult<Entry> = apiCall { api.createEntry(tripId, request) }

        override suspend fun updateEntry(tripId: Long, entryId: Long, request: UpdateEntryRequest): AppResult<Entry> =
            apiCall { api.updateEntry(tripId, entryId, request) }

        override suspend fun deleteEntry(tripId: Long, entryId: Long): AppResult<Unit> = apiCall { api.deleteEntry(tripId, entryId) }.map { }

        override suspend fun places(tripId: Long): AppResult<List<TripPlace>> = apiCall { api.places(tripId) }.map { it.places }

        override suspend fun putPlace(tripId: Long, placeKey: String, request: PutPlaceRequest): AppResult<PutPlaceResponse> =
            apiCall { api.putPlace(tripId, placeKey, request.toJsonObject()) }.onSuccess { gameEvents.publish(it.game) }

        override suspend fun deletePlace(tripId: Long, placeKey: String): AppResult<Unit> = apiCall { api.deletePlace(tripId, placeKey) }.map { }
    }
