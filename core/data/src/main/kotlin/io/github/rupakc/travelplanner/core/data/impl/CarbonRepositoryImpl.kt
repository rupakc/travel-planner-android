package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.CarbonRepository
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.datastore.CarbonPrefsStore
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import io.github.rupakc.travelplanner.core.model.carbon.CarbonFootprint
import io.github.rupakc.travelplanner.core.network.api.CarbonApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject

@Singleton
class CarbonRepositoryImpl
    @Inject
    constructor(
        private val api: CarbonApi,
        private val flags: JourneyFlagsRepository,
        private val prefs: CarbonPrefsStore,
    ) : CarbonRepository {
        override suspend fun available(): Boolean = flags.flags().carbon

        override val diet: Flow<CarbonDiet> = prefs.diet.map(CarbonDiet::fromWire).distinctUntilChanged()

        override suspend fun setDiet(diet: CarbonDiet) = prefs.setDiet(diet.wire)

        override suspend fun footprint(body: JsonObject): AppResult<CarbonFootprint> = apiCall { api.trip(body) }
    }
