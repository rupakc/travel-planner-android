package io.github.rupakc.travelplanner.core.model.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject

/**
 * A result item that remembers the exact JSON object it was decoded from.
 *
 * Why: My Plan stores "the full section result object the user picked"
 * (api-contract §5). Re-encoding a typed model would drop keys the app does not
 * model, and the web would then see a poorer object. Raw-backed items encode
 * back to [raw] when present, so plans round-trip losslessly between web and app.
 */
interface RawBacked<T> {
    val raw: JsonObject

    fun withRaw(raw: JsonObject): T
}

/** The JSON for an item: its original object when known, else the typed encoding. */
fun <T : RawBacked<T>> T.toJson(serializer: KSerializer<T>, json: Json = TravelJson): JsonObject =
    raw.takeIf { it.isNotEmpty() } ?: (json.encodeToJsonElement(serializer, this) as JsonObject)

/** Decodes one raw-backed item, or null when [element] is not a decodable object. */
fun <T : RawBacked<T>> decodeRawBacked(serializer: KSerializer<T>, element: JsonElement?, json: Json = TravelJson): T? {
    val obj = element as? JsonObject ?: return null
    return runCatching { json.decodeFromJsonElement(serializer, obj).withRaw(obj) }.getOrNull()
}

/** Decodes each object of a JSON array, skipping malformed items (tolerant). */
fun <T : RawBacked<T>> decodeRawBackedList(serializer: KSerializer<T>, element: JsonElement?, json: Json = TravelJson): List<T> =
    (element as? JsonArray)?.mapNotNull { decodeRawBacked(serializer, it, json) }.orEmpty()

/** Base for the per-type list serializers used with `@Serializable(with = …)`. */
abstract class RawBackedListSerializer<T : RawBacked<T>>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    override val descriptor: SerialDescriptor = ListSerializer(element).descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder ?: return ListSerializer(element).deserialize(decoder)
        return decodeRawBackedList(element, jsonDecoder.decodeJsonElement(), jsonDecoder.json)
    }

    override fun serialize(encoder: Encoder, value: List<T>) {
        val jsonEncoder = encoder as? JsonEncoder ?: return ListSerializer(element).serialize(encoder, value)
        jsonEncoder.encodeJsonElement(JsonArray(value.map { it.toJson(element, jsonEncoder.json) }))
    }
}
