package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.PlacesData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Test helpers: build typed sections from the web tests' JSON literals. */
internal fun obj(json: String): JsonObject = TravelJson.parseToJsonElement(json).jsonObject

internal fun flightsOf(json: String) = SectionDecoder.decode(SectionId.FLIGHTS, obj(json)) as FlightsData

internal fun hotelsOf(json: String) = SectionDecoder.decode(SectionId.HOTELS, obj(json)) as HotelsData

internal fun activitiesOf(json: String) = SectionDecoder.decode(SectionId.ACTIVITIES, obj(json)) as ActivitiesData

internal fun placesOf(json: String) = SectionDecoder.decode(SectionId.PLACES_TO_SEE, obj(json)) as PlacesData

internal fun toursOf(json: String) = SectionDecoder.decode(SectionId.WALKING_TOURS, obj(json)) as WalkingToursData

internal fun simOf(json: String) = SectionDecoder.decode(SectionId.SIM, obj(json)) as SimData

internal fun selectionsOf(json: String) = PlanSelectionsSerializer.fromJson(obj(json))

internal fun search(
    departure: String = "2026-04-01",
    returnDate: String? = "2026-04-08",
    budget: Double? = 3000.0,
    destinations: List<String>? = null,
) = TravelSearchRequest(
    origin = "NYC",
    destination = destinations?.last() ?: "Paris",
    departureDate = departure,
    returnDate = returnDate,
    nationality = "American",
    budgetUsd = budget,
    destinations = destinations,
)
