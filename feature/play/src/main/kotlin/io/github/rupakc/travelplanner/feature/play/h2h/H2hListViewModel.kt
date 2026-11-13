package io.github.rupakc.travelplanner.feature.play.h2h

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.repository.PlayRepository
import io.github.rupakc.travelplanner.core.model.play.H2hMatch
import io.github.rupakc.travelplanner.core.model.social.SocialErrorCode
import io.github.rupakc.travelplanner.core.model.social.socialErrorCode
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the head-to-head list is. */
sealed interface H2hListPhase {
    data object Loading : H2hListPhase

    /** `403 game_hidden`, or Play is off: the shell leaves the screen. */
    data object Hidden : H2hListPhase

    /** [message] is the backend's own words (429...), or null for the local copy. */
    data class Error(val message: String?) : H2hListPhase

    data object Loaded : H2hListPhase
}

@Immutable
data class H2hListUiState(val phase: H2hListPhase = H2hListPhase.Loading, val matches: List<H2hMatch> = emptyList())

sealed interface H2hListEvent {
    data object Retry : H2hListEvent

    /** The list came back to the foreground (after a match): reload quietly. */
    data object Resumed : H2hListEvent
}

/** `GET /play/h2h`: the caller's matches. Creating and playing are other screens; nothing here scores anything. */
@HiltViewModel
class H2hListViewModel
    @Inject
    constructor(
        private val play: PlayRepository,
        private val journeyFlags: JourneyFlagsRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(H2hListUiState())
        val state: StateFlow<H2hListUiState> = mutable.asStateFlow()
        private var job: Job? = null
        private var resumedOnce = false

        init {
            load(quiet = false)
        }

        fun onEvent(event: H2hListEvent) {
            when (event) {
                H2hListEvent.Retry -> load(quiet = false)
                H2hListEvent.Resumed -> if (resumedOnce) load(quiet = true) else resumedOnce = true
            }
        }

        private fun load(quiet: Boolean) {
            job?.cancel()
            if (!quiet) mutable.value = H2hListUiState()
            job =
                viewModelScope.launch {
                    // Play (or the game) is off: nothing is requested, the shell leaves the screen.
                    val flags = journeyFlags.flags()
                    if (!flags.play || !flags.game) {
                        mutable.value = H2hListUiState(H2hListPhase.Hidden)
                        return@launch
                    }
                    when (val result = play.h2hMatches()) {
                        is AppResult.Success -> mutable.value = H2hListUiState(H2hListPhase.Loaded, result.value.matches)
                        is AppResult.Failure -> if (!quiet || mutable.value.phase != H2hListPhase.Loaded) mutable.value = H2hListUiState(failure(result.error))
                    }
                }
        }

        private fun failure(error: AppError): H2hListPhase =
            when (error.socialErrorCode()) {
                SocialErrorCode.GAME_HIDDEN, SocialErrorCode.PLAY_DISABLED -> H2hListPhase.Hidden
                else -> if (error is AppError.NotFound) H2hListPhase.Hidden else H2hListPhase.Error(error.message)
            }
    }
