package io.github.rupakc.travelplanner.feature.social.idea

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.social.Comment
import io.github.rupakc.travelplanner.core.model.social.ContentStatus
import io.github.rupakc.travelplanner.core.model.social.Idea
import io.github.rupakc.travelplanner.feature.social.R
import io.github.rupakc.travelplanner.feature.social.feed.COMMENT_MAX
import io.github.rupakc.travelplanner.feature.social.feed.LikeButton
import io.github.rupakc.travelplanner.feature.social.feed.ideaCounts
import io.github.rupakc.travelplanner.feature.social.feed.ideaSummary
import io.github.rupakc.travelplanner.feature.social.profile.errorText
import io.github.rupakc.travelplanner.feature.social.report.ReportDialog

/** Route: one idea. [onOpenProfile] opens the author, [onOpenPlan] the plan a save created, [onBack] leaves (also after delete or block). */
@Composable
internal fun IdeaDetailRoute(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenPlan: (Long) -> Unit,
    viewModel: IdeaDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                IdeaDetailEffect.Leave -> onBack()
                is IdeaDetailEffect.OpenPlan -> onOpenPlan(it.planId)
            }
        }
    }
    IdeaDetailScreen(state, viewModel::onEvent, onBack, onOpenProfile)
}

/** Stateless idea screen: loading, error with retry, "not available", then the idea, its comments and the comment box. */
@Composable
internal fun IdeaDetailScreen(
    state: IdeaDetailUiState,
    onEvent: (IdeaDetailEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onEvent(IdeaDetailEvent.MessageShown)
        }
    }
    val idea = state.idea
    Scaffold(
        modifier = modifier,
        topBar = {
            TpTopBar(
                title = stringResource(R.string.social_idea_title),
                onBack = onBack,
                actions = { if (idea != null && state.phase == IdeaPhase.Content) IdeaMenu(idea, onEvent) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (idea != null && state.phase == IdeaPhase.Content && idea.contentStatus == ContentStatus.PUBLISHED) CommentBox(state, onEvent)
        },
    ) { padding ->
        val body = Modifier.padding(padding).fillMaxSize()
        when {
            state.phase == IdeaPhase.Loading -> {
                SkeletonList(body.padding(TravelTheme.spacing.gutter), lines = 5)
            }

            state.phase == IdeaPhase.Gone -> {
                EmptyState(stringResource(R.string.social_idea_unavailable), body, message = stringResource(R.string.social_idea_unavailable_body))
            }

            state.phase == IdeaPhase.Error || idea == null -> {
                ErrorState(errorText(state.error), body, onRetry = { onEvent(IdeaDetailEvent.Retry) })
            }

            else -> {
                IdeaContent(idea, state, onEvent, onOpenProfile, body)
            }
        }
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = R.string.social_delete_idea_title,
            body = R.string.social_delete_idea_body,
            confirm = R.string.social_delete_confirm,
            onConfirm = { onEvent(IdeaDetailEvent.ConfirmDelete) },
            onDismiss = { onEvent(IdeaDetailEvent.DismissDelete) },
        )
    }
    if (state.confirmBlock && idea != null) {
        ConfirmDialog(
            titleText = stringResource(R.string.social_block_title, idea.author.handle),
            body = R.string.social_block_body,
            confirm = R.string.social_block,
            onConfirm = { onEvent(IdeaDetailEvent.ConfirmBlock) },
            onDismiss = { onEvent(IdeaDetailEvent.DismissBlock) },
        )
    }
    if (state.reporting != null) {
        ReportDialog(
            onDismiss = { onEvent(IdeaDetailEvent.DismissReport) },
            onSubmit = { onEvent(IdeaDetailEvent.SendReport(it)) },
            sending = state.reportSending,
            error = state.reportError?.let { errorText(it) },
        )
    }
}

@Composable
private fun IdeaContent(idea: Idea, state: IdeaDetailUiState, onEvent: (IdeaDetailEvent) -> Unit, onOpenProfile: (String) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        item(key = "idea", contentType = "idea") { IdeaHeader(idea, state, onEvent, onOpenProfile) }
        item(key = "comments-title", contentType = "title") {
            Text(
                stringResource(R.string.social_comments_title),
                Modifier.padding(top = TravelTheme.spacing.md).semantics { heading() },
                style = TravelTheme.typography.titleMedium,
            )
        }
        when {
            state.commentsError != null && state.comments.isEmpty() -> {
                item(key = "comments-error", contentType = "error") {
                    Column {
                        Text(stringResource(R.string.social_comments_error), color = TravelTheme.colors.error, style = TravelTheme.typography.bodyMedium)
                        TextButton(onClick = { onEvent(IdeaDetailEvent.RetryComments) }) { Text(stringResource(R.string.social_retry)) }
                    }
                }
            }

            !state.commentsLoaded -> {
                item(key = "comments-loading", contentType = "loading") { SkeletonList(lines = 2) }
            }

            state.comments.isEmpty() -> {
                item(key = "comments-empty", contentType = "empty") {
                    Text(stringResource(R.string.social_comments_empty), style = TravelTheme.typography.bodyMedium)
                }
            }
        }
        items(state.comments, key = { it.id }, contentType = { "comment" }) { comment ->
            CommentRow(comment, idea, busy = state.busyComment == comment.id, onEvent = onEvent, onOpenProfile = onOpenProfile)
        }
        if (state.commentsNext != null) {
            item(key = "comments-more", contentType = "more") {
                TpButton(
                    stringResource(R.string.social_load_more),
                    { onEvent(IdeaDetailEvent.LoadMoreComments) },
                    Modifier.fillMaxWidth(),
                    TpButtonVariant.Secondary,
                    loading = state.loadingMoreComments,
                    tid = "social.comments.more",
                )
            }
        }
    }
}

@Composable
private fun IdeaHeader(idea: Idea, state: IdeaDetailUiState, onEvent: (IdeaDetailEvent) -> Unit, onOpenProfile: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(idea.title, style = TravelTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(R.string.social_idea_author, idea.author.handle),
            Modifier.heightIn(min = TravelTheme.spacing.minTouchTarget).clickable {
                onOpenProfile(idea.author.handle)
            }.padding(vertical = TravelTheme.spacing.sm),
            style = TravelTheme.typography.titleSmall,
            color = TravelTheme.colors.primary,
        )
        when (idea.contentStatus) {
            ContentStatus.HELD -> Notice(R.string.social_idea_held_notice)
            ContentStatus.HIDDEN, ContentStatus.REMOVED -> Notice(R.string.social_idea_hidden_notice)
            else -> Unit
        }
        val summary = ideaSummary(idea)
        if (summary.isNotEmpty()) Text(summary, style = TravelTheme.typography.bodyLarge)
        if (idea.caption.isNotBlank()) Text(idea.caption, style = TravelTheme.typography.bodyMedium)
        SnapshotItems(idea)
        val counts = ideaCounts(idea)
        if (counts.isNotEmpty()) Text(counts, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            LikeButton(idea.liked, idea.likes ?: 0, enabled = !state.working, onToggle = { onEvent(IdeaDetailEvent.ToggleLike) })
            if (!idea.mine) {
                TpButton(
                    stringResource(if (idea.saved) R.string.social_idea_saved else R.string.social_idea_save),
                    { onEvent(IdeaDetailEvent.ToggleSave) },
                    variant = if (idea.saved) TpButtonVariant.Text else TpButtonVariant.Primary,
                    enabled = !state.working,
                    tid = "social.idea.save",
                )
            }
        }
    }
}

@Composable
private fun SnapshotItems(idea: Idea) {
    val sections = idea.snapshot.items.filterValues { it.any { item -> item.name.isNotBlank() } }
    if (sections.isEmpty()) return
    Text(stringResource(R.string.social_idea_includes), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    sections.forEach { (key, items) ->
        Text(stringResource(sectionLabelRes(key)), style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.onSurfaceVariant)
        items.filter { it.name.isNotBlank() }.forEach { Text(it.name, style = TravelTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Notice(text: Int) {
    Surface(color = TravelTheme.colors.secondaryContainer, shape = TravelShape.ItemCard, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(text),
            Modifier.padding(TravelTheme.spacing.md),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSecondaryContainer,
        )
    }
}

@Composable
private fun CommentRow(comment: Comment, idea: Idea, busy: Boolean, onEvent: (IdeaDetailEvent) -> Unit, onOpenProfile: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.social_idea_author, comment.author.handle),
                Modifier.weight(1f).heightIn(min = TravelTheme.spacing.minTouchTarget).clickable {
                    onOpenProfile(comment.author.handle)
                }.padding(vertical = TravelTheme.spacing.sm),
                style = TravelTheme.typography.labelLarge,
            )
            CommentMenu(comment, idea, busy, onEvent)
        }
        Text(comment.text, style = TravelTheme.typography.bodyMedium)
        if (comment.helpful) {
            Text(
                stringResource(R.string.social_comment_helpful),
                style = TravelTheme.typography.labelMedium,
                color = TravelTheme.colors.primary,
            )
        }
        if (comment.contentStatus == ContentStatus.HELD) {
            Text(stringResource(R.string.social_comment_held_label), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun CommentMenu(comment: Comment, idea: Idea, busy: Boolean, onEvent: (IdeaDetailEvent) -> Unit) {
    val canDelete = comment.mine || idea.mine
    val canMark = idea.mine && !comment.mine
    val canReport = !comment.mine
    if (!canDelete && !canMark && !canReport) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, enabled = !busy) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.social_more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canMark) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (comment.helpful) R.string.social_comment_unmark_helpful else R.string.social_comment_mark_helpful)) },
                    onClick = {
                        open = false
                        onEvent(IdeaDetailEvent.ToggleHelpful(comment.id))
                    },
                )
            }
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.social_comment_delete)) },
                    onClick = {
                        open = false
                        onEvent(IdeaDetailEvent.DeleteComment(comment.id))
                    },
                )
            }
            if (canReport) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.social_comment_report)) },
                    onClick = {
                        open = false
                        onEvent(IdeaDetailEvent.AskReport(ReportSubject.OfComment(comment.id)))
                    },
                )
            }
        }
    }
}

@Composable
private fun IdeaMenu(idea: Idea, onEvent: (IdeaDetailEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.social_more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (idea.mine) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.social_delete_idea)) },
                    onClick = {
                        open = false
                        onEvent(IdeaDetailEvent.AskDelete)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.social_report_idea)) },
                    onClick = {
                        open = false
                        onEvent(IdeaDetailEvent.AskReport(ReportSubject.OfIdea))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.social_block_author, idea.author.handle)) },
                    onClick = {
                        open = false
                        onEvent(IdeaDetailEvent.AskBlock)
                    },
                )
            }
        }
    }
}

@Composable
private fun CommentBox(state: IdeaDetailUiState, onEvent: (IdeaDetailEvent) -> Unit) {
    Surface(tonalElevation = TravelTheme.spacing.xs, modifier = Modifier.imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        ) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = { onEvent(IdeaDetailEvent.DraftChanged(it)) },
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.social_comment_hint)) },
                supportingText = { Text(stringResource(R.string.social_bio_count, state.draft.length, COMMENT_MAX)) },
                enabled = !state.posting,
                maxLines = 3,
            )
            TpButton(
                stringResource(R.string.social_comment_post),
                { onEvent(IdeaDetailEvent.PostComment) },
                Modifier.padding(top = TravelTheme.spacing.xs),
                enabled = state.canPost,
                loading = state.posting,
                tid = "social.comment.post",
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    body: Int,
    confirm: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: Int? = null,
    titleText: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText ?: stringResource(requireNotNull(title))) },
        text = { Text(stringResource(body), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.social_cancel)) } },
    )
}

@Composable
private fun messageText(message: IdeaMessage): String =
    when (message) {
        IdeaMessage.Reported -> stringResource(R.string.social_report_done)
        IdeaMessage.Blocked -> stringResource(R.string.social_blocked_done)
        IdeaMessage.Deleted -> stringResource(R.string.social_idea_deleted)
        IdeaMessage.CommentHeld -> stringResource(R.string.social_comment_held)
        is IdeaMessage.Failed -> errorText(message.error)
    }

/** The heading of a snapshot section key; a key this app does not know falls under "Also included". */
private fun sectionLabelRes(key: String): Int =
    when (key) {
        "flights" -> R.string.social_section_flights
        "hotels" -> R.string.social_section_hotels
        "activities" -> R.string.social_section_activities
        "places", "places_to_see" -> R.string.social_section_places
        "walking_tours" -> R.string.social_section_walking_tours
        "events" -> R.string.social_section_events
        else -> R.string.social_section_other
    }
