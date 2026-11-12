package io.github.rupakc.travelplanner.feature.alerts.story

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AlertsV2Repository
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.alerts.AdviceTip
import io.github.rupakc.travelplanner.core.model.alerts.AlertKind
import io.github.rupakc.travelplanner.core.model.alerts.AlertsAdviceCode
import io.github.rupakc.travelplanner.core.model.alerts.PriceStory
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A failed call: the backend text (shown as-is when we have no translation) and the typed failure. */
@Immutable
data class StoryError(val failure: TipFailure, val message: String?)

/** The AI timing tip, flights only. */
sealed interface TipState {
    data object Idle : TipState

    data object Loading : TipState

    data class Ready(val tip: String, val cached: Boolean) : TipState

    data class Failed(val error: StoryError) : TipState
}

/** State of the price story sheet. */
@Immutable
data class PriceStoryUiState(
    val loading: Boolean = true,
    val story: PriceStory? = null,
    val error: StoryError? = null,
    val tip: TipState = TipState.Idle,
) {
    /** A tip can be asked for: a flight watch with a story that is not waiting for its first price. */
    val tipAvailable: Boolean get() = story?.kind == AlertKind.FLIGHT.wire && story.adviceCode != AlertsAdviceCode.PENDING
}

/** Loads one watch's story (and, on request, the AI tip). Flags are checked by the caller; this never calls v2 routes with them off. */
@HiltViewModel
class PriceStoryViewModel
    @Inject
    constructor(
        private val repo: AlertsV2Repository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(PriceStoryUiState())
        val state: StateFlow<PriceStoryUiState> = mutable.asStateFlow()
        private var watch: Pair<AlertKind, Long>? = null
        private var job: Job? = null

        /** Starts loading for a watch; calling again for the same watch does nothing unless the last load failed. */
        fun load(kind: AlertKind, watchId: Long) {
            val key = kind to watchId
            if (watch == key && mutable.value.error == null) return
            watch = key
            mutable.value = PriceStoryUiState()
            fetch(key)
        }

        /** Retry after an error. */
        fun retry() {
            val key = watch ?: return
            mutable.update { it.copy(loading = true, error = null) }
            fetch(key)
        }

        private fun fetch(key: Pair<AlertKind, Long>) {
            job?.cancel()
            job =
                viewModelScope.launch {
                    when (val r = repo.story(key.first, key.second)) {
                        is AppResult.Success -> {
                            mutable.update { it.copy(loading = false, story = r.value, error = null) }
                            telemetry.emit(
                                TelemetryEvents.ALERT_STORY_VIEW,
                                mapOf("kind" to key.first.wire, "verdict" to telemetryVerdict(r.value)),
                            )
                        }

                        is AppResult.Failure -> {
                            mutable.update { it.copy(loading = false, error = r.error.toStoryError()) }
                        }
                    }
                }
        }

        /** "Get a timing tip". */
        fun requestTip() {
            val key = watch ?: return
            if (!mutable.value.tipAvailable || mutable.value.tip == TipState.Loading) return
            mutable.update { it.copy(tip = TipState.Loading) }
            viewModelScope.launch {
                val outcome: String
                val next: TipState
                when (val r = repo.advice(key.first, key.second)) {
                    is AppResult.Success -> {
                        val tip = r.value.usableTip()
                        outcome =
                            if (tip == null) {
                                "error"
                            } else if (r.value.cached) {
                                "cached"
                            } else {
                                "ok"
                            }
                        next = tip?.let { TipState.Ready(it, r.value.cached) } ?: TipState.Failed(StoryError(TipFailure.FAILED, null))
                    }

                    is AppResult.Failure -> {
                        outcome = "error"
                        next = TipState.Failed(r.error.toStoryError())
                    }
                }
                mutable.update { it.copy(tip = next) }
                telemetry.emit(TelemetryEvents.ALERT_ADVICE, mapOf("kind" to key.first.wire, "outcome" to outcome))
            }
        }

        private fun AdviceTip.usableTip(): String? = tip?.trim()?.takeIf { it.isNotEmpty() }

        private fun AppError.toStoryError() = StoryError(tipFailure(this), message)
    }

/** Whether the story entry shows: the v2 routes exist on this backend (stories work from stored data without live prices). */
@HiltViewModel
class StoryGateViewModel
    @Inject
    constructor(private val flags: JourneyFlagsRepository) : ViewModel() {
        private val mutable = MutableStateFlow(false)
        val available: StateFlow<Boolean> = mutable.asStateFlow()

        init {
            viewModelScope.launch { mutable.value = flags.flags().alerts.v2Available }
        }
    }
