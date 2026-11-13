package io.github.rupakc.travelplanner.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.R
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.network.EmailVerificationGate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the "send link" action stands. */
internal sealed interface VerifySend {
    data object Idle : VerifySend

    data object Sending : VerifySend

    data object Sent : VerifySend

    /** [detail] is the backend's own text; [noEmail] hides the action (nothing to send to). */
    data class Failed(val detail: String?, val noEmail: Boolean) : VerifySend
}

internal data class VerifyBannerState(val visible: Boolean = false, val send: VerifySend = VerifySend.Idle)

/**
 * The "confirm your email" banner (web `VerifyBanner`): shown to a signed-in user whose account is unconfirmed
 * (`account_verified == false`) or after an AI route answered `403 email_unverified`. An older backend that sends no
 * `account_verified` counts as verified. Dismissal lasts until the app restarts or another user signs in.
 */
@HiltViewModel
internal class VerifyBannerViewModel
    @Inject
    constructor(
        private val auth: AuthRepository,
        private val account: AccountRepository,
        private val gate: EmailVerificationGate,
        private val telemetry: Telemetry,
        private val language: AppLanguage,
    ) : ViewModel() {
        private val sendState = MutableStateFlow<VerifySend>(VerifySend.Idle)
        private val dismissedFor = MutableStateFlow<String?>(null)
        private var reportedShown = false

        private val userKey = auth.session.map { it?.user?.username }.distinctUntilChanged()

        val state: StateFlow<VerifyBannerState> =
            combine(auth.session, gate.blocked, dismissedFor, sendState) { session, blocked, dismissed, send ->
                val user = session?.user
                val show = user != null && (!user.accountVerified || blocked) && dismissed != user.username
                VerifyBannerState(show, send)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VerifyBannerState())

        init {
            viewModelScope.launch {
                // A confirmed account (this device or another) lowers the gate; a new user starts clean.
                auth.session.collect { session -> if (session?.user?.accountVerified == true) gate.clear() }
            }
            viewModelScope.launch {
                userKey.collect {
                    sendState.value = VerifySend.Idle
                    gate.clear()
                }
            }
            viewModelScope.launch {
                gate.blocked.collect { blocked ->
                    // The stored user may be stale (confirmed elsewhere): refresh once; a confirmed user clears the gate.
                    if (blocked) (auth.refreshUser() as? AppResult.Success)?.let { if (it.value.accountVerified) gate.clear() }
                }
            }
            viewModelScope.launch {
                state.collect { s ->
                    if (s.visible && !reportedShown) {
                        reportedShown = true
                        telemetry.emit(TelemetryEvents.QUOTA_BLOCK, mapOf("bucket" to "search", "reason" to "unverified"))
                    }
                }
            }
        }

        fun send() {
            if (sendState.value == VerifySend.Sending) return
            sendState.value = VerifySend.Sending
            telemetry.emit(TelemetryEvents.ACCOUNT_ACTION, mapOf("action" to "email_verify_request"))
            viewModelScope.launch {
                sendState.value =
                    when (val result = account.requestEmailVerification(language.current())) {
                        is AppResult.Success -> VerifySend.Sent
                        is AppResult.Failure -> VerifySend.Failed(result.error.userFacingDetail(), result.error.isNoEmail())
                    }
            }
        }

        fun dismiss() {
            viewModelScope.launch { dismissedFor.value = auth.session.first()?.user?.username }
        }

        private fun AppError.isNoEmail(): Boolean =
            when (this) {
                is AppError.Client -> code
                is AppError.Forbidden -> code
                else -> null
            } == "no_email"

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** Slim banner above the tab content; renders nothing when the account is confirmed. */
@Composable
internal fun VerifyBanner(modifier: Modifier = Modifier, viewModel: VerifyBannerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.visible) return
    val send = state.send
    Column(
        modifier
            .fillMaxWidth()
            .background(TravelTheme.colors.tertiaryContainer, TravelShape.Input)
            .padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
    ) {
        Text(
            stringResource(if (send == VerifySend.Sent) R.string.verify_banner_sent else R.string.verify_banner_text),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onTertiaryContainer,
        )
        if (send is VerifySend.Failed) {
            Text(
                send.detail ?: stringResource(R.string.verify_banner_error),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.error,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            val canSend = send != VerifySend.Sent && !(send is VerifySend.Failed && send.noEmail)
            if (canSend) {
                TpButton(
                    stringResource(R.string.verify_banner_send),
                    onClick = viewModel::send,
                    variant = TpButtonVariant.Tonal,
                    loading = send == VerifySend.Sending,
                    tid = "verify_banner.send",
                )
            }
            TpButton(
                stringResource(R.string.verify_banner_dismiss),
                onClick = viewModel::dismiss,
                variant = TpButtonVariant.Text,
                tid = "verify_banner.dismiss",
            )
        }
    }
}
