package io.github.rupakc.travelplanner.feature.social.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpTab
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.social.FeedScope
import io.github.rupakc.travelplanner.feature.social.R
import io.github.rupakc.travelplanner.feature.social.profile.errorText

/** Route: the ideas feed. [onOpenIdea] opens an idea, [onOpenPlan] the plan a save created. */
@Composable
internal fun FeedRoute(onBack: () -> Unit, onOpenIdea: (Long) -> Unit, onOpenPlan: (Long) -> Unit, viewModel: FeedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is FeedEffect.OpenPlan) onOpenPlan(it.planId) }
    }
    FeedScreen(state, viewModel::onEvent, onBack, onOpenIdea)
}

/** Stateless feed: the three scopes as tabs, then loading, error with retry, empty or the cards with a "load more" at the end. */
@Composable
internal fun FeedScreen(
    state: FeedUiState,
    onEvent: (FeedEvent) -> Unit,
    onBack: () -> Unit,
    onOpenIdea: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val message =
        when (val m = state.message) {
            null -> null
            FeedMessage.Gone -> stringResource(R.string.social_idea_gone)
            is FeedMessage.Failed -> errorText(m.error)
        }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onEvent(FeedEvent.MessageShown)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TpTopBar(
                title = stringResource(R.string.social_feed_title),
                onBack = onBack,
                actions = {
                    TpIconButton(onClick = {
                        onEvent(FeedEvent.Refresh)
                    }, modifier = Modifier.heightIn(min = TravelTheme.spacing.minTouchTarget), tid = "social.social_feed_refresh") {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.social_feed_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = state.scope.ordinal) {
                FeedScope.entries.forEach { scope ->
                    TpTab(
                        selected = scope == state.scope,
                        onClick = { onEvent(FeedEvent.SelectScope(scope)) },
                        modifier = Modifier.heightIn(min = TravelTheme.spacing.minTouchTarget),
                        text = { Text(stringResource(scope.titleRes())) },
                        tid = "social.feed_screen_tab",
                    )
                }
            }
            ScopeContent(state, onEvent, onOpenIdea, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ScopeContent(state: FeedUiState, onEvent: (FeedEvent) -> Unit, onOpenIdea: (Long) -> Unit, modifier: Modifier) {
    val current = state.current
    when {
        current.loading && !current.loaded -> {
            SkeletonList(modifier.padding(TravelTheme.spacing.gutter), lines = 4)
        }

        current.error != null && !current.loaded -> {
            ErrorState(errorText(current.error), modifier, onRetry = { onEvent(FeedEvent.Refresh) })
        }

        current.ideas.isEmpty() -> {
            EmptyState(
                stringResource(R.string.social_feed_empty),
                modifier,
                message = stringResource(if (state.scope == FeedScope.FOLLOWING) R.string.social_feed_empty_following else R.string.social_feed_empty_body),
            )
        }

        else -> {
            LazyColumn(
                modifier,
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = TravelTheme.spacing.sm),
            ) {
                items(current.ideas, key = { it.id }, contentType = { "idea" }) { idea ->
                    IdeaCard(
                        idea = idea,
                        busy = idea.id in state.busy,
                        onOpen = {
                            onEvent(FeedEvent.IdeaOpened)
                            onOpenIdea(idea.id)
                        },
                        onLike = { onEvent(FeedEvent.ToggleLike(idea.id)) },
                        onSave = { onEvent(FeedEvent.ToggleSave(idea.id)) },
                    )
                }
                if (current.next != null) {
                    item(key = "more", contentType = "more") {
                        TpButton(
                            stringResource(R.string.social_load_more),
                            { onEvent(FeedEvent.LoadMore) },
                            Modifier.fillMaxWidth().padding(TravelTheme.spacing.gutter),
                            TpButtonVariant.Secondary,
                            loading = current.loadingMore,
                            tid = "social.feed.more",
                        )
                    }
                }
            }
        }
    }
}

private fun FeedScope.titleRes(): Int =
    when (this) {
        FeedScope.FOR_YOU -> R.string.social_scope_for_you
        FeedScope.FOLLOWING -> R.string.social_scope_following
        FeedScope.NEW -> R.string.social_scope_new
    }
