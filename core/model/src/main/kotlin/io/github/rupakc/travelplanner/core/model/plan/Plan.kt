package io.github.rupakc.travelplanner.core.model.plan

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.search.ImportSource
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A saved plan (api-contract §2.5). [searchData] is kept as raw JSON because the
 * web may store keys the app does not model; [searchRequest] decodes it.
 * Timestamps are SQLite UTC strings "YYYY-MM-DD HH:MM:SS".
 */
@Serializable
data class Plan(
    val id: Long,
    val username: String? = null,
    val name: String = "",
    @SerialName("search_data") val searchData: JsonObject = EmptyJsonObject,
    val selections: PlanSelections = PlanSelections(),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("share_token") val shareToken: String? = null,
) {
    val searchRequest: TravelSearchRequest?
        get() = runCatching { TravelJson.decodeFromJsonElement(TravelSearchRequest.serializer(), searchData) }.getOrNull()
}

/** `POST /api/plans` body (all required). */
@Serializable
data class CreatePlanRequest(
    val name: String,
    @SerialName("search_data") val searchData: JsonObject,
    val selections: PlanSelections,
)

/** `PUT /api/plans/{id}` body; `search_data` is immutable after create. */
@Serializable
data class UpdatePlanRequest(val name: String? = null, val selections: PlanSelections? = null)

@Serializable
data class ShareTokenResponse(
    @SerialName("share_token") val shareToken: String,
)

/** `{"success":true}` responses. */
@Serializable
data class SuccessResponse(val success: LenientBoolean = null)

/** `GET /api/share/{token}` (public; personal fields stripped). */
@Serializable
data class SharedPlan(
    val name: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("search_data") val searchData: SharedSearchData = SharedSearchData(),
    val selections: PlanSelections = PlanSelections(),
)

@Serializable
data class SharedSearchData(
    val origin: String? = null,
    val destination: String? = null,
    val destinations: List<String>? = null,
    @SerialName("departure_date") val departureDate: String? = null,
    @SerialName("return_date") val returnDate: String? = null,
    val interests: List<String> = emptyList(),
    @SerialName("num_travelers") val numTravelers: LenientInt = null,
    val adults: LenientInt = null,
    val children: LenientInt = null,
    val seniors: LenientInt = null,
    val infants: LenientInt = null,
    val pace: String? = null,
    @SerialName("import_source") val importSource: ImportSource? = null,
)
