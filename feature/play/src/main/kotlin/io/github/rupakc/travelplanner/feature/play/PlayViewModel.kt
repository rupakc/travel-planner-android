package io.github.rupakc.travelplanner.feature.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.GameRepository
import io.github.rupakc.travelplanner.core.data.repository.TripsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.trips.Stats
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Play: level, badges, streaks, recent awards and (with trips) countries and the bucket-list goal.
 * `GET /game/me` 404 or `hidden` hides the screen. [trips] is empty until a Trips implementation is bound.
 */
@HiltViewModel
class PlayViewModel
    @Inject
    constructor(
        private val game: GameRepository,
        private val trips: Optional<TripsRepository>,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(PlayUiState())
        val state: StateFlow<PlayUiState> = mutable.asStateFlow()
        private var job: Job? = null

        init {
            load()
        }

        fun onEvent(event: PlayEvent) {
            when (event) {
                PlayEvent.Retry -> load()
            }
        }

        private fun load() {
            job?.cancel()
            mutable.value = PlayUiState(phase = PlayPhase.Loading)
            job =
                viewModelScope.launch {
                    when (val me = game.me()) {
                        is AppResult.Failure -> {
                            mutable.value = PlayUiState(phase = failurePhase(me.error))
                        }

                        is AppResult.Success -> {
                            if (me.value.hidden) {
                                mutable.value = PlayUiState(phase = PlayPhase.Hidden)
                            } else {
                                val catalog = (game.catalog() as? AppResult.Success)?.value
                                mutable.value = PlayMapper.loaded(me.value, catalog, stats())
                                telemetry.emit(TelemetryEvents.GAME_VIEW, mapOf("surface" to "nav"))
                            }
                        }
                    }
                }
        }

        /** Null when trips are unavailable (not bound, 404, any failure): the section hides rather than errors. */
        private suspend fun stats(): Stats? = trips.orElse(null)?.let { (it.stats() as? AppResult.Success)?.value }

        private fun failurePhase(error: AppError): PlayPhase =
            when (error) {
                is AppError.NotFound -> PlayPhase.Hidden
                else -> PlayPhase.Error(error.message)
            }
    }
