package io.github.rupakc.travelplanner.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.auth.SessionInfo

@Composable
internal fun SessionsRoute(onBack: () -> Unit, onSignedOut: () -> Unit, viewModel: SessionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it == SessionsEffect.SignedOut) onSignedOut() }
    }
    SessionsScreen(state, viewModel::onEvent, onBack)
}

@Composable
internal fun SessionsScreen(state: SessionsUiState, onEvent: (SessionsEvent) -> Unit, onBack: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.text()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onEvent(SessionsEvent.MessageShown)
        }
    }
    Scaffold(
        topBar = { TpTopBar(title = stringResource(R.string.account_sessions_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state.phase) {
            SessionsPhase.Loading -> SkeletonList(modifier.padding(TravelTheme.spacing.gutter), lines = 4)
            SessionsPhase.Error -> ErrorState(state.error?.text(), modifier.fillMaxSize(), onRetry = { onEvent(SessionsEvent.Retry) })
            SessionsPhase.List, SessionsPhase.Unsupported -> SessionsList(state, onEvent, modifier)
        }
    }
    state.confirmRevoke?.let { RevokeDialog(it, onEvent) }
    if (state.confirmSignOutAll) {
        AlertDialog(
            onDismissRequest = { onEvent(SessionsEvent.Dismiss) },
            title = { Text(stringResource(R.string.account_signout_all_title)) },
            text = { Text(stringResource(R.string.account_signout_all_body)) },
            confirmButton = { TextButton(onClick = { onEvent(SessionsEvent.ConfirmSignOutAll) }) { Text(stringResource(R.string.account_signout_all)) } },
            dismissButton = { TextButton(onClick = { onEvent(SessionsEvent.Dismiss) }) { Text(stringResource(R.string.account_cancel)) } },
        )
    }
}

@Composable
private fun SessionsList(state: SessionsUiState, onEvent: (SessionsEvent) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        if (state.phase == SessionsPhase.Unsupported) {
            item(key = "unsupported") { Text(stringResource(R.string.account_sessions_unsupported), style = TravelTheme.typography.bodyMedium) }
        } else if (state.sessions.isEmpty()) {
            item(key = "empty") { Text(stringResource(R.string.account_sessions_empty), style = TravelTheme.typography.bodyMedium) }
        }
        items(state.sessions, key = { it.id }) { session ->
            TpCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(session.deviceName ?: stringResource(R.string.account_sessions_unknown_device), style = TravelTheme.typography.titleSmall)
                        session.lastUsedAt?.let {
                            Text(stringResource(R.string.account_sessions_last_used, it.take(DATE_CHARS)), style = TravelTheme.typography.bodySmall)
                        }
                        if (session.current) Text(stringResource(R.string.account_sessions_this_device), style = TravelTheme.typography.labelMedium)
                    }
                    if (!session.current) {
                        TextButton(onClick = { onEvent(SessionsEvent.AskRevoke(session)) }, enabled = state.revoking != session.id) {
                            Text(stringResource(R.string.account_sessions_revoke))
                        }
                    }
                }
            }
        }
        item(key = "all") {
            TpButton(
                text = stringResource(R.string.account_signout_all),
                onClick = { onEvent(SessionsEvent.AskSignOutAll) },
                loading = state.busy,
                variant = TpButtonVariant.Destructive,
                modifier = Modifier.fillMaxWidth(),
                tid = "account.signout_all",
            )
        }
    }
}

@Composable
private fun RevokeDialog(session: SessionInfo, onEvent: (SessionsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SessionsEvent.Dismiss) },
        title = { Text(stringResource(R.string.account_sessions_revoke_title)) },
        text = { Text(session.deviceName ?: stringResource(R.string.account_sessions_unknown_device)) },
        confirmButton = { TextButton(onClick = { onEvent(SessionsEvent.ConfirmRevoke) }) { Text(stringResource(R.string.account_sessions_revoke)) } },
        dismissButton = { TextButton(onClick = { onEvent(SessionsEvent.Dismiss) }) { Text(stringResource(R.string.account_cancel)) } },
    )
}

private const val DATE_CHARS = 10
