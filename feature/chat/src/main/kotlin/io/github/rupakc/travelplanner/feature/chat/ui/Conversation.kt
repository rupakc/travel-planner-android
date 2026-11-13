package io.github.rupakc.travelplanner.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpFilledIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.TpSmallFloatingActionButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchSpecs
import io.github.rupakc.travelplanner.core.ui.format.localUsd
import io.github.rupakc.travelplanner.feature.chat.ChatUiEvent
import io.github.rupakc.travelplanner.feature.chat.ChatUiState
import io.github.rupakc.travelplanner.feature.chat.R
import io.github.rupakc.travelplanner.feature.chat.ReplyAnnouncement
import io.github.rupakc.travelplanner.feature.chat.model.ChatTurn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Test tags used by the Compose tests. */
internal object ChatTags {
    const val MESSAGES = "chat_messages"
    const val INPUT = "chat_input"
    const val ANNOUNCER = "chat_announcer"
}

/**
 * The open conversation: messages (newest at the bottom; the list sticks to the latest
 * message unless the user scrolled up, then "Jump to latest" appears), the polite
 * announcement of finished replies, and the composer.
 */
@Composable
internal fun ConversationPane(state: ChatUiState, onEvent: (ChatUiEvent) -> Unit, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val plan =
        remember(state.selections, onOpenLink, state.watchAvailable, state.watchTrip) {
            PlanCallbacks(
                state.selections,
                { onEvent(ChatUiEvent.TogglePlanItem(it)) },
                onOpenLink,
                onWatch = if (state.watchAvailable) ({ onEvent(ChatUiEvent.WatchItem(it)) }) else null,
                canWatch = { QuickWatchSpecs.matchesTrip(it, state.watchTrip) },
            )
        }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                // A reply streaming into a just-created thread shows at once, not behind a skeleton.
                state.loadingMessages && state.streaming == null -> SkeletonList(Modifier.padding(TravelTheme.spacing.gutter), lines = SKELETON_LINES)

                state.isEmptyConversation -> EmptyConversation(onSend = { onEvent(ChatUiEvent.Send(it)) })

                else -> MessageList(state, plan, onEvent)
            }
        }
        ReplyAnnouncer(state.announcement, onShown = { onEvent(ChatUiEvent.AnnouncementShown(it)) })
        Composer(isStreaming = state.isStreaming, onSend = { onEvent(ChatUiEvent.Send(it)) }, onStop = { onEvent(ChatUiEvent.Stop) })
    }
}

@Composable
private fun MessageList(state: ChatUiState, plan: PlanCallbacks, onEvent: (ChatUiEvent) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Reverse layout: index 0 is the newest message at the bottom; growth of a streaming
    // reply stays anchored to the bottom without per-token scrolling.
    var stickToBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) }
            .collect { (scrolling, atBottom) -> if (scrolling || atBottom) stickToBottom = atBottom }
    }
    val newestKey = state.streaming?.let(::itemKey) ?: state.turns.lastOrNull()?.let(::itemKey)
    LaunchedEffect(newestKey) { if (stickToBottom) listState.scrollToItem(0) }
    // Also inside a long newest item (a trip plan scrolled up to Day 1).
    val threshold = with(LocalDensity.current) { TravelTheme.spacing.minTouchTarget.roundToPx() }
    val showJump by remember(threshold) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > threshold }
    }
    val reversed = remember(state.turns) { state.turns.asReversed() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().testTag(ChatTags.MESSAGES),
            contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xl, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.streaming?.let { turn ->
                item(key = itemKey(turn), contentType = "assistant") { Turn(turn, plan, state.savingPlanTurnId == turn.id, onEvent) }
            }
            items(reversed, key = ::itemKey, contentType = { if (it.isUser) "user" else "assistant" }) { turn ->
                Turn(turn, plan, state.savingPlanTurnId == turn.id, onEvent)
            }
        }
        if (showJump) {
            val label = stringResource(R.string.chat_jump_latest)
            TpSmallFloatingActionButton(
                onClick = {
                    stickToBottom = true
                    scope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(TravelTheme.spacing.gutter).semantics { contentDescription = label },
                tid = "chat.conversation_small_floating_action_button",
            ) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
        }
    }
}

private fun itemKey(turn: ChatTurn): Any = if (turn.id == ChatTurn.PENDING_ID) "pending" else turn.id

@Composable
private fun Turn(turn: ChatTurn, plan: PlanCallbacks, saving: Boolean, onEvent: (ChatUiEvent) -> Unit) {
    val m = Modifier.widthIn(max = ChatDimens.MaxConversationWidth).fillMaxWidth()
    if (turn.isUser) UserBubble(turn, m) else AssistantMessage(turn, plan, saving, onEvent, m)
}

/** Polite live region: announces each finished reply once (spec §9). */
@Composable
private fun ReplyAnnouncer(announcement: ReplyAnnouncement?, onShown: (Int) -> Unit) {
    val announced =
        when (announcement) {
            is ReplyAnnouncement.Text -> {
                stringResource(R.string.chat_reply_announce, announcement.text)
            }

            is ReplyAnnouncement.PlanReady -> {
                announcement.destination?.takeIf(String::isNotBlank)?.let { stringResource(R.string.chat_plan_ready_announce, it) }
                    ?: stringResource(R.string.chat_plan_ready_announce_generic)
            }

            is ReplyAnnouncement.Failed -> {
                stringResource(R.string.chat_reply_failed_announce)
            }

            null -> {
                ""
            }
        }
    // Each reply is announced once: the last read seq survives leaving and re-entering the screen,
    // and the text is cleared afterwards so swipe navigation doesn't land on a stale announcement.
    var lastSeq by rememberSaveable { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    val shown by rememberUpdatedState(onShown)
    LaunchedEffect(announcement?.seq) {
        val seq = announcement?.seq ?: return@LaunchedEffect
        if (seq == lastSeq || announced.isEmpty()) return@LaunchedEffect
        lastSeq = seq
        text = announced
        delay(ANNOUNCE_CLEAR_MILLIS)
        text = ""
        shown(seq)
    }
    Box(
        Modifier
            .size(ChatDimens.A11yNode)
            .testTag(ChatTags.ANNOUNCER)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
    )
}

/** Empty state (web): plane tile, title, intro and the four starter prompts. */
@Composable
private fun EmptyConversation(onSend: (String) -> Unit) {
    val starters =
        listOf(
            stringResource(R.string.chat_starter_1, localUsd(STARTER_BUDGET_USD)),
            stringResource(R.string.chat_starter_2),
            stringResource(R.string.chat_starter_3),
            stringResource(R.string.chat_starter_4),
        )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(ChatDimens.EmptyLogo).clip(TravelShape.ItemCard).background(TravelTheme.colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_chat_plane),
                contentDescription = null,
                tint = TravelTheme.colors.onPrimary,
                modifier = Modifier.size(ChatDimens.EmptyLogoIcon),
            )
        }
        Text(
            stringResource(R.string.chat_title, stringResource(io.github.rupakc.travelplanner.core.ui.R.string.brand_name)),
            style = TravelTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.chat_message),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = ChatDimens.StarterMaxWidth),
        )
        Column(Modifier.widthIn(max = ChatDimens.StarterMaxWidth), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            starters.forEach { prompt ->
                TpCard(modifier = Modifier.fillMaxWidth(), onClick = { onSend(prompt) }) {
                    Text(prompt, style = TravelTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** IME-aware composer: multi-line input, Send (IME action or button) and Stop while streaming. */
@Composable
internal fun Composer(isStreaming: Boolean, onSend: (String) -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank() && !isStreaming) {
            onSend(text)
            text = ""
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(TravelTheme.colors.surface)
            .padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.widthIn(max = ChatDimens.MaxConversationWidth).fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        ) {
            TpOutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).heightIn(max = ChatDimens.ComposerMaxHeight).testTag(ChatTags.INPUT),
                placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                enabled = !isStreaming,
                shape = TravelShape.ItemCard,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                maxLines = COMPOSER_MAX_LINES,
                tid = "chat.chat_input_placeholder",
            )
            if (isStreaming) {
                TpFilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = TravelTheme.colors.error, contentColor = TravelTheme.colors.onError),
                    modifier = Modifier.size(TravelTheme.spacing.minTouchTarget),
                    tid = "chat.conversation_filled_icon_button",
                ) {
                    Icon(painterResource(R.drawable.ic_chat_stop), contentDescription = stringResource(R.string.chat_stop))
                }
            } else {
                TpFilledIconButton(
                    onClick = send,
                    enabled = text.isNotBlank(),
                    modifier = Modifier.size(TravelTheme.spacing.minTouchTarget),
                    tid = "chat.chat_send",
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                }
            }
        }
        Text(
            stringResource(R.string.chat_disclaimer),
            style = TravelTheme.typography.labelSmall,
            color = TravelTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TravelTheme.spacing.xs),
        )
    }
}

private const val SKELETON_LINES = 4
private const val ANNOUNCE_CLEAR_MILLIS = 5_000L
private const val COMPOSER_MAX_LINES = 6

/** The budget in the first starter prompt (web copy: "$2000"). */
private const val STARTER_BUDGET_USD = 2000.0
