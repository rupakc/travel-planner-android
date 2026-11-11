package io.github.rupakc.travelplanner.core.data.telemetry

import io.github.rupakc.travelplanner.core.domain.telemetry.QueuedEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Prop values (String, Long, Double, Boolean) as a JSON object. */
internal fun Map<String, Any>.toJsonProps(): JsonObject =
    JsonObject(
        mapNotNull { (key, value) ->
            when (value) {
                is String -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                is Long -> JsonPrimitive(value)
                is Float -> JsonPrimitive(value)
                is Double -> JsonPrimitive(value)
                else -> null
            }?.let { key to it }
        }.toMap(),
    )

/** Reads a stored props object back; values of any other shape are dropped. */
internal fun JsonObject.toProps(): Map<String, Any> {
    val out = LinkedHashMap<String, Any>()
    for ((key, element) in this) {
        val p = element as? JsonPrimitive ?: continue
        val value: Any? =
            when {
                p.isString -> p.contentOrNull
                else -> p.booleanOrNull ?: p.longOrNull ?: p.doubleOrNull
            }
        if (value != null) out[key] = value
    }
    return out
}

/** The on-disk form of an event: `{event, ts, screen, sid, props}`. */
internal fun QueuedEvent.toStoredJson(): JsonObject =
    JsonObject(
        mapOf(
            "event" to JsonPrimitive(event),
            "ts" to JsonPrimitive(ts),
            "screen" to JsonPrimitive(screen),
            "sid" to JsonPrimitive(sessionId),
            "props" to props.toJsonProps(),
        ),
    )

/** Parses one stored event; null when a required field is missing or mistyped. */
internal fun JsonObject.toQueuedEvent(): QueuedEvent? {
    fun str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val strings = listOf("event", "screen", "sid").map(::str)
    val ts = (this["ts"] as? JsonPrimitive)?.longOrNull
    val props = (this["props"] as? JsonObject)?.toProps() ?: emptyMap()
    val (event, screen, sid) = strings
    return if (strings.any { it == null } || ts == null) {
        null
    } else {
        QueuedEvent(event = event!!, ts = ts, props = props, screen = screen!!, sessionId = sid!!)
    }
}
