package io.github.rupakc.travelplanner.core.model.section

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientString
import io.github.rupakc.travelplanner.core.model.json.LenientStringList
import io.github.rupakc.travelplanner.core.model.json.RawBacked
import io.github.rupakc.travelplanner.core.model.json.RawBackedListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/** §3.4.3 hotels (luxury → budget tiers; multi-city adds [Hotel.city]). */
@Serializable
data class HotelsData(
    @Serializable(with = HotelListSerializer::class) val results: List<Hotel> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class Hotel(
    val name: String? = null,
    @SerialName("star_rating") val starRating: LenientDouble = null,
    @SerialName("price_per_night_usd") val pricePerNightUsd: LenientDouble = null,
    @SerialName("total_price_usd") val totalPriceUsd: LenientDouble = null,
    val location: String? = null,
    val amenities: LenientStringList = emptyList(),
    @SerialName("booking_url") val bookingUrl: String? = null,
    @SerialName("review_score") val reviewScore: LenientDouble = null,
    val source: String? = null,
    @SerialName("source_snippet") val sourceSnippet: String? = null,
    @SerialName("link_verified") val linkVerified: LenientBoolean = null,
    val city: String? = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<Hotel> {
    override fun withRaw(raw: JsonObject): Hotel = copy(raw = raw)
}

object HotelListSerializer : RawBackedListSerializer<Hotel>(Hotel.serializer())

/** §3.4.4 activities (15–20, relevance [Activity.similarityScore] 0..1). */
@Serializable
data class ActivitiesData(
    @Serializable(with = ActivityListSerializer::class) val results: List<Activity> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class Activity(
    val name: String? = null,
    val description: String? = null,
    /** food|history|adventure|culture|nature|shopping|nightlife|wellness|art|sport|family|general */
    val category: String? = null,
    @SerialName("duration_hours") val durationHours: LenientDouble = null,
    @SerialName("price_usd") val priceUsd: LenientDouble = null,
    val location: String? = null,
    /** getyourguide|viator|klook|tripadvisor|tiqets|musement|web */
    val source: String? = null,
    @SerialName("similarity_score") val similarityScore: LenientDouble = null,
    val rating: LenientDouble = null,
    @SerialName("review_count") val reviewCount: LenientInt = null,
    @SerialName("accessibility_features") val accessibilityFeatures: LenientStringList = emptyList(),
    @SerialName("family_friendly") val familyFriendly: LenientBoolean = null,
    @SerialName("age_suitability") val ageSuitability: LenientString = null,
    @SerialName("hidden_gem") val hiddenGem: LenientBoolean = null,
    @SerialName("booking_url") val bookingUrl: String? = null,
    val city: String? = null,
    /** Link-import extra (shape UNVERIFIED beyond truthiness). */
    @SerialName("from_link") val fromLink: LenientBoolean = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<Activity> {
    override fun withRaw(raw: JsonObject): Activity = copy(raw = raw)
}

object ActivityListSerializer : RawBackedListSerializer<Activity>(Activity.serializer())

/** §3.4.5 places to see. */
@Serializable
data class PlacesData(
    @Serializable(with = PlaceListSerializer::class) val results: List<PlaceToSee> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class PlaceToSee(
    val name: String? = null,
    val category: String? = null,
    val description: String? = null,
    val neighbourhood: String? = null,
    val address: String? = null,
    val rating: LenientDouble = null,
    @SerialName("review_count") val reviewCount: LenientInt = null,
    @SerialName("visit_duration_hours") val visitDurationHours: LenientDouble = null,
    @SerialName("best_time_to_visit") val bestTimeToVisit: LenientString = null,
    /** 0 = free, null = unknown. */
    @SerialName("admission_fee_usd") val admissionFeeUsd: LenientDouble = null,
    val highlights: LenientStringList = emptyList(),
    @SerialName("info_url") val infoUrl: String? = null,
    val source: String? = null,
    @SerialName("accessibility_features") val accessibilityFeatures: LenientStringList = emptyList(),
    @SerialName("wheelchair_accessible") val wheelchairAccessible: LenientBoolean = null,
    @SerialName("family_friendly") val familyFriendly: LenientBoolean = null,
    @SerialName("hidden_gem") val hiddenGem: LenientBoolean = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<PlaceToSee> {
    override fun withRaw(raw: JsonObject): PlaceToSee = copy(raw = raw)
}

object PlaceListSerializer : RawBackedListSerializer<PlaceToSee>(PlaceToSee.serializer())

/** §3.4.6 free walking tours (crawler). */
@Serializable
data class WalkingToursData(
    @Serializable(with = WalkingTourListSerializer::class) val results: List<WalkingTour> = emptyList(),
    /** Per-provider crawl status; element keys beyond `status` UNVERIFIED. */
    val sources: List<JsonObject> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class WalkingTour(
    val id: String? = null,
    val name: String? = null,
    val provider: String? = null,
    @SerialName("provider_key") val providerKey: String? = null,
    val url: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val description: String? = null,
    val rating: LenientDouble = null,
    @SerialName("review_count") val reviewCount: LenientInt = null,
    val duration: LenientString = null,
    @SerialName("price_note") val priceNote: LenientString = null,
    val city: String? = null,
    val reason: String? = null,
    @SerialName("top_pick") val topPick: LenientBoolean = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<WalkingTour> {
    override fun withRaw(raw: JsonObject): WalkingTour = copy(raw = raw)
}

object WalkingTourListSerializer : RawBackedListSerializer<WalkingTour>(WalkingTour.serializer())

/** §3.4.7 events ("What's On"). */
@Serializable
data class EventsData(
    @Serializable(with = TravelEventListSerializer::class) val results: List<TravelEvent> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class TravelEvent(
    val name: String? = null,
    /** festival|music|exhibition|sports|market|seasonal|holiday|other */
    val category: String? = null,
    val description: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    /** confirmed_annual | typical_season */
    @SerialName("date_certainty") val dateCertainty: String? = null,
    val location: String? = null,
    /** "Free" | "$30-80" | "Varies" — free text, never parsed into a number. */
    val price: LenientString = null,
    /** highlight | consider */
    val impact: String? = null,
    @SerialName("interest_match") val interestMatch: LenientStringList = emptyList(),
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<TravelEvent> {
    override fun withRaw(raw: JsonObject): TravelEvent = copy(raw = raw)
}

object TravelEventListSerializer : RawBackedListSerializer<TravelEvent>(TravelEvent.serializer())
