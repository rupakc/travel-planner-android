package io.github.rupakc.travelplanner.feature.settings.taste

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TasteUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val profile: TasteProfile? = null,
    val confirmingReset: Boolean = false,
    val resetting: Boolean = false,
    val resetError: AppError? = null,
) {
    /** Web `hasSignals`: total_signals > 0. */
    val hasSignals: Boolean get() = (profile?.totalSignals ?: 0) > 0
}

sealed interface TasteEvent {
    data object Retry : TasteEvent

    data object AskReset : TasteEvent

    data object CancelReset : TasteEvent

    data object ConfirmReset : TasteEvent
}

/** Settings → Taste Graph (`GET/DELETE /api/taste-profile`), web TasteProfileCard. */
@HiltViewModel
class TasteProfileViewModel
    @Inject
    constructor(
        private val preferencesRepository: PreferencesRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(TasteUiState())
        val state: StateFlow<TasteUiState> = mutableState.asStateFlow()

        init {
            load()
        }

        fun onEvent(event: TasteEvent) {
            when (event) {
                TasteEvent.Retry -> load()
                TasteEvent.AskReset -> mutableState.update { it.copy(confirmingReset = true, resetError = null) }
                TasteEvent.CancelReset -> mutableState.update { it.copy(confirmingReset = false) }
                TasteEvent.ConfirmReset -> reset()
            }
        }

        private fun load() {
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = preferencesRepository.tasteProfile()) {
                    is AppResult.Success -> mutableState.update { it.copy(loading = false, profile = result.value) }
                    is AppResult.Failure -> mutableState.update { it.copy(loading = false, error = result.error) }
                }
            }
        }

        private fun reset() {
            mutableState.update { it.copy(confirmingReset = false, resetting = true, resetError = null) }
            viewModelScope.launch {
                when (val result = preferencesRepository.resetTasteProfile()) {
                    is AppResult.Success -> {
                        mutableState.update { it.copy(resetting = false) }
                        load()
                    }

                    is AppResult.Failure -> {
                        mutableState.update { it.copy(resetting = false, resetError = result.error) }
                    }
                }
            }
        }
    }
