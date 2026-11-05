package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.admin.AdminStatusResponse
import io.github.rupakc.travelplanner.core.model.admin.AdminUser
import io.github.rupakc.travelplanner.core.model.admin.CreateUserRequest
import io.github.rupakc.travelplanner.core.model.admin.ResetPasswordRequest
import io.github.rupakc.travelplanner.core.model.alerts.CreateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.DeletedResponse
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.FareWatchMatch
import io.github.rupakc.travelplanner.core.model.alerts.FareWatchMatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.MarkReadRequest
import io.github.rupakc.travelplanner.core.model.alerts.MarkReadResponse
import io.github.rupakc.travelplanner.core.model.alerts.NotificationsPage
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchMatch
import io.github.rupakc.travelplanner.core.model.alerts.UnreadCount
import io.github.rupakc.travelplanner.core.model.alerts.UpdateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.UpdatePriceWatchRequest
import io.github.rupakc.travelplanner.core.model.auth.ChangePasswordRequest
import io.github.rupakc.travelplanner.core.model.auth.EmailResponse
import io.github.rupakc.travelplanner.core.model.auth.EmailUpdateRequest
import io.github.rupakc.travelplanner.core.model.auth.LoginRequest
import io.github.rupakc.travelplanner.core.model.auth.LoginResponse
import io.github.rupakc.travelplanner.core.model.auth.TokenResponse
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.devices.Device
import io.github.rupakc.travelplanner.core.model.devices.DeviceList
import io.github.rupakc.travelplanner.core.model.devices.RegisterDeviceRequest
import io.github.rupakc.travelplanner.core.model.devices.UnregisterDeviceRequest
import io.github.rupakc.travelplanner.core.model.feedback.AnalyticsBatch
import io.github.rupakc.travelplanner.core.model.feedback.FeedbackRequest
import io.github.rupakc.travelplanner.core.model.health.AgentsHealth
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.ImportRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvePlaceRequest
import io.github.rupakc.travelplanner.core.model.importlink.ResolvedPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyRequest
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.plan.CreatePlanRequest
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.ShareTokenResponse
import io.github.rupakc.travelplanner.core.model.plan.SharedPlan
import io.github.rupakc.travelplanner.core.model.plan.SuccessResponse
import io.github.rupakc.travelplanner.core.model.plan.UpdatePlanRequest
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.model.preferences.TasteResetResponse
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.DiscoveryRequest
import io.github.rupakc.travelplanner.core.model.search.DiscoveryResponse
import io.github.rupakc.travelplanner.core.model.search.FareCalendar
import io.github.rupakc.travelplanner.core.model.search.LayoverPlan
import io.github.rupakc.travelplanner.core.model.search.LayoverRequest
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Paths are relative (no leading slash) so they resolve against the configured base URL, prefix included.

/** §2.4 */
interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(
        @Body body: LoginRequest,
    ): LoginResponse

    @GET("api/auth/me")
    suspend fun me(): User

    @POST("api/auth/change-password")
    suspend fun changePassword(
        @Body body: ChangePasswordRequest,
    ): TokenResponse

    @PUT("api/auth/email")
    suspend fun updateEmail(
        @Body body: EmailUpdateRequest,
    ): EmailResponse
}

/**
 * §2.1 non-streaming search & single-agent routes. Section responses are returned
 * as raw JSON and decoded with `SectionDecoder` so they stay tolerant.
 * `/filtered` bodies are the request JSON merged with filter fields.
 */
interface SearchApi {
    @POST("api/search/sync")
    suspend fun searchSync(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/flights")
    suspend fun flights(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/flights/filtered")
    suspend fun flightsFiltered(
        @Body body: JsonObject,
    ): JsonObject

    @POST("api/flights/fare-calendar")
    suspend fun fareCalendar(
        @Body body: TravelSearchRequest,
    ): FareCalendar

    @POST("api/hotels")
    suspend fun hotels(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/hotels/filtered")
    suspend fun hotelsFiltered(
        @Body body: JsonObject,
    ): JsonObject

    @POST("api/activities")
    suspend fun activities(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/activities/filtered")
    suspend fun activitiesFiltered(
        @Body body: JsonObject,
    ): JsonObject

    @POST("api/visa")
    suspend fun visa(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/sim")
    suspend fun sim(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/tips")
    suspend fun tips(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/getting-around")
    suspend fun gettingAround(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/forex")
    suspend fun forex(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/itinerary")
    suspend fun itinerary(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/events")
    suspend fun events(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/walking-tours")
    suspend fun walkingTours(
        @Body body: TravelSearchRequest,
    ): JsonObject

    @POST("api/layover")
    suspend fun layover(
        @Body body: LayoverRequest,
    ): LayoverPlan

    /** TravelSearchRequest + `itinerary` (+ optional flights/visa/weather). */
    @POST("api/stress-test")
    suspend fun stressTest(
        @Body body: JsonObject,
    ): JsonObject

    @POST("api/discover")
    suspend fun discover(
        @Body body: DiscoveryRequest,
    ): DiscoveryResponse

    @POST("api/compare/destination")
    suspend fun compareDestination(
        @Body body: TravelSearchRequest,
    ): CompareSummary
}

/** §2.3 typeaheads (no auth). */
interface ReferenceApi {
    @GET("api/airports/search")
    suspend fun airports(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
    ): List<Airport>

    @GET("api/nationalities/search")
    suspend fun nationalities(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
    ): List<Nationality>
}

/** §2.5 */
interface PlansApi {
    @GET("api/plans")
    suspend fun list(): List<Plan>

    @POST("api/plans")
    suspend fun create(
        @Body body: CreatePlanRequest,
    ): Plan

    @GET("api/plans/{id}")
    suspend fun get(
        @Path("id") id: Long,
    ): Plan

    @PUT("api/plans/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body body: UpdatePlanRequest,
    ): Plan

    @DELETE("api/plans/{id}")
    suspend fun delete(
        @Path("id") id: Long,
    ): SuccessResponse

    @POST("api/plans/{id}/share")
    suspend fun share(
        @Path("id") id: Long,
    ): ShareTokenResponse

    @DELETE("api/plans/{id}/share")
    suspend fun unshare(
        @Path("id") id: Long,
    ): SuccessResponse

    /** Public (no auth). */
    @GET("api/share/{token}")
    suspend fun shared(
        @Path("token") token: String,
    ): SharedPlan
}

/** §2.6 */
interface PreferencesApi {
    @GET("api/preferences")
    suspend fun get(): Preferences

    @PUT("api/preferences")
    suspend fun put(
        @Body body: Preferences,
    ): Preferences

    @GET("api/taste-profile")
    suspend fun tasteProfile(): TasteProfile

    @DELETE("api/taste-profile")
    suspend fun resetTasteProfile(): TasteResetResponse

    @GET("api/recent-searches")
    suspend fun recentSearches(): List<RecentSearch>

    @POST("api/recent-searches")
    suspend fun addRecentSearch(
        @Body body: RecentSearchIn,
    ): RecentSearch

    @DELETE("api/recent-searches/{id}")
    suspend fun deleteRecentSearch(
        @Path("id") id: String,
    ): Response<Unit>
}

/** §2.10 */
interface FareWatchApi {
    @GET("api/fare-watches")
    suspend fun list(): List<FareWatch>

    @POST("api/fare-watches/match")
    suspend fun match(
        @Body body: FareWatchMatchRequest,
    ): FareWatchMatch

    @POST("api/fare-watches")
    suspend fun create(
        @Body body: CreateFareWatchRequest,
    ): FareWatch

    @GET("api/fare-watches/{id}")
    suspend fun get(
        @Path("id") id: Long,
    ): FareWatch

    @PATCH("api/fare-watches/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body body: UpdateFareWatchRequest,
    ): FareWatch

    @DELETE("api/fare-watches/{id}")
    suspend fun delete(
        @Path("id") id: Long,
    ): DeletedResponse

    @POST("api/fare-watches/{id}/check")
    suspend fun check(
        @Path("id") id: Long,
    ): FareWatch
}

/** §2.11. Create/match bodies carry the spec under a key equal to `kind`, so they are raw JSON. */
interface PriceWatchApi {
    @GET("api/price-watches")
    suspend fun list(
        @Query("kind") kind: String? = null,
    ): List<PriceWatch>

    @POST("api/price-watches/match")
    suspend fun match(
        @Body body: JsonObject,
    ): PriceWatchMatch

    @POST("api/price-watches")
    suspend fun create(
        @Body body: JsonObject,
    ): PriceWatch

    @GET("api/price-watches/{id}")
    suspend fun get(
        @Path("id") id: Long,
    ): PriceWatch

    @PATCH("api/price-watches/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body body: UpdatePriceWatchRequest,
    ): PriceWatch

    @DELETE("api/price-watches/{id}")
    suspend fun delete(
        @Path("id") id: Long,
    ): DeletedResponse

    @POST("api/price-watches/{id}/check")
    suspend fun check(
        @Path("id") id: Long,
    ): PriceWatch
}

/** §2.9 */
interface NotificationsApi {
    @GET("api/notifications")
    suspend fun list(
        @Query("unread_only") unreadOnly: Boolean = false,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): NotificationsPage

    @GET("api/notifications/unread-count")
    suspend fun unreadCount(): UnreadCount

    @POST("api/notifications/read")
    suspend fun markRead(
        @Body body: MarkReadRequest,
    ): MarkReadResponse
}

/** §2.1 import. Uses the 300 s AI client (the web uses 90 s; extraction is an LLM call). */
interface ImportApi {
    @POST("api/import/extract")
    suspend fun extract(
        @Body body: ImportRequest,
    ): ImportPreview

    @POST("api/import/resolve-place")
    suspend fun resolvePlace(
        @Body body: ResolvePlaceRequest,
    ): ResolvedPlace
}

/** Spec §7 (new): push device registration. */
interface DevicesApi {
    @POST("api/devices")
    suspend fun register(
        @Body body: RegisterDeviceRequest,
    ): Device

    @GET("api/devices")
    suspend fun list(): DeviceList

    @DELETE("api/devices/{id}")
    suspend fun delete(
        @Path("id") id: Long,
    ): Response<Unit>

    /** Idempotent; call on sign-out. */
    @POST("api/devices/unregister")
    suspend fun unregister(
        @Body body: UnregisterDeviceRequest,
    ): Response<Unit>
}

/** Spec §7 (new): near-me recommendations; cache miss takes 10–40 s. */
interface NearbyApi {
    @POST("api/nearby")
    suspend fun nearby(
        @Body body: NearbyRequest,
    ): NearbyResponse
}

/** §2.7 */
interface FeedbackApi {
    @POST("api/feedback")
    suspend fun feedback(
        @Body body: FeedbackRequest,
    ): JsonObject

    @POST("api/analytics/events")
    suspend fun analytics(
        @Body body: AnalyticsBatch,
    ): Response<Unit>
}

/** §2.8 admin (not used by any v1 screen). */
interface AdminApi {
    @POST("api/admin/users")
    suspend fun createUser(
        @Body body: CreateUserRequest,
    ): AdminUser

    @GET("api/admin/users")
    suspend fun users(): List<AdminUser>

    @DELETE("api/admin/users/{username}")
    suspend fun deactivate(
        @Path("username") username: String,
    ): Response<Unit>

    @POST("api/admin/users/{username}/reactivate")
    suspend fun reactivate(
        @Path("username") username: String,
    ): AdminStatusResponse

    @POST("api/admin/users/{username}/reset-password")
    suspend fun resetPassword(
        @Path("username") username: String,
        @Body body: ResetPasswordRequest,
    ): AdminStatusResponse

    @GET("api/admin/feedback")
    suspend fun feedback(
        @Query("page") page: String? = null,
        @Query("category") category: String? = null,
        @Query("min_rating") minRating: Int? = null,
    ): List<JsonObject>
}

/** §0.6 (outside /api). */
interface HealthApi {
    @GET("health")
    suspend fun health(): Health

    @GET("health/agents")
    suspend fun agents(): AgentsHealth
}
