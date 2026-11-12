package io.github.rupakc.travelplanner.feature.alerts.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.alerts.SuggestionReason
import io.github.rupakc.travelplanner.feature.alerts.R

/** Suggested watches (one tap each). Copy comes from the server's `reason` code; the app builds no advice text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertSuggestionsSheet(
    load: SuggestionsLoad,
    onWatch: (Int) -> Unit,
    onDismissRow: (Int) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = TravelTheme.spacing.gutter), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            Text(stringResource(R.string.alerts_suggest_title), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.alerts_suggest_intro), color = TravelTheme.colors.onSurfaceVariant)
            SuggestionsBody(load, onWatch, onDismissRow, onRetry)
            TpButton(
                stringResource(R.string.alerts_suggest_close),
                onClose,
                Modifier.fillMaxWidth(),
                variant = TpButtonVariant.Text,
                tid = "alerts.suggest.close",
            )
        }
    }
}

@Composable
private fun SuggestionsBody(load: SuggestionsLoad, onWatch: (Int) -> Unit, onDismissRow: (Int) -> Unit, onRetry: () -> Unit) {
    when (load) {
        SuggestionsLoad.Idle, SuggestionsLoad.Loading -> {
            SkeletonList(message = stringResource(R.string.alerts_loading_short))
        }

        is SuggestionsLoad.Failed -> {
            ErrorState(load.message, title = stringResource(R.string.alerts_suggest_load_failed), onRetry = onRetry)
        }

        is SuggestionsLoad.Ready -> {
            if (load.rows.isEmpty()) {
                EmptyState(stringResource(R.string.alerts_suggest_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
                    items(load.rows, key = { it.key }, contentType = { "suggestion" }) { row -> SuggestionCard(row, onWatch, onDismissRow) }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(row: SuggestionRow, onWatch: (Int) -> Unit, onDismissRow: (Int) -> Unit) {
    val s = row.suggestion
    val title = s.title.orEmpty()
    TpCard(Modifier.fillMaxWidth()) {
        Text(title, style = TravelTheme.typography.titleMedium)
        s.subtitle?.takeIf(String::isNotBlank)?.let { Text(it, color = TravelTheme.colors.onSurfaceVariant) }
        Text(reasonText(s.reasonKind), color = TravelTheme.colors.onSurfaceVariant, style = TravelTheme.typography.labelMedium)
        StatusLine(row)
        Row(Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.sm), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            val watchLabel = stringResource(R.string.alerts_suggest_watch)
            val watchA11y = stringResource(R.string.alerts_suggest_watch_a11y, title)
            if (row.watching) {
                TpButton(
                    text =
                        stringResource(
                            if (s.alreadyWatching ||
                                row.status == SuggestionStatus.Existing
                            ) {
                                R.string.alerts_suggest_already
                            } else {
                                R.string.alerts_suggest_watching
                            },
                        ),
                    onClick = {},
                    enabled = false,
                    variant = TpButtonVariant.Tonal,
                    leadingIcon = Icons.Filled.CheckCircle,
                )
            } else {
                TpButton(
                    text = watchLabel,
                    onClick = { onWatch(row.key) },
                    modifier = Modifier.semantics { contentDescription = watchA11y },
                    loading = row.status == SuggestionStatus.Adding,
                    leadingIcon = Icons.Filled.Notifications,
                    tid = "alerts.suggest.watch",
                )
            }
            if (!row.watching) {
                val dismissA11y = stringResource(R.string.alerts_suggest_dismiss_a11y, title)
                TpButton(
                    text = stringResource(R.string.alerts_suggest_dismiss),
                    onClick = { onDismissRow(row.key) },
                    modifier = Modifier.semantics { contentDescription = dismissA11y },
                    variant = TpButtonVariant.Text,
                    tid = "alerts.suggest.dismiss",
                )
            }
        }
    }
}

@Composable
private fun StatusLine(row: SuggestionRow) {
    val text =
        when (val st = row.status) {
            SuggestionStatus.Added -> stringResource(R.string.alerts_suggest_pending)
            is SuggestionStatus.Blocked -> blockedText(st)
            else -> null
        } ?: return
    Text(
        text,
        color = if (row.status is SuggestionStatus.Blocked) TravelTheme.colors.error else TravelTheme.colors.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun blockedText(st: SuggestionStatus.Blocked): String =
    when (st.reason) {
        SuggestionBlock.LIVE_PRICES_OFF -> stringResource(R.string.alerts_suggest_live_off)
        SuggestionBlock.LIMIT -> st.detail ?: stringResource(R.string.alerts_suggest_limit)
        SuggestionBlock.INVALID -> st.detail ?: stringResource(R.string.alerts_suggest_failed)
        SuggestionBlock.FAILED -> st.detail ?: stringResource(R.string.alerts_suggest_failed)
    }

@Composable
private fun reasonText(reason: SuggestionReason): String =
    stringResource(
        when (reason) {
            SuggestionReason.SELECTED_IN_PLAN -> R.string.alerts_suggest_reason_selected_in_plan
            SuggestionReason.TRIP_TOTAL -> R.string.alerts_suggest_reason_trip_total
            SuggestionReason.DEPARTURE_SOON -> R.string.alerts_suggest_reason_departure_soon
            else -> R.string.alerts_suggest_reason_other
        },
    )
