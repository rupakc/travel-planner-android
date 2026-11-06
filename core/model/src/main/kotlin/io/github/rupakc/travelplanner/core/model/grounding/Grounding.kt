package io.github.rupakc.travelplanner.core.model.grounding

import io.github.rupakc.travelplanner.core.model.json.LenientString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

/**
 * One grounding source (`{title, uri}`) of a live answer: a Google Search result for
 * `/api/live-check`, a Google Maps place for `/api/nearby` v2. Shown as-is under the content
 * it supports; never stored.
 */
@Serializable
data class GroundingSource(val title: LenientString = null, val uri: LenientString = null)

/**
 * A list whose malformed elements are skipped instead of failing the whole response; anything
 * that isn't an array (null, an object, a string) decodes to an empty list. For LLM-backed payloads.
 */
abstract class TolerantListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(element)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        val array = decoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { runCatching { decoder.json.decodeFromJsonElement(element, it) }.getOrNull() }
    }

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)
}

/** Tolerant `sources[]`. */
object GroundingSourceListSerializer : TolerantListSerializer<GroundingSource>(GroundingSource.serializer())
