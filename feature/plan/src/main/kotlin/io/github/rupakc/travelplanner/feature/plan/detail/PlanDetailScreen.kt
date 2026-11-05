package io.github.rupakc.travelplanner.feature.plan.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.export.PlanCalendarEvent
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.plan.R
import io.github.rupakc.travelplanner.feature.plan.model.PlanCategory
import io.github.rupakc.travelplanner.feature.plan.model.SelectionEntry
import io.github.rupakc.travelplanner.feature.plan.model.SelectionGroup
import io.github.rupakc.travelplanner.feature.plan.model.TripSummary
import io.github.rupakc.travelplanner.feature.plan.model.TripTiming
import io.github.rupakc.travelplanner.feature.plan.ui.ConfirmDialog
import io.github.rupakc.travelplanner.feature.plan.ui.CostCard
import io.github.rupakc.travelplanner.feature.plan.ui.DateBadge
import io.github.rupakc.travelplanner.feature.plan.ui.FactPill
import io.github.rupakc.travelplanner.feature.plan.ui.SectionTitle
import io.github.rupakc.travelplanner.feature.plan.ui.displayName
import io.github.rupakc.travelplanner.feature.plan.ui.itineraryItems
import io.github.rupakc.travelplanner.feature.plan.ui.medium
import io.github.rupakc.travelplanner.feature.plan.ui.selectionGroups
import io.github.rupakc.travelplanner.feature.plan.ui.travellersLabel
import io.github.rupakc.travelplanner.feature.plan.ui.tripDates
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One saved trip. [onBack] null = shown as the detail pane next to the list (no back arrow).
 * Messages go to [snackbar]; the route owns it so export failures land in the same place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanDetailScreen(
    state: PlanDetailUiState,
    onEvent: (PlanDetailEvent) -> Unit,
    onOpenLink: (String) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    /** "Watch whole trip" (Team G's price-watch sheet); null hides the button (not watchable / no live prices). */
    onWatchTrip: (() -> Unit)? = null,
) {
    val content = state.content
    Scaffold(
        modifier = modifier,
        topBar = {
            TpTopBar(
                title = content?.summary?.displayName() ?: stringResource(R.string.plan_detail_title),
                onBack = onBack,
                actions = { if (content != null) OverflowMenu(onEvent, deleteEnabled = !state.offline) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                content != null -> {
                    DetailBody(content, state, onEvent, onOpenLink, onWatchTrip)
                }

                state.error != null -> {
                    ErrorState(
                        message = state.error.message ?: stringResource(R.string.plan_not_found),
                        onRetry = { onEvent(PlanDetailEvent.Retry) },
                    )
                }

                else -> {
                    SkeletonList(Modifier.padding(TravelTheme.spacing.gutter), lines = SKELETON_LINES)
                }
            }
        }
    }
    DetailMessages(state.message, snackbar, onEvent)
    state.confirm?.let { ConfirmFor(it, content?.summary, onEvent) }
    state.calendarEvents?.let { CalendarSheet(it, onEvent) }
}

private const val SKELETON_LINES = 6

@Composable
private fun OverflowMenu(onEvent: (PlanDetailEvent) -> Unit, deleteEnabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.plan_more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plan_action_share_text)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                onClick = {
                    open = false
                    onEvent(PlanDetailEvent.ShareAsText)
                },
            )
            DropdownMenuItem(
                enabled = deleteEnabled,
                text = { Text(stringResource(R.string.plan_action_delete)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    onEvent(PlanDetailEvent.Delete)
                },
            )
        }
    }
}

@Composable
private fun DetailBody(
    content: PlanDetailContent,
    state: PlanDetailUiState,
    onEvent: (PlanDetailEvent) -> Unit,
    onOpenLink: (String) -> Unit,
    onWatchTrip: (() -> Unit)?,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.md),
    ) {
        state.staleError?.let { error ->
            item(key = "stale", contentType = "banner") { StaleBanner(error, onRetry = { onEvent(PlanDetailEvent.Retry) }) }
        }
        item(key = "header", contentType = "header") { TripHeader(content.summary) }
        item(key = "actions", contentType = "actions") { ActionRow(content.summary, state, onEvent, onWatchTrip) }
        if (content.costParts.isNotEmpty()) {
            item(key = "cost", contentType = "cost") {
                CostCard(content.costParts, content.budgetStatus, content.hasTransport, Modifier.padding(top = TravelTheme.spacing.lg))
            }
        }
        if (content.groups.isEmpty() && content.itinerary.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                Text(
                    stringResource(R.string.plan_no_selections),
                    style = TravelTheme.typography.bodyMedium,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = TravelTheme.spacing.xl),
                )
            }
        }
        selectionGroups(content.groups, onOpenLink)
        itineraryItems(content.itinerary)
    }
}

/** Header: date badge + destination as the headline, origin line, then plain fact pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TripHeader(summary: TripSummary, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBadge(summary.startDate, summary.timing)
            Column(Modifier.padding(start = TravelTheme.spacing.md).weight(1f)) {
                Text(
                    summary.destination ?: stringResource(R.string.plan_destination_unknown),
                    style = TravelTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                summary.origin?.let {
                    Text(stringResource(R.string.plan_from, it), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            tripDates(summary.startDate, summary.endDate)?.let { FactPill(Icons.Filled.DateRange, it) }
            travellersLabel(summary.travellers)?.let { FactPill(Icons.Filled.Person, it) }
            if (summary.totalCostUsd > 0) FactPill(null, stringResource(R.string.plan_planned_spend, formatPrice(summary.totalCostUsd)))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(summary: TripSummary, state: PlanDetailUiState, onEvent: (PlanDetailEvent) -> Unit, onWatchTrip: (() -> Unit)?) {
    Column(Modifier.padding(top = TravelTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        TpButton(
            stringResource(R.string.plan_action_open_results),
            onClick = { onEvent(PlanDetailEvent.OpenInResults) },
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            TpButton(
                stringResource(R.string.plan_action_share_link),
                onClick = { onEvent(PlanDetailEvent.ShareLink) },
                variant = TpButtonVariant.Tonal,
                loading = state.shareBusy,
                enabled = !state.offline,
                leadingIcon = Icons.Filled.Share,
            )
            TpButton(
                stringResource(R.string.plan_action_calendar),
                onClick = { onEvent(PlanDetailEvent.ShowCalendarEvents) },
                variant = TpButtonVariant.Tonal,
                leadingIcon = Icons.Filled.DateRange,
            )
            if (onWatchTrip != null) {
                TpButton(
                    stringResource(R.string.plan_action_watch_trip),
                    onClick = onWatchTrip,
                    variant = TpButtonVariant.Tonal,
                    enabled = !state.offline,
                    leadingIcon = Icons.Filled.Notifications,
                )
            }
        }
        if (summary.isShared) {
            Surface(color = TravelTheme.colors.secondaryContainer, shape = TravelShape.ItemCard, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(TravelTheme.spacing.md)) {
                    Text(
                        stringResource(R.string.plan_shared_status),
                        style = TravelTheme.typography.bodyMedium,
                        color = TravelTheme.colors.onSecondaryContainer,
                    )
                    TpButton(
                        stringResource(R.string.plan_action_revoke),
                        onClick = { onEvent(PlanDetailEvent.RevokeShare) },
                        variant = TpButtonVariant.Text,
                        enabled = !state.shareBusy && !state.offline,
                    )
                }
            }
        } else {
            Text(stringResource(R.string.plan_share_hint), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun DetailMessages(message: DetailMessage?, snackbar: SnackbarHostState, onEvent: (PlanDetailEvent) -> Unit) {
    val text =
        when (message) {
            null -> null
            DetailMessage.LinkRevoked -> stringResource(R.string.plan_link_revoked)
            DetailMessage.NeedsConnection -> stringResource(R.string.plan_needs_connection)
            DetailMessage.NothingToExport -> stringResource(R.string.plan_nothing_to_export)
            is DetailMessage.Failed -> message.error.message ?: stringResource(io.github.rupakc.travelplanner.core.designsystem.R.string.ds_error_generic)
        }
    LaunchedEffect(message) {
        if (text != null) {
            snackbar.showSnackbar(text)
            onEvent(PlanDetailEvent.MessageShown)
        }
    }
}

@Composable
private fun ConfirmFor(confirm: DetailConfirm, summary: TripSummary?, onEvent: (PlanDetailEvent) -> Unit) {
    val dismiss = { onEvent(PlanDetailEvent.DismissConfirm) }
    val ok = { onEvent(PlanDetailEvent.Confirm) }
    when (confirm) {
        DetailConfirm.DELETE -> {
            ConfirmDialog(
                stringResource(R.string.plan_confirm_delete_title),
                stringResource(R.string.plan_confirm_delete_message, summary?.displayName().orEmpty()),
                stringResource(R.string.plan_confirm_delete),
                ok,
                dismiss,
            )
        }

        DetailConfirm.REVOKE_SHARE -> {
            ConfirmDialog(
                stringResource(R.string.plan_confirm_revoke_title),
                stringResource(R.string.plan_confirm_revoke_message),
                stringResource(R.string.plan_confirm_revoke),
                ok,
                dismiss,
            )
        }

        DetailConfirm.DISCARD_DRAFT -> {
            ConfirmDialog(
                stringResource(R.string.plan_confirm_discard_title),
                stringResource(R.string.plan_confirm_discard_message),
                stringResource(R.string.plan_confirm_discard),
                ok,
                dismiss,
            )
        }
    }
}

/** "Add to calendar": export everything as .ics, or add single events to the phone's calendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarSheet(events: List<PlanCalendarEvent>, onEvent: (PlanDetailEvent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onEvent(PlanDetailEvent.HideCalendarEvents) }) {
        LazyColumn(contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm)) {
            item(key = "title", contentType = "title") {
                SectionTitle(stringResource(R.string.plan_calendar_title))
            }
            item(key = "all", contentType = "all") {
                Column(Modifier.padding(vertical = TravelTheme.spacing.sm)) {
                    TpButton(
                        stringResource(R.string.plan_calendar_export_all),
                        onClick = { onEvent(PlanDetailEvent.ExportCalendar) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.plan_calendar_export_hint),
                        style = TravelTheme.typography.bodySmall,
                        color = TravelTheme.colors.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            items(events, key = { it.id }, contentType = { "event" }) { event -> CalendarEventRow(event, onEvent) }
        }
    }
}

@Composable
private fun CalendarEventRow(event: PlanCalendarEvent, onEvent: (PlanDetailEvent) -> Unit) {
    val title = event.summary ?: stringResource(R.string.plan_item_unnamed)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val whenText =
        if (event.allDay) {
            "${event.start.toLocalDate().medium()}, ${stringResource(R.string.plan_calendar_all_day)}"
        } else {
            "${event.start.toLocalDate().medium()}, ${time.format(event.start)}"
        }
    Row(Modifier.fillMaxWidth().padding(vertical = TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TravelTheme.typography.bodyLarge)
            Text(whenText, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        val a11y = stringResource(R.string.plan_calendar_add_one_a11y, title)
        TpButton(
            stringResource(R.string.plan_calendar_add_one),
            onClick = { onEvent(PlanDetailEvent.AddEventToCalendar(event)) },
            variant = TpButtonVariant.Secondary,
            modifier = Modifier.semantics { contentDescription = a11y },
        )
    }
}

// --- previews --------------------------------------------------------------------------------

private val previewContent =
    PlanDetailContent(
        summary =
            TripSummary(
                7,
                "The Foodie Tokyo Journey",
                "Tokyo, Japan",
                "New York",
                LocalDate.of(2027, 4, 1),
                LocalDate.of(2027, 4, 8),
                2,
                3000.0,
                listOf("food"),
                "tok",
                null,
                3,
                2480.0,
                TripTiming.UPCOMING,
            ),
        groups =
            listOf(
                SelectionGroup(PlanCategory.HOTEL, listOf(SelectionEntry("h", "Park Hyatt Tokyo", listOf("Shinjuku"), 2400.0))),
                SelectionGroup(PlanCategory.ACTIVITIES, listOf(SelectionEntry("a", "Sushi making class", listOf("Tsukiji"), 80.0))),
            ),
        itinerary = emptyList(),
        costParts =
            listOf(
                io.github.rupakc.travelplanner.core.domain.plan.CostPart(io.github.rupakc.travelplanner.core.domain.plan.CostCategory.HOTEL, 2400.0),
            ),
        budgetStatus = io.github.rupakc.travelplanner.core.domain.plan.BudgetStatus(over = false, amount = 600),
        hasTransport = false,
    )

@Preview(name = "Trip detail — light")
@Composable
private fun DetailLightPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        PlanDetailScreen(PlanDetailUiState(isLoading = false, content = previewContent), {}, {}, onBack = {})
    }
}

@Preview(name = "Trip detail — dark")
@Composable
private fun DetailDarkPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) {
        PlanDetailScreen(PlanDetailUiState(isLoading = false, content = previewContent), {}, {}, onBack = {})
    }
}

@Preview(name = "Trip detail — not found")
@Composable
private fun DetailErrorPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        PlanDetailScreen(PlanDetailUiState(isLoading = false, error = io.github.rupakc.travelplanner.core.common.AppError.NotFound()), {}, {}, onBack = {})
    }
}

/** Shown above a trip served from the phone's cache after a failed refresh; says what is off and why. */
@Composable
private fun StaleBanner(error: io.github.rupakc.travelplanner.core.common.AppError, onRetry: () -> Unit) {
    val offline = error is io.github.rupakc.travelplanner.core.common.AppError.Network
    Surface(
        color = TravelTheme.extendedColors.warningContainer,
        contentColor = TravelTheme.extendedColors.onWarningContainer,
        shape = TravelShape.ItemCard,
        modifier = Modifier.fillMaxWidth().padding(bottom = TravelTheme.spacing.md),
    ) {
        Column(Modifier.padding(TravelTheme.spacing.md)) {
            Text(
                stringResource(if (offline) R.string.plan_detail_offline else R.string.plan_detail_stale),
                style = TravelTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            TpButton(stringResource(R.string.plan_refresh), onClick = onRetry, variant = TpButtonVariant.Text)
        }
    }
}
