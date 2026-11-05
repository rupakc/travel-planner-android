package io.github.rupakc.travelplanner.core.model.section

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientString
import io.github.rupakc.travelplanner.core.model.json.RawBacked
import io.github.rupakc.travelplanner.core.model.json.RawBackedListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/** §3.4.14 search itinerary (slots[]; different from the chat itinerary). */
@Serializable
data class ItineraryData(
    val days: List<ItineraryDay> = emptyList(),
    @SerialName("total_estimated_cost_usd") val totalEstimatedCostUsd: LenientDouble = null,
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class ItineraryDay(
    @SerialName("day_number") val dayNumber: LenientInt = null,
    val date: String? = null,
    val theme: String? = null,
    val city: String? = null,
    @Serializable(with = ItinerarySlotListSerializer::class) val slots: List<ItinerarySlot> = emptyList(),
    @SerialName("daily_estimated_cost_usd") val dailyEstimatedCostUsd: LenientDouble = null,
)

/**
 * One itinerary slot. Also the element of `selections.itinerary_slots`, where
 * the web adds [key] (`"<day>-<time_of_day>"`), [dayNumber] and [date].
 */
@Serializable
data class ItinerarySlot(
    /** morning | afternoon | evening */
    @SerialName("time_of_day") val timeOfDay: String? = null,
    val activity: String? = null,
    val location: String? = null,
    val city: String? = null,
    val lat: LenientDouble = null,
    val lng: LenientDouble = null,
    @SerialName("duration_hours") val durationHours: LenientDouble = null,
    val notes: LenientString = null,
    @SerialName("estimated_cost_usd") val estimatedCostUsd: LenientDouble = null,
    @SerialName("from_link") val fromLink: LenientBoolean = null,
    @SerialName("link_quote") val linkQuote: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val key: String? = null,
    @SerialName("day_number") val dayNumber: LenientInt = null,
    val date: String? = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<ItinerarySlot> {
    override fun withRaw(raw: JsonObject): ItinerarySlot = copy(raw = raw)

    companion object {
        /** Web slot identity: `"<day_number>-<time_of_day>"`, e.g. "1-morning". */
        fun slotKey(dayNumber: Int, timeOfDay: String): String = "$dayNumber-$timeOfDay"
    }
}

object ItinerarySlotListSerializer : RawBackedListSerializer<ItinerarySlot>(ItinerarySlot.serializer())

/** §3.4.15 packing list (`source` may be "static" for the template fallback). */
@Serializable
data class PackingListData(
    val categories: List<PackingCategory> = emptyList(),
    @SerialName("luggage_note") val luggageNote: LenientString = null,
    @SerialName("total_items") val totalItems: LenientInt = null,
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class PackingCategory(
    val name: String? = null,
    /** An emoji. */
    val icon: String? = null,
    @SerialName("weather_note") val weatherNote: LenientString = null,
    val items: List<PackingItem> = emptyList(),
)

@Serializable
data class PackingItem(val item: String? = null, val essential: LenientBoolean = null, val note: LenientString = null)

/** §3.4.16 trip health check (stress test). */
@Serializable
data class StressTestData(
    /** green | amber | red */
    val overall: String? = null,
    val summary: String? = null,
    val score: LenientInt = null,
    val findings: List<StressFinding> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class StressFinding(
    val severity: String? = null,
    val category: String? = null,
    @SerialName("day_number") val dayNumber: LenientInt = null,
    val issue: String? = null,
    val suggestion: String? = null,
)

/** A section whose payload could not be decoded at all; the raw JSON is kept. */
data class UndecodedSection(
    val raw: JsonObject,
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData
