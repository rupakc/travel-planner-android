package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.alerts.AdviceTip
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefs
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefsUpdate
import io.github.rupakc.travelplanner.core.model.alerts.PriceStory
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchResponse
import io.github.rupakc.travelplanner.core.model.alerts.Savings
import io.github.rupakc.travelplanner.core.model.alerts.SuggestionsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Alerts API 2.0. Only called when `features.alerts_api >= 2` or `features.price_alerts_v2` (an older backend answers 404);
 * quick-watch and suggestions additionally need `features.live_prices`. [quickWatch] returns 201 (new) or 200 (existing).
 */
interface AlertsV2Api {
    @GET("api/alerts/summary")
    suspend fun summary(): Savings

    @GET("api/alerts/watches/{kind}/{id}/story")
    suspend fun story(
        @Path("kind") kind: String,
        @Path("id") id: Long,
    ): PriceStory

    @POST("api/alerts/watches/{kind}/{id}/advice")
    suspend fun advice(
        @Path("kind") kind: String,
        @Path("id") id: Long,
    ): AdviceTip

    @POST("api/alerts/quick-watch")
    suspend fun quickWatch(
        @Body body: QuickWatchRequest,
    ): QuickWatchResponse

    @GET("api/alerts/suggestions")
    suspend fun suggestions(
        @Query("plan_id") planId: Long? = null,
    ): SuggestionsResponse

    @GET("api/alerts/preferences")
    suspend fun preferences(): AlertPrefs

    @PUT("api/alerts/preferences")
    suspend fun updatePreferences(
        @Body body: AlertPrefsUpdate,
    ): AlertPrefs
}
