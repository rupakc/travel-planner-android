package io.github.rupakc.travelplanner.core.model.alerts

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Price watch row (hotel / activity / trip; api-contract §2.11). */
@Serializable
data class PriceWatch(
    val id: Long,
    /** hotel | activity | trip */
    val kind: String? = null,
    @SerialName("plan_id") val planId: Long? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val subject: JsonObject = EmptyJsonObject,
    val currency: String? = null,
    @SerialName("alert_mode") val alertMode: String? = null,
    @SerialName("target_price") val targetPrice: LenientDouble = null,
    @SerialName("email_enabled") val emailEnabled: LenientBoolean = null,
    val status: String? = null,
    @SerialName("baseline_price") val baselinePrice: LenientDouble = null,
    @SerialName("last_price") val lastPrice: LenientDouble = null,
    @SerialName("lowest_price") val lowestPrice: LenientDouble = null,
    @SerialName("last_notified_price") val lastNotifiedPrice: LenientDouble = null,
    @SerialName("last_notified_at") val lastNotifiedAt: String? = null,
    @SerialName("own_peak_price") val ownPeakPrice: LenientDouble = null,
    @SerialName("failure_count") val failureCount: LenientInt = null,
    @SerialName("unavailable_count") val unavailableCount: LenientInt = null,
    /** `{status, link, …}` or null (keys beyond status/link UNVERIFIED). */
    @SerialName("last_observation") val lastObservation: JsonObject? = null,
    @SerialName("check_count") val checkCount: LenientInt = null,
    @SerialName("last_checked_at") val lastCheckedAt: String? = null,
    @SerialName("next_check_at") val nextCheckAt: String? = null,
    @SerialName("expires_on") val expiresOn: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val problem: String? = null,
    @SerialName("already_below_target") val alreadyBelowTarget: LenientBoolean = null,
    val link: String? = null,
    val history: List<PriceWatchHistory> = emptyList(),
    @SerialName("already_watching") val alreadyWatching: LenientBoolean = null,
    /** checked | unavailable | failed | errored | expired | postponed | deleted | … */
    @SerialName("check_status") val checkStatus: String? = null,
    val notification: AppNotification? = null,
)

@Serializable
data class PriceWatchHistory(
    val id: Long? = null,
    @SerialName("watch_id") val watchId: Long? = null,
    @SerialName("checked_at") val checkedAt: String? = null,
    val price: LenientDouble = null,
    /** Shape UNVERIFIED. */
    val breakdown: kotlinx.serialization.json.JsonElement? = null,
    val observed: LenientBoolean = null,
)

/**
 * `POST /api/price-watches` body. The spec rides under a key equal to [kind]
 * (`{"kind":"hotel","hotel":{…}}`), so the repository builds the JSON via [toJson].
 */
data class CreatePriceWatchRequest(
    val kind: String,
    val spec: JsonObject,
    val planId: Long? = null,
    val alertMode: String = ALERT_ANY_DROP,
    val targetPrice: Double? = null,
    val targetCurrency: String? = null,
    val emailEnabled: Boolean = false,
)

/** Typed specs; encode with TravelJson and pass as [CreatePriceWatchRequest.spec]. */
@Serializable
data class HotelWatchSpec(
    val name: String,
    val city: String,
    @SerialName("check_in") val checkIn: String,
    @SerialName("check_out") val checkOut: String,
    val adults: Int,
    val rooms: Int? = null,
    @SerialName("property_token") val propertyToken: String? = null,
    @SerialName("estimate_total") val estimateTotal: Double? = null,
    @SerialName("plan_id") val planId: Long? = null,
)

@Serializable
data class ActivityWatchSpec(
    val name: String,
    val city: String,
    val travellers: Int? = null,
    val date: String? = null,
    @SerialName("listing_url") val listingUrl: String? = null,
    @SerialName("estimate_price") val estimatePrice: Double? = null,
)

@Serializable
data class TripWatchSpec(
    @SerialName("plan_id") val planId: Long,
)

@Serializable
data class PriceWatchMatch(val watch: PriceWatch? = null)

@Serializable
data class UpdatePriceWatchRequest(
    val status: String? = null,
    @SerialName("alert_mode") val alertMode: String? = null,
    @SerialName("target_price") val targetPrice: Double? = null,
    @SerialName("email_enabled") val emailEnabled: Boolean? = null,
)

/** Element of `suggestions` in a `no_live_price` 422 (`price_watch.NoLivePrice`). */
@Serializable
data class PriceSuggestion(
    val title: String? = null,
    val subtitle: String? = null,
    val price: LenientDouble = null,
    val link: String? = null,
    /** Send back as-is to watch this one instead. */
    val spec: JsonObject? = null,
)

/** 422 `no_live_price` body. `estimate` e.g. `{"price":420.0,"source":"ai_estimate"}` — always label as estimate. */
@Serializable
data class NoLivePriceError(
    val detail: String? = null,
    val code: String? = null,
    val message: String? = null,
    val suggestions: List<PriceSuggestion> = emptyList(),
    val estimate: JsonObject? = null,
)

/** 422 `target_currency_mismatch` body. */
@Serializable
data class TargetCurrencyMismatchError(
    val detail: String? = null,
    val code: String? = null,
    val message: String? = null,
    val currency: String? = null,
    val price: LenientDouble = null,
    @SerialName("unit_price") val unitPrice: LenientDouble = null,
)

object PriceWatchKinds {
    const val HOTEL = "hotel"
    const val ACTIVITY = "activity"
    const val TRIP = "trip"
}
