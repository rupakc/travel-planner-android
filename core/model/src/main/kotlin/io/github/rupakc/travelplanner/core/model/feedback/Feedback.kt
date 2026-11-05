package io.github.rupakc.travelplanner.core.model.feedback

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `POST /api/feedback` (201). category: bug | feature_request | general | praise. */
@Serializable
data class FeedbackRequest(
    val page: String,
    val rating: Int,
    val category: String,
    val message: String? = null,
    val metadata: JsonObject? = null,
)

/** `POST /api/analytics/events` (204), ≤ 50 events. */
@Serializable
data class AnalyticsBatch(val events: List<AnalyticsEvent>)

@Serializable
data class AnalyticsEvent(val feature: String, val page: String, val metadata: JsonObject? = null, val ts: Long? = null)
