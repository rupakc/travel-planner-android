package io.github.rupakc.travelplanner.feature.alerts.story

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.alerts.AlertKind
import io.github.rupakc.travelplanner.core.model.alerts.AlertsAdviceCode
import io.github.rupakc.travelplanner.core.model.alerts.PriceStory
import io.github.rupakc.travelplanner.core.model.alerts.StoryVerdict
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.feature.alerts.R

private const val SKELETON_LINES = 4

/** The "Price story" bottom sheet of one watch; [title] is the watch's heading. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PriceStorySheet(
    kind: AlertKind,
    watchId: Long,
    title: String?,
    onDismiss: () -> Unit,
    viewModel: PriceStoryViewModel = hiltViewModel(key = "story-${kind.wire}-$watchId"),
) {
    LaunchedEffect(kind, watchId) { viewModel.load(kind, watchId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PriceStoryContent(state, title, viewModel::retry, viewModel::requestTip, onDismiss)
    }
}

/** Stateless story panel: verdict, advice, "checked N days ago", chart, legend and the flight timing tip. */
@Composable
internal fun PriceStoryContent(
    state: PriceStoryUiState,
    title: String?,
    onRetry: () -> Unit,
    onTip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        Text(stringResource(R.string.alerts_story_title), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        title?.let { Text(it, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant) }
        val story = state.story
        val error = state.error
        when {
            state.loading -> {
                SkeletonList(message = stringResource(R.string.alerts_story_loading), lines = SKELETON_LINES)
            }

            error != null -> {
                Text(
                    storyErrorText(error, R.string.alerts_story_error_load),
                    style = TravelTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (error.failure != TipFailure.NOT_FOUND) {
                    TpButton(
                        stringResource(R.string.alerts_retry),
                        onRetry,
                        variant = TpButtonVariant.Secondary,
                        leadingIcon = Icons.Filled.Refresh,
                        tid = "alerts.story.retry",
                    )
                }
            }

            story != null -> {
                StoryBody(story, state, onTip)
            }
        }
        HorizontalDivider(color = TravelTheme.colors.outlineVariant)
        TpButton(stringResource(R.string.alerts_story_close), onClose, variant = TpButtonVariant.Text, tid = "alerts.story.close")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryBody(story: PriceStory, state: PriceStoryUiState, onTip: () -> Unit) {
    val currency = story.currency
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        VerdictChip(story.verdictKind)
        checkedAgeDays(story)?.let { days ->
            Text(
                if (days == 0) stringResource(R.string.alerts_story_checked_today) else pluralStringResource(R.plurals.alerts_story_checked_ago, days, days),
                style = TravelTheme.typography.labelLarge,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
    story.latest?.price?.let { Text(stringResource(R.string.alerts_story_latest) + " " + localPrice(it, currency), style = TravelTheme.typography.titleMedium) }
    Text(adviceText(story), style = TravelTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    story.estimate?.price?.let { Text(stringResource(R.string.alerts_story_estimate, localPrice(it, currency)), style = TravelTheme.typography.bodyMedium) }
    story.caught?.takeIf {
        it > 0
    }?.let { Text(stringResource(R.string.alerts_story_caught, localPrice(it, currency)), style = TravelTheme.typography.bodyMedium) }
    StoryChart(chartModel(story), observedCount(story), currency)
    Legend(story)
    if (state.tipAvailable) TipSection(state.tip, onTip)
}

@Composable
private fun VerdictChip(verdict: StoryVerdict) {
    if (verdict == StoryVerdict.UNKNOWN) return
    val label = stringResource(verdictRes(verdict))
    val (container, content) = verdictColors(verdict)
    val a11y = stringResource(R.string.alerts_story_verdict_label, label)
    Surface(shape = TravelShape.Pill, color = container, modifier = Modifier.semantics { contentDescription = a11y }) {
        Text(
            label,
            style = TravelTheme.typography.labelLarge,
            color = content,
            modifier = Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs),
        )
    }
}

@Composable
private fun verdictColors(verdict: StoryVerdict): Pair<Color, Color> {
    val ext = TravelTheme.extendedColors
    return when (verdict) {
        StoryVerdict.GREAT, StoryVerdict.GOOD -> ext.successContainer to ext.onSuccessContainer
        StoryVerdict.TYPICAL -> ext.infoContainer to ext.info
        StoryVerdict.HIGH -> ext.warningContainer to ext.onWarningContainer
        StoryVerdict.UNKNOWN -> TravelTheme.colors.surface to TravelTheme.colors.onSurface
    }
}

private fun verdictRes(verdict: StoryVerdict): Int =
    when (verdict) {
        StoryVerdict.GREAT -> R.string.alerts_story_verdict_great
        StoryVerdict.GOOD -> R.string.alerts_story_verdict_good
        StoryVerdict.TYPICAL -> R.string.alerts_story_verdict_typical
        StoryVerdict.HIGH, StoryVerdict.UNKNOWN -> R.string.alerts_story_verdict_high
    }

/** The advice line from the server's `advice.code` and numbers (never recomputed); an unknown code shows a generic line. */
@Composable
internal fun adviceText(story: PriceStory): String {
    val p = story.advice?.params
    val currency = story.currency
    return when (story.adviceCode) {
        AlertsAdviceCode.PENDING -> {
            stringResource(R.string.alerts_story_advice_pending)
        }

        AlertsAdviceCode.CHECKS_PAUSED -> {
            (p?.days ?: story.checkedDaysAgo ?: 0).let {
                pluralStringResource(R.plurals.alerts_story_advice_checks_paused, it, it)
            }
        }

        AlertsAdviceCode.BELOW_TARGET -> {
            stringResource(R.string.alerts_story_advice_below_target, localPrice(p?.by, currency))
        }

        AlertsAdviceCode.NEAR_TARGET -> {
            stringResource(R.string.alerts_story_advice_near_target, localPrice(p?.gap, currency))
        }

        AlertsAdviceCode.AT_LOWEST -> {
            stringResource(R.string.alerts_story_advice_at_lowest)
        }

        AlertsAdviceCode.BELOW_TYPICAL -> {
            stringResource(R.string.alerts_story_advice_below_typical)
        }

        AlertsAdviceCode.WITHIN_TYPICAL -> {
            stringResource(R.string.alerts_story_advice_within_typical)
        }

        AlertsAdviceCode.ABOVE_TYPICAL -> {
            stringResource(R.string.alerts_story_advice_above_typical)
        }

        AlertsAdviceCode.STEADY -> {
            (p?.days ?: 0).let { pluralStringResource(R.plurals.alerts_story_advice_steady, it, it) }
        }

        AlertsAdviceCode.FEW_CHECKS -> {
            (p?.n ?: story.points.size).let { pluralStringResource(R.plurals.alerts_story_advice_few_checks, it, it) }
        }

        AlertsAdviceCode.UNKNOWN -> {
            stringResource(R.string.alerts_story_advice_generic)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(story: PriceStory) {
    val currency = story.currency
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        val style = TravelTheme.typography.bodySmall
        val color = TravelTheme.colors.onSurfaceVariant
        val low = story.typical?.low
        val high = story.typical?.high
        if (low != null && high != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                Text(stringResource(R.string.alerts_story_chart_typical), style = style, color = color)
                Text("${localPrice(low, currency)} – ${localPrice(high, currency)}", style = style, color = color)
            }
        }
        story.lowest?.price?.let { Text(stringResource(R.string.alerts_story_chart_lowest, localPrice(it, currency)), style = style, color = color) }
        story.target?.let { Text(stringResource(R.string.alerts_story_chart_target, localPrice(it, currency)), style = style, color = color) }
        story.baseline?.let { Text(stringResource(R.string.alerts_story_chart_baseline, localPrice(it, currency)), style = style, color = color) }
    }
}

@Composable
private fun TipSection(tip: TipState, onTip: () -> Unit) {
    HorizontalDivider(color = TravelTheme.colors.outlineVariant)
    Text(stringResource(R.string.alerts_story_tip_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    when (tip) {
        TipState.Idle -> {
            TpButton(stringResource(R.string.alerts_story_tip_button), onTip, variant = TpButtonVariant.Secondary, tid = "alerts.story.tip")
        }

        TipState.Loading -> {
            SkeletonList(message = stringResource(R.string.alerts_story_tip_loading), lines = 2)
        }

        is TipState.Ready -> {
            Text(tip.tip, style = TravelTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Text(stringResource(R.string.alerts_story_tip_ai), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
        }

        is TipState.Failed -> {
            Text(
                storyErrorText(tip.error, R.string.alerts_story_tip_error),
                style = TravelTheme.typography.bodyMedium,
                modifier =
                    Modifier.semantics {
                        liveRegion =
                            LiveRegionMode.Polite
                    },
            )
            if (tip.error.failure != TipFailure.LIMIT && tip.error.failure != TipFailure.BILLING && tip.error.failure != TipFailure.NO_DATA) {
                TpButton(stringResource(R.string.alerts_retry), onTip, variant = TpButtonVariant.Text, tid = "alerts.story.tip_retry")
            }
        }
    }
}

/** Translated text for a known code; otherwise the backend text, else [fallback]. */
@Composable
internal fun storyErrorText(error: StoryError, fallback: Int): String =
    when (error.failure) {
        TipFailure.LIMIT -> stringResource(R.string.alerts_story_tip_limit)
        TipFailure.BILLING -> stringResource(R.string.alerts_story_tip_billing)
        TipFailure.NO_DATA -> stringResource(R.string.alerts_story_tip_no_data)
        TipFailure.UNSUPPORTED -> stringResource(R.string.alerts_story_tip_unsupported)
        TipFailure.NOT_FOUND -> stringResource(R.string.alerts_story_not_found)
        TipFailure.FAILED -> stringResource(fallback)
        TipFailure.OTHER -> error.message ?: stringResource(fallback)
    }
