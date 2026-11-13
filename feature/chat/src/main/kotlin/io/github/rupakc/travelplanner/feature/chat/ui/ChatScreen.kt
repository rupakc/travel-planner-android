package io.github.rupakc.travelplanner.feature.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.rupakc.travelplanner.core.data.repository.ChatThread
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.chat.ChatUiEvent
import io.github.rupakc.travelplanner.feature.chat.ChatUiState
import io.github.rupakc.travelplanner.feature.chat.R

/**
 * Chat, adaptive: from [ChatDimens.ExpandedWidth] the threads list and the conversation
 * sit side by side; below it the conversation fills the screen and "Chats" opens the
 * threads list in its place (Back returns).
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    nowMillis: Long = System.currentTimeMillis(),
) {
    var showThreads by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChatThread?>(null) }
    Box(modifier.fillMaxSize().imePadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= ChatDimens.ExpandedWidth
            val threads: @Composable (Modifier, () -> Unit) -> Unit = { m, opened ->
                ThreadsPane(state.threads, state.threadQuery, state.currentThreadId, nowMillis, onEvent, { renaming = it }, opened, m)
            }
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    threads(Modifier.width(ChatDimens.ThreadsPaneWidth).fillMaxHeight()) {}
                    VerticalDivider()
                    ConversationColumn(state, onEvent, onOpenLink, showThreadsButton = false, onShowThreads = {}, Modifier.weight(1f))
                }
            } else if (showThreads) {
                BackHandler { showThreads = false }
                threads(Modifier.fillMaxSize()) { showThreads = false }
            } else {
                ConversationColumn(state, onEvent, onOpenLink, showThreadsButton = true, onShowThreads = { showThreads = true }, Modifier.fillMaxSize())
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = TravelTheme.spacing.xxl))
    }
    renaming?.let { thread ->
        RenameDialog(thread, onDismiss = { renaming = null }) { title ->
            onEvent(ChatUiEvent.RenameThread(thread.id, title))
            renaming = null
        }
    }
    if (state.confirmReplaceTurnId != null) {
        AlertDialog(
            onDismissRequest = { onEvent(ChatUiEvent.DismissReplacePlan) },
            title = { Text(stringResource(R.string.chat_replace_title)) },
            text = { Text(stringResource(R.string.chat_replace_message)) },
            confirmButton = {
                TpTextButton(onClick = {
                    onEvent(ChatUiEvent.ConfirmReplacePlan)
                }, tid = "chat.chat_replace_confirm") { Text(stringResource(R.string.chat_replace_confirm)) }
            },
            dismissButton = {
                TpTextButton(onClick = { onEvent(ChatUiEvent.DismissReplacePlan) }, tid = "chat.chat_cancel") { Text(stringResource(R.string.chat_cancel)) }
            },
        )
    }
}

@Composable
private fun ConversationColumn(
    state: ChatUiState,
    onEvent: (ChatUiEvent) -> Unit,
    onOpenLink: (String) -> Unit,
    showThreadsButton: Boolean,
    onShowThreads: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        ConversationHeader(state, onEvent, showThreadsButton, onShowThreads)
        HorizontalDivider()
        ConversationPane(state, onEvent, onOpenLink, Modifier.weight(1f))
    }
}

/** Thread title, Chats (compact), New chat and the My Plan button with its item count. */
@Composable
private fun ConversationHeader(state: ChatUiState, onEvent: (ChatUiEvent) -> Unit, showThreadsButton: Boolean, onShowThreads: () -> Unit) {
    Surface(color = TravelTheme.colors.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showThreadsButton) {
                TpIconButton(onClick = onShowThreads, tid = "chat.chat_threads_show") {
                    Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.chat_threads_show))
                }
            }
            Text(
                state.currentTitle ?: stringResource(R.string.chat_new),
                style = TravelTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = TravelTheme.spacing.sm).semantics { heading() },
            )
            TpIconButton(onClick = { onEvent(ChatUiEvent.NewChat) }, enabled = !state.isStreaming && state.currentThreadId != null, tid = "chat.chat_new") {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_new))
            }
            val planA11y = pluralStringResource(R.plurals.chat_my_plan_a11y, state.planCount, state.planCount)
            BadgedBox(
                badge = { if (state.planCount > 0) Badge(Modifier.clearAndSetSemantics { }) { Text("${state.planCount}") } },
            ) {
                TpButton(
                    text = stringResource(R.string.chat_my_plan),
                    onClick = { onEvent(ChatUiEvent.OpenMyPlan) },
                    variant = TpButtonVariant.Text,
                    modifier = Modifier.semantics { contentDescription = planA11y },
                    tid = "chat.chat_my_plan",
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(thread: ChatThread, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by rememberSaveable(thread.id) { mutableStateOf(thread.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_rename_title)) },
        text = {
            TpOutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.chat_rename_label)) },
                singleLine = true,
                tid = "chat.chat_rename_label",
            )
        },
        confirmButton = {
            TpTextButton(onClick = { onSave(title) }, enabled = title.isNotBlank(), tid = "chat.chat_save") { Text(stringResource(R.string.chat_save)) }
        },
        dismissButton = { TpTextButton(onClick = onDismiss, tid = "chat.chat_cancel_2") { Text(stringResource(R.string.chat_cancel)) } },
    )
}
