package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.SectionResult
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import io.github.rupakc.travelplanner.core.testing.Fixtures
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSection
import io.github.rupakc.travelplanner.feature.results.sections.SectionActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The last `/api/search` event of [id] in the recorded contract fixture (`search_full.sse`). */
internal inline fun <reified T : SectionData> fixture(id: SectionId): T =
    Fixtures.searchEvents(Fixtures.SEARCH_FULL).filterIsInstance<SearchEvent.Section>().last { it.id == id }.data as T

internal fun fixtureEvent(id: SectionId): SearchEvent.Section =
    Fixtures.searchEvents(Fixtures.SEARCH_FULL).filterIsInstance<SearchEvent.Section>().last { it.id == id }

internal val TestRequest =
    TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-04-01", returnDate = "2026-04-08", nationality = "American")

/**
 * SectionActions over an in-memory draft. Selections live in Compose state (so `isSelected` reads
 * recompose like the real screen) and are mirrored to [selections].
 */
internal class FakeSectionActions(
    initial: PlanSelections = PlanSelections.EMPTY,
    sections: Map<SectionId, SectionResult> = emptyMap(),
    override val itineraryEditingLocked: Boolean = false,
    var recheckResult: AppResult<StressTestData> = AppResult.Failure(AppError.Network()),
) : SectionActions {
    private var current by mutableStateOf(initial)
    private val flow = MutableStateFlow(initial)
    val opened = mutableListOf<String>()
    val payloads = mutableListOf<StressTestPayload>()

    val value: PlanSelections get() = current

    override val selections: StateFlow<PlanSelections> get() = flow
    override val results: StateFlow<SearchResultsState> = MutableStateFlow(SearchResultsState(request = TestRequest, sections = sections))

    override fun onUpdateSelections(transform: (PlanSelections) -> PlanSelections) {
        current = transform(current)
        flow.value = current
    }

    override fun onToggle(item: PlanItem) = onUpdateSelections { PlanSelectionRules.toggle(it, item) }

    override fun isSelected(item: PlanItem): Boolean = PlanSelectionRules.isSelected(current, item)

    override fun onOpenLink(url: String) {
        opened += url
    }

    override fun onWatchFare(flight: Flight) = Unit

    override fun onWatchHotel(hotel: Hotel) = Unit

    override fun onWatchActivity(activity: Activity) = Unit

    override fun onRetry(section: ResultsSection) = Unit

    override suspend fun recheckStressTest(payload: StressTestPayload): AppResult<StressTestData> {
        payloads += payload
        return recheckResult
    }
}

internal fun sectionResult(id: SectionId, data: SectionData) = SectionResult(id, data, kotlinx.serialization.json.JsonObject(emptyMap()), null)
