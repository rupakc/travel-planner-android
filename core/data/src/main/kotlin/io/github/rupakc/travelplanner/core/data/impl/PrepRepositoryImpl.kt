package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.PrepRepository
import io.github.rupakc.travelplanner.core.model.trips.PrepResponse
import io.github.rupakc.travelplanner.core.model.trips.PrepToggleRequest
import io.github.rupakc.travelplanner.core.model.trips.PrepToggleResponse
import io.github.rupakc.travelplanner.core.network.api.TripsApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Prep checklist over [TripsApi]; the `trip_ready` game delta of a toggle goes to [GameEvents]. Callers know `features.trips`. */
@Singleton
class PrepRepositoryImpl
    @Inject
    constructor(
        private val api: TripsApi,
        private val gameEvents: GameEvents,
    ) : PrepRepository {
        override suspend fun prep(tripId: Long, today: String): AppResult<PrepResponse> = apiCall { api.prep(tripId, today) }

        override suspend fun toggle(tripId: Long, itemId: String, done: Boolean): AppResult<PrepToggleResponse> =
            apiCall { api.togglePrep(tripId, itemId, PrepToggleRequest(done)) }.onSuccess { gameEvents.publish(it.game) }
    }
