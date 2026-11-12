package io.github.rupakc.travelplanner.feature.social.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.social.R
import io.github.rupakc.travelplanner.feature.social.profile.errorText

/** Route: requests, followers, following and blocked. [onOpenProfile] opens a handle. */
@Composable
internal fun ConnectionsRoute(onBack: () -> Unit, onOpenProfile: (String) -> Unit, viewModel: ConnectionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConnectionsScreen(state, viewModel::onEvent, onBack, onOpenProfile)
}

/** Stateless connections screen: one tab row, then loading, error with retry, empty or the list of handles. */
@Composable
internal fun ConnectionsScreen(
    state: ConnectionsUiState,
    onEvent: (ConnectionsEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = (state.message as? ConnectionsMessage.Failed)?.error?.let { errorText(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onEvent(ConnectionsEvent.MessageShown)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { TpTopBar(title = stringResource(R.string.social_connections_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = TravelTheme.spacing.gutter) {
                ConnectionTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { onEvent(ConnectionsEvent.SelectTab(tab)) },
                        modifier = Modifier.heightIn(min = TravelTheme.spacing.minTouchTarget),
                        text = { Text(stringResource(tab.titleRes())) },
                    )
                }
            }
            TabContent(state, onEvent, onOpenProfile, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TabContent(state: ConnectionsUiState, onEvent: (ConnectionsEvent) -> Unit, onOpenProfile: (String) -> Unit, modifier: Modifier) {
    val tab = state.tab
    val current = state.current
    when {
        current.loading && !current.loaded -> {
            SkeletonList(modifier.padding(TravelTheme.spacing.gutter), lines = 4)
        }

        current.error != null && !current.loaded -> {
            ErrorState(errorText(current.error), modifier, onRetry = { onEvent(ConnectionsEvent.Retry) })
        }

        current.handles.isEmpty() -> {
            EmptyState(stringResource(tab.emptyRes()), modifier)
        }

        else -> {
            LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                items(current.handles, key = { it }, contentType = { "handle" }) { handle ->
                    HandleRow(tab, handle, busy = state.busyHandle == handle, anyBusy = state.busyHandle != null, onEvent, onOpenProfile)
                }
                if (current.next != null) {
                    item(key = "more", contentType = "more") {
                        TpButton(
                            stringResource(R.string.social_load_more),
                            { onEvent(ConnectionsEvent.LoadMore) },
                            Modifier.fillMaxWidth().padding(TravelTheme.spacing.gutter),
                            TpButtonVariant.Secondary,
                            loading = current.loadingMore,
                            tid = "social.connections.more",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HandleRow(
    tab: ConnectionTab,
    handle: String,
    busy: Boolean,
    anyBusy: Boolean,
    onEvent: (ConnectionsEvent) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        // A blocked profile is not viewable (404), so that row has no link.
        val open = if (tab == ConnectionTab.Blocked) Modifier else Modifier.clickable { onOpenProfile(handle) }
        Text(
            "@$handle",
            Modifier.weight(1f).heightIn(min = TravelTheme.spacing.minTouchTarget).then(open).padding(vertical = TravelTheme.spacing.sm),
            style = TravelTheme.typography.bodyLarge,
        )
        when (tab) {
            ConnectionTab.Requests -> {
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                        TpButton(stringResource(R.string.social_decline), {
                            onEvent(ConnectionsEvent.Decline(handle))
                        }, variant = TpButtonVariant.Text, enabled = !anyBusy, tid = "social.request.decline")
                        TpButton(stringResource(R.string.social_accept), {
                            onEvent(ConnectionsEvent.Accept(handle))
                        }, variant = TpButtonVariant.Tonal, enabled = !anyBusy, loading = busy, tid = "social.request.accept")
                    }
                }
            }

            ConnectionTab.Following -> {
                TpButton(stringResource(R.string.social_unfollow), {
                    onEvent(ConnectionsEvent.Unfollow(handle))
                }, variant = TpButtonVariant.Text, enabled = !anyBusy, loading = busy, tid = "social.following.unfollow")
            }

            ConnectionTab.Blocked -> {
                TpButton(stringResource(R.string.social_unblock), {
                    onEvent(ConnectionsEvent.Unblock(handle))
                }, variant = TpButtonVariant.Text, enabled = !anyBusy, loading = busy, tid = "social.blocked.unblock")
            }

            ConnectionTab.Followers -> {
                Unit
            }
        }
    }
}

private fun ConnectionTab.titleRes(): Int =
    when (this) {
        ConnectionTab.Requests -> R.string.social_requests_title
        ConnectionTab.Followers -> R.string.social_followers_title
        ConnectionTab.Following -> R.string.social_following_title
        ConnectionTab.Blocked -> R.string.social_blocked_title
    }

private fun ConnectionTab.emptyRes(): Int =
    when (this) {
        ConnectionTab.Requests -> R.string.social_requests_empty
        ConnectionTab.Followers -> R.string.social_followers_empty
        ConnectionTab.Following -> R.string.social_following_empty
        ConnectionTab.Blocked -> R.string.social_blocked_empty
    }
