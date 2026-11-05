package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
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

/**
 * Maps each [ResultsSection] to its content composable inside [SectionScaffold].
 * Loading → skeleton; error with no data → ErrorState with retry; otherwise the section body.
 */
@Composable
internal fun ResultsSectionCard(
    section: ResultsSection,
    status: SectionStatus,
    data: SectionData?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    actions: SectionActions,
    modifier: Modifier = Modifier,
) {
    SectionScaffold(
        section = section.id,
        status = status,
        expanded = expanded,
        onToggle = onToggleExpanded,
        modifier = modifier,
        badge = data?.verification?.let { v -> { VerificationChip(v) } },
    ) {
        when {
            data == null -> SkeletonList()
            data.isError -> ErrorState(message = stringResource(R.string.results_section_error), onRetry = { actions.onRetry(section) })
            else -> SectionContent(section, data, actions)
        }
    }
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
