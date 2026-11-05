package io.github.rupakc.travelplanner.core.model.section

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Decodes a section `data` object into its typed [SectionData]; never throws. */
object SectionDecoder {
    fun decode(id: SectionId, data: JsonObject, json: Json = TravelJson): SectionData {
        val serializer: KSerializer<out SectionData> = when (id) {
            SectionId.FLIGHTS -> FlightsData.serializer()
            SectionId.WEATHER -> WeatherData.serializer()
            SectionId.HOTELS -> HotelsData.serializer()
            SectionId.ACTIVITIES -> ActivitiesData.serializer()
            SectionId.PLACES_TO_SEE -> PlacesData.serializer()
            SectionId.WALKING_TOURS -> WalkingToursData.serializer()
            SectionId.EVENTS -> EventsData.serializer()
            SectionId.VISA -> VisaData.serializer()
            SectionId.SIM -> SimData.serializer()
            SectionId.TIPS -> TipsData.serializer()
            SectionId.EMERGENCY_CARD -> EmergencyCardData.serializer()
            SectionId.GETTING_AROUND -> GettingAroundData.serializer()
            SectionId.FOREX -> ForexData.serializer()
            SectionId.ITINERARY -> ItineraryData.serializer()
            SectionId.STRESS_TEST -> StressTestData.serializer()
            SectionId.PACKING_LIST -> PackingListData.serializer()
        }
        return runCatching { json.decodeFromJsonElement(serializer, data) }
            .getOrElse { UndecodedSection(raw = data, error = (data["error"] as? JsonPrimitive)?.contentOrNull) }
    }
}
