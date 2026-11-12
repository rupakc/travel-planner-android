package io.github.rupakc.travelplanner.core.model.trips

import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class PackDestination(
    val city: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_name") val countryName: String? = null,
)

/** One city of the emergency card; the free-form parts stay raw JSON (rendered by the shared emergency view). */
@Serializable
data class PackEmergencyCity(
    val city: String? = null,
    @SerialName("emergency_numbers") val emergencyNumbers: JsonObject = JsonObject(emptyMap()),
    val embassy: JsonObject? = null,
    val hospitals: List<JsonElement> = emptyList(),
    @SerialName("local_phrases") val localPhrases: List<JsonElement> = emptyList(),
)

/** [source] is `ai` | `static` | null. */
@Serializable
data class PackEmergency(
    val source: String? = null,
    @SerialName("as_of") val asOf: String? = null,
    val cities: List<PackEmergencyCity> = emptyList(),
)

/** A phrase with an optional romanisation. */
@Serializable
data class PackPhrase(
    val id: String? = null,
    val text: String = "",
    val romanized: String? = null,
)

/** A phrase text without an id (the address card's `take_me_to`). */
@Serializable
data class PackText(val text: String = "", val romanized: String? = null)

/** `null` for English-speaking countries. Ids: help, call_police, need_doctor, take_me_to, thank_you, how_much, where_toilet, dont_understand. */
@Serializable
data class PackPhrases(
    val language: String? = null,
    @SerialName("language_name") val languageName: String? = null,
    val items: List<PackPhrase> = emptyList(),
)

@Serializable
data class PackAddressCard(
    @SerialName("hotel_name") val hotelName: String? = null,
    val area: String? = null,
    val city: String? = null,
    @SerialName("take_me_to") val takeMeTo: PackText? = null,
)

/** [rate] is local units per 1 [home] unit; null when unknown. [source] is `snapshot` | null. */
@Serializable
data class PackCurrency(
    val local: String? = null,
    val home: String? = null,
    val rate: LenientDouble = null,
    @SerialName("as_of") val asOf: String? = null,
    val source: String? = null,
)

/**
 * `GET /trips/{id}/offline-pack`. [selections] keeps the plan keys (`itinerary`, `itinerary_layout`,
 * `itinerary_edits`, `itinerary_notes`, `itinerary_slots`, `hotel`, `flight`, `sim`, `packing_list`) raw so
 * the shared itinerary code reads them.
 */
@Serializable
data class OfflinePack(
    val version: Int = 1,
    @SerialName("trip_id") val tripId: Long = 0,
    @SerialName("generated_at") val generatedAt: String? = null,
    val title: String = "",
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val destinations: List<PackDestination> = emptyList(),
    val emergency: PackEmergency? = null,
    val phrases: PackPhrases? = null,
    @SerialName("address_card") val addressCard: PackAddressCard? = null,
    val currency: PackCurrency? = null,
    val selections: JsonObject = JsonObject(emptyMap()),
    val checkins: List<String> = emptyList(),
)

/**
 * `PUT /trips/{id}/snapshot` body: each section is the object as streamed (without `verification`) or null.
 * Built as a [JsonObject] by the caller so only the keys present are sent.
 */
typealias SnapshotBody = JsonObject
