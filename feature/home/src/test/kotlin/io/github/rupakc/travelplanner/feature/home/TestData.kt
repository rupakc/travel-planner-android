package io.github.rupakc.travelplanner.feature.home

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.preferences.RecentStop
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import kotlinx.serialization.json.jsonObject

internal fun plan(id: Long, name: String, destination: String, departure: String, returnDate: String? = null): Plan {
    val request = TravelSearchRequest(origin = "NYC", destination = destination, departureDate = departure, returnDate = returnDate, nationality = "American")
    return Plan(id = id, name = name, searchData = TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), request).jsonObject)
}

internal fun recent(
    id: String,
    destination: String,
    budget: Double? = null,
    children: Int = 0,
    access: List<String> = emptyList(),
    stops: List<String> = emptyList(),
): RecentSearch =
    RecentSearch(
        id = id,
        search =
            RecentSearchIn(
                origin = "New York",
                destination = destination,
                departureDate = "2027-04-01",
                returnDate = "2027-04-08",
                budgetUsd = budget,
                children = children,
                accessibilityNeeds = access,
                stops = stops.map { RecentStop(it) },
            ),
    )

/** Catalogue fake: every type turns on [defaults]. */
internal class FakeFeatureCatalogue(private val defaults: Set<io.github.rupakc.travelplanner.core.domain.feature.FeatureId>) :
    io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue {
    override val all = emptyList<io.github.rupakc.travelplanner.core.domain.feature.FeatureDescriptor>()

    override fun defaultsFor(types: Collection<io.github.rupakc.travelplanner.core.domain.traveller.TravellerType>) = defaults
}

internal class FixedClock(private val date: java.time.LocalDate) : io.github.rupakc.travelplanner.core.common.AppClock {
    override fun now(): java.time.Instant = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()

    override fun zone(): java.time.ZoneId = java.time.ZoneOffset.UTC
}
