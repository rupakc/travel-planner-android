package io.github.rupakc.travelplanner.feature.trips.offline

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.repository.OfflinePackRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.trips.PackPhrase
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a save, refresh or remove failed. [message] is backend text shown as-is, else our own copy. */
@Immutable
data class PackFailure(val kind: Kind, val message: String? = null) {
    enum class Kind { FAILED, QUOTA, UNSUPPORTED }
}

@Immutable
data class OfflinePackUiState(
    val loading: Boolean = true,
    val view: PackView? = null,
    val busy: Boolean = false,
    val failure: PackFailure? = null,
    /** The last refresh failed for lack of a connection: the saved pack is being shown. */
    val offline: Boolean = false,
    /** The phrase held up to a local, full screen. */
    val shownPhrase: PackPhrase? = null,
    val amount: String = "",
    /** true: home to local. */
    val toLocal: Boolean = true,
)

sealed interface OfflinePackEvent {
    data object Save : OfflinePackEvent

    data object Remove : OfflinePackEvent

    data class ShowPhrase(val phrase: PackPhrase) : OfflinePackEvent

    data object ClosePhrase : OfflinePackEvent

    data class SetAmount(val text: String) : OfflinePackEvent

    data object Swap : OfflinePackEvent
}

/**
 * The offline pack of one trip. Reads only the saved copy (so it works with the network off); Save/Refresh
 * downloads a fresh one. The repository purges everything and reports `unsupported` when the server says the
 * feature is off (`404 offline_pack_disabled`).
 */
@HiltViewModel
class OfflinePackViewModel
    @Inject
    constructor(
        private val repository: OfflinePackRepository,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : ViewModel() {
        private val _state = MutableStateFlow(OfflinePackUiState())
        val state: StateFlow<OfflinePackUiState> = _state.asStateFlow()

        private var tripId: Long? = null
        private var watching: Job? = null
        private var opened = false

        /** Starts reading [id]'s saved pack; calling it again for the same trip does nothing. */
        fun start(id: Long) {
            if (tripId == id) return
            tripId = id
            opened = false
            watching?.cancel()
            _state.update { OfflinePackUiState() }
            watching =
                viewModelScope.launch {
                    repository.saved(id).collect { pack ->
                        val view = pack?.let(PackView::of)
                        if (view != null && !opened) {
                            opened = true
                            telemetry.emit(TelemetryEvents.OFFLINE_PACK, mapOf("action" to "open", "result" to "ok", "kb" to 0))
                        }
                        _state.update { it.copy(loading = false, view = view) }
                    }
                }
        }

        fun onEvent(event: OfflinePackEvent) {
            when (event) {
                OfflinePackEvent.Save -> save()
                OfflinePackEvent.Remove -> remove()
                is OfflinePackEvent.ShowPhrase -> _state.update { it.copy(shownPhrase = event.phrase) }
                OfflinePackEvent.ClosePhrase -> _state.update { it.copy(shownPhrase = null) }
                is OfflinePackEvent.SetAmount -> _state.update { it.copy(amount = event.text) }
                OfflinePackEvent.Swap -> _state.update { it.copy(toLocal = !it.toLocal) }
            }
        }

        private fun save() {
            val id = tripId ?: return
            if (_state.value.busy) return
            _state.update { it.copy(busy = true, failure = null) }
            viewModelScope.launch {
                val result = repository.save(id, clock.today().toString())
                _state.update {
                    when (result) {
                        is AppResult.Success -> it.copy(busy = false, offline = false)
                        is AppResult.Failure -> it.copy(busy = false, offline = result.error is AppError.Network, failure = failure(result.error))
                    }
                }
            }
        }

        private fun remove() {
            val id = tripId ?: return
            if (_state.value.busy) return
            _state.update { it.copy(busy = true, failure = null) }
            viewModelScope.launch {
                val result = repository.remove(id)
                _state.update { it.copy(busy = false, failure = if (result is AppResult.Failure) PackFailure(PackFailure.Kind.FAILED) else null) }
            }
        }

        private fun failure(error: AppError): PackFailure =
            when {
                error is AppError.NotFound && error.code == DISABLED -> PackFailure(PackFailure.Kind.UNSUPPORTED)
                error is AppError.Unknown && error.message == OfflinePackRepository.STORAGE_FULL -> PackFailure(PackFailure.Kind.QUOTA)
                else -> PackFailure(PackFailure.Kind.FAILED, error.userFacingDetail())
            }

        private companion object {
            const val DISABLED = "offline_pack_disabled"
        }
    }
