package io.github.rupakc.travelplanner.core.ui.watch

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.ui.R

/** One row of the "watch prices for this trip?" prompt. */
@Immutable
data class WatchOfferRow(val title: String, val subtitle: String?, val state: WatchOfferState = WatchOfferState.Idle)

enum class WatchOfferState { Idle, Adding, Watching }

/** Snackbar text for a one-tap outcome (shared by chat cards, plan detail and the offer). */
fun quickWatchText(context: Context, outcome: QuickWatchOutcome): String =
    when (outcome) {
        is QuickWatchOutcome.Added -> context.getString(R.string.watch_quick_watched)
        is QuickWatchOutcome.Existing -> context.getString(R.string.watch_quick_existing)
        QuickWatchOutcome.LivePricesOff -> context.getString(R.string.watch_quick_live_off)
        is QuickWatchOutcome.Limit -> outcome.detail?.takeIf(String::isNotBlank) ?: context.getString(R.string.watch_quick_limit)
        is QuickWatchOutcome.Invalid -> context.getString(R.string.watch_quick_invalid)
        is QuickWatchOutcome.Failed -> outcome.detail?.takeIf(String::isNotBlank) ?: context.getString(R.string.watch_quick_failed)
    }

/**
 * Non-blocking, dismissible prompt offered when a plan is saved: up to a few watch suggestions, each one tap. Colours come
 * from the theme, so it follows light and dark. [tidPrefix] names the interactive elements in telemetry.
 */
@Composable
fun WatchOfferCard(
    rows: List<WatchOfferRow>,
    onWatch: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tidPrefix: String = "watch_offer",
) {
    if (rows.isEmpty()) return
    TpCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(stringResource(R.string.watch_offer_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.watch_offer_body), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            rows.forEachIndexed { i, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.title, style = TravelTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        row.subtitle?.let {
                            Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TpButton(
                        stringResource(if (row.state == WatchOfferState.Watching) R.string.watch_quick_watching else R.string.watch_offer_watch),
                        { onWatch(i) },
                        variant = TpButtonVariant.Tonal,
                        enabled = row.state == WatchOfferState.Idle,
                        loading = row.state == WatchOfferState.Adding,
                        tid = "$tidPrefix.watch",
                    )
                }
            }
            TpButton(stringResource(R.string.watch_offer_dismiss), onDismiss, variant = TpButtonVariant.Text, tid = "$tidPrefix.dismiss")
        }
    }
}
