package io.github.rupakc.travelplanner.feature.share.create

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.ShareCardStore
import io.github.rupakc.travelplanner.core.data.repository.SharesRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.share.CreatedShare
import io.github.rupakc.travelplanner.core.model.share.ShareDates
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.share.ShareOptions
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A one-line result shown in the dialog's live region. */
internal sealed interface ShareCreateMessage {
    data object Created : ShareCreateMessage

    data object Updated : ShareCreateMessage

    data object Copied : ShareCreateMessage

    data object Revoked : ShareCreateMessage

    data object Offline : ShareCreateMessage

    /** The card could not be fetched: the link alone goes to the share sheet. */
    data object CardUnavailable : ShareCreateMessage

    data object ShareFailed : ShareCreateMessage

    /** [error] carries the backend text and `error_code`. [revoke] = the failing call was the revoke. */
    data class Failed(val error: AppError, val revoke: Boolean = false) : ShareCreateMessage
}

@Immutable
internal data class ShareCreateUiState(
    val kind: ShareKind = ShareKind.PLAN,
    val options: ShareOptions = ShareOptions.PRIVATE,
    /** The link answered by the server; null until "Create link". */
    val created: CreatedShare? = null,
    /** The options differ from the ones the server has: the link must be updated before it is shared. */
    val dirty: Boolean = false,
    /** Create / update in flight. */
    val working: Boolean = false,
    /** Preparing the card for the share sheet. */
    val sharing: Boolean = false,
    val confirmRevoke: Boolean = false,
    val revoking: Boolean = false,
    val message: ShareCreateMessage? = null,
) {
    val busy: Boolean get() = working || sharing || revoking
    val canShare: Boolean get() = created != null && !dirty && !busy
}

internal sealed interface ShareCreateEvent {
    data class SetDates(val dates: ShareDates) : ShareCreateEvent

    data class SetShowOrigin(val on: Boolean) : ShareCreateEvent

    data class SetShowName(val on: Boolean) : ShareCreateEvent

    /** "Create link" / "Update link". */
    data object Submit : ShareCreateEvent

    data object Share : ShareCreateEvent

    data object Copy : ShareCreateEvent

    data object AskRevoke : ShareCreateEvent

    data object CancelRevoke : ShareCreateEvent

    data object ConfirmRevoke : ShareCreateEvent

    data object MessageShown : ShareCreateEvent

    /** The platform could not open a share sheet. */
    data object SendFailed : ShareCreateEvent
}

/** Platform work the screen performs once. */
internal sealed interface ShareCreateEffect {
    /** Open the system share sheet with the link and, when it was fetched, the story card PNG. */
    data class Send(val kind: ShareKind, val url: String, val card: File?) : ShareCreateEffect

    data class CopyLink(val url: String) : ShareCreateEffect
}

/**
 * The share dialog: choose what the link shows (private by default), create or update it (`POST /shares`, idempotent per
 * trip so a second tap replaces the options), then share it with the card image, copy it, or stop sharing it.
 * Opened for one [ShareKind] (+ plan id) from `ShareCreateRoute`. Telemetry carries enum values only.
 */
@HiltViewModel
internal class ShareCreateViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val shares: SharesRepository,
        private val cards: ShareCardStore,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val kind = ShareKind.from(savedStateHandle.get<String>(KIND_ARG))
        private val planId: Long? = savedStateHandle.get<Long>(PLAN_ARG)
        private val sessionId: String? = savedStateHandle.get<String>(SESSION_ARG)
        private val _state = MutableStateFlow(ShareCreateUiState(kind = kind))
        val state: StateFlow<ShareCreateUiState> = _state.asStateFlow()
        private val _effects = Channel<ShareCreateEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        init {
            kindProp()?.let { telemetry.emit(TelemetryEvents.SHARE_OPEN, mapOf("kind" to it)) }
        }

        fun onEvent(event: ShareCreateEvent) {
            when (event) {
                is ShareCreateEvent.SetDates -> edit { it.copy(dates = event.dates.wire) }
                is ShareCreateEvent.SetShowOrigin -> edit { it.copy(showOrigin = event.on) }
                is ShareCreateEvent.SetShowName -> edit { it.copy(showName = event.on) }
                ShareCreateEvent.Submit -> submit()
                ShareCreateEvent.Share -> send()
                ShareCreateEvent.Copy -> copy()
                ShareCreateEvent.AskRevoke -> _state.update { if (it.created != null && !it.busy) it.copy(confirmRevoke = true) else it }
                ShareCreateEvent.CancelRevoke -> _state.update { it.copy(confirmRevoke = false) }
                ShareCreateEvent.ConfirmRevoke -> revoke()
                ShareCreateEvent.MessageShown -> _state.update { it.copy(message = null) }
                ShareCreateEvent.SendFailed -> _state.update { it.copy(message = ShareCreateMessage.ShareFailed) }
            }
        }

        private fun kindProp(): String? = kind.wire

        private fun edit(change: (ShareOptions) -> ShareOptions) =
            _state.update {
                if (it.busy) {
                    it
                } else {
                    val next = change(it.options)
                    it.copy(options = next, dirty = it.created != null && next != it.created.options, message = null)
                }
            }

        private fun submit() {
            val current = _state.value
            if (current.busy) return
            val updating = current.created != null
            _state.update { it.copy(working = true, message = null) }
            viewModelScope.launch {
                when (val result = shares.create(kind, planId, current.options, sessionId)) {
                    is AppResult.Success -> {
                        kindProp()?.let {
                            telemetry.emit(TelemetryEvents.SHARE_CREATE, mapOf("kind" to it, "dates" to current.options.dates))
                        }
                        _state.update {
                            it.copy(
                                working = false,
                                created = result.value,
                                dirty = false,
                                message = if (updating) ShareCreateMessage.Updated else ShareCreateMessage.Created,
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        val error = result.error
                        _state.update {
                            it.copy(working = false, message = if (error is AppError.Network) ShareCreateMessage.Offline else ShareCreateMessage.Failed(error))
                        }
                    }
                }
            }
        }

        /** Ruling R5: the story card goes to chat apps with the link; when it cannot be fetched the link alone is shared. */
        private fun send() {
            val link = _state.value.takeIf { it.canShare }?.created ?: return
            _state.update { it.copy(sharing = true, message = null) }
            viewModelScope.launch {
                val cardUrl = link.cardUrl.story?.takeIf { it.isNotBlank() }
                val card = cardUrl?.let { (cards.download(it) as? AppResult.Success)?.value }
                _state.update { it.copy(sharing = false, message = if (card == null) ShareCreateMessage.CardUnavailable else null) }
                emitAction(TARGET_NATIVE)
                _effects.send(ShareCreateEffect.Send(kind, link.url, card))
            }
        }

        private fun copy() {
            val link = _state.value.takeIf { it.canShare }?.created ?: return
            emitAction(TARGET_COPY)
            _state.update { it.copy(message = ShareCreateMessage.Copied) }
            _effects.trySend(ShareCreateEffect.CopyLink(link.url))
        }

        private fun revoke() {
            val link = _state.value.created ?: return
            if (_state.value.busy) return
            _state.update { it.copy(confirmRevoke = false, revoking = true, message = null) }
            viewModelScope.launch {
                when (val result = shares.revoke(link.token)) {
                    is AppResult.Success -> {
                        kindProp()?.let { telemetry.emit(TelemetryEvents.SHARE_REVOKE, mapOf("kind" to it)) }
                        _state.update { it.copy(revoking = false, created = null, dirty = false, message = ShareCreateMessage.Revoked) }
                    }

                    is AppResult.Failure -> {
                        val error = result.error
                        _state.update {
                            it.copy(
                                revoking = false,
                                message = if (error is AppError.Network) ShareCreateMessage.Offline else ShareCreateMessage.Failed(error, revoke = true),
                            )
                        }
                    }
                }
            }
        }

        private fun emitAction(target: String) {
            kindProp()?.let { telemetry.emit(TelemetryEvents.SHARE_ACTION, mapOf("kind" to it, "target" to target)) }
        }

        companion object {
            /** Type-safe navigation stores `ShareCreateRoute` fields under their property names. */
            const val KIND_ARG = "kind"
            const val PLAN_ARG = "planId"
            const val SESSION_ARG = "sessionId"
            private const val TARGET_NATIVE = "native"
            private const val TARGET_COPY = "copy"
        }
    }
