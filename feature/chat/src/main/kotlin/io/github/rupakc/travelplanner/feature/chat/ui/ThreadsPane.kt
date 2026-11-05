package io.github.rupakc.travelplanner.feature.chat.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import io.github.rupakc.travelplanner.core.data.repository.ChatThread
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.chat.ChatUiEvent
import io.github.rupakc.travelplanner.feature.chat.R

/**
 * Saved conversations (this device): search by title, new chat, open, and per-thread
 * Rename / Delete (undo via snackbar). [onThreadOpened] lets the compact layout switch
 * back to the conversation.
 */
@Composable
internal fun ThreadsPane(
    threads: List<ChatThread>,
    query: String,
    currentThreadId: Long?,
    nowMillis: Long,
    onEvent: (ChatUiEvent) -> Unit,
    onRename: (ChatThread) -> Unit,
    onThreadOpened: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(TravelTheme.colors.surfaceContainerLow)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.chat_threads), style = TravelTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            TpButton(
                text = stringResource(R.string.chat_new),
                onClick = {
                    onEvent(ChatUiEvent.NewChat)
                    onThreadOpened()
                },
                variant = TpButtonVariant.Tonal,
                leadingIcon = Icons.Filled.Add,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { onEvent(ChatUiEvent.SearchThreads(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.gutter),
            placeholder = { Text(stringResource(R.string.chat_threads_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onEvent(ChatUiEvent.SearchThreads("")) }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.chat_threads_clear_search))
                    }
                }
            },
            singleLine = true,
            shape = TravelShape.Input,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        if (threads.isEmpty()) {
            EmptyState(
                title = if (query.isBlank()) stringResource(R.string.chat_threads_empty) else stringResource(R.string.chat_threads_no_match, query),
                message = if (query.isBlank()) stringResource(R.string.chat_threads_empty_message) else null,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = TravelTheme.spacing.sm)) {
                items(threads, key = { it.id }, contentType = { "thread" }) { thread ->
                    ThreadRow(
                        thread = thread,
                        selected = thread.id == currentThreadId,
                        nowMillis = nowMillis,
                        onOpen = {
                            onEvent(ChatUiEvent.SelectThread(thread.id))
                            onThreadOpened()
                        },
                        onRename = { onRename(thread) },
                        onDelete = { onEvent(ChatUiEvent.DeleteThread(thread.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: ChatThread, selected: Boolean, nowMillis: Long, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val background = if (selected) TravelTheme.colors.secondaryContainer else TravelTheme.colors.surfaceContainerLow
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TravelTheme.spacing.minTouchTarget)
            .background(background)
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = TravelTheme.spacing.gutter, top = TravelTheme.spacing.sm, bottom = TravelTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(thread.title, style = TravelTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (thread.updatedAtMillis > 0) {
                Text(
                    DateUtils.getRelativeTimeSpanString(thread.updatedAtMillis, nowMillis, DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.chat_thread_menu, thread.title))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_rename)) }, onClick = {
                    menu = false
                    onRename()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_delete)) }, onClick = {
                    menu = false
                    onDelete()
                })
            }
        }
    }
}
