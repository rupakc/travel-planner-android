package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.carbon.CarbonFootprint
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Trip carbon footprint (web `docs/Carbon-Footprint.md`): pure computation, anonymous-friendly, own `carbon` rate
 * bucket. Only called when `features.carbon >= 1`; an older or flag-off backend answers 404.
 */
interface CarbonApi {
    /** [body] holds `search` plus exactly one of `selections` / `results` (else 422); see `CarbonRequests`. */
    @POST("api/carbon/trip")
    suspend fun trip(
        @Body body: JsonObject,
    ): CarbonFootprint
}
