package io.github.rupakc.travelplanner.feature.results.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.navigation.CompareRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResultsRoute
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.StreamingStatusAnnouncer
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.ResultsViewModel
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSection
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSectionCard
import io.github.rupakc.travelplanner.feature.results.sections.SectionActions

/** Streamed results + compare table. */
fun NavGraphBuilder.resultsGraph(onBack: () -> Unit) {
    composable<ResultsRoute> {
        val viewModel: ResultsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val open = rememberLinkOpener()
        val actions = remember(viewModel) {
            object : SectionActions {
                override fun onToggle(item: PlanItem) = viewModel.toggle(item)

                override fun isSelected(item: PlanItem) = viewModel.isSelected(item)

                override fun onOpenLink(url: String) = open(url)

                override fun onWatchFare(flight: Flight) = Unit

                override fun onWatchHotel(hotel: Hotel) = Unit

                override fun onWatchActivity(activity: Activity) = Unit

                override fun onRetry(section: ResultsSection) = Unit
            }
        }
        ResultsScreen(state = state, actions = actions, onBack = onBack)
    }
    composable<CompareRoute> {
        FeaturePlaceholder(
            title = stringResource(R.string.results_compare_title),
            message = stringResource(R.string.results_compare_message),
            onBack = onBack,
        )
    }
}

/** Stateless results screen: one SectionScaffold per section in web order. */
@Composable
internal fun ResultsScreen(state: SearchResultsState, actions: SectionActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val collapsed = remember { mutableStateMapOf<ResultsSection, Boolean>() }
    Scaffold(modifier = modifier, topBar = { TpTopBar(stringResource(R.string.results_title), onBack = onBack) }) { padding ->
        StreamingStatusAnnouncer(state.statuses)
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            items(ResultsSection.entries, key = { it.name }, contentType = { "section" }) { section ->
                ResultsSectionCard(
                    section = section,
                    status = state.statuses[section.id] ?: SectionStatus.WAITING,
                    data = state.sections[section.id]?.data,
                    expanded = collapsed[section] != true,
                    onToggleExpanded = { collapsed[section] = collapsed[section] != true },
                    actions = actions,
                )
            }
        }
    }
}
