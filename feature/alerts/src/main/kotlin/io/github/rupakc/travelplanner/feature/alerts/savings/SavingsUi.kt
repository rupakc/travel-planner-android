package io.github.rupakc.travelplanner.feature.alerts.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonBlock
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.Tone
import io.github.rupakc.travelplanner.core.model.alerts.DigestWatch
import io.github.rupakc.travelplanner.core.model.alerts.Savings
import io.github.rupakc.travelplanner.core.model.alerts.SavingsBest
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.core.ui.format.localUsd
import io.github.rupakc.travelplanner.core.ui.game.ConfettiBurst
import io.github.rupakc.travelplanner.core.ui.share.ShareEntryButton
import io.github.rupakc.travelplanner.core.ui.watch.ToneBox
import io.github.rupakc.travelplanner.feature.alerts.R
import kotlin.math.abs

/** The savings tally for the top of the Alerts screen (state from [SavingsViewModel]). */
@Composable
fun SavingsHeaderRoute(modifier: Modifier = Modifier, viewModel: SavingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SavingsHeader(state, viewModel::onEvent, modifier)
}

/** Stateless tally: nothing for [SavingsUiState.Hidden], a skeleton while loading, an error with retry, else the card. */
@Composable
fun SavingsHeader(state: SavingsUiState, onEvent: (SavingsEvent) -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        SavingsUiState.Hidden -> Unit
        SavingsUiState.Loading -> SkeletonBlock(modifier.fillMaxWidth(), height = TravelTheme.spacing.xxl)
        is SavingsUiState.Failed -> SavingsError(state.message, { onEvent(SavingsEvent.Retry) }, modifier)
        is SavingsUiState.Loaded -> SavingsCard(state.view, state.livePricesPaused, modifier)
    }
}

@Composable
private fun SavingsError(message: String?, onRetry: () -> Unit, modifier: Modifier) {
    TpCard(modifier.fillMaxWidth()) {
        Text(message ?: stringResource(R.string.alerts_savings_error), style = TravelTheme.typography.bodyMedium)
        TpButton(
            stringResource(R.string.alerts_action_retry),
            onRetry,
            Modifier.padding(top = TravelTheme.spacing.sm),
            TpButtonVariant.Secondary,
            tid = "alerts.savings.retry",
        )
    }
}

@Composable
internal fun SavingsCard(view: SavingsView, livePricesPaused: Boolean, modifier: Modifier = Modifier) {
    TpCard(modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(
                stringResource(R.string.alerts_savings_title),
                style = TravelTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            if (livePricesPaused) ToneBox(Tone.WARN) { Text(stringResource(R.string.alerts_savings_paused), style = TravelTheme.typography.bodySmall) }
            if (view.isEmpty) {
                Text(stringResource(R.string.alerts_savings_empty), style = TravelTheme.typography.bodyMedium)
                Text(stringResource(R.string.alerts_savings_empty_hint), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            } else {
                SavingsBody(view)
                // Share links 2.0: draws nothing without `features.sharing` (core:ui reads the shell's launcher).
                ShareEntryButton(ShareKind.SAVINGS, null, tid = "alerts.savings.share")
            }
        }
    }
}

@Composable
private fun SavingsBody(view: SavingsView) {
    if (view.totalUsd > 0) {
        Text(
            // The card title already says "Drops caught"; the headline is just the amount.
            localUsd(view.totalUsd),
            style = TravelTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Milestone(view)
    }
    if (view.otherCurrencies.isNotEmpty()) {
        Text(stringResource(R.string.alerts_savings_other_currencies), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
        view.otherCurrencies.forEach { (code, amount) ->
            Text(localPrice(amount, code), style = TravelTheme.typography.bodyMedium)
        }
    }
    Text(
        pluralStringResource(R.plurals.alerts_savings_drops, view.drops, view.drops) +
            if (view.targets > 0) " · " + pluralStringResource(R.plurals.alerts_savings_targets, view.targets, view.targets) else "",
        style = TravelTheme.typography.bodyMedium,
    )
    view.best?.let { best -> Text(bestText(best), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
    Text(stringResource(R.string.alerts_savings_caveat), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
}

@Composable
private fun bestText(best: SavingsBest): String {
    val amount = localPrice(best.caught, best.currency)
    return if (best.title.isNullOrBlank()) {
        stringResource(
            R.string.alerts_savings_best_untitled,
            amount,
        )
    } else {
        stringResource(R.string.alerts_savings_best, best.title.orEmpty(), amount)
    }
}

@Composable
private fun Milestone(view: SavingsView) {
    val next = view.nextMilestone
    val label =
        if (next ==
            null
        ) {
            stringResource(R.string.alerts_savings_all_done)
        } else {
            stringResource(R.string.alerts_savings_progress, view.progressPct, localUsd(next.toDouble()))
        }
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        LinearProgressIndicator(
            progress = { view.progressPct / PERCENT },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = label
                        progressBarRangeInfo = ProgressBarRangeInfo(view.progressPct.toFloat(), 0f..PERCENT)
                    },
        )
        Text(label, style = TravelTheme.typography.labelMedium)
    }
}

/** The "caught a drop" block under a drop notification (target-hit variant, optional XP line per R4). */
@Composable
internal fun DropBlock(drop: DropView, modifier: Modifier = Modifier) {
    Box(modifier) {
        ToneBox(Tone.GOOD, Modifier) {
            Text(
                stringResource(if (drop.targetHit) R.string.alerts_drop_target else R.string.alerts_drop_title),
                style = TravelTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            drop.amount?.let { amount ->
                val money = localPrice(amount, drop.currency)
                Text(
                    drop.percent?.let { stringResource(R.string.alerts_drop_caught_pct, money, it) } ?: stringResource(R.string.alerts_drop_caught, money),
                    style = TravelTheme.typography.bodyMedium,
                )
            }
            drop.to?.let { Text(stringResource(R.string.alerts_drop_now_at, localPrice(it, drop.currency)), style = TravelTheme.typography.bodySmall) }
            drop.xp?.let { Text(stringResource(R.string.alerts_drop_xp, it), style = TravelTheme.typography.labelMedium) }
            Text(stringResource(R.string.alerts_drop_note), style = TravelTheme.typography.bodySmall)
        }
        if (drop.targetHit) ConfettiBurst(Modifier.matchParentSize())
    }
}

/** The weekly digest summary under a `price_digest` notification. */
@Composable
internal fun DigestBlock(digest: DigestView, modifier: Modifier = Modifier) {
    ToneBox(Tone.INFO, modifier) {
        digest.week?.let { Text(stringResource(R.string.alerts_digest_week, it), style = TravelTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
        digest.rows.forEach { DigestRow(it) }
        if (digest.more > 0) Text(pluralStringResource(R.plurals.alerts_digest_more, digest.more, digest.more), style = TravelTheme.typography.labelMedium)
        digest.savings?.takeIf { it.totalUsd > 0 }?.let {
            Text(stringResource(R.string.alerts_savings_amount, localUsd(it.totalUsd)), style = TravelTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DigestRow(w: DigestWatch) {
    Row(Modifier.fillMaxWidth().heightIn(min = TravelTheme.spacing.lg), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(w.title.orEmpty(), style = TravelTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val change = w.change7d
            if (change != null && change != 0.0) {
                val money = localPrice(abs(change), w.currency)
                Text(
                    stringResource(if (change < 0) R.string.alerts_digest_down else R.string.alerts_digest_up, money),
                    style = TravelTheme.typography.bodySmall,
                )
            }
        }
        w.latest?.let { Text(localPrice(it, w.currency), style = TravelTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
    }
}

private const val PERCENT = 100f

@Preview(name = "Savings · light")
@Composable
private fun SavingsPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        SavingsHeader(
            SavingsUiState.Loaded(
                Savings(
                    totalUsd = 245.0,
                    dropsCaught = 4,
                    targetsHit = 1,
                    nextMilestone = 1000,
                    progressPct = 24,
                    best = SavingsBest("hotel", 3, "Lumen Hotel", 120.0, "USD"),
                ).view(),
                livePricesPaused = true,
            ),
            {},
        )
    }
}

@Preview(name = "Savings · dark, empty")
@Composable
private fun SavingsEmptyPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { SavingsHeader(SavingsUiState.Loaded(Savings().view(), livePricesPaused = false), {}) }
}
