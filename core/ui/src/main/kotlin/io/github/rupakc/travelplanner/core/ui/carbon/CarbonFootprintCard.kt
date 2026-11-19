package io.github.rupakc.travelplanner.core.ui.carbon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.R as DsR
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonBlock
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonCardState
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonView
import io.github.rupakc.travelplanner.core.model.carbon.CarbonCategory
import io.github.rupakc.travelplanner.core.model.carbon.CarbonFootprint
import io.github.rupakc.travelplanner.core.model.carbon.CarbonHint
import io.github.rupakc.travelplanner.core.model.carbon.CarbonLine
import io.github.rupakc.travelplanner.core.model.carbon.CarbonSource
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.ui.R
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener

private const val REFRESHING_ALPHA = 0.7f
private const val TONNE_KG = 1000.0
private const val DEFAULT_BUDGET_KG = 2000.0
private val BarHeight = 10.dp
private val LegendDot = 8.dp

/**
 * The trip's carbon footprint (web `CarbonFootprint`): total, per person, a breakdown bar with a legend,
 * comparisons, the lower-carbon train hint, a meals picker and "How we calculate" (every line with its method,
 * the assumptions and the sources, with each crawler number's own source). Renders nothing while [state] is
 * [CarbonCardState.Hidden]. [compact] is the My Plan form (smaller title and total).
 */
@Composable
fun CarbonFootprintCard(
    state: CarbonCardState,
    onDiet: (CarbonDiet) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (state is CarbonCardState.Hidden) return
    var open by rememberSaveable { mutableStateOf(false) }
    TpCard(modifier.fillMaxWidth().testTag("carbon_footprint")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.carbon_title),
                style = if (compact) TravelTheme.typography.titleSmall else TravelTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state is CarbonCardState.Ready) {
                val stateText = stringResource(if (open) R.string.watch_expanded else R.string.watch_collapsed)
                TpTextButton(onClick = { open = !open }, tid = "carbon.how", modifier = Modifier.semantics { stateDescription = stateText }) {
                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(TravelTheme.spacing.xs))
                    Text(stringResource(R.string.carbon_how), style = TravelTheme.typography.labelMedium)
                }
            }
        }
        when (state) {
            is CarbonCardState.Loading -> {
                Text(
                    stringResource(R.string.carbon_loading),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = TravelTheme.spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
                )
                SkeletonBlock(Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.sm))
            }

            is CarbonCardState.Failed -> {
                Text(
                    stringResource(R.string.carbon_error),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = TravelTheme.spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
                )
                TpTextButton(onClick = onRetry, tid = "carbon.retry") { Text(stringResource(DsR.string.ds_retry)) }
            }

            is CarbonCardState.Ready -> {
                Column(Modifier.alpha(if (state.refreshing) REFRESHING_ALPHA else 1f)) {
                    ReadyBody(state.footprint, compact)
                    DietPicker(state.diet, onDiet)
                    if (open) HowWeCalculate(state.footprint)
                }
            }

            is CarbonCardState.Hidden -> {
                Unit
            }
        }
    }
}

@Composable
private fun ReadyBody(footprint: CarbonFootprint, compact: Boolean) {
    val total = footprint.totalKg ?: 0.0
    val locale = currentCarbonLocale()
    Row(
        Modifier.padding(top = TravelTheme.spacing.sm).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            stringResource(R.string.carbon_total, CarbonFormat.kg0(total, locale)),
            style = if (compact) TravelTheme.typography.titleMedium else TravelTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("carbon_total"),
        )
        if ((footprint.travellers ?: 1) > 1 && footprint.perPersonKg != null) {
            Spacer(Modifier.width(TravelTheme.spacing.sm))
            Text(
                stringResource(R.string.carbon_per_person, CarbonFormat.kg0(footprint.perPersonKg ?: 0.0, locale)),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
    // As on the web, only the bar, legend and comparisons need a positive total; live sources and the hint always show.
    if (total > 0) {
        Breakdown(CarbonView.visibleCategories(footprint))
        Comparisons(footprint)
    }
    LiveSources(CarbonView.liveSources(footprint))
    CarbonView.bestHint(footprint)?.let { RailHint(it) }
}

@Composable
private fun Breakdown(categories: List<CarbonCategory>) {
    if (categories.isEmpty()) return
    val locale = currentCarbonLocale()
    val rows = categories.map { c -> Triple(c, categoryColor(c.key), categoryLabel(c.key)) }
    val description = rows.joinToString(", ") { (c, _, label) -> "$label ${CarbonView.pctLabel(c.share)}" }
    val barText = stringResource(R.string.carbon_bar_a11y, description)
    Row(
        Modifier
            .padding(top = TravelTheme.spacing.sm)
            .fillMaxWidth()
            .height(BarHeight)
            .clip(CircleShape)
            .background(TravelTheme.colors.surfaceVariant)
            .clearAndSetSemantics { contentDescription = barText },
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        val weights = rows.map { (c, _, _) -> (c.share ?: 0.0).toFloat().coerceAtLeast(MIN_WEIGHT) }
        rows.forEachIndexed { i, (c, color, _) ->
            Box(Modifier.weight(weights[i]).fillMaxHeight().background(color).testTag("carbon_bar_${c.key}"))
        }
        // Web: each segment is `share * 100%` wide, so shares that add up to less than 1 leave the grey track showing.
        val rest = 1f - weights.sum()
        if (rest > MIN_WEIGHT) Spacer(Modifier.weight(rest).testTag("carbon_bar_rest"))
    }
    Column(Modifier.padding(top = TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
        rows.forEach { (c, color, label) ->
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(LegendDot).clip(CircleShape).background(color))
                Spacer(Modifier.width(TravelTheme.spacing.sm))
                Text(label, style = TravelTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.carbon_kg, CarbonFormat.kg0(c.kg ?: 0.0, locale)),
                    style = TravelTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(TravelTheme.spacing.sm))
                Text(CarbonView.pctLabel(c.share), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
        }
    }
}

private const val MIN_WEIGHT = 0.001f

@Composable
private fun Comparisons(footprint: CarbonFootprint) {
    val cmp = footprint.comparisons ?: return
    val locale = currentCarbonLocale()
    val carKm = cmp.carKm ?: 0.0
    val share = cmp.budgetShare ?: 0.0
    if (carKm <= 0 && share <= 0) return
    Column(Modifier.padding(top = TravelTheme.spacing.sm)) {
        if (carKm > 0) Small(stringResource(R.string.carbon_car_km, CarbonFormat.number(carKm, locale)))
        if (share > 0) {
            val tonnes = (cmp.annualBudgetKg?.takeIf { it > 0 } ?: DEFAULT_BUDGET_KG) / TONNE_KG
            Small(stringResource(R.string.carbon_budget, CarbonView.pctLabel(share), CarbonFormat.number(tonnes, locale)))
        }
    }
}

@Composable
private fun LiveSources(sources: List<CarbonSource>) {
    if (sources.isEmpty()) return
    Column(Modifier.padding(top = TravelTheme.spacing.sm).testTag("carbon_live_sources")) {
        Small(stringResource(R.string.carbon_live_data))
        sources.forEach { SourceLine(it) }
    }
}

@Composable
private fun RailHint(hint: CarbonHint) {
    val locale = currentCarbonLocale()
    val x = TravelTheme.extendedColors
    Surface(
        Modifier.padding(top = TravelTheme.spacing.sm).fillMaxWidth().testTag("carbon_rail_hint"),
        shape = TravelShape.ItemCard,
        color = x.successContainer,
        contentColor = x.onSuccessContainer,
    ) {
        Text(
            stringResource(
                R.string.carbon_rail_hint,
                hint.route.orEmpty(),
                CarbonFormat.number(hint.railKm ?: 0.0, locale),
                CarbonFormat.kg0(hint.savingKg ?: 0.0, locale),
            ),
            style = TravelTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xs),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DietPicker(diet: CarbonDiet, onDiet: (CarbonDiet) -> Unit) {
    Text(
        stringResource(R.string.carbon_diet_label),
        style = TravelTheme.typography.labelMedium,
        color = TravelTheme.colors.onSurfaceVariant,
        modifier = Modifier.padding(top = TravelTheme.spacing.md),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        CarbonDiet.entries.forEach { d ->
            TpChip(label = dietLabel(d), selected = d == diet, onClick = { if (d != diet) onDiet(d) }, tid = "carbon.diet")
        }
    }
}

@Composable
private fun HowWeCalculate(footprint: CarbonFootprint) {
    val locale = currentCarbonLocale()
    Surface(
        Modifier.padding(top = TravelTheme.spacing.md).fillMaxWidth().testTag("carbon_how_panel"),
        shape = TravelShape.ItemCard,
        color = TravelTheme.colors.surfaceVariant,
        contentColor = TravelTheme.colors.onSurfaceVariant,
    ) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(stringResource(R.string.carbon_how_intro), style = TravelTheme.typography.bodySmall)
            PanelTitle(stringResource(R.string.carbon_lines_title))
            footprint.lines.forEach { line -> LineRow(line, locale) }
            if (footprint.assumptions.isNotEmpty()) {
                PanelTitle(stringResource(R.string.carbon_assumptions_title))
                footprint.assumptions.forEach { a -> Small("• " + assumptionText(a.code, a.params, a.text)) }
            }
            PanelTitle(stringResource(R.string.carbon_sources_title))
            footprint.sources.forEach { s -> SourceRow(s) }
        }
    }
}

@Composable
private fun LineRow(line: CarbonLine, locale: java.util.Locale) {
    Column {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
            methodLabel(line.method)?.let { MethodBadge(it, line.method) }
            Spacer(Modifier.width(TravelTheme.spacing.sm))
            Text(lineText(line.kind, line.params, line.label), style = TravelTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(TravelTheme.spacing.sm))
            Text(
                stringResource(R.string.carbon_kg, CarbonFormat.kg1(line.kg ?: 0.0, locale)),
                style = TravelTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
        line.crawlerSource?.let { SourceLine(it, Modifier.padding(start = TravelTheme.spacing.xs)) }
    }
}

@Composable
private fun MethodBadge(text: String, method: String?) {
    val x = TravelTheme.extendedColors
    val (container, content) =
        when (method) {
            "measured_tim" -> x.successContainer to x.onSuccessContainer
            "estimate" -> x.warningContainer to x.onWarningContainer
            else -> x.infoContainer to x.info
        }
    Surface(shape = TravelShape.Pill, color = container, contentColor = content) {
        Text(text, style = TravelTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = TravelTheme.spacing.xs))
    }
}

@Composable
private fun SourceRow(source: CarbonSource) {
    val open = rememberLinkOpener()
    val url = CarbonView.safeUrl(source.url)
    val name = source.name ?: source.id.orEmpty()
    Column {
        if (url != null) {
            TpTextButton(onClick = { open(url) }, tid = "carbon.source") { Text(name, style = TravelTheme.typography.labelMedium) }
        } else {
            Text(name, style = TravelTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
        val meta = listOfNotNull(source.publisher, source.licence).joinToString(" · ")
        if (meta.isNotEmpty()) Small(meta)
        source.attribution?.let { Small(it) }
    }
}

/** Web `SourceLine`: "Source: <name>" (linked) · "as of <date>", with the licence attribution under it. */
@Composable
private fun SourceLine(source: CarbonSource, modifier: Modifier = Modifier) {
    val open = rememberLinkOpener()
    val name = source.name ?: source.sourceId ?: return
    val url = CarbonView.safeUrl(source.url)
    val asOf = CarbonView.day(source.asOf)?.let { CarbonFormat.date(it, currentCarbonLocale()) }
    val label = stringResource(R.string.carbon_source, name)
    Column(modifier.testTag("carbon_source_line")) {
        if (url != null) {
            TpTextButton(onClick = { open(url) }, tid = "carbon.crawler_source") { Text(label, style = TravelTheme.typography.labelSmall) }
        } else {
            Small(label)
        }
        asOf?.let { Small(stringResource(R.string.carbon_as_of, it)) }
        source.attribution?.let { attribution ->
            val licence = CarbonView.safeUrl(source.licenceUrl)
            if (licence != null) {
                TpTextButton(onClick = { open(licence) }, tid = "carbon.licence") { Text(attribution, style = TravelTheme.typography.labelSmall) }
            } else {
                Small(attribution)
            }
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(text, style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.onSurface, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Small(text: String) {
    Text(text, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
}

@Composable
private fun categoryColor(key: String?): Color {
    val section =
        when (key) {
            "flights" -> SectionId.FLIGHTS
            "stays" -> SectionId.HOTELS
            "ground" -> SectionId.VISA
            "food" -> SectionId.ACTIVITIES
            else -> SectionId.EVENTS
        }
    return TravelTheme.sectionAccents[section].bar
}
