package io.github.rupakc.travelplanner.feature.chat.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.chat.parseFollowUps
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.ui.titleRes
import io.github.rupakc.travelplanner.feature.chat.ChatUiEvent
import io.github.rupakc.travelplanner.feature.chat.R
import io.github.rupakc.travelplanner.feature.chat.canRetry
import io.github.rupakc.travelplanner.feature.chat.model.ChatSectionStatus
import io.github.rupakc.travelplanner.feature.chat.model.ChatTurn
import io.github.rupakc.travelplanner.feature.chat.model.TurnError
import io.github.rupakc.travelplanner.feature.chat.showsErrorCopy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The user's message: a teal bubble at the end edge. */
@Composable
internal fun UserBubble(turn: ChatTurn, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.chat_user_message, turn.content)
    val shape = RoundedCornerShape(ChatDimens.BubbleCorner, ChatDimens.BubbleCorner, ChatDimens.BubbleTail, ChatDimens.BubbleCorner)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = shape,
            color = TravelTheme.colors.primary,
            contentColor = TravelTheme.colors.onPrimary,
            modifier =
                Modifier
                    .widthIn(max = maxWidth * ChatDimens.BUBBLE_MAX_FRACTION)
                    .semantics(mergeDescendants = true) { contentDescription = description },
        ) {
            Text(
                turn.content,
                style = TravelTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = TravelTheme.spacing.lg, vertical = TravelTheme.spacing.md),
            )
        }
    }
}

/**
 * An assistant turn: no bubble — full-width text beside a small plane tile, so long
 * answers and trip plans read like a page. Structured turns render the trip plan or
 * the streamed section cards above the text.
 */
@Composable
internal fun AssistantMessage(
    turn: ChatTurn,
    plan: PlanCallbacks,
    savingPlan: Boolean,
    onEvent: (ChatUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(turn.content, turn.streaming) { if (turn.streaming) null else parseFollowUps(turn.content) }
    val body = parsed?.body ?: turn.content
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        AssistantAvatar()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            if (turn.isStructured) StructuredBody(turn, plan, savingPlan, onEvent)
            if (body.isNotBlank() && !(turn.showsErrorCopy && turn.error != TurnError.STREAM)) {
                MarkdownText(body, onOpenLink = plan.onOpenLink, color = TravelTheme.colors.onSurface)
            }
            StatusLine(turn)
            if (turn.showsErrorCopy) ErrorCopy(turn)
            if (!turn.streaming) {
                MessageActions(turn, body, onEvent)
                val chips = parsed?.followUps.orEmpty() + turn.suggestions
                if (chips.isNotEmpty()) SuggestionChips(chips.distinct()) { onEvent(ChatUiEvent.Send(it)) }
                turn.importLink?.let { url ->
                    TpButton(
                        text = stringResource(R.string.chat_import_link),
                        onClick = { onEvent(ChatUiEvent.OpenImport(url)) },
                        variant = TpButtonVariant.Tonal,
                        leadingIcon = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        Modifier
            .size(ChatDimens.Avatar)
            .clip(TravelShape.Input)
            .background(TravelTheme.colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_chat_plane),
            contentDescription = null,
            tint = TravelTheme.colors.onPrimary,
            modifier = Modifier.size(ChatDimens.AvatarIcon),
        )
    }
}

@Composable
private fun StructuredBody(turn: ChatTurn, plan: PlanCallbacks, savingPlan: Boolean, onEvent: (ChatUiEvent) -> Unit) {
    val tripMap = turn.tripMap
    if (tripMap != null) {
        TripPlanView(
            tripMap = tripMap,
            itinerary = turn.itinerary,
            itineraryError = turn.itineraryError,
            generating = turn.itineraryGenerating || (turn.streaming && turn.itinerary == null && turn.itineraryError == null),
            saving = savingPlan,
            onSave = if (turn.itinerary != null && !turn.streaming && turn.id > 0) ({ onEvent(ChatUiEvent.SaveItinerary(turn.id)) }) else null,
        )
        return
    }
    if (turn.sections.isNotEmpty() || turn.pendingAgents.isNotEmpty()) {
        PlanningProgress(turn)
        CHAT_SECTION_ORDER.forEach { id -> turn.sections[id.wire]?.takeIf { it.data != null }?.let { ChatSectionCard(it, plan) } }
        turn.pendingAgents.mapNotNull(SectionId::fromWire).filter { it in CHAT_SECTION_ORDER }.forEach { id ->
            InlineStatus(stringResource(R.string.chat_searching, stringResource(id.titleRes())))
        }
    }
}

/** Web planning progress bar ("n/9"). */
@Composable
private fun PlanningProgress(turn: ChatTurn) {
    val total = CHAT_SECTION_ORDER.size
    val done = turn.sections.values.count { it.status != ChatSectionStatus.LOADING }.coerceAtMost(total)
    val label = stringResource(R.string.chat_progress, done, total)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        LinearProgressIndicator(
            progress = { done.toFloat() / total },
            modifier = Modifier.weight(1f).height(ChatDimens.ProgressHeight).semantics { contentDescription = label },
        )
        Text(label, style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
    }
}

/** What the assistant is doing right now (only while streaming). */
@Composable
private fun StatusLine(turn: ChatTurn) {
    if (!turn.streaming) return
    val text =
        when {
            turn.itineraryGenerating && turn.tripMap == null -> stringResource(R.string.chat_building_itinerary)
            turn.planning && turn.tripMap == null && turn.sections.isEmpty() && turn.pendingAgents.isEmpty() -> stringResource(R.string.chat_planning)
            turn.content.isEmpty() && !turn.isStructured -> stringResource(R.string.chat_thinking)
            else -> null
        } ?: return
    InlineStatus(text)
}

@Composable
private fun InlineStatus(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        CircularProgressIndicator(Modifier.size(ChatDimens.SmallIcon), strokeWidth = ChatDimens.RouteSpine)
        Text(text, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.primary)
    }
}

@Composable
private fun ErrorCopy(turn: ChatTurn) {
    val text =
        when (turn.error) {
            TurnError.EMPTY -> stringResource(R.string.chat_error_empty)
            TurnError.REQUEST -> turn.errorDetail?.takeIf(String::isNotBlank) ?: stringResource(R.string.chat_error_request)
            else -> stringResource(R.string.chat_error_stream)
        }
    Surface(shape = TravelShape.ItemCard, color = TravelTheme.colors.errorContainer, contentColor = TravelTheme.colors.onErrorContainer) {
        Text(text, style = TravelTheme.typography.bodyMedium, modifier = Modifier.padding(TravelTheme.spacing.md))
    }
}

/** Copy (web MessageActions) and Retry on failed turns; a "Stopped" note after Stop. */
@Composable
private fun MessageActions(turn: ChatTurn, body: String, onEvent: (ChatUiEvent) -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    if (copied) {
        LaunchedEffect(Unit) {
            delay(COPIED_MILLIS)
            copied = false
        }
    }
    val copyLabel = stringResource(R.string.chat_copy)
    if (body.isBlank() && !turn.canRetry && !turn.stopped) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        if (body.isNotBlank()) {
            TpButton(
                text = stringResource(if (copied) R.string.chat_copied else R.string.chat_copy),
                onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, body))) }
                    copied = true
                },
                variant = TpButtonVariant.Text,
            )
        }
        if (turn.canRetry && turn.id > 0) {
            TpButton(
                text = stringResource(R.string.chat_retry),
                onClick = { onEvent(ChatUiEvent.Retry(turn.id)) },
                variant = TpButtonVariant.Text,
                leadingIcon = Icons.Filled.Refresh,
            )
        }
        if (turn.stopped) {
            Text(stringResource(R.string.chat_stopped), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

/** Tappable follow-up questions; each sends itself. */
@Composable
internal fun SuggestionChips(chips: List<String>, onSelect: (String) -> Unit) {
    val label = stringResource(R.string.chat_suggestions)
    FlowRow(
        Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        chips.forEach { chip ->
            SuggestionChip(onClick = { onSelect(chip) }, label = { Text(chip) }, shape = TravelShape.Pill)
        }
    }
}

private const val COPIED_MILLIS = 1_500L
