package io.github.rupakc.travelplanner.core.model.alerts

import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fare watch row (api-contract §2.10). `email_enabled` may be 0/1 → lenient. */
@Serializable
data class FareWatch(
    val id: Long,
    @SerialName("plan_id") val planId: Long? = null,
    @SerialName("origin_code") val originCode: String? = null,
    @SerialName("destination_code") val destinationCode: String? = null,
    @SerialName("origin_label") val originLabel: String? = null,
    @SerialName("destination_label") val destinationLabel: String? = null,
    @SerialName("departure_date") val departureDate: String? = null,
    @SerialName("return_date") val returnDate: String? = null,
    @SerialName("num_travelers") val numTravelers: LenientInt = null,
    val cabin: String? = null,
    /** 0 | 3 */
    @SerialName("flexible_days") val flexibleDays: LenientInt = null,
    /** any_drop | below_target */
    @SerialName("alert_mode") val alertMode: String? = null,
    @SerialName("target_price") val targetPrice: LenientDouble = null,
    @SerialName("email_enabled") val emailEnabled: LenientBoolean = null,
    /** active | paused | error | expired */
    val status: String? = null,
    @SerialName("baseline_price") val baselinePrice: LenientDouble = null,
    @SerialName("last_price") val lastPrice: LenientDouble = null,
    @SerialName("lowest_price") val lowestPrice: LenientDouble = null,
    @SerialName("lowest_price_date") val lowestPriceDate: String? = null,
    @SerialName("last_notified_price") val lastNotifiedPrice: LenientDouble = null,
    @SerialName("last_notified_at") val lastNotifiedAt: String? = null,
    @SerialName("baseline_price_level") val baselinePriceLevel: String? = null,
    @SerialName("own_peak_price") val ownPeakPrice: LenientDouble = null,
    @SerialName("own_lowest_price") val ownLowestPrice: LenientDouble = null,
    @SerialName("cheaper_notified_price") val cheaperNotifiedPrice: LenientDouble = null,
    @SerialName("cheaper_notified_at") val cheaperNotifiedAt: String? = null,
    @SerialName("cheaper_peak_price") val cheaperPeakPrice: LenientDouble = null,
    @SerialName("google_price_level") val googlePriceLevel: String? = null,
    @SerialName("google_typical_low") val googleTypicalLow: LenientDouble = null,
    @SerialName("google_typical_high") val googleTypicalHigh: LenientDouble = null,
    @SerialName("check_count") val checkCount: LenientInt = null,
    @SerialName("failure_count") val failureCount: LenientInt = null,
    @SerialName("last_checked_at") val lastCheckedAt: String? = null,
    @SerialName("next_check_at") val nextCheckAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Friendly failure text or null. */
    val problem: String? = null,
    @SerialName("already_below_target") val alreadyBelowTarget: LenientBoolean = null,
    @SerialName("already_low") val alreadyLow: LenientBoolean = null,
    /** Alerts 2.0: true until the first check ran (then `last_price` is null). Absent on older backends. */
    @SerialName("pending_first_check") val pendingFirstCheck: LenientBoolean = null,
    val history: List<FareWatchHistory> = emptyList(),
    /** Google Flights link. */
    @SerialName("booking_url") val bookingUrl: String? = null,
    /** Create responses only. */
    @SerialName("already_watching") val alreadyWatching: LenientBoolean = null,
    /** Check responses only. */
    @SerialName("check_status") val checkStatus: String? = null,
    /** Check responses only. */
    val notification: AppNotification? = null,
)

@Serializable
data class FareWatchHistory(
    val id: Long? = null,
    @SerialName("watch_id") val watchId: Long? = null,
    @SerialName("checked_at") val checkedAt: String? = null,
    val price: LenientDouble = null,
    @SerialName("cheapest_price") val cheapestPrice: LenientDouble = null,
    @SerialName("cheapest_departure_date") val cheapestDepartureDate: String? = null,
    @SerialName("cheapest_return_date") val cheapestReturnDate: String? = null,
    @SerialName("price_level") val priceLevel: String? = null,
)

/** `POST /api/fare-watches` body. `target_price` is required when `alert_mode` = below_target. */
@Serializable
data class CreateFareWatchRequest(
    @SerialName("plan_id") val planId: Long? = null,
    val origin: String? = null,
    val destination: String? = null,
    val destinations: List<String>? = null,
    @SerialName("departure_date") val departureDate: String? = null,
    @SerialName("return_date") val returnDate: String? = null,
    @SerialName("num_travelers") val numTravelers: Int = 1,
    @SerialName("alert_mode") val alertMode: String = ALERT_ANY_DROP,
    @SerialName("target_price") val targetPrice: Double? = null,
    /** 0 or 3 */
    @SerialName("flexible_days") val flexibleDays: Int = 0,
    @SerialName("email_enabled") val emailEnabled: Boolean = false,
)

/** `POST /api/fare-watches/match` body (trip fields). */
@Serializable
data class FareWatchMatchRequest(
    @SerialName("plan_id") val planId: Long? = null,
    val origin: String? = null,
    val destination: String? = null,
    val destinations: List<String>? = null,
    @SerialName("departure_date") val departureDate: String? = null,
    @SerialName("return_date") val returnDate: String? = null,
    @SerialName("num_travelers") val numTravelers: Int = 1,
)

@Serializable
data class FareWatchMatch(val watch: FareWatch? = null)

/** `PATCH /api/fare-watches/{id}`; send only what changes. */
@Serializable
data class UpdateFareWatchRequest(
    /** active | paused */
    val status: String? = null,
    @SerialName("alert_mode") val alertMode: String? = null,
    @SerialName("target_price") val targetPrice: Double? = null,
    @SerialName("flexible_days") val flexibleDays: Int? = null,
    @SerialName("email_enabled") val emailEnabled: Boolean? = null,
)

/** `{"deleted":true}` */
@Serializable
data class DeletedResponse(val deleted: LenientBoolean = null)

const val ALERT_ANY_DROP = "any_drop"
const val ALERT_BELOW_TARGET = "below_target"
