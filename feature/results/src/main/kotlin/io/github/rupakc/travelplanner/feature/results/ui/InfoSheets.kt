package io.github.rupakc.travelplanner.feature.results.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.LoadingIndicator
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.ui.PriceText
import io.github.rupakc.travelplanner.feature.results.LayoverUi
import io.github.rupakc.travelplanner.feature.results.LoadStatus
import io.github.rupakc.travelplanner.feature.results.NoLivePriceUi
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.VerificationDetail
import io.github.rupakc.travelplanner.feature.results.sections.commerce.Pill
import io.github.rupakc.travelplanner.feature.results.sections.commerce.PillTone
import io.github.rupakc.travelplanner.feature.results.sections.formatHours

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(
                rememberScrollState(),
            ).padding(horizontal = TravelTheme.spacing.gutter).padding(bottom = TravelTheme.spacing.xl).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) { content() }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })

/** Web LayoverModal: can you leave the airport, transit visa, a mini-plan, buffers, airside alternative. */
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun LayoverSheet(ui: LayoverUi, onDismiss: () -> Unit) {
    val place = ui.layover.city ?: ui.layover.airport.orEmpty()
    Sheet(onDismiss) {
        Title(stringResource(R.string.results_layover_title, ui.layover.durationHours?.let(::formatHours).orEmpty(), place))
        when (ui.status) {
            LoadStatus.IDLE, LoadStatus.LOADING -> {
                LoadingIndicator(label = stringResource(R.string.results_layover_loading))
            }

            LoadStatus.ERROR -> {
                Text(
                    ui.error?.message ?: stringResource(R.string.results_layover_error),
                    color = TravelTheme.colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            LoadStatus.DONE -> {
                val plan = ui.plan ?: return@Sheet
                val canLeave = plan.feasibleToExit == true
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Pill(
                        stringResource(if (canLeave) R.string.results_layover_leave else R.string.results_layover_airside),
                        tone = if (canLeave) PillTone.Positive else PillTone.Warning,
                    )
                    plan.verdictSummary?.let { Text(it, style = TravelTheme.typography.bodyMedium) }
                    if (canLeave) {
                        plan.usableCityHours?.takeIf { it > 0 }?.let {
                            Text(
                                stringResource(R.string.results_layover_usable, formatHours(it)),
                                style = TravelTheme.typography.bodySmall,
                                color = TravelTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                plan.transitVisa?.let { v ->
                    Text(
                        listOfNotNull(
                            stringResource(
                                if (v.required ==
                                    true
                                ) {
                                    R.string.results_layover_visa_required
                                } else {
                                    R.string.results_layover_visa_none
                                },
                            ),
                            v.notes,
                        ).joinToString(" "),
                        style = TravelTheme.typography.bodyMedium,
                    )
                }
                if (plan.plan.isNotEmpty()) {
                    Text(stringResource(R.string.results_layover_plan), style = TravelTheme.typography.titleSmall)
                    plan.plan.forEach { step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            step.timeSlot?.let { Pill(it, tone = PillTone.Info) }
                            Text(step.activity.orEmpty(), style = TravelTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            step.costUsd?.let { PriceText(it, isEstimate = true, style = TravelTheme.typography.labelMedium) }
                        }
                    }
                }
                plan.bufferAdvice?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.extendedColors.info) }
                plan.airsideAlternative?.let { Text(stringResource(R.string.results_layover_airside_alt, it), style = TravelTheme.typography.bodySmall) }
                plan.notes.forEach { Text("• $it", style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
            }
        }
    }
}

/** Web verification popover: label, summary, checks (pass / fail / note) each with an optional "Source" link. */
@Composable
internal fun VerificationSheet(detail: VerificationDetail, onOpenLink: (String) -> Unit, onDismiss: () -> Unit) {
    val v = detail.verification
    Sheet(onDismiss) {
        Title(stringResource(R.string.results_verification_title, v.label.orEmpty()))
        v.summary?.let { Text(it, style = TravelTheme.typography.bodyMedium) }
        v.checks.forEachIndexed { i, check ->
            if (i > 0) HorizontalDivider(color = TravelTheme.colors.outlineVariant)
            val (icon, label, tint) =
                when (check.status) {
                    "pass" -> Triple(Icons.Filled.CheckCircle, R.string.results_check_pass, TravelTheme.extendedColors.success)
                    "fail" -> Triple(Icons.Filled.Clear, R.string.results_check_fail, TravelTheme.colors.error)
                    else -> Triple(Icons.Filled.Info, R.string.results_check_note, TravelTheme.colors.onSurfaceVariant)
                }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                Icon(icon, contentDescription = stringResource(label), tint = tint)
                Column(Modifier.weight(1f)) {
                    check.label?.let { Text(it, style = TravelTheme.typography.titleSmall) }
                    check.detail?.let { Text(it, style = TravelTheme.typography.bodySmall) }
                    check.url?.takeIf { it.startsWith("http") }?.let { url ->
                        TpButton(stringResource(R.string.results_source), onClick = { onOpenLink(url) }, variant = TpButtonVariant.Text)
                    }
                }
            }
        }
    }
}

/** 422 `no_live_price`: the backend's message and "Live prices we did find:" with "Watch this one instead". */
@Composable
internal fun NoLivePriceSheet(
    ui: NoLivePriceUi,
    onWatch: (io.github.rupakc.travelplanner.core.model.alerts.PriceSuggestion) -> Unit,
    onOpenLink: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        Title(ui.itemName)
        Text(
            ui.message ?: stringResource(R.string.results_no_live_price),
            style = TravelTheme.typography.bodyMedium,
            modifier =
                Modifier.semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                },
        )
        if (ui.suggestions.isNotEmpty()) {
            Text(stringResource(R.string.results_live_prices_found), style = TravelTheme.typography.titleSmall)
            ui.suggestions.forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                    Text(s.title.orEmpty(), style = TravelTheme.typography.titleSmall)
                    listOfNotNull(s.subtitle, s.price?.let { formatPrice(it) }).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                        if (s.spec !=
                            null
                        ) {
                            TpButton(stringResource(R.string.results_watch_this_instead), onClick = { onWatch(s) }, variant = TpButtonVariant.Tonal)
                        }
                        s.link?.takeIf {
                            it.startsWith("http")
                        }?.let { TpButton(stringResource(R.string.results_open_link), onClick = { onOpenLink(it) }, variant = TpButtonVariant.Text) }
                    }
                }
            }
        }
        TpButton(stringResource(R.string.results_done), onClick = onDismiss, variant = TpButtonVariant.Secondary)
    }
}
