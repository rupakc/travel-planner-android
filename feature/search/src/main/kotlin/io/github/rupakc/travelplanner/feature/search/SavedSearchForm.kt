package io.github.rupakc.travelplanner.feature.search

import io.github.rupakc.travelplanner.core.domain.search.ExtraStop
import io.github.rupakc.travelplanner.core.domain.search.SearchForm
import io.github.rupakc.travelplanner.core.domain.search.TravellerCounts
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.search.ImportSource
import io.github.rupakc.travelplanner.core.model.search.MustIncludePlace
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import kotlinx.serialization.Serializable

/**
 * The known-destination form as saved in `SavedStateHandle`, so it survives process death
 * (the domain [SearchForm] stays serialization-free).
 */
@Serializable
internal data class SavedSearchForm(
    val origin: String = "",
    val destination: String = "",
    val departureDate: String = "",
    val returnDate: String = "",
    val nationality: String = "",
    val residencePermits: List<String> = emptyList(),
    val existingVisas: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val budgetUsd: String = "",
    val adults: Int = 1,
    val children: Int = 0,
    val seniors: Int = 0,
    val infants: Int = 0,
    val accessibilityNeeds: List<String> = emptyList(),
    val serendipity: Double = 0.5,
    val pace: String = TravelSearchRequest.PACE_BALANCED,
    val mustInclude: List<MustIncludePlace>? = null,
    val importSource: ImportSource? = null,
    val stopCities: List<String> = emptyList(),
    val stopNights: List<String> = emptyList(),
    val modeName: String = SearchMode.KNOWN.name,
) {
    val form: SearchForm
        get() =
            SearchForm(
                origin = origin,
                destination = destination,
                departureDate = departureDate,
                returnDate = returnDate,
                nationality = nationality,
                residencePermits = residencePermits,
                existingVisas = existingVisas,
                interests = interests,
                budgetUsd = budgetUsd,
                travellers = TravellerCounts.of(adults, children, seniors, infants) ?: TravellerCounts(),
                accessibilityNeeds = accessibilityNeeds,
                serendipity = serendipity,
                pace = pace,
                mustInclude = mustInclude,
                importSource = importSource,
            )
    val stops: List<ExtraStop> get() = stopCities.mapIndexed { i, city -> ExtraStop(city, stopNights.getOrElse(i) { "" }) }
    val mode: SearchMode get() = SearchMode.entries.firstOrNull { it.name == modeName } ?: SearchMode.KNOWN

    fun encode(): String = TravelJson.encodeToString(serializer(), this)

    companion object {
        operator fun invoke(form: SearchForm, stops: List<ExtraStop>, mode: SearchMode): SavedSearchForm =
            SavedSearchForm(
                origin = form.origin,
                destination = form.destination,
                departureDate = form.departureDate,
                returnDate = form.returnDate,
                nationality = form.nationality,
                residencePermits = form.residencePermits,
                existingVisas = form.existingVisas,
                interests = form.interests,
                budgetUsd = form.budgetUsd,
                adults = form.travellers.adults,
                children = form.travellers.children,
                seniors = form.travellers.seniors,
                infants = form.travellers.infants,
                accessibilityNeeds = form.accessibilityNeeds,
                serendipity = form.serendipity,
                pace = form.pace,
                mustInclude = form.mustInclude,
                importSource = form.importSource,
                stopCities = stops.map { it.city },
                stopNights = stops.map { it.nights },
                modeName = mode.name,
            )

        fun decode(json: String): SavedSearchForm? = runCatching { TravelJson.decodeFromString(serializer(), json) }.getOrNull()
    }
}

/** Onboarding question id for trip pace (A1's `TravellerQuiz.PACE`); its option ids are the `pace` values. */
private const val QUIZ_PACE = "pace"
private val PACES = setOf(TravelSearchRequest.PACE_RELAXED, TravelSearchRequest.PACE_BALANCED, TravelSearchRequest.PACE_PACKED)

/**
 * The stored onboarding pace answer as a search `pace`, or null when skipped (same rule as A1's
 * `quizPace(answers)` in core:domain profile; switch to it once both are on main).
 */
internal fun quizPaceAnswer(answers: Map<String, List<String>>): String? = answers[QUIZ_PACE]?.firstOrNull()?.takeIf { it in PACES }
