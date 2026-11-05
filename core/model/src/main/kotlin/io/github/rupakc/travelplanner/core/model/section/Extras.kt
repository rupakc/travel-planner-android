package io.github.rupakc.travelplanner.core.model.section

import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** §3.4.17 Travel Ease grade (`confidence` event; not a section tile). green ≥ 71, amber ≥ 41. */
@Serializable
data class ConfidenceData(
    val total: LenientInt = null,
    /** green | amber | red */
    val grade: String? = null,
    val destination: String? = null,
    /** Keys: visa_ease, safety, english_friendliness, cost_vs_budget, tourist_infrastructure. */
    @SerialName("sub_scores") val subScores: Map<String, ConfidenceSubScore> = emptyMap(),
    val error: String? = null,
)

@Serializable
data class ConfidenceSubScore(
    val score: LenientInt = null,
    val max: LenientInt = null,
    val label: String? = null,
    val note: String? = null,
)

/** §3.4.18 pricing advisor (banner inside Flights; may never arrive). */
@Serializable
data class PricingAdvisorData(
    val route: String? = null,
    @SerialName("current_avg_price_usd") val currentAvgPriceUsd: LenientDouble = null,
    @SerialName("price_status") val priceStatus: String? = null,
    @SerialName("price_status_label") val priceStatusLabel: String? = null,
    /** "wait" | … */
    val recommendation: String? = null,
    @SerialName("recommendation_detail") val recommendationDetail: String? = null,
    @SerialName("optimal_booking_window") val optimalBookingWindow: LenientString = null,
    @SerialName("trend_data") val trendData: List<PriceTrendPoint> = emptyList(),
    val confidence: LenientString = null,
    val disclaimer: String? = null,
    val error: String? = null,
)

@Serializable
data class PriceTrendPoint(
    @SerialName("days_before") val daysBefore: LenientInt = null,
    @SerialName("relative_price") val relativePrice: LenientDouble = null,
)
