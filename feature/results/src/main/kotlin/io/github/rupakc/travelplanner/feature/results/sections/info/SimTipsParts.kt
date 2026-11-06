package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.NetworkQuality
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.core.model.section.SimPlan
import io.github.rupakc.travelplanner.core.model.section.Tip
import io.github.rupakc.travelplanner.core.model.section.TipsData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.core.ui.PriceText
import io.github.rupakc.travelplanner.feature.results.R

// ---- SIM ----------------------------------------------------------------------------------

/** Coverage chip tone (web: excellent green, good blue, moderate yellow, other red). */
internal fun coverageTone(rating: String?): Tone =
    when (rating) {
        "excellent" -> Tone.Success
        "good" -> Tone.Info
        "moderate" -> Tone.Warning
        else -> Tone.Danger
    }

@Composable
internal fun SimBody(
    data: SimData,
    isSelected: (PlanItem) -> Boolean,
    onToggle: (PlanItem) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (data.plans.isEmpty()) {
        EmptyState(stringResource(R.string.sim_empty), modifier)
        return
    }
    AdaptiveGrid(data.plans, modifier) { plan, cell ->
        val item = PlanItem.SimItem(plan)
        SimCard(plan, isSelected(item), { onToggle(item) }, onOpenLink, cell)
    }
}

@Composable
private fun SimCard(plan: SimPlan, selected: Boolean, onToggle: () -> Unit, onOpenLink: (String) -> Unit, modifier: Modifier) {
    val accent = TravelTheme.sectionAccents[SectionId.SIM]
    TpCard(modifier.fillMaxWidth(), selected = selected, accent = accent.bar) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(plan.provider.orEmpty(), style = TravelTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                PriceText(plan.priceUsd, style = TravelTheme.typography.titleMedium, hideWhenUnknown = true)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
                plan.planName?.let { MetaText(it) }
                CityChip(cityOf(plan.raw))
            }
            MetaText(plan.dataGb?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.sim_data, it) } ?: stringResource(R.string.sim_unlimited))
            plan.validityDays?.takeIf { it > 0 }?.let { MetaText(pluralStringResource(R.plurals.sim_validity, it, it)) }
            plan.networkQuality?.let { NetworkLine(it) }
            LinkAndAddRow(
                link = { LinkButton(stringResource(R.string.sim_get_plan), plan.url, onOpenLink) },
                add = { AddToPlanButton(selected, listOfNotNull(plan.provider, plan.planName).joinToString(" "), onToggle) },
            )
        }
    }
}

@Composable
private fun NetworkLine(quality: NetworkQuality) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
        quality.speed?.takeIf { it.isNotBlank() }?.let { MetaText(it) }
        quality.coverageRating?.takeIf { it.isNotBlank() }?.let { InfoPill(it, tone = coverageTone(it)) }
    }
    quality.coverageDescription?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
    }
}

// ---- Tips ---------------------------------------------------------------------------------

/** Web `sc` map: danger / warning / info (anything else reads as info). */
internal enum class TipSeverity(val tone: Tone, val icon: ImageVector, val label: Int) {
    DANGER(Tone.Danger, Icons.Filled.Warning, R.string.tips_severity_danger),
    WARNING(Tone.Warning, Icons.Filled.Warning, R.string.tips_severity_warning),
    INFO(Tone.Info, Icons.Filled.Info, R.string.tips_severity_info),
    ;

    companion object {
        fun of(severity: String?): TipSeverity =
            when (severity) {
                "danger" -> DANGER
                "warning" -> WARNING
                else -> INFO
            }
    }
}

@Composable
internal fun TipsBody(
    data: TipsData,
    isSelected: (PlanItem) -> Boolean,
    onToggle: (PlanItem) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (data.tips.isEmpty()) {
        EmptyState(stringResource(R.string.tips_empty), modifier)
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        data.tips.forEach { tip ->
            val item = PlanItem.TipItem(tip)
            TipRow(tip, isSelected(item), { onToggle(item) }, onOpenLink)
        }
    }
}

@Composable
private fun TipRow(tip: Tip, selected: Boolean, onToggle: () -> Unit, onOpenLink: (String) -> Unit) {
    val severity = TipSeverity.of(tip.severity)
    val colors = toneColors(severity.tone)
    val border = if (selected) TravelTheme.sectionAccents[SectionId.TIPS].bar else colors.content.copy(alpha = BORDER_ALPHA)
    Surface(
        shape = TravelShape.ItemCard,
        color = colors.container,
        border = BorderStroke(if (selected) InfoDimens.Border * 2 else InfoDimens.Border, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(TravelTheme.spacing.md), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            Icon(severity.icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(InfoDimens.SmallIcon))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tip.title.orEmpty(), style = TravelTheme.typography.titleSmall)
                    InfoPill(stringResource(severity.label), tone = severity.tone)
                    tip.category?.takeIf { it.isNotBlank() }?.let { InfoPill(it) }
                    CityChip(cityOf(tip.raw))
                }
                tip.body?.let { Text(it, style = TravelTheme.typography.bodyMedium, color = colors.content) }
                LinkAndAddRow(
                    link = { LinkButton(stringResource(R.string.info_source), tip.sourceUrl, onOpenLink) },
                    add = { AddToPlanButton(selected, tip.title.orEmpty(), onToggle) },
                )
            }
        }
    }
}

private const val BORDER_ALPHA = 0.3f

private val previewSim =
    SimData(
        plans =
            listOf(
                SimPlan(
                    provider = "Vodafone PT",
                    planName = "Tourist 15GB",
                    dataGb = "15",
                    validityDays = 15,
                    priceUsd = 20.0,
                    url = "https://example.org/sim",
                ),
                SimPlan(
                    provider = "Airalo",
                    planName = "Portugal eSIM",
                    validityDays = 7,
                    priceUsd = 9.5,
                    networkQuality = NetworkQuality("4G/5G", "excellent"),
                ),
            ),
    )

private val previewTips =
    TipsData(
        tips =
            listOf(
                Tip(title = "Pickpockets on Tram 28", body = "Keep bags zipped and in front.", category = "Safety", severity = "warning"),
                Tip(title = "Tap water is safe", body = "Refill bottles freely.", category = "Health", severity = "info", sourceUrl = "https://example.org"),
            ),
    )

@Preview(name = "SIM and tips – light")
@Composable
private fun SimTipsLightPreview() =
    PreviewSurface(ThemeMode.LIGHT) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            SimBody(previewSim, { it is PlanItem.SimItem && it.plan.provider == "Airalo" }, {}, {})
            TipsBody(previewTips, { false }, {}, {})
        }
    }

@Preview(name = "SIM and tips – dark")
@Composable
private fun SimTipsDarkPreview() =
    PreviewSurface(ThemeMode.DARK) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            SimBody(previewSim, { false }, {}, {})
            TipsBody(previewTips, { true }, {}, {})
        }
    }
