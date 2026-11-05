package io.github.rupakc.travelplanner.core.model.stream

import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.TripMapCity
import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.json.lenientDouble
import io.github.rupakc.travelplanner.core.model.section.ConfidenceData
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.PricingAdvisorData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Decodes one SSE `data:` payload (the JSON after `data: `) into a typed event.
 * Pure and total: malformed JSON → `null` (skip the line, as the web does),
 * unknown `type` → `Unknown`.
 */
object EventDecoder {
    fun parseObject(payload: String, json: Json = TravelJson): JsonObject? =
        runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject

    fun decodeSearch(payload: String, json: Json = TravelJson): SearchEvent? = parseObject(payload, json)?.let { decodeSearch(it, json) }

    fun decodeSearch(obj: JsonObject, json: Json = TravelJson): SearchEvent {
        val type = obj.string("type")
        val source = DataSource.fromWire(obj.string("source"))
        val data = obj["data"] as? JsonObject ?: EmptyJsonObject
        SectionId.fromWire(type)?.let { id ->
            return SearchEvent.Section(id, source, SectionDecoder.decode(id, data, json), data)
        }
        return when (type) {
            "confidence" -> {
                SearchEvent.Confidence(
                    runCatching { json.decodeFromJsonElement(ConfidenceData.serializer(), data) }.getOrElse { ConfidenceData() },
                    source,
                )
            }

            "pricing_advisor" -> {
                SearchEvent.PricingAdvisor(
                    runCatching { json.decodeFromJsonElement(PricingAdvisorData.serializer(), data) }.getOrElse { PricingAdvisorData() },
                    source,
                )
            }

            "agent_status" -> {
                SearchEvent.AgentStatus(obj.string("agent").orEmpty(), obj.string("status").orEmpty())
            }

            "done" -> {
                SearchEvent.Done
            }

            else -> {
                SearchEvent.Unknown(type, obj)
            }
        }
    }

    fun decodeChat(payload: String, json: Json = TravelJson): ChatEvent? = parseObject(payload, json)?.let { decodeChat(it, json) }

    @Suppress("CyclomaticComplexMethod")
    fun decodeChat(obj: JsonObject, json: Json = TravelJson): ChatEvent {
        val type = obj.string("type")
        return when (type) {
            "delta" -> {
                ChatEvent.Delta(obj.string("text").orEmpty())
            }

            "suggestions" -> {
                ChatEvent.Suggestions(
                    (obj["chips"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty(),
                )
            }

            "session_context_update" -> {
                ChatEvent.SessionContextUpdate(obj["context"] as? JsonObject ?: EmptyJsonObject)
            }

            "plan_action" -> {
                ChatEvent.PlanAction(
                    action = PlanActionType.fromWire(obj.string("action")),
                    field = obj.string("field").orEmpty(),
                    data = obj["data"] as? JsonObject,
                )
            }

            "planning_start" -> {
                ChatEvent.PlanningStart(obj.string("destination"))
            }

            "trip_map" -> {
                ChatEvent.TripMap(
                    origin = obj.string("origin"),
                    originLat = lenientDouble(obj["origin_lat"]),
                    originLng = lenientDouble(obj["origin_lng"]),
                    cities =
                        (obj["cities"] as? JsonArray)?.mapNotNull { el ->
                            runCatching { json.decodeFromJsonElement(TripMapCity.serializer(), el) }.getOrNull()
                        }.orEmpty(),
                    departureDate = obj.string("departure_date"),
                    returnDate = obj.string("return_date"),
                    numTravelers = lenientDouble(obj["num_travelers"])?.toInt(),
                )
            }

            "itinerary_generating" -> {
                ChatEvent.ItineraryGenerating
            }

            "comprehensive_itinerary" -> {
                decodeItinerary(obj, json)
            }

            "agent_status" -> {
                ChatEvent.AgentStatus(obj.string("agent").orEmpty(), obj.string("status").orEmpty())
            }

            "section_result" -> {
                val name = obj.string("section").orEmpty()
                val id = SectionId.fromWire(name)
                val data = obj["data"] as? JsonObject ?: EmptyJsonObject
                ChatEvent.SectionResult(name, id, DataSource.fromWire(obj.string("source")), id?.let { SectionDecoder.decode(it, data, json) }, data)
            }

            "planning_done" -> {
                ChatEvent.PlanningDone
            }

            "import_link" -> {
                obj.string("url")?.let { ChatEvent.ImportLink(it, obj.string("href")) } ?: ChatEvent.Unknown(type, obj)
            }

            "error" -> {
                ChatEvent.Error(obj.string("text"))
            }

            "message", "result" -> {
                ChatEvent.Message(obj.string("text").orEmpty())
            }

            "done" -> {
                ChatEvent.Done
            }

            else -> {
                ChatEvent.Unknown(type, obj)
            }
        }
    }

    private fun decodeItinerary(obj: JsonObject, json: Json): ChatEvent.ComprehensiveItinerary {
        val data = obj["data"] as? JsonObject ?: EmptyJsonObject
        val error = data.string("error")
        val itinerary = if (error != null) null else runCatching { json.decodeFromJsonElement(ChatItinerary.serializer(), data) }.getOrNull()
        return ChatEvent.ComprehensiveItinerary(itinerary, error ?: if (itinerary == null) "" else null, data)
    }

    private fun JsonObject.string(key: String): String? =
        when (val el: JsonElement? = this[key]) {
            null, JsonNull -> null
            is JsonPrimitive -> el.contentOrNull
            else -> null
        }
}
