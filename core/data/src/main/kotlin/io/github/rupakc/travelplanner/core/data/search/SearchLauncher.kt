package io.github.rupakc.travelplanner.core.data.search

import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.search.searchKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one way to start a full search (web `runSearch`, ResultsPage.jsx:3632): a new search starts a new,
 * unsaved plan for that search, so Results shows the new stream instead of the previous draft's snapshot.
 * Used by Search submit, Compare / Surprise me "Plan this trip", From a link, Alerts re-plan and the Results
 * re-runs (remix, retry the whole search, fare calendar).
 */
interface SearchLauncher {
    /**
     * Replaces the draft with a new plan for [request] (optionally pre-filled with [preloadSelections]) in one
     * step, then starts the stream.
     */
    fun startNewSearch(request: TravelSearchRequest, preloadSelections: PlanSelections? = null)
}

@Singleton
class SearchLauncherImpl
    @Inject
    constructor(
        private val plan: CurrentPlanStore,
        private val search: SearchRepository,
    ) : SearchLauncher {
        override fun startNewSearch(request: TravelSearchRequest, preloadSelections: PlanSelections?) {
            val picks = preloadSelections ?: PlanSelections()
            plan.replace(
                PlanDraft(
                    searchRequest = request,
                    searchKey = request.searchKey(),
                    selections = picks,
                    dirty = !picks.isEmpty,
                ),
            )
            search.start(request)
        }
    }
