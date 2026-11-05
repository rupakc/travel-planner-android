package io.github.rupakc.travelplanner.feature.search.compare

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.CompareRepository
import io.github.rupakc.travelplanner.core.domain.search.CompareErrorReason
import io.github.rupakc.travelplanner.core.domain.search.CompareRow
import io.github.rupakc.travelplanner.core.domain.search.MAX_COMPARE_DESTINATIONS
import io.github.rupakc.travelplanner.core.domain.search.MIN_COMPARE_DESTINATIONS
import io.github.rupakc.travelplanner.core.domain.search.SearchForm
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.applyPreferences
import io.github.rupakc.travelplanner.core.domain.search.bestByRow
import io.github.rupakc.travelplanner.core.domain.search.buildCompareRequests
import io.github.rupakc.travelplanner.core.domain.search.compareErrorReason
import io.github.rupakc.travelplanner.core.domain.search.validateTripDates
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One compared destination (web column: loading / error + Retry / done). */
@Immutable
data class CompareColumn(
    val destination: String,
    val status: Status,
    val summary: CompareSummary? = null,
    val error: CompareErrorReason? = null,
) {
    enum class Status { LOADING, ERROR, DONE }
}

/** Why Compare can't run yet. */
enum class CompareIssue { NEED_TWO_DESTINATIONS, ORIGIN_REQUIRED, NATIONALITY_REQUIRED }

@Immutable
data class CompareUiState(
    val today: LocalDate,
    /** Shared trip fields; null until Compare is first opened (then seeded once from the known form, web parity). */
    val form: SearchForm? = null,
    val destinations: List<String> = listOf("", ""),
    val columns: List<CompareColumn>? = null,
    val issue: CompareIssue? = null,
    val dateIssues: List<SearchFormIssue> = emptyList(),
) {
    val loading: Boolean get() = columns?.any { it.status == CompareColumn.Status.LOADING } == true
    val canAddDestination: Boolean get() = destinations.size < MAX_COMPARE_DESTINATIONS
    val canRemoveDestination: Boolean get() = destinations.size > MIN_COMPARE_DESTINATIONS

    /** Best column indexes per row, over loaded columns only. */
    val best: Map<CompareRow, Set<Int>>
        get() = bestByRow(columns.orEmpty().map { c -> c.summary.takeIf { c.status == CompareColumn.Status.DONE } })
}

sealed interface CompareEvent {
    data class UpdateForm(val form: SearchForm) : CompareEvent

    data class UpdateDestination(val index: Int, val value: String) : CompareEvent

    data object AddDestination : CompareEvent

    data class RemoveDestination(val index: Int) : CompareEvent

    data object Run : CompareEvent

    data class Retry(val index: Int) : CompareEvent
}

/**
 * Web Compare mode: 2–3 destinations + shared fields; one `POST /api/compare/destination`
 * per destination in parallel, each column with its own loading/error/Retry.
 * Responses from an older run are ignored (web `compareRun`).
 */
@HiltViewModel
class CompareViewModel
    @Inject
    constructor(
        private val compareRepository: CompareRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val _state = MutableStateFlow(CompareUiState(today = clock.today()))
        val state: StateFlow<CompareUiState> = _state.asStateFlow()
        private var requests: List<TravelSearchRequest> = emptyList()
        private var run = 0

        /** The user edited the shared fields: late preferences must not overwrite them. */
        private var touched = false

        /** Entering Compare seeds it, once, from the known form (its destination becomes the first). */
        fun seed(known: SearchForm) {
            if (_state.value.form != null) return
            _state.update {
                it.copy(form = known.copy(destination = "", mustInclude = null, importSource = null), destinations = listOf(known.destination, ""))
            }
        }

        /** Preferences that arrive after seeding pre-fill the shared fields while the user hasn't touched them (web parity). */
        fun applyPreferences(prefs: Preferences) {
            if (touched) return
            _state.update { s -> s.copy(form = s.form?.let { applyPreferences(it, prefs) }) }
        }

        /** The compared request behind column [index] ("Plan this trip"), or null. */
        fun requestAt(index: Int): TravelSearchRequest? = requests.getOrNull(index)

        fun onEvent(event: CompareEvent) {
            when (event) {
                is CompareEvent.UpdateForm -> {
                    touched = true
                    _state.update { it.copy(form = event.form, dateIssues = emptyList(), issue = null) }
                }

                is CompareEvent.UpdateDestination -> {
                    _state.update { s -> s.copy(destinations = s.destinations.mapIndexed { i, d -> if (i == event.index) event.value else d }, issue = null) }
                }

                CompareEvent.AddDestination -> {
                    _state.update { s -> if (s.canAddDestination) s.copy(destinations = s.destinations + "") else s }
                }

                is CompareEvent.RemoveDestination -> {
                    _state.update { s -> if (s.canRemoveDestination) s.copy(destinations = s.destinations.filterIndexed { i, _ -> i != event.index }) else s }
                }

                CompareEvent.Run -> {
                    runCompare()
                }

                is CompareEvent.Retry -> {
                    if (_state.value.columns?.getOrNull(event.index)?.status != CompareColumn.Status.LOADING) load(event.index)
                }
            }
        }

        private fun runCompare() {
            val s = _state.value
            val form = s.form ?: return
            if (s.loading) return
            val today = clock.today()
            _state.update { it.copy(today = today) }
            val built = buildCompareRequests(form, s.destinations)
            val issue =
                when {
                    built.size < MIN_COMPARE_DESTINATIONS -> CompareIssue.NEED_TWO_DESTINATIONS
                    form.origin.isBlank() -> CompareIssue.ORIGIN_REQUIRED
                    form.nationality.isBlank() -> CompareIssue.NATIONALITY_REQUIRED
                    else -> null
                }
            val dateIssues = validateTripDates(form.departureDate, form.returnDate, today)
            _state.update { it.copy(issue = issue, dateIssues = dateIssues) }
            if (issue != null || dateIssues.isNotEmpty()) return
            run++
            requests = built
            _state.update { it.copy(columns = built.map { r -> CompareColumn(r.destination, CompareColumn.Status.LOADING) }) }
            built.indices.forEach(::load)
        }

        private fun load(index: Int) {
            val request = requests.getOrNull(index) ?: return
            val thisRun = run
            patch(index) { it.copy(status = CompareColumn.Status.LOADING, error = null) }
            viewModelScope.launch {
                val result = compareRepository.compare(request)
                if (thisRun != run) return@launch
                when (result) {
                    is AppResult.Success -> patch(index) { it.copy(status = CompareColumn.Status.DONE, summary = result.value) }
                    is AppResult.Failure -> patch(index) { it.copy(status = CompareColumn.Status.ERROR, error = compareErrorReason(result.error)) }
                }
            }
        }

        private fun patch(index: Int, transform: (CompareColumn) -> CompareColumn) =
            _state.update { s -> s.copy(columns = s.columns?.mapIndexed { i, c -> if (i == index) transform(c) else c }) }
    }
