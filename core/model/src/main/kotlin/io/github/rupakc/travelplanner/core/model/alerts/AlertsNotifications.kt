package io.github.rupakc.travelplanner.core.model.alerts

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** The v2 notification kind carrying a weekly digest. */
const val NOTIFICATION_KIND_PRICE_DIGEST = "price_digest"

private fun <T> AppNotification.decodeData(key: String, serializer: KSerializer<T>): T? {
    val element = data[key]?.takeIf { it !is JsonNull } ?: return null
    return runCatching { TravelJson.decodeFromJsonElement(serializer, element) }.getOrNull()
}

/** `data.drop` of a drop notification, or null (old rows, other kinds, malformed). */
fun AppNotification.dropData(): DropData? = decodeData("drop", DropData.serializer())

/** `data.game` (a nullable `GameDelta`; null when the game is off or the key is absent). */
fun AppNotification.gameDelta(): GameDelta? = decodeData("game", GameDelta.serializer())

/** `data.digest` of a `price_digest` notification, or null. */
fun AppNotification.digestData(): DigestData? = decodeData("digest", DigestData.serializer())

/** `data.group_count` of a grouped push (sent as a string), or null. */
val AppNotification.groupCount: Int? get() = (data["group_count"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

/** True for a `price_digest` row. */
val AppNotification.isPriceDigest: Boolean get() = kind == NOTIFICATION_KIND_PRICE_DIGEST

/** The v2 machine code of this failure ([AlertsErrorCode.UNKNOWN] when it is none of ours). */
fun AppError.alertsErrorCode(): AlertsErrorCode =
    AlertsErrorCode.from(
        when (this) {
            is AppError.NotFound -> code
            is AppError.Client -> code
            is AppError.Server -> code
            is AppError.Forbidden -> code
            is AppError.RateLimited -> code
            else -> null
        },
    )
