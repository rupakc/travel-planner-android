package io.github.rupakc.travelplanner.feature.results.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.rememberReduceMotion
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.EmergencyCardData
import io.github.rupakc.travelplanner.core.model.section.EventsData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.ForexData
import io.github.rupakc.travelplanner.core.model.section.GettingAroundData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.PackingListData
import io.github.rupakc.travelplanner.core.model.section.PlacesData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.model.section.TipsData
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.core.ui.SectionScaffold
import io.github.rupakc.travelplanner.core.ui.VerificationChip
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.SectionUi
import io.github.rupakc.travelplanner.feature.results.sections.commerce.Pill
import io.github.rupakc.travelplanner.feature.results.sections.commerce.PillTone
import io.github.rupakc.travelplanner.feature.results.sections.info.rendersOwnError
import kotlinx.coroutines.delay

private const val LOADING_ROTATE_MS = 3_000L

/**
 * Maps each [ResultsSection] into [SectionScaffold]: verification chip, header
 * actions (filters, fare calendar, watch), rotating loading skeleton, error with
 * retry, the "No results" fallback, and the section body.
 */
@Suppress("CyclomaticComplexMethod") // one branch per section state, kept together on purpose
@Composable
internal fun ResultsSectionCard(
    section: SectionUi,
    actions: SectionActions,
    onToggleExpanded: () -> Unit,
    onShowVerification: () -> Unit,
    modifier: Modifier = Modifier,
    onRunSearch: () -> Unit = {},
    /** The cards of flights/hotels/activities are emitted as separate lazy items ([commerceRows]); render only their lead here. */
    cardsOutside: Boolean = false,
    headerActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val data = section.data
    SectionScaffold(
        section = section.id,
        status = section.status,
        expanded = section.expanded,
        onToggle = onToggleExpanded,
        modifier = modifier,
        badge = data?.verification?.let { v -> { VerificationChip(v, onClick = onShowVerification) } },
        headerActions = headerActions,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            section.filterError?.let { error ->
                Text(
                    error.message ?: stringResource(R.string.results_filter_error),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (section.activeFilterCount > 0) {
                Pill(pluralStringResource(R.plurals.results_filters_active, section.activeFilterCount, section.activeFilterCount), tone = PillTone.Info)
            }
            when {
                section.notLoaded -> {
                    Text(
                        stringResource(R.string.results_section_not_loaded),
                        style = TravelTheme.typography.bodyMedium,
                        color = TravelTheme.colors.onSurfaceVariant,
                    )
                    TpButton(
                        stringResource(R.string.results_run_search),
                        onClick = onRunSearch,
                        variant = TpButtonVariant.Tonal,
                        tid = "results.results_run_search",
                    )
                }

                data == null && section.status == SectionStatus.DONE -> {
                    Text(stringResource(R.string.results_no_results), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
                }

                data == null && section.status == SectionStatus.WAITING -> {
                    Text(
                        stringResource(R.string.results_section_waiting),
                        style = TravelTheme.typography.bodyMedium,
                        color = TravelTheme.colors.onSurfaceVariant,
                    )
                }

                data == null || section.filterLoading -> {
                    LoadingSkeleton(section.section)
                }

                // Walking tours, events, health check and forex show the web's own error copy (Team E).
                data.isError && !section.section.rendersOwnError -> {
                    ErrorState(message = stringResource(R.string.results_section_error), onRetry = {
                        actions.onRetry(section.section)
                    }, tid = "results.section_registry_retry")
                }

                cardsOutside && data is FlightsData -> {
                    FlightsLead(data, actions)
                }

                cardsOutside && data is HotelsData -> {
                    if (data.results.isEmpty()) HotelsEmpty()
                }

                cardsOutside && data is ActivitiesData -> {
                    if (data.results.isEmpty()) ActivitiesEmpty()
                }

                else -> {
                    SectionContent(section.section, data, actions)
                }
            }
        }
    }
}

/** Web skeleton: rotating message every 3 s (static under reduce-motion) + shimmer bars. */
@Composable
private fun LoadingSkeleton(section: ResultsSection) {
    val messages = loadingMessages(section)
    val reduceMotion = rememberReduceMotion()
    var index by remember(section) { mutableIntStateOf(0) }
    if (!reduceMotion && messages.size > 1) {
        LaunchedEffect(section) {
            while (true) {
                delay(LOADING_ROTATE_MS)
                index = (index + 1) % messages.size
            }
        }
    }
    SkeletonList(message = stringResource(messages[index % messages.size]))
}

@StringRes
private fun loadingMessages(section: ResultsSection): List<Int> =
    when (section) {
        ResultsSection.Flights -> listOf(R.string.results_loading_flights_1, R.string.results_loading_flights_2, R.string.results_loading_flights_3)
        ResultsSection.Weather -> listOf(R.string.results_loading_weather)
        ResultsSection.Hotels -> listOf(R.string.results_loading_hotels_1, R.string.results_loading_hotels_2)
        ResultsSection.Activities -> listOf(R.string.results_loading_activities)
        ResultsSection.PlacesToSee -> listOf(R.string.results_loading_places)
        ResultsSection.WalkingTours -> listOf(R.string.results_loading_walking_tours)
        ResultsSection.Events -> listOf(R.string.results_loading_events)
        ResultsSection.Visa -> listOf(R.string.results_loading_visa)
        ResultsSection.Sim -> listOf(R.string.results_loading_sim)
        ResultsSection.Tips -> listOf(R.string.results_loading_tips)
        ResultsSection.EmergencyCard -> listOf(R.string.results_loading_emergency)
        ResultsSection.GettingAround -> listOf(R.string.results_loading_getting_around)
        ResultsSection.Forex -> listOf(R.string.results_loading_forex)
        ResultsSection.Itinerary -> listOf(R.string.results_loading_itinerary)
        ResultsSection.StressTest -> listOf(R.string.results_loading_stress_test)
        ResultsSection.PackingList -> listOf(R.string.results_loading_packing)
    }

@Suppress("CyclomaticComplexMethod")
@Composable
internal fun SectionContent(section: ResultsSection, data: SectionData, actions: SectionActions, modifier: Modifier = Modifier) {
    when (section) {
        ResultsSection.Flights -> (data as? FlightsData)?.let { FlightsSectionContent(it, actions, modifier) }
        ResultsSection.Weather -> (data as? WeatherData)?.let { WeatherSectionContent(it, actions, modifier) }
        ResultsSection.Hotels -> (data as? HotelsData)?.let { HotelsSectionContent(it, actions, modifier) }
        ResultsSection.Activities -> (data as? ActivitiesData)?.let { ActivitiesSectionContent(it, actions, modifier) }
        ResultsSection.PlacesToSee -> (data as? PlacesData)?.let { PlacesToSeeSectionContent(it, actions, modifier) }
        ResultsSection.WalkingTours -> (data as? WalkingToursData)?.let { WalkingToursSectionContent(it, actions, modifier) }
        ResultsSection.Events -> (data as? EventsData)?.let { EventsSectionContent(it, actions, modifier) }
        ResultsSection.Visa -> (data as? VisaData)?.let { VisaSectionContent(it, actions, modifier) }
        ResultsSection.Sim -> (data as? SimData)?.let { SimSectionContent(it, actions, modifier) }
        ResultsSection.Tips -> (data as? TipsData)?.let { TipsSectionContent(it, actions, modifier) }
        ResultsSection.EmergencyCard -> (data as? EmergencyCardData)?.let { EmergencyCardSectionContent(it, actions, modifier) }
        ResultsSection.GettingAround -> (data as? GettingAroundData)?.let { GettingAroundSectionContent(it, actions, modifier) }
        ResultsSection.Forex -> (data as? ForexData)?.let { ForexSectionContent(it, actions, modifier) }
        ResultsSection.Itinerary -> (data as? ItineraryData)?.let { ItinerarySectionContent(it, actions, modifier) }
        ResultsSection.StressTest -> (data as? StressTestData)?.let { StressTestSectionContent(it, actions, modifier) }
        ResultsSection.PackingList -> (data as? PackingListData)?.let { PackingListSectionContent(it, actions, modifier) }
    }
}
