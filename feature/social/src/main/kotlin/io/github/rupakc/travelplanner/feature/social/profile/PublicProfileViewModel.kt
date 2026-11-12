package io.github.rupakc.travelplanner.feature.social.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.data.repository.SocialRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.social.FollowState
import io.github.rupakc.travelplanner.core.model.social.PublicProfile
import io.github.rupakc.travelplanner.core.model.social.ReportReason
import io.github.rupakc.travelplanner.core.model.social.ReportTarget
import io.github.rupakc.travelplanner.core.model.social.SocialErrorCode
import io.github.rupakc.travelplanner.core.model.social.socialErrorCode
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ViewPhase { Loading, Error, Unavailable, Content }

/** The caller's relation, as the buttons show it. */
internal enum class FollowUi { None, Following, Pending }

internal sealed interface ProfileMessage {
    data object Blocked : ProfileMessage

    data object Reported : ProfileMessage

    data class Failed(val error: AppError) : ProfileMessage
}

@Immutable
internal data class PublicProfileUiState(
    val handle: String = "",
    val phase: ViewPhase = ViewPhase.Loading,
    val error: AppError? = null,
    val profile: PublicProfile? = null,
    val follow: FollowUi = FollowUi.None,
    /** This is the caller's own profile: no follow, block or report. */
    val isMe: Boolean = false,
    /** `hide_game` on the viewer's side: no level or badges are shown. */
    val hideGame: Boolean = false,
    val working: Boolean = false,
    val confirmBlock: Boolean = false,
    val reporting: Boolean = false,
    val reportSending: Boolean = false,
    val reportError: AppError? = null,
    val message: ProfileMessage? = null,
)

internal sealed interface PublicProfileEvent {
    data object Retry : PublicProfileEvent

    data object ToggleFollow : PublicProfileEvent

    data object AskBlock : PublicProfileEvent

    data object ConfirmBlock : PublicProfileEvent

    data object DismissBlock : PublicProfileEvent

    data object AskReport : PublicProfileEvent

    data object DismissReport : PublicProfileEvent

    data class SendReport(val reason: ReportReason) : PublicProfileEvent

    data object MessageShown : PublicProfileEvent
}

internal sealed interface PublicProfileEffect {
    /** The profile is blocked: leave it. */
    data object Leave : PublicProfileEffect
}

/**
 * A public profile (`GET /social/profile/{handle}`): optional fields only when present, follow / unfollow / cancel request,
 * block (confirmed, two-way and silent) and report. `404 profile_not_found` is one calm "not available" state (private, unknown
 * or blocked look the same). Telemetry: `social_profile_view`, `social_follow` (state), `social_block`, `social_report`.
 */
@HiltViewModel
internal class PublicProfileViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val social: SocialRepository,
        private val account: AccountRepository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val handle: String = savedState.get<String>(ARG_HANDLE).orEmpty()
        private val _state = MutableStateFlow(PublicProfileUiState(handle = handle))
        val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()
        private val _effects = Channel<PublicProfileEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: PublicProfileEvent) {
            when (event) {
                PublicProfileEvent.Retry -> load()
                PublicProfileEvent.ToggleFollow -> toggleFollow()
                PublicProfileEvent.AskBlock -> _state.update { it.copy(confirmBlock = true) }
                PublicProfileEvent.DismissBlock -> _state.update { it.copy(confirmBlock = false) }
                PublicProfileEvent.ConfirmBlock -> block()
                PublicProfileEvent.AskReport -> _state.update { it.copy(reporting = true, reportError = null) }
                PublicProfileEvent.DismissReport -> _state.update { it.copy(reporting = false, reportError = null) }
                is PublicProfileEvent.SendReport -> report(event.reason)
                PublicProfileEvent.MessageShown -> _state.update { it.copy(message = null) }
            }
        }

        private fun load() {
            _state.update { it.copy(phase = ViewPhase.Loading, error = null) }
            viewModelScope.launch {
                val hideGame = (account.settings() as? AppResult.Success)?.value?.hideGame ?: false
                val mine = (social.ownProfile() as? AppResult.Success)?.value?.handle
                when (val result = social.profile(handle)) {
                    is AppResult.Success -> {
                        val profile = result.value
                        telemetry.emit(TelemetryEvents.SOCIAL_PROFILE_VIEW, emptyMap())
                        _state.update {
                            it.copy(
                                phase = ViewPhase.Content,
                                profile = profile,
                                follow = profile.you.toFollowUi(),
                                isMe = mine != null && mine.equals(profile.handle, ignoreCase = true),
                                hideGame = hideGame,
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        val gone = result.error.socialErrorCode() == SocialErrorCode.PROFILE_NOT_FOUND
                        _state.update { it.copy(phase = if (gone) ViewPhase.Unavailable else ViewPhase.Error, error = result.error) }
                    }
                }
            }
        }

        private fun io.github.rupakc.travelplanner.core.model.social.ProfileRelation.toFollowUi(): FollowUi =
            when {
                following -> FollowUi.Following
                pending -> FollowUi.Pending
                else -> FollowUi.None
            }

        private fun toggleFollow() {
            val current = _state.value
            if (current.working || current.isMe || current.profile == null) return
            _state.update { it.copy(working = true) }
            viewModelScope.launch {
                if (current.follow == FollowUi.None) {
                    when (val result = social.follow(handle)) {
                        is AppResult.Success -> {
                            val next = if (result.value.followState == FollowState.PENDING) FollowUi.Pending else FollowUi.Following
                            telemetry.emit(TelemetryEvents.SOCIAL_FOLLOW, mapOf("state" to if (next == FollowUi.Pending) "pending" else "following"))
                            _state.update { it.copy(working = false, follow = next) }
                        }

                        is AppResult.Failure -> {
                            _state.update { it.copy(working = false, message = ProfileMessage.Failed(result.error)) }
                        }
                    }
                } else {
                    when (val result = social.unfollow(handle)) {
                        is AppResult.Success -> {
                            telemetry.emit(TelemetryEvents.SOCIAL_FOLLOW, mapOf("state" to "none"))
                            _state.update { it.copy(working = false, follow = FollowUi.None) }
                        }

                        is AppResult.Failure -> {
                            _state.update { it.copy(working = false, message = ProfileMessage.Failed(result.error)) }
                        }
                    }
                }
            }
        }

        private fun block() {
            if (_state.value.working) return
            _state.update { it.copy(confirmBlock = false, working = true) }
            viewModelScope.launch {
                when (val result = social.block(handle)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.SOCIAL_BLOCK, emptyMap())
                        _state.update { it.copy(working = false, message = ProfileMessage.Blocked) }
                        _effects.send(PublicProfileEffect.Leave)
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(working = false, message = ProfileMessage.Failed(result.error)) }
                    }
                }
            }
        }

        private fun report(reason: ReportReason) {
            if (_state.value.reportSending) return
            _state.update { it.copy(reportSending = true, reportError = null) }
            viewModelScope.launch {
                when (val result = social.report(ReportTarget.PROFILE, handle, reason)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.SOCIAL_REPORT, mapOf("target" to ReportTarget.PROFILE.wire, "reason" to reason.wire))
                        _state.update { it.copy(reportSending = false, reporting = false, message = ProfileMessage.Reported) }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(reportSending = false, reportError = result.error) }
                    }
                }
            }
        }

        companion object {
            /** SavedStateHandle key of `ProfileRoute.handle`. */
            const val ARG_HANDLE = "handle"
        }
    }
