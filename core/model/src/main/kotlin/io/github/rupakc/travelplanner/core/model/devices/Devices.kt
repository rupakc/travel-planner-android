package io.github.rupakc.travelplanner.core.model.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /api/devices {token, platform:"android", locale?}` → [Device] (spec §7). */
@Serializable
data class RegisterDeviceRequest(val token: String, val platform: String = PLATFORM_ANDROID, val locale: String? = null) {
    companion object {
        const val PLATFORM_ANDROID = "android"
    }
}

/** `POST /api/devices/unregister {token}` → 204, idempotent (use on sign-out). */
@Serializable
data class UnregisterDeviceRequest(val token: String)

/** A registered device. [token] is masked by the server ("…abc123"). */
@Serializable
data class Device(
    val id: Long? = null,
    val token: String? = null,
    val platform: String? = null,
    val locale: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

/** `GET /api/devices`. */
@Serializable
data class DeviceList(val items: List<Device> = emptyList())

/** FCM data payload keys (all values are strings). */
object PushDataKeys {
    const val KIND = "kind"
    const val NOTIFICATION_ID = "notification_id"

    /** Web path, e.g. `/alerts?kind=hotel&watch=3` or `/alerts?watch=3`. */
    const val LINK = "link"
    const val WATCH_ID = "watch_id"
    const val WATCH_KIND = "watch_kind"
}
