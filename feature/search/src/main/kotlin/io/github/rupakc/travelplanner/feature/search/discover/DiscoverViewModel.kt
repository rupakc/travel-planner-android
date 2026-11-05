package io.github.rupakc.travelplanner.feature.search.discover

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.getOrNull
import io.github.rupakc.travelplanner.core.data.repository.DiscoverRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.domain.search.SearchForm
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.TravellerCounts
import io.github.rupakc.travelplanner.core.domain.search.canDiscover
import io.github.rupakc.travelplanner.core.domain.search.toDiscoveryRequest
import io.github.rupakc.travelplanner.core.domain.search.validateTripDates
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Surprise me: the discover form, the call, and its 5 suggestions. */
@Immutable
data class DiscoverUiState(
    val today: LocalDate,
    val form: SearchForm = SearchForm.initial(today),
    val loading: Boolean = false,
    /** null = not run yet; empty = "No destinations found…". */
    val destinations: List<DiscoveredDestination>? = null,
    /** The failure; its message (backend detail) is shown as-is, else the web's fallback copy. */
    val error: AppError? = null,
    val dateIssues: List<SearchFormIssue> = emptyList(),
) {
    /** Web: the button is disabled until origin + nationality. */
    val canSubmit: Boolean get() = canDiscover(form) && !loading
}

sealed interface DiscoverEvent {
    data class UpdateForm(val form: SearchForm) : DiscoverEvent

    data object Submit : DiscoverEvent
}

/** Web Surprise me (`POST /api/discover`), pre-filled from preferences like the web. */
@HiltViewModel
class DiscoverViewModel
    @Inject
    constructor(
        private val discoverRepository: DiscoverRepository,
        private val preferencesRepository: PreferencesRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DiscoverUiState(today = clock.today()))
        val state: StateFlow<DiscoverUiState> = _state.asStateFlow()
        private var job: Job? = null

        init {
            viewModelScope.launch {
                val prefs = preferencesRepository.get().getOrNull() ?: return@launch
                _state.update { s ->
                    s.copy(
                        form =
                            s.form.copy(
                                nationality = prefs.nationality.ifBlank { s.form.nationality },
                                interests = prefs.interests.ifEmpty { s.form.interests },
                                travellers = TravellerCounts.of(prefs.adults, prefs.children, prefs.seniors, prefs.infants) ?: s.form.travellers,
                            ),
                    )
                }
            }
        }

        fun onEvent(event: DiscoverEvent) {
            when (event) {
                is DiscoverEvent.UpdateForm -> _state.update { it.copy(form = event.form, dateIssues = emptyList()) }
                DiscoverEvent.Submit -> submit()
            }
        }

        private fun submit() {
            val s = _state.value
            if (!s.canSubmit) return
            val dateIssues = validateTripDates(s.form.departureDate, s.form.returnDate, clock.today().also { t -> _state.update { it.copy(today = t) } })
            if (dateIssues.isNotEmpty()) {
                _state.update { it.copy(dateIssues = dateIssues) }
                return
            }
            job?.cancel()
            _state.update { it.copy(loading = true, error = null, destinations = null) }
            job =
                viewModelScope.launch {
                    when (val result = discoverRepository.discover(toDiscoveryRequest(s.form))) {
                        is AppResult.Success -> _state.update { it.copy(loading = false, destinations = result.value.destinations) }
                        is AppResult.Failure -> _state.update { it.copy(loading = false, error = result.error) }
                    }
                }
        }
    }
