package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.RecapRepository
import io.github.rupakc.travelplanner.core.model.trips.Recap
import io.github.rupakc.travelplanner.core.model.trips.RecapCompleteResponse
import io.github.rupakc.travelplanner.core.network.api.TripsApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Recap over [TripsApi]; the game delta of a completion goes to [GameEvents]. Callers know `features.trips`. */
@Singleton
class RecapRepositoryImpl
    @Inject
    constructor(
        private val api: TripsApi,
        private val gameEvents: GameEvents,
    ) : RecapRepository {
        override suspend fun recap(tripId: Long, today: String): AppResult<Recap> = apiCall { api.recap(tripId, today) }

        override suspend fun complete(tripId: Long, today: String): AppResult<RecapCompleteResponse> =
            apiCall { api.completeRecap(tripId, today) }.onSuccess { gameEvents.publish(it.game) }
    }
