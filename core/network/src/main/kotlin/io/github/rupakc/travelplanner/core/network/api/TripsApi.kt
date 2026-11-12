package io.github.rupakc.travelplanner.core.network.api

import io.github.rupakc.travelplanner.core.model.trips.CheckinRequest
import io.github.rupakc.travelplanner.core.model.trips.CheckinResponse
import io.github.rupakc.travelplanner.core.model.trips.CheckinsResponse
import io.github.rupakc.travelplanner.core.model.trips.CreateEntryRequest
import io.github.rupakc.travelplanner.core.model.trips.CreateExpenseRequest
import io.github.rupakc.travelplanner.core.model.trips.CreateTripRequest
import io.github.rupakc.travelplanner.core.model.trips.Entry
import io.github.rupakc.travelplanner.core.model.trips.Expense
import io.github.rupakc.travelplanner.core.model.trips.ExpensesResponse
import io.github.rupakc.travelplanner.core.model.trips.JournalResponse
import io.github.rupakc.travelplanner.core.model.trips.OfflinePack
import io.github.rupakc.travelplanner.core.model.trips.PlacesResponse
import io.github.rupakc.travelplanner.core.model.trips.PrepResponse
import io.github.rupakc.travelplanner.core.model.trips.PrepToggleRequest
import io.github.rupakc.travelplanner.core.model.trips.PrepToggleResponse
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceRequest
import io.github.rupakc.travelplanner.core.model.trips.PutPlaceResponse
import io.github.rupakc.travelplanner.core.model.trips.Recap
import io.github.rupakc.travelplanner.core.model.trips.RecapCompleteResponse
import io.github.rupakc.travelplanner.core.model.trips.Stats
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.model.trips.TripsResponse
import io.github.rupakc.travelplanner.core.model.trips.UpdateEntryRequest
import io.github.rupakc.travelplanner.core.model.trips.UpdateExpenseRequest
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Trips-API. Only called when `features.trips` is set. `today` is always the device's local date.
 * A trip that is not the caller's answers `404 trip_not_found`.
 */
interface TripsApi {
    @GET("api/trips")
    suspend fun list(
        @Query("today") today: String?,
    ): TripsResponse

    @POST("api/trips")
    suspend fun create(
        @Body body: CreateTripRequest,
    ): Trip

    @GET("api/trips/stats")
    suspend fun stats(): Stats

    @GET("api/trips/{id}")
    suspend fun get(
        @Path("id") id: Long,
        @Query("today") today: String?,
    ): Trip

    /** [patch] holds any subset of `title`, `start_date`, `end_date`, `phase`; a JSON null clears a date or the override. */
    @PATCH("api/trips/{id}")
    suspend fun update(
        @Path("id") id: Long,
        @Body patch: JsonObject,
    ): Trip

    @DELETE("api/trips/{id}")
    suspend fun delete(
        @Path("id") id: Long,
    )

    @GET("api/trips/{id}/prep")
    suspend fun prep(
        @Path("id") id: Long,
        @Query("today") today: String?,
    ): PrepResponse

    @PUT("api/trips/{id}/prep/{itemId}")
    suspend fun togglePrep(
        @Path("id") id: Long,
        @Path("itemId") itemId: String,
        @Body body: PrepToggleRequest,
    ): PrepToggleResponse

    /** [body] holds only the allowed section keys (`emergency_card`, `forex`, `visa`, `packing_list`, `weather`), no `verification`. */
    @PUT("api/trips/{id}/snapshot")
    suspend fun putSnapshot(
        @Path("id") id: Long,
        @Body body: JsonObject,
    )

    /** `Cache-Control: no-store`; `404 offline_pack_disabled` when the feature is off. */
    @GET("api/trips/{id}/offline-pack")
    suspend fun offlinePack(
        @Path("id") id: Long,
        @Query("today") today: String?,
    ): OfflinePack

    @GET("api/trips/{id}/expenses")
    suspend fun expenses(
        @Path("id") id: Long,
    ): ExpensesResponse

    @POST("api/trips/{id}/expenses")
    suspend fun createExpense(
        @Path("id") id: Long,
        @Body body: CreateExpenseRequest,
    ): Expense

    @PATCH("api/trips/{id}/expenses/{eid}")
    suspend fun updateExpense(
        @Path("id") id: Long,
        @Path("eid") expenseId: Long,
        @Body body: UpdateExpenseRequest,
    ): Expense

    @DELETE("api/trips/{id}/expenses/{eid}")
    suspend fun deleteExpense(
        @Path("id") id: Long,
        @Path("eid") expenseId: Long,
    )

    @GET("api/trips/{id}/journal")
    suspend fun journal(
        @Path("id") id: Long,
    ): JournalResponse

    @POST("api/trips/{id}/journal")
    suspend fun createEntry(
        @Path("id") id: Long,
        @Body body: CreateEntryRequest,
    ): Entry

    @PATCH("api/trips/{id}/journal/{eid}")
    suspend fun updateEntry(
        @Path("id") id: Long,
        @Path("eid") entryId: Long,
        @Body body: UpdateEntryRequest,
    ): Entry

    @DELETE("api/trips/{id}/journal/{eid}")
    suspend fun deleteEntry(
        @Path("id") id: Long,
        @Path("eid") entryId: Long,
    )

    @GET("api/trips/{id}/places")
    suspend fun places(
        @Path("id") id: Long,
    ): PlacesResponse

    @PUT("api/trips/{id}/places/{placeKey}")
    suspend fun putPlace(
        @Path("id") id: Long,
        @Path("placeKey") placeKey: String,
        @Body body: PutPlaceRequest,
    ): PutPlaceResponse

    @DELETE("api/trips/{id}/places/{placeKey}")
    suspend fun deletePlace(
        @Path("id") id: Long,
        @Path("placeKey") placeKey: String,
    )

    @POST("api/trips/{id}/checkin")
    suspend fun checkin(
        @Path("id") id: Long,
        @Body body: CheckinRequest,
    ): CheckinResponse

    @GET("api/trips/{id}/checkins")
    suspend fun checkins(
        @Path("id") id: Long,
    ): CheckinsResponse

    @GET("api/trips/{id}/recap")
    suspend fun recap(
        @Path("id") id: Long,
        @Query("today") today: String?,
    ): Recap

    @POST("api/trips/{id}/recap/complete")
    suspend fun completeRecap(
        @Path("id") id: Long,
        @Query("today") today: String?,
    ): RecapCompleteResponse
}
