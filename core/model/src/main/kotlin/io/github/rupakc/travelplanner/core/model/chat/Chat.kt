package io.github.rupakc.travelplanner.core.model.chat

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientString
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `POST /api/chat` body (api-contract §4.1). Auth required. Never send empty [ChatMessage.content]. */
@Serializable
data class ChatRequest(
    val messages: List<ChatMessage>,
    val selections: PlanSelections = PlanSelections(),
    /** Latest sections object the chat received, keyed by section wire name. */
    @SerialName("search_results") val searchResults: JsonObject = EmptyJsonObject,
    /** Echo of the last `session_context_update.context`. */
    @SerialName("session_context") val sessionContext: JsonObject = EmptyJsonObject,
)

@Serializable
data class ChatMessage(
    /** "user" | "assistant" */
    val role: String,
    val content: String,
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

@Serializable
data class TripMapCity(val city: String? = null, val lat: LenientDouble = null, val lng: LenientDouble = null)

/** `comprehensive_itinerary.data` (api-contract §4.3). */
@Serializable
data class ChatItinerary(
    @SerialName("trip_summary") val tripSummary: TripSummary? = null,
    @SerialName("intercity_travel") val intercityTravel: List<IntercityTravel> = emptyList(),
    val days: List<ChatItineraryDay> = emptyList(),
)

@Serializable
data class TripSummary(
    val origin: String? = null,
    @SerialName("origin_lat") val originLat: LenientDouble = null,
    @SerialName("origin_lng") val originLng: LenientDouble = null,
    val cities: List<TripSummaryCity> = emptyList(),
    @SerialName("departure_date") val departureDate: String? = null,
    @SerialName("return_date") val returnDate: String? = null,
    @SerialName("total_nights") val totalNights: LenientInt = null,
    @SerialName("total_cost_usd") val totalCostUsd: LenientDouble = null,
    val travelers: LenientInt = null,
)

@Serializable
data class TripSummaryCity(
    val name: String? = null,
    val lat: LenientDouble = null,
    val lng: LenientDouble = null,
    val nights: LenientInt = null,
)

@Serializable
data class IntercityTravel(val from: String? = null, val to: String? = null, val options: List<IntercityOption> = emptyList())

@Serializable
data class IntercityOption(
    val mode: String? = null,
    val duration: LenientString = null,
    @SerialName("price_usd") val priceUsd: LenientDouble = null,
    val tip: String? = null,
)

@Serializable
data class ChatItineraryDay(
    @SerialName("day_number") val dayNumber: LenientInt = null,
    val date: String? = null,
    val city: String? = null,
    val country: String? = null,
    val theme: String? = null,
    @SerialName("is_travel_day") val isTravelDay: LenientBoolean = null,
    val hotel: ChatHotel? = null,
    val morning: ChatSlot? = null,
    val afternoon: ChatSlot? = null,
    val evening: ChatSlot? = null,
    @SerialName("places_to_see") val placesToSee: List<ChatPlace> = emptyList(),
    val dining: List<ChatDining> = emptyList(),
    @SerialName("daily_cost_usd") val dailyCostUsd: LenientDouble = null,
)

@Serializable
data class ChatHotel(
    val name: String? = null,
    val neighbourhood: String? = null,
    val stars: LenientDouble = null,
    @SerialName("price_per_night_usd") val pricePerNightUsd: LenientDouble = null,
    val tier: String? = null,
    val highlight: String? = null,
)

@Serializable
data class ChatSlot(
    val activity: String? = null,
    val place: String? = null,
    val lat: LenientDouble = null,
    val lng: LenientDouble = null,
    @SerialName("duration_hours") val durationHours: LenientDouble = null,
    @SerialName("cost_usd") val costUsd: LenientDouble = null,
    val tip: String? = null,
)

@Serializable
data class ChatPlace(
    val name: String? = null,
    val category: String? = null,
    val lat: LenientDouble = null,
    val lng: LenientDouble = null,
    @SerialName("entry_usd") val entryUsd: LenientDouble = null,
    val why: String? = null,
)

@Serializable
data class ChatDining(
    val name: String? = null,
    val cuisine: String? = null,
    @SerialName("price_range") val priceRange: String? = null,
    val neighbourhood: String? = null,
)
