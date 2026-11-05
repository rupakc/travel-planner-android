package io.github.rupakc.travelplanner.feature.results

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** Placeholder wiring: exposes the app-wide search and the plan draft. */
@HiltViewModel
class ResultsViewModel
    @Inject
    constructor(
        private val searchRepository: SearchRepository,
        private val currentPlan: CurrentPlanStore,
    ) : ViewModel() {
        val state: StateFlow<SearchResultsState> = searchRepository.current

        fun toggle(item: PlanItem) = currentPlan.toggle(item)

        fun isSelected(item: PlanItem): Boolean = currentPlan.isSelected(item)

        fun newSearch() = searchRepository.clear()
    }
