package io.github.rupakc.travelplanner.feature.play.board

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.PlayRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.play.BoardRow
import io.github.rupakc.travelplanner.core.model.play.BoardScope
import io.github.rupakc.travelplanner.core.model.play.BoardWeek
import io.github.rupakc.travelplanner.core.model.play.BoardYou
import io.github.rupakc.travelplanner.core.model.social.SocialErrorCode
import io.github.rupakc.travelplanner.core.model.social.socialErrorCode
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BoardPhase {
    data object Loading : BoardPhase

    /** `403 game_hidden`, or Play is off: the shell leaves the screen. */
    data object Hidden : BoardPhase

    data class Error(val message: String?) : BoardPhase

    data object Loaded : BoardPhase
}

@Immutable
data class BoardUiState(
    val scope: BoardScope = BoardScope.GLOBAL,
    val week: BoardWeek = BoardWeek.CURRENT,
    val phase: BoardPhase = BoardPhase.Loading,
    /** ISO week label of the server (`2026-W46`). */
    val weekLabel: String? = null,
    val rows: List<BoardRow> = emptyList(),
    /** The caller's own place; null without a handle (no profile yet). */
    val you: BoardYou? = null,
) {
    /** The global board lists opted-in users only: someone with a place who has not opted in gets the prompt. */
    val showOptInPrompt: Boolean get() = phase == BoardPhase.Loaded && scope == BoardScope.GLOBAL && you != null && !you.optedIn
}

sealed interface BoardEvent {
    data class Scope(val scope: BoardScope) : BoardEvent

    data class Week(val week: BoardWeek) : BoardEvent

    data object Retry : BoardEvent
}

/** `GET /play/leaderboard`: global or friends, this or last week. Handles only; the app never ranks or scores anything. */
@HiltViewModel
class BoardViewModel
    @Inject
    constructor(
        private val play: PlayRepository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(BoardUiState())
        val state: StateFlow<BoardUiState> = mutable.asStateFlow()
        private var job: Job? = null

        init {
            load()
        }

        fun onEvent(event: BoardEvent) {
            when (event) {
                is BoardEvent.Scope -> if (event.scope != mutable.value.scope) change { it.copy(scope = event.scope) }
                is BoardEvent.Week -> if (event.week != mutable.value.week) change { it.copy(week = event.week) }
                BoardEvent.Retry -> load()
            }
        }

        private fun change(edit: (BoardUiState) -> BoardUiState) {
            mutable.update(edit)
            load()
        }

        private fun load() {
            job?.cancel()
            val asked = mutable.value
            mutable.update { BoardUiState(scope = asked.scope, week = asked.week) }
            job =
                viewModelScope.launch {
                    when (val result = play.leaderboard(asked.scope, asked.week)) {
                        is AppResult.Success -> {
                            val board = result.value
                            mutable.update {
                                it.copy(phase = BoardPhase.Loaded, weekLabel = board.week, rows = board.rows, you = board.you)
                            }
                            telemetry.emit(TelemetryEvents.PLAY_BOARD_VIEW, mapOf("scope" to asked.scope.wire))
                        }

                        is AppResult.Failure -> {
                            mutable.update { it.copy(phase = failure(result.error)) }
                        }
                    }
                }
        }

        private fun failure(error: AppError): BoardPhase =
            when (error.socialErrorCode()) {
                SocialErrorCode.GAME_HIDDEN, SocialErrorCode.PLAY_DISABLED -> BoardPhase.Hidden
                else -> if (error is AppError.NotFound) BoardPhase.Hidden else BoardPhase.Error(error.message)
            }
    }
