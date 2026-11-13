package io.github.rupakc.travelplanner.feature.play.hub

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.model.play.Challenge
import io.github.rupakc.travelplanner.core.model.play.DailyState
import io.github.rupakc.travelplanner.core.model.play.HistoryEntry
import io.github.rupakc.travelplanner.core.model.play.PackInfo

/** Where the quiz hub (daily card, packs, challenge, history) is. It sits above the Phase 1/2 progress content of the Play tab. */
sealed interface PlayHubPhase {
    data object Loading : PlayHubPhase

    /** `GET /play/state` answered 404 / `play_disabled`: Play quizzes do not exist on this server, nothing is drawn. */
    data object Off : PlayHubPhase

    /** `403 game_hidden`: the whole Play entry hides (the shell leaves the tab). */
    data object GameHidden : PlayHubPhase

    /** [message] is the backend's own words (429...), or null for the local copy. */
    data class Error(val message: String?) : PlayHubPhase

    data object Loaded : PlayHubPhase
}

@Immutable
data class PlayHubUiState(
    val phase: PlayHubPhase = PlayHubPhase.Loading,
    val daily: DailyState = DailyState(),
    val packs: List<PackInfo> = emptyList(),
    /** The weekly challenge card (T5.5 owns its detail; here it only starts the challenge quiz). */
    val challenge: Challenge? = null,
    /** Open head-to-head matches: a placeholder for T5.5, not drawn yet. */
    val openH2h: Int = 0,
    val history: List<HistoryEntry> = emptyList(),
)

sealed interface PlayHubEvent {
    data object Retry : PlayHubEvent

    /** The Play tab came back to the foreground (after a quiz): reload quietly. */
    data object Resumed : PlayHubEvent
}
