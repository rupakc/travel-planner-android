package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import io.github.rupakc.travelplanner.core.model.carbon.CarbonFootprint
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * Trip carbon footprint (`POST /api/carbon/trip`, web `docs/Carbon-Footprint.md`). Screens use
 * [io.github.rupakc.travelplanner.core.data.carbon.cardStates], which applies the feature gate, the debounce and the
 * 404 rule.
 */
interface CarbonRepository {
    /** `features.carbon >= 1` in `/api/config`; false against an older backend (never call [footprint] then). */
    suspend fun available(): Boolean

    /** The meals choice, remembered on the device (default [CarbonDiet.MIXED]). */
    val diet: Flow<CarbonDiet>

    suspend fun setDiet(diet: CarbonDiet)

    /** One calculation; a 404 is [io.github.rupakc.travelplanner.core.common.AppError.NotFound] (endpoint gone or off). */
    suspend fun footprint(body: JsonObject): AppResult<CarbonFootprint>
}
