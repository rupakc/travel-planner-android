package io.github.rupakc.travelplanner.core.model.alerts

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** One in-app notification (api-contract §2.9). */
@Serializable
data class AppNotification(
    val id: Long,
    /** fare_target, fare_drop, fare_low_level, fare_cheaper_date, <kind>_drop/_target, watch_expired, watch_error, … */
    val kind: String? = null,
    val title: String? = null,
    val body: String? = null,
    /** Includes at least `watch_id`, `watch_kind` for price watches; rest UNVERIFIED. */
    val data: JsonObject = EmptyJsonObject,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("emailed_at") val emailedAt: String? = null,
) {
    val isUnread: Boolean get() = readAt == null
    val watchId: Long? get() = (data["watch_id"] as? JsonPrimitive)?.longOrNull
    val watchKind: String? get() = (data["watch_kind"] as? JsonPrimitive)?.contentOrNull
}

/** `GET /api/notifications` page (`{items, total, unread}`). */
@Serializable
data class NotificationsPage(
    val items: List<AppNotification> = emptyList(),
    val total: LenientInt = null,
    val unread: Int = 0,
)

@Serializable
data class UnreadCount(val count: Int = 0)

/** `POST /api/notifications/read`: either [ids] or [all] = true. */
@Serializable
data class MarkReadRequest(val ids: List<Long>? = null, val all: Boolean? = null)

@Serializable
data class MarkReadResponse(val updated: Int = 0, val unread: Int = 0)
