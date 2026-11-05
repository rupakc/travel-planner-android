package io.github.rupakc.travelplanner.core.model.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The one Json configuration for the whole app (network, cache, fixtures).
 * Tolerant by design (spec §4 Networking): unknown keys ignored, invalid enum
 * values / nulls for non-null fields coerced to defaults, lenient parsing,
 * nulls omitted on encode, defaults encoded.
 */
val TravelJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }

/** A shared empty object (default for raw-backed models). */
val EmptyJsonObject: JsonObject = JsonObject(emptyMap())
