package io.github.rupakc.travelplanner.feature.chat

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.feature.chat.ui.ChatScreen
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Chat route: wires [ChatViewModel] to [ChatScreen] and turns effects into navigation
 * (import → Search "From a link", My Plan, a saved plan) and snackbars (delete with Undo).
 */
@Composable
fun ChatRoute(
    threadId: Long?,
    onOpenImport: (url: String) -> Unit,
    onOpenMyPlan: () -> Unit,
    onOpenPlan: (planId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val openLink = rememberLinkOpener()
    val deleted = stringResource(R.string.chat_deleted)
    val undo = stringResource(R.string.chat_undo)
    val saveFailed = stringResource(R.string.chat_save_failed)
    val import by rememberUpdatedState(onOpenImport)
    val myPlan by rememberUpdatedState(onOpenMyPlan)
    val plan by rememberUpdatedState(onOpenPlan)

    LaunchedEffect(threadId) { viewModel.open(threadId) }
    LaunchedEffect(viewModel) {
        coroutineScope {
            viewModel.effectFlow.collect { effect ->
                when (effect) {
                    is ChatEffect.OpenImport -> {
                        import(effect.url)
                    }

                    ChatEffect.OpenMyPlan -> {
                        myPlan()
                    }

                    is ChatEffect.OpenPlan -> {
                        plan(effect.planId)
                    }

                    is ChatEffect.SaveFailed -> {
                        launch { snackbar.showSnackbar(effect.error.message ?: saveFailed) }
                    }

                    is ChatEffect.ThreadDeleted -> {
                        launch {
                            val result = snackbar.showSnackbar(deleted, actionLabel = undo, duration = SnackbarDuration.Short)
                            viewModel.onEvent(
                                if (result == SnackbarResult.ActionPerformed) {
                                    ChatUiEvent.UndoDelete(effect.threadId)
                                } else {
                                    ChatUiEvent.CommitDelete(effect.threadId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    ChatScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier, onOpenLink = openLink, snackbarHostState = snackbar)
}
