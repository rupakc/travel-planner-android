package io.github.rupakc.travelplanner.feature.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AlertAction
import io.github.rupakc.travelplanner.core.domain.alerts.AlertIcon
import io.github.rupakc.travelplanner.core.domain.alerts.AlertLink
import io.github.rupakc.travelplanner.core.domain.alerts.alertKindFor
import io.github.rupakc.travelplanner.core.domain.alerts.keepDeltaTogether
import io.github.rupakc.travelplanner.core.domain.alerts.notificationLink
import io.github.rupakc.travelplanner.core.domain.alerts.timeAgo
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.watch.toneColors
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class NotificationsUiState(
    val items: List<AppNotification>? = null,
    val failed: Boolean = false,
    val unread: Int = 0,
    val now: Instant = Instant.EPOCH,
)

sealed interface NotificationsEvent {
    data object Retry : NotificationsEvent

    data class Open(val notification: AppNotification) : NotificationsEvent

    data object MarkAllRead : NotificationsEvent
}

/** The notifications inbox (web NotificationBell panel, as a screen). */
@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val repository: NotificationsRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(NotificationsUiState(now = clock.now()))
        val state: StateFlow<NotificationsUiState> = mutable.asStateFlow()
        private val opens = Channel<AlertLink>(Channel.BUFFERED)

        /** Where a tapped notification leads (the Alerts screen focused on its watch). */
        val open: Flow<AlertLink> = opens.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: NotificationsEvent) {
            when (event) {
                NotificationsEvent.Retry -> load()
                is NotificationsEvent.Open -> open(event.notification)
                NotificationsEvent.MarkAllRead -> markAll()
            }
        }

        private fun load() {
            mutable.update { it.copy(failed = false, now = clock.now()) }
            viewModelScope.launch {
                when (val page = repository.list(limit = PAGE)) {
                    is AppResult.Success -> {
                        mutable.update { it.copy(items = page.value.items, unread = page.value.unread) }
                        // Seen here: the poller won't post these as system notifications later.
                        page.value.items.map { it.id }.takeIf { it.isNotEmpty() }?.let { repository.markSeen(it) }
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(failed = true) }
                    }
                }
            }
        }

        private fun open(n: AppNotification) {
            if (n.isUnread) {
                val read = clock.now().toString()
                mutable.update { s ->
                    s.copy(
                        items =
                            s.items?.map {
                                if (it.id ==
                                    n.id
                                ) {
                                    it.copy(readAt = read)
                                } else {
                                    it
                                }
                            },
                        unread = (s.unread - 1).coerceAtLeast(0),
                    )
                }
                viewModelScope.launch { repository.markRead(listOf(n.id)) }
            }
            viewModelScope.launch { opens.send(notificationLink(n)) }
        }

        private fun markAll() {
            viewModelScope.launch {
                if (repository.markAllRead() is AppResult.Success) {
                    val read = clock.now().toString()
                    mutable.update { s -> s.copy(items = s.items?.map { it.copy(readAt = it.readAt ?: read) }, unread = 0) }
                }
            }
        }

        private companion object {
            const val PAGE = 50
        }
    }

@Composable
fun NotificationsInboxRoute(onBack: () -> Unit, onOpenAlert: (AlertLink) -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.open.collect(onOpenAlert) }
    NotificationsScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun NotificationsScreen(state: NotificationsUiState, onEvent: (NotificationsEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TpTopBar(
                title = stringResource(R.string.alerts_notifications_title),
                onBack = onBack,
                actions = {
                    if (state.unread > 0) {
                        IconButton(onClick = { onEvent(NotificationsEvent.MarkAllRead) }) {
                            Icon(Icons.Filled.Done, contentDescription = stringResource(R.string.alerts_mark_all_read))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val items = state.items
            when {
                state.failed -> {
                    ErrorState(stringResource(R.string.alerts_notifications_error), onRetry = { onEvent(NotificationsEvent.Retry) })
                }

                items == null -> {
                    SkeletonList(Modifier.padding(TravelTheme.spacing.gutter), message = stringResource(R.string.alerts_loading_short), lines = 5)
                }

                items.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.alerts_notifications_empty),
                        message = stringResource(R.string.alerts_notifications_empty_message),
                        icon = Icons.Filled.Notifications,
                    )
                }

                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items, key = { it.id }, contentType = { "notification" }) { n ->
                            NotificationRow(n, state.now) { onEvent(NotificationsEvent.Open(n)) }
                            HorizontalDivider(color = TravelTheme.colors.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun AlertIcon.vector(): ImageVector =
    when (this) {
        AlertIcon.TREND_DOWN, AlertIcon.TARGET -> Icons.Filled.Star
        AlertIcon.SPARKLE -> Icons.Filled.Star
        AlertIcon.CALENDAR -> Icons.Filled.DateRange
        AlertIcon.HOTEL -> Icons.Filled.Home
        AlertIcon.TICKET -> Icons.Filled.ShoppingCart
        AlertIcon.LUGGAGE -> Icons.Filled.Notifications
        AlertIcon.ENDED -> Icons.Filled.Info
        AlertIcon.WARNING -> Icons.Filled.Warning
    }

@Composable
private fun actionText(action: AlertAction): String =
    stringResource(
        when (action) {
            AlertAction.SEE_FARE -> R.string.alerts_action_see_fare
            AlertAction.SEE_DATES -> R.string.alerts_action_see_dates
            AlertAction.SEE_HOTEL -> R.string.alerts_action_see_hotel
            AlertAction.SEE_ACTIVITY -> R.string.alerts_action_see_activity
            AlertAction.SEE_TRIP -> R.string.alerts_action_see_trip
            AlertAction.CHECK_TRIP -> R.string.alerts_action_check_trip
            AlertAction.VIEW -> R.string.alerts_action_view
            AlertAction.RETRY -> R.string.alerts_action_retry
        },
    )

@Composable
internal fun NotificationRow(n: AppNotification, now: Instant, onOpen: () -> Unit) {
    val style = alertKindFor(n)
    val (bg, fg) = toneColors(style.tone)
    val unread = n.isUnread
    val stateText = stringResource(if (unread) R.string.alerts_unread else R.string.alerts_read)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TravelTheme.spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { stateDescription = stateText }
            .padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Surface(color = bg, contentColor = fg, shape = CircleShape, modifier = Modifier.size(TravelTheme.spacing.xxl)) {
            Box(contentAlignment = Alignment.Center) { Icon(style.icon.vector(), contentDescription = null) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
            Text(
                keepDeltaTogether(n.title).orEmpty(),
                style = TravelTheme.typography.titleSmall,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
            )
            n.body?.let {
                Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                timeAgo(n.createdAt, now)?.let { Text(timeAgoText(it), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant) }
                Text(actionText(style.action), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.primary)
            }
        }
        if (unread) {
            Surface(
                color = TravelTheme.colors.primary,
                shape = CircleShape,
                modifier = Modifier.padding(top = TravelTheme.spacing.sm).size(TravelTheme.spacing.sm),
            ) {
            }
        }
    }
}

/** Unread count for the shell's bell; refreshed whenever the app comes to the foreground. */
@HiltViewModel
class NotificationBellViewModel
    @Inject
    constructor(
        private val repository: NotificationsRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(0)
        val unread: StateFlow<Int> = mutable.asStateFlow()

        fun refresh() {
            viewModelScope.launch { (repository.unreadCount() as? AppResult.Success)?.let { mutable.value = it.value } }
        }
    }

/**
 * Bell for the shell's top app bar (web NotificationBell): unread badge capped at "9+", an
 * accessible label with the count, and a polite announcement when new alerts arrive.
 */
@Composable
fun NotificationBellAction(onOpen: () -> Unit, viewModel: NotificationBellViewModel = hiltViewModel()) {
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    NotificationBell(unread, onOpen)
}

@Composable
fun NotificationBell(unread: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (unread > 0) pluralStringResource(R.plurals.alerts_bell_unread, unread, unread) else stringResource(R.string.alerts_bell)
    // The label carries the count; as a polite live region it is announced when the count changes.
    IconButton(
        onClick = onOpen,
        modifier =
            modifier.semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        BadgedBox(
            badge = {
                if (unread > 0) {
                    Badge(Modifier.widthIn(min = TravelTheme.spacing.lg)) { Text(if (unread > BADGE_CAP) "$BADGE_CAP+" else unread.toString()) }
                }
            },
        ) { Icon(Icons.Filled.Notifications, contentDescription = null) }
    }
}

private const val BADGE_CAP = 9

@Preview(name = "Notifications · light")
@Composable
private fun NotificationsPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        NotificationsScreen(
            NotificationsUiState(
                items =
                    listOf(
                        AppNotification(
                            id = 2,
                            kind = "fare_drop",
                            title = "Berlin → Lisbon dropped to $454 (−$46)",
                            body = "For 1–8 Dec · total for 2 travellers.",
                            createdAt = "2026-11-05T09:00:00Z",
                        ),
                        AppNotification(
                            id = 1,
                            kind = "hotel_target",
                            title = "Lumen Hotel is below €400",
                            body = "€398 for 4 nights",
                            readAt = "2026-11-04T00:00:00Z",
                            createdAt = "2026-11-03T09:00:00Z",
                        ),
                    ),
                unread = 1,
                now = Instant.parse("2026-11-05T12:00:00Z"),
            ),
            {},
            {},
        )
    }
}

@Preview(name = "Notifications · dark, empty")
@Composable
private fun NotificationsEmptyPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { NotificationsScreen(NotificationsUiState(items = emptyList()), {}, {}) }
}
