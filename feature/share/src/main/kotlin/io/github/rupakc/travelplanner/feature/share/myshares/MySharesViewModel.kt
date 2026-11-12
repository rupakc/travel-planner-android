package io.github.rupakc.travelplanner.feature.share.myshares

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.ShareCardStore
import io.github.rupakc.travelplanner.core.data.repository.SharesRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.share.LegacyPlanShare
import io.github.rupakc.travelplanner.core.model.share.ShareErrorCode
import io.github.rupakc.travelplanner.core.model.share.ShareRow
import io.github.rupakc.travelplanner.core.model.share.shareErrorCode
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class MySharesPhase { Loading, Error, Content }

/** A row the user acts on: one of their Phase 4 shares or a legacy plan share. */
internal sealed interface RevokeTarget {
    val url: String
    val key: String
    val kindWire: String?

    data class Share(val row: ShareRow) : RevokeTarget {
        override val url: String get() = row.url
        override val key: String get() = row.token
        override val kindWire: String? get() = row.shareKind.wire
    }

    data class Legacy(val share: LegacyPlanShare) : RevokeTarget {
        override val url: String get() = share.url
        override val key: String get() = "legacy-${share.planId}"
        override val kindWire: String get() = "plan"
    }
}

internal sealed interface MySharesMessage {
    data object Copied : MySharesMessage

    data object Revoked : MySharesMessage

    data object Offline : MySharesMessage

    data object CardUnavailable : MySharesMessage

    data object ShareFailed : MySharesMessage

    /** [error] carries the backend text (shown as is, e.g. a 429). */
    data class Failed(val error: AppError) : MySharesMessage
}

@Immutable
internal data class MySharesUiState(
    val phase: MySharesPhase = MySharesPhase.Loading,
    val shares: List<ShareRow> = emptyList(),
    val legacy: List<LegacyPlanShare> = emptyList(),
    val error: AppError? = null,
    /** The row waiting for "Stop sharing?" confirmation. */
    val confirm: RevokeTarget? = null,
    /** Key of the row with a revoke in flight. */
    val revokingKey: String? = null,
    /** Token or key of the row whose card is being prepared for the share sheet. */
    val sharingToken: String? = null,
    val message: MySharesMessage? = null,
) {
    val isEmpty: Boolean get() = shares.isEmpty() && legacy.isEmpty()
    val busy: Boolean get() = revokingKey != null || sharingToken != null
}

internal sealed interface MySharesEvent {
    data object Retry : MySharesEvent

    data class Copy(val url: String, val kind: String?) : MySharesEvent

    data class ShareAgain(val target: RevokeTarget) : MySharesEvent

    data class AskRevoke(val target: RevokeTarget) : MySharesEvent

    data object ConfirmRevoke : MySharesEvent

    data object Dismiss : MySharesEvent

    data object MessageShown : MySharesEvent

    /** The platform could not open a share sheet. */
    data object SendFailed : MySharesEvent
}

internal sealed interface MySharesEffect {
    data class CopyLink(val url: String) : MySharesEffect

    /** Open the system share sheet with [url] and, when it was fetched, the story [card]. */
    data class Send(val url: String, val kind: String?, val card: File?) : MySharesEffect
}

/**
 * "My shares": the caller's own share links (`GET /shares`, newest first) plus the plans that still have a legacy share token.
 * Copy, share again (R5: story card + link, link alone when the card cannot be fetched) and revoke after a confirmation.
 * Revoking a legacy token goes through the plan (`DELETE /plans/{id}/share`). Telemetry carries enum values only.
 */
@HiltViewModel
internal class MySharesViewModel
    @Inject
    constructor(
        private val shares: SharesRepository,
        private val plans: PlansRepository,
        private val cards: ShareCardStore,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MySharesUiState())
        val state: StateFlow<MySharesUiState> = _state.asStateFlow()
        private val _effects = Channel<MySharesEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: MySharesEvent) {
            when (event) {
                MySharesEvent.Retry -> load()
                is MySharesEvent.Copy -> copy(event)
                is MySharesEvent.ShareAgain -> shareAgain(event.target)
                is MySharesEvent.AskRevoke -> _state.update { if (it.busy) it else it.copy(confirm = event.target) }
                MySharesEvent.ConfirmRevoke -> revoke()
                MySharesEvent.Dismiss -> _state.update { it.copy(confirm = null) }
                MySharesEvent.MessageShown -> _state.update { it.copy(message = null) }
                MySharesEvent.SendFailed -> _state.update { it.copy(message = MySharesMessage.ShareFailed) }
            }
        }

        private fun load() {
            _state.update { it.copy(phase = MySharesPhase.Loading, error = null) }
            viewModelScope.launch {
                when (val result = shares.list()) {
                    is AppResult.Success -> {
                        _state.update {
                            it.copy(phase = MySharesPhase.Content, shares = result.value.shares, legacy = result.value.legacyPlanShares)
                        }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(phase = MySharesPhase.Error, error = result.error) }
                    }
                }
            }
        }

        private fun copy(event: MySharesEvent.Copy) {
            emitAction(event.kind, TARGET_COPY)
            _state.update { it.copy(message = MySharesMessage.Copied) }
            _effects.trySend(MySharesEffect.CopyLink(event.url))
        }

        private fun shareAgain(target: RevokeTarget) {
            if (_state.value.busy) return
            _state.update { it.copy(sharingToken = target.key, message = null) }
            viewModelScope.launch {
                val cardUrl = (target as? RevokeTarget.Share)?.let { storyCardUrl(it.row.url, it.row.token) }
                val card = cardUrl?.let { (cards.download(it) as? AppResult.Success)?.value }
                _state.update { it.copy(sharingToken = null, message = if (cardUrl != null && card == null) MySharesMessage.CardUnavailable else null) }
                emitAction(target.kindWire, TARGET_NATIVE)
                _effects.send(MySharesEffect.Send(target.url, target.kindWire, card))
            }
        }

        private fun revoke() {
            val target = _state.value.confirm ?: return
            if (_state.value.busy) return
            _state.update { it.copy(confirm = null, revokingKey = target.key, message = null) }
            viewModelScope.launch {
                val result =
                    when (target) {
                        is RevokeTarget.Share -> shares.revoke(target.row.token)
                        is RevokeTarget.Legacy -> plans.unshare(target.share.planId)
                    }
                // A share that is already gone is the outcome the user wanted.
                val gone =
                    result is AppResult.Success ||
                        (result as? AppResult.Failure)?.error?.shareErrorCode() == ShareErrorCode.SHARE_NOT_FOUND
                if (gone) {
                    target.kindWire?.let { telemetry.emit(TelemetryEvents.SHARE_REVOKE, mapOf("kind" to it)) }
                    _state.update {
                        it.copy(
                            revokingKey = null,
                            shares = it.shares.filterNot { row -> target is RevokeTarget.Share && row.token == target.row.token },
                            legacy = it.legacy.filterNot { row -> target is RevokeTarget.Legacy && row.planId == target.share.planId },
                            message = MySharesMessage.Revoked,
                        )
                    }
                } else {
                    val error = (result as AppResult.Failure).error
                    _state.update {
                        it.copy(revokingKey = null, message = if (error is AppError.Network) MySharesMessage.Offline else MySharesMessage.Failed(error))
                    }
                }
            }
        }

        private fun emitAction(kind: String?, target: String) {
            if (kind != null) telemetry.emit(TelemetryEvents.SHARE_ACTION, mapOf("kind" to kind, "target" to target))
        }

        private companion object {
            const val TARGET_NATIVE = "native"
            const val TARGET_COPY = "copy"
        }
    }
