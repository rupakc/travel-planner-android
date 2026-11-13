package io.github.rupakc.travelplanner.feature.play.h2h

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.PlayRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.play.QuizPack
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

/** A create failure, as a safe reason: never says whether the handle exists, is private or has blocked the caller. */
enum class CreateProblem { LIMIT, CANNOT_CHALLENGE, SELF, RESTRICTED, PROFILE_REQUIRED, EMAIL_UNVERIFIED, OTHER }

@Immutable
data class H2hCreateUiState(
    val handle: String = "",
    val pack: QuizPack = QuizPack.FLAGS,
    val busy: Boolean = false,
    val problem: CreateProblem? = null,
    /** The backend's own words for a failure with no code of ours (429...). */
    val problemMessage: String? = null,
    val hidden: Boolean = false,
) {
    val valid: Boolean get() = isHandleShape(normalizeHandle(handle))
}

sealed interface H2hCreateEvent {
    data class Handle(val value: String) : H2hCreateEvent

    data class Pack(val pack: QuizPack) : H2hCreateEvent

    data object Submit : H2hCreateEvent
}

/** `POST /play/h2h`: challenge a handle on a pack. On success the creator plays at once ([sessions] carries the session id). */
@HiltViewModel
class H2hCreateViewModel
    @Inject
    constructor(
        private val play: PlayRepository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(H2hCreateUiState())
        val state: StateFlow<H2hCreateUiState> = mutable.asStateFlow()
        private val sessionChannel = Channel<String>(Channel.BUFFERED)

        /** The session id of the created match, once: the screen opens the quiz on it. */
        val sessions: Flow<String> = sessionChannel.receiveAsFlow()

        fun onEvent(event: H2hCreateEvent) {
            when (event) {
                is H2hCreateEvent.Handle -> mutable.update { it.copy(handle = event.value.take(MAX_INPUT), problem = null, problemMessage = null) }
                is H2hCreateEvent.Pack -> mutable.update { it.copy(pack = event.pack) }
                H2hCreateEvent.Submit -> submit()
            }
        }

        private fun submit() {
            val s = mutable.value
            val handle = normalizeHandle(s.handle)
            if (s.busy || !isHandleShape(handle)) return
            mutable.update { it.copy(busy = true, problem = null, problemMessage = null) }
            viewModelScope.launch {
                when (val result = play.createH2h(handle, s.pack)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.PLAY_H2H_CREATE, emptyMap())
                        mutable.update { it.copy(busy = false) }
                        sessionChannel.send(result.value.sessionId)
                    }

                    is AppResult.Failure -> {
                        mutable.update { failed(it, result.error) }
                    }
                }
            }
        }

        private fun failed(s: H2hCreateUiState, error: AppError): H2hCreateUiState =
            when (error.socialErrorCode()) {
                SocialErrorCode.GAME_HIDDEN, SocialErrorCode.PLAY_DISABLED -> {
                    s.copy(busy = false, hidden = true)
                }

                SocialErrorCode.LIMIT_REACHED -> {
                    s.copy(busy = false, problem = CreateProblem.LIMIT)
                }

                // A private, missing or blocking opponent all look the same on purpose.
                SocialErrorCode.PROFILE_NOT_FOUND, SocialErrorCode.NOT_FOUND -> {
                    s.copy(busy = false, problem = CreateProblem.CANNOT_CHALLENGE)
                }

                SocialErrorCode.CANNOT_CHALLENGE_SELF -> {
                    s.copy(busy = false, problem = CreateProblem.SELF)
                }

                SocialErrorCode.RESTRICTED -> {
                    s.copy(busy = false, problem = CreateProblem.RESTRICTED)
                }

                SocialErrorCode.PROFILE_REQUIRED -> {
                    s.copy(busy = false, problem = CreateProblem.PROFILE_REQUIRED)
                }

                SocialErrorCode.EMAIL_UNVERIFIED -> {
                    s.copy(busy = false, problem = CreateProblem.EMAIL_UNVERIFIED)
                }

                else -> {
                    s.copy(
                        busy = false,
                        problem = CreateProblem.OTHER,
                        problemMessage = error.message.takeIf { error is AppError.RateLimited },
                    )
                }
            }

        private companion object {
            const val MAX_INPUT = 32
        }
    }
