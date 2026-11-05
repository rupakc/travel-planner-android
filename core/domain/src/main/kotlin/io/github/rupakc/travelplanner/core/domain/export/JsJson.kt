package io.github.rupakc.travelplanner.core.domain.export

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/*
 * Small helpers that give raw JSON the JavaScript semantics the web's export code relies on
 * (truthiness, `Number(v)`, template-literal strings), so the ports read like the originals.
 */

/** JS truthiness of a JSON value (missing/null/""/0/NaN/false are falsy). */
internal fun JsonElement?.truthy(): Boolean =
    when (this) {
        null, JsonNull -> false
        is JsonPrimitive -> if (isString) content.isNotEmpty() else booleanOrNull ?: doubleOrNull?.let { it != 0.0 && !it.isNaN() } ?: false
        else -> true
    }

/** A string value, or null when absent / JSON null / not a string. */
internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** Truthy string, else null (`x || fallback` on strings). */
internal fun JsonElement?.text(): String? = if (truthy()) js() else null

/** A number literal only (JS `typeof n === 'number' && Number.isFinite(n)`). */
internal fun JsonElement?.strictNumber(): Double? =
    (this as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull?.takeIf { it.isFinite() }

/** JS `Number(v)` for comparisons such as `v > 0`: numbers and numeric strings; otherwise null. */
internal fun JsonElement?.looseNumber(): Double? =
    when {
        this !is JsonPrimitive || this is JsonNull -> null
        isString -> content.trim().toDoubleOrNull()
        else -> doubleOrNull
    }?.takeIf { it.isFinite() }

/** `Number.isInteger(n)` on a number literal. */
internal fun JsonElement?.strictInt(): Int? = strictNumber()?.takeIf { it % 1.0 == 0.0 }?.toInt()

/** The value as a JS template literal would print it (`${v}`): integral numbers without ".0". */
internal fun JsonElement?.js(): String =
    when (this) {
        null -> "undefined"
        JsonNull -> "null"
        is JsonPrimitive -> if (isString) content else doubleOrNull?.let(::jsNumber) ?: content
        else -> toString()
    }

internal fun jsNumber(value: Double): String =
    if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e21) value.toLong().toString() else BigDecimal(value.toString()).stripTrailingZeros().toPlainString()

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.array(): List<JsonElement> = (this as? JsonArray).orEmpty()

internal fun JsonElement?.objects(): List<JsonObject> = array().filterIsInstance<JsonObject>()

/** `a ?? b`: [this] unless absent or JSON null. */
internal fun JsonElement?.orIfNull(other: () -> JsonElement?): JsonElement? = if (this == null || this == JsonNull) other() else this

/** `Number(n).toLocaleString('en-US')`: grouping, at most 3 fraction digits. */
internal fun enUs(value: Double): String = DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.US)).format(value)
