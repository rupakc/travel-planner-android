package io.github.rupakc.travelplanner.feature.play.h2h

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.repository.PlayRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.play.H2hMatch
import io.github.rupakc.travelplanner.core.model.play.H2hState
import io.github.rupakc.travelplanner.core.model.play.H2hWinner
import io.github.rupakc.travelplanner.core.model.social.SocialErrorCode
import io.github.rupakc.travelplanner.core.model.social.socialErrorCode
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface H2hDetailPhase {
    data object Loading : H2hDetailPhase

    data object Hidden : H2hDetailPhase

    /** The list has no such match (a stale link, or someone else's). */
    data object Missing : H2hDetailPhase

    data class Error(val message: String?) : H2hDetailPhase

    data object Loaded : H2hDetailPhase
}

@Immutable
data class H2hDetailUiState(
    val phase: H2hDetailPhase = H2hDetailPhase.Loading,
    val match: H2hMatch? = null,
    val busy: Boolean = false,
    /** A failed accept or decline: the backend's words, or null for the local copy. */
    val actionError: ActionFailure? = null,
)

/** [message] is the backend text (429...) or null. */
data class ActionFailure(val message: String?)

sealed interface H2hDetailEvent {
    data object Retry : H2hDetailEvent

    data object Resumed : H2hDetailEvent

    /** Play the invited match: `POST /play/h2h/{id}/accept`, then the quiz on the returned session. */
    data object Accept : H2hDetailEvent

    data object Decline : H2hDetailEvent
}

/** One match (`/play/h2h/{id}` of the web link): there is no single-match route, so it is found in `GET /play/h2h`. */
@HiltViewModel
class H2hDetailViewModel
    @Inject
    constructor(
        handle: SavedStateHandle,
        private val play: PlayRepository,
        private val telemetry: Telemetry,
        private val journeyFlags: JourneyFlagsRepository,
    ) : ViewModel() {
        private val matchId: String = handle.get<String>(ARG_MATCH_ID).orEmpty()
        private val mutable = MutableStateFlow(H2hDetailUiState())
        val state: StateFlow<H2hDetailUiState> = mutable.asStateFlow()
        private val playChannel = Channel<String>(Channel.BUFFERED)
        private val closeChannel = Channel<Unit>(Channel.BUFFERED)
        private var opened = false
        private var resultReported = false
        private var resumedOnce = false

        /** The session id to play (after an accept). */
        val sessions: Flow<String> = playChannel.receiveAsFlow()

        /** Fires once after a decline: the screen goes back. */
        val closed: Flow<Unit> = closeChannel.receiveAsFlow()

        init {
            load(quiet = false)
        }

        fun onEvent(event: H2hDetailEvent) {
            when (event) {
                H2hDetailEvent.Retry -> load(quiet = false)
                H2hDetailEvent.Resumed -> if (resumedOnce) load(quiet = true) else resumedOnce = true
                H2hDetailEvent.Accept -> accept()
                H2hDetailEvent.Decline -> decline()
            }
        }

        private fun load(quiet: Boolean) {
            if (!quiet) mutable.value = H2hDetailUiState()
            viewModelScope.launch {
                // Play (or the game) is off: nothing is requested, the shell leaves the screen.
                val flags = journeyFlags.flags()
                if (!flags.play || !flags.game) {
                    mutable.update { it.copy(phase = H2hDetailPhase.Hidden) }
                    return@launch
                }
                when (val result = play.h2hMatches()) {
                    is AppResult.Success -> shown(result.value.matches.firstOrNull { it.id == matchId })
                    is AppResult.Failure -> failed(result.error, quiet)
                }
            }
        }

        private fun shown(match: H2hMatch?) {
            if (match == null) {
                mutable.update { it.copy(phase = H2hDetailPhase.Missing, match = null, busy = false) }
                return
            }
            mutable.update { it.copy(phase = H2hDetailPhase.Loaded, match = match, busy = false) }
            if (!opened) {
                opened = true
                telemetry.emit(TelemetryEvents.PLAY_H2H_OPEN, emptyMap())
            }
            val winner = match.matchWinner
            val decided = winner != null && winner != H2hWinner.UNKNOWN
            if (match.matchState == H2hState.DONE && decided && !resultReported) {
                resultReported = true
                telemetry.emit(TelemetryEvents.PLAY_H2H_RESULT, mapOf("winner" to (winner?.wire ?: "draw")))
            }
        }

        private fun failed(error: AppError, quiet: Boolean) {
            val code = error.socialErrorCode()
            when {
                code == SocialErrorCode.GAME_HIDDEN || code == SocialErrorCode.PLAY_DISABLED || error is AppError.NotFound -> {
                    mutable.update { it.copy(phase = H2hDetailPhase.Hidden) }
                }

                quiet && mutable.value.phase == H2hDetailPhase.Loaded -> {
                    Unit
                }

                else -> {
                    mutable.update { it.copy(phase = H2hDetailPhase.Error(error.message)) }
                }
            }
        }

        private fun accept() {
            val s = mutable.value
            if (s.busy || s.match?.matchState != H2hState.WAITING_YOU) return
            mutable.update { it.copy(busy = true, actionError = null) }
            viewModelScope.launch {
                when (val result = play.acceptH2h(matchId)) {
                    is AppResult.Success -> {
                        mutable.update { it.copy(busy = false) }
                        playChannel.send(result.value.sessionId)
                    }

                    is AppResult.Failure -> {
                        actionFailed(result.error)
                    }
                }
            }
        }

        private fun decline() {
            val s = mutable.value
            if (s.busy || s.match?.matchState != H2hState.WAITING_YOU) return
            mutable.update { it.copy(busy = true, actionError = null) }
            viewModelScope.launch {
                when (val result = play.declineH2h(matchId)) {
                    is AppResult.Success -> {
                        mutable.update { it.copy(busy = false) }
                        closeChannel.send(Unit)
                    }

                    is AppResult.Failure -> {
                        actionFailed(result.error)
                    }
                }
            }
        }

        private fun actionFailed(error: AppError) {
            when (error.socialErrorCode()) {
                // The match moved on (expired, already answered): show it as the server now has it.
                SocialErrorCode.MATCH_EXPIRED, SocialErrorCode.INVALID_STATE, SocialErrorCode.MATCH_NOT_FOUND -> {
                    mutable.update { it.copy(busy = false) }
                    load(quiet = true)
                }

                SocialErrorCode.GAME_HIDDEN, SocialErrorCode.PLAY_DISABLED -> {
                    mutable.update { it.copy(busy = false, phase = H2hDetailPhase.Hidden) }
                }

                else -> {
                    mutable.update { it.copy(busy = false, actionError = ActionFailure(error.message.takeIf { error is AppError.RateLimited })) }
                }
            }
        }

        companion object {
            const val ARG_MATCH_ID = "matchId"
        }
    }
