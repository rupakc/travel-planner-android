package io.github.rupakc.travelplanner.feature.share.open

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.share.ShareCardSnapshot
import io.github.rupakc.travelplanner.feature.share.R
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MaxContentWidth = 640.dp

/** `period` (`YYYY-MM`) as month and year, or null when it is not one (a bad value is simply not shown). */
internal fun periodLabel(period: String?): String? =
    runCatching { DateTimeFormatter.ofPattern("LLLL yyyy").format(YearMonth.parse(period ?: return null)) }.getOrNull()

/** The plural resource of a card count chip, or null for a stat key this app does not know (it is skipped, never guessed). */
internal fun statPlural(key: String): Int? =
    when (key) {
        "activities" -> R.plurals.share_stat_activities
        "hotels" -> R.plurals.share_stat_hotels
        "places" -> R.plurals.share_stat_places
        "drops_caught" -> R.plurals.share_stat_drops_caught
        "targets_hit" -> R.plurals.share_stat_targets_hit
        else -> null
    }

/** A recap or savings share: the server's headline and count chips, plus who shared it only when they said so. No dates beyond the month. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShareCardBody(card: ShareCardSnapshot, ownerName: String?, onPlanOwn: () -> Unit, footer: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = MaxContentWidth).fillMaxSize().verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Surface(color = TravelTheme.colors.primary, contentColor = TravelTheme.colors.onPrimary, shape = TravelShape.LargeCard) {
            Column(Modifier.fillMaxWidth().padding(TravelTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                Text(
                    stringResource(R.string.share_brand, stringResource(io.github.rupakc.travelplanner.core.ui.R.string.brand_name)),
                    style = TravelTheme.typography.labelLarge,
                )
                Text(card.headline, style = TravelTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                listOfNotNull(card.destination?.takeIf(String::isNotBlank), periodLabel(card.period)).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = TravelTheme.typography.bodyMedium)
                }
            }
        }
        val chips = card.stats.mapNotNull { stat -> statPlural(stat.key)?.let { stat to it } }.filter { (stat, _) -> stat.count > 0 }
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                chips.forEach { (stat, plural) ->
                    AssistChip(onClick = {}, enabled = false, label = { Text(pluralStringResource(plural, stat.count, stat.count)) })
                }
            }
        }
        ownerName?.let { Text(stringResource(R.string.share_shared_by, it), style = TravelTheme.typography.bodyMedium) }
        TpButton(stringResource(R.string.share_plan_own), onClick = onPlanOwn, modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.share_footer),
            style = TravelTheme.typography.bodySmall,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        Column(Modifier.align(Alignment.CenterHorizontally)) { footer() }
    }
}

/**
 * A kind this app does not know (or a card it cannot read): treated as a plain link. [plainLink] is the public https page, opened in
 * a Custom Tab by [onOpenLink] (which only ever opens http/https), or null when no web host is configured.
 */
@Composable
internal fun UnknownShareBody(plainLink: String?, onOpenLink: (String) -> Unit, onPlanOwn: () -> Unit, footer: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            title = stringResource(R.string.share_unknown_title),
            message = stringResource(R.string.share_unknown_message),
            actionLabel = plainLink?.let { stringResource(R.string.share_unknown_open) },
            onAction = { plainLink?.let(onOpenLink) },
        )
        TpButton(stringResource(R.string.share_plan_own), onClick = onPlanOwn, variant = TpButtonVariant.Text)
        footer()
    }
}
