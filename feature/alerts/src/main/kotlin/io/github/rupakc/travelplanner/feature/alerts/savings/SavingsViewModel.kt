package io.github.rupakc.travelplanner.feature.alerts.savings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AlertsV2Repository
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The savings tally header of the Alerts screen. [Hidden] against a backend without Alerts 2.0 (nothing is called). */
@Immutable
sealed interface SavingsUiState {
    data object Hidden : SavingsUiState

    data object Loading : SavingsUiState

    /** [message] is the backend's text (429 and the like), else null. */
    data class Failed(val message: String?) : SavingsUiState

    /** [livePricesPaused]: the server has no price source, so the figures come from stored data. */
    data class Loaded internal constructor(internal val view: SavingsView, val livePricesPaused: Boolean) : SavingsUiState
}

sealed interface SavingsEvent {
    data object Retry : SavingsEvent
}

/** `GET /alerts/summary` behind `AlertsFlags.v2Available`; works from stored data when live prices are off. */
@HiltViewModel
class SavingsViewModel
    @Inject
    constructor(
        private val repository: AlertsV2Repository,
        private val flags: JourneyFlagsRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow<SavingsUiState>(SavingsUiState.Loading)
        val state: StateFlow<SavingsUiState> = mutable.asStateFlow()

        init {
            load()
        }

        fun onEvent(event: SavingsEvent) {
            when (event) {
                SavingsEvent.Retry -> load()
            }
        }

        private fun load() {
            mutable.update { SavingsUiState.Loading }
            viewModelScope.launch {
                val alerts = flags.flags().alerts
                if (!alerts.v2Available) {
                    mutable.value = SavingsUiState.Hidden
                    return@launch
                }
                mutable.value =
                    when (val result = repository.summary()) {
                        is AppResult.Success -> SavingsUiState.Loaded(result.value.view(), livePricesPaused = !alerts.livePricesAvailable)
                        is AppResult.Failure -> SavingsUiState.Failed(result.error.message)
                    }
            }
        }
    }
