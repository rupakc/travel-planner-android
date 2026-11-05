package io.github.rupakc.travelplanner.core.model.json

import kotlin.math.roundToLong
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Numbers the LLM-backed API may send as numbers, numeric strings ("1,200",
 * "$45"), words ("varies") or nothing. Unparseable values decode to null —
 * never to an invented number.
 */
typealias LenientDouble =
    @Serializable(with = LenientDoubleSerializer::class)
    Double?

/** Integer version of [LenientDouble] (rounds 2.0 → 2; "3 days" → 3). */
typealias LenientInt =
    @Serializable(with = LenientIntSerializer::class)
    Int?

/** Booleans sent as true/false, 0/1 (SQLite) or "true"/"yes". */
typealias LenientBoolean =
    @Serializable(with = LenientBooleanSerializer::class)
    Boolean?

/** Strings that may arrive as numbers or booleans (e.g. a rating "4.5" or 4.5). Objects/arrays → null. */
typealias LenientString =
    @Serializable(with = LenientStringSerializer::class)
    String?

private val NUMBER = Regex("""-?\d+(?:\.\d+)?""")

/** Parses the first number in [element]; see [LenientDouble]. */
fun lenientDouble(element: JsonElement?): Double? {
    val primitive = element as? JsonPrimitive ?: return null
    if (!primitive.isString) return primitive.doubleOrNull?.takeIf { it.isFinite() }
    val cleaned = primitive.content.replace(",", "").trim()
    if (cleaned.isEmpty()) return null
    cleaned.toDoubleOrNull()?.let { return it.takeIf(Double::isFinite) }
    return NUMBER.find(cleaned)?.value?.toDoubleOrNull()
}

fun lenientBoolean(element: JsonElement?): Boolean? {
    val primitive = element as? JsonPrimitive ?: return null
    primitive.booleanOrNull?.let { return it }
    return when (primitive.content.trim().lowercase()) {
        "1", "true", "yes", "y" -> true
        "0", "false", "no", "n" -> false
        else -> null
    }
}

fun lenientString(element: JsonElement?): String? {
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive is kotlinx.serialization.json.JsonNull) return null
    return primitive.content
}

private fun Decoder.element(): JsonElement? = (this as? JsonDecoder)?.decodeJsonElement()

object LenientDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("travelplanner.LenientDouble", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? =
        if (decoder is JsonDecoder) {
            lenientDouble(
                decoder.element(),
            )
        } else {
            decoder.decodeDouble()
        }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

object LenientIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("travelplanner.LenientInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? =
        if (decoder is JsonDecoder) {
            lenientDouble(decoder.element())?.roundToLong()?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt()
        } else {
            decoder.decodeInt()
        }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

object LenientBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("travelplanner.LenientBoolean", PrimitiveKind.BOOLEAN).nullable

    override fun deserialize(decoder: Decoder): Boolean? =
        if (decoder is JsonDecoder) lenientBoolean(decoder.element()) else decoder.decodeBoolean()

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
}

object LenientStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("travelplanner.LenientString", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? =
        if (decoder is JsonDecoder) lenientString(decoder.element()) else decoder.decodeString()

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** Lists that may arrive as a list of strings, a single string, or nothing. */
typealias LenientStringList =
    @Serializable(with = LenientStringListSerializer::class)
    List<String>

object LenientStringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        return when (val element = decoder.decodeJsonElement()) {
            is kotlinx.serialization.json.JsonArray -> element.mapNotNull { lenientString(it)?.takeIf(String::isNotBlank) }
            is JsonPrimitive -> listOfNotNull(lenientString(element)?.takeIf(String::isNotBlank))
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) = delegate.serialize(encoder, value)
}
