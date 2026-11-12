package io.github.rupakc.travelplanner.feature.play.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.PlayRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.social.SocialErrorCode
import io.github.rupakc.travelplanner.core.model.social.socialErrorCode
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Play state (daily availability, streak, packs, challenge, open head-to-head count) and the recent quiz history. T5.5 extends the
 * same state with head-to-head and boards; nothing here scores anything.
 */
@HiltViewModel
class PlayHubViewModel
    @Inject
    constructor(
        private val play: PlayRepository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(PlayHubUiState())
        val state: StateFlow<PlayHubUiState> = mutable.asStateFlow()
        private var job: Job? = null
        private var resumedOnce = false

        init {
            load(quiet = false)
        }

        fun onEvent(event: PlayHubEvent) {
            when (event) {
                PlayHubEvent.Retry -> {
                    load(quiet = false)
                }

                PlayHubEvent.Resumed -> {
                    // The first resume is the screen opening, which `init` already loads.
                    if (resumedOnce) load(quiet = true) else resumedOnce = true
                }
            }
        }

        private fun load(quiet: Boolean) {
            job?.cancel()
            if (!quiet) mutable.value = PlayHubUiState()
            job =
                viewModelScope.launch {
                    when (val result = play.state()) {
                        is AppResult.Failure -> {
                            // A quiet reload keeps what is on screen.
                            if (!quiet || mutable.value.phase != PlayHubPhase.Loaded) mutable.value = PlayHubUiState(phase = failurePhase(result.error))
                        }

                        is AppResult.Success -> {
                            val s = result.value
                            val history = (play.history(HISTORY_LIMIT) as? AppResult.Success)?.value?.sessions.orEmpty()
                            val firstView = mutable.value.phase != PlayHubPhase.Loaded
                            mutable.value =
                                PlayHubUiState(
                                    phase = PlayHubPhase.Loaded,
                                    daily = s.daily,
                                    packs = s.packs,
                                    challenge = s.challenge,
                                    openH2h = s.openH2h ?: 0,
                                    history = history,
                                )
                            if (firstView) telemetry.emit(TelemetryEvents.PLAY_STATE_VIEW, emptyMap())
                        }
                    }
                }
        }

        private fun failurePhase(error: AppError): PlayHubPhase =
            when (error.socialErrorCode()) {
                SocialErrorCode.GAME_HIDDEN -> PlayHubPhase.GameHidden
                SocialErrorCode.PLAY_DISABLED -> PlayHubPhase.Off
                else -> if (error is AppError.NotFound) PlayHubPhase.Off else PlayHubPhase.Error(error.message)
            }

        private companion object {
            const val HISTORY_LIMIT = 10
        }
    }
