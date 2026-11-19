package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.CarbonRepository
import io.github.rupakc.travelplanner.core.datastore.CarbonPrefsStore
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import io.github.rupakc.travelplanner.core.model.carbon.CarbonFootprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject

/**
 * [CarbonRepository] for tests: off unless [enabled]; [result] answers every call (a 404 by default when off);
 * [requests] records each body sent.
 */
class FakeCarbonRepository(
    var enabled: Boolean = false,
    var result: AppResult<CarbonFootprint> = AppResult.Success(CarbonFootprint(totalKg = 245.1, perPersonKg = 122.5, travellers = 2)),
) : CarbonRepository {
    val requests = mutableListOf<JsonObject>()
    private val dietFlow = MutableStateFlow(CarbonDiet.MIXED)

    override suspend fun available(): Boolean = enabled

    override val diet: Flow<CarbonDiet> = dietFlow

    override suspend fun setDiet(diet: CarbonDiet) {
        dietFlow.value = diet
    }

    override suspend fun footprint(body: JsonObject): AppResult<CarbonFootprint> {
        requests += body
        return if (enabled) result else AppResult.Failure(AppError.NotFound())
    }
}

class FakeCarbonPrefsStore : CarbonPrefsStore {
    override val diet = MutableStateFlow<String?>(null)

    override suspend fun setDiet(wire: String) {
        diet.value = wire
    }
}
