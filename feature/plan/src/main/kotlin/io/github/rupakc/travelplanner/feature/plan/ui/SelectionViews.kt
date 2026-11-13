package io.github.rupakc.travelplanner.feature.plan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.export.safeUrl
import io.github.rupakc.travelplanner.core.domain.plan.BudgetStatus
import io.github.rupakc.travelplanner.core.domain.plan.CostCategory
import io.github.rupakc.travelplanner.core.domain.plan.CostPart
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.ui.PriceText
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.feature.plan.R
import io.github.rupakc.travelplanner.feature.plan.model.PlanCategory
import io.github.rupakc.travelplanner.feature.plan.model.Removal
import io.github.rupakc.travelplanner.feature.plan.model.SelectionEntry
import io.github.rupakc.travelplanner.feature.plan.model.SelectionGroup
import kotlin.math.roundToInt

/** Layout constants (defined once). */
private val CostBarHeight = 12.dp
private val LegendDot = 8.dp

private const val CONTENT_HEADER = "group-header"
private const val CONTENT_ENTRY = "group-entry"

/** Title of a plan category (web drawer labels). */
@Composable
internal fun PlanCategory.title(): String =
    stringResource(
        when (this) {
            PlanCategory.FLIGHT -> R.string.plan_cat_flight
            PlanCategory.FLIGHT_LEGS -> R.string.plan_cat_flight_legs
            PlanCategory.HOTEL -> R.string.plan_cat_hotel
            PlanCategory.ACTIVITIES -> R.string.plan_cat_activities
            PlanCategory.PLACES -> R.string.plan_cat_places
            PlanCategory.WALKING_TOURS -> R.string.plan_cat_walking_tours
            PlanCategory.SIM -> R.string.plan_cat_sim
            PlanCategory.GETTING_AROUND -> R.string.plan_cat_getting_around
            PlanCategory.TIPS -> R.string.plan_cat_tips
            PlanCategory.EVENTS -> R.string.plan_cat_events
            PlanCategory.NOTES -> R.string.plan_cat_notes
            PlanCategory.ITINERARY_SLOTS -> R.string.plan_cat_itinerary
            PlanCategory.PACKING_LIST -> R.string.plan_cat_packing
        },
    )

/** "Morning" / "Afternoon" / "Evening"; other values as the plan stores them. */
@Composable
internal fun timeOfDayLabel(value: String?): String? =
    when (value) {
        "morning" -> stringResource(R.string.plan_time_morning)
        "afternoon" -> stringResource(R.string.plan_time_afternoon)
        "evening" -> stringResource(R.string.plan_time_evening)
        null -> null
        else -> value.replaceFirstChar { it.uppercase() }
    }

/**
 * The grouped selections as lazy items (stable keys, two content types). [onRemove] null = read-only.
 * Each group starts with a heading that carries the section accent, like the results screen.
 */
internal fun LazyListScope.selectionGroups(
    groups: List<SelectionGroup>,
    onOpenLink: (String) -> Unit,
    onRemove: ((Removal, String?) -> Unit)? = null,
) {
    groups.forEach { group ->
        item(key = "h-${group.category}", contentType = CONTENT_HEADER) { GroupHeader(group) }
        group.entries.forEach { entry ->
            item(key = "e-${group.category}-${entry.key}", contentType = CONTENT_ENTRY) {
                SelectionRow(group.category, entry, onOpenLink, onRemove)
            }
        }
    }
}

@Composable
private fun GroupHeader(group: SelectionGroup) {
    val accent = TravelTheme.sectionAccents[group.category.section]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = TravelTheme.spacing.lg, bottom = TravelTheme.spacing.xs)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(TravelTheme.spacing.accentBar).height(TravelTheme.spacing.lg).background(accent.bar))
        Spacer(Modifier.width(TravelTheme.spacing.sm))
        Text(
            stringResource(R.string.plan_category_heading, group.category.title(), group.entries.size),
            style = TravelTheme.typography.titleSmall,
            color = accent.text,
        )
    }
}

@Composable
private fun SelectionRow(
    category: PlanCategory,
    entry: SelectionEntry,
    onOpenLink: (String) -> Unit,
    onRemove: ((Removal, String?) -> Unit)?,
) {
    val title = entryTitle(category, entry)
    val link = safeUrl(entry.url)
    Row(
        Modifier.fillMaxWidth().padding(vertical = TravelTheme.spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
            entryOverline(entry)?.let {
                Text(it, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.primary)
            }
            Text(title, style = TravelTheme.typography.bodyLarge)
            entry.dataGb?.let {
                Text(stringResource(R.string.plan_data_gb, it), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            entry.details.forEach {
                Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            EntryPrice(entry)
        }
        if (link != null) {
            val label = stringResource(R.string.plan_open_link_a11y, title)
            TpIconButton(onClick = { onOpenLink(link) }, tid = "plan.selection_views_icon_button") {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = label)
            }
        }
        val removal = entry.removal
        if (onRemove != null && removal != null) {
            TpIconButton(onClick = { onRemove(removal, entry.title) }, tid = "plan.plan_remove_a11y") {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.plan_remove_a11y, title))
            }
        }
    }
}

@Composable
private fun entryTitle(category: PlanCategory, entry: SelectionEntry): String =
    when {
        category == PlanCategory.PACKING_LIST -> stringResource(R.string.plan_cat_packing)
        !entry.title.isNullOrBlank() -> entry.title
        else -> stringResource(R.string.plan_item_unnamed)
    }

@Composable
private fun entryOverline(entry: SelectionEntry): String? =
    when {
        entry.legNumber != null -> stringResource(R.string.plan_leg, entry.legNumber)
        entry.dayNumber != null -> listOfNotNull(stringResource(R.string.plan_day, entry.dayNumber), timeOfDayLabel(entry.timeOfDay)).joinToString(", ")
        else -> null
    }

@Composable
private fun EntryPrice(entry: SelectionEntry) {
    when {
        entry.packedCount != null && entry.packTotal != null -> {
            Text(pluralStringResource(R.plurals.plan_packed, entry.packTotal, entry.packedCount, entry.packTotal), style = TravelTheme.typography.labelLarge)
        }

        entry.isFree -> {
            Text(stringResource(R.string.plan_free_entry), style = TravelTheme.typography.labelLarge, color = TravelTheme.extendedColors.success)
        }

        entry.priceUsd != null && entry.perNight -> {
            Text(stringResource(R.string.plan_per_night, localPrice(entry.priceUsd)), style = TravelTheme.typography.labelLarge)
        }

        entry.priceUsd != null -> {
            PriceText(entry.priceUsd, isEstimate = entry.isEstimate, style = TravelTheme.typography.labelLarge)
        }
    }
}

private fun CostCategory.section(): SectionId =
    when (this) {
        CostCategory.FLIGHTS -> SectionId.FLIGHTS
        CostCategory.HOTEL -> SectionId.HOTELS
        CostCategory.ACTIVITIES -> SectionId.ACTIVITIES
        CostCategory.SIM -> SectionId.SIM
    }

@Composable
private fun CostCategory.label(): String =
    stringResource(
        when (this) {
            CostCategory.FLIGHTS -> R.string.plan_cost_flights
            CostCategory.HOTEL -> R.string.plan_cost_hotel
            CostCategory.ACTIVITIES -> R.string.plan_cost_activities
            CostCategory.SIM -> R.string.plan_cost_sim
        },
    )

/**
 * Estimated cost + budget status + "Where the money goes" (web CostBreakdown): one stacked bar
 * (described as text for TalkBack) and a legend with amounts and percentages.
 */
@Composable
internal fun CostCard(
    parts: List<CostPart>,
    budgetStatus: BudgetStatus?,
    hasTransport: Boolean,
    modifier: Modifier = Modifier,
) {
    if (parts.isEmpty()) return
    val total = parts.sumOf { it.amount }
    TpCard(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.plan_estimated_cost), style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.onSurfaceVariant)
        Text(localPrice(total), style = TravelTheme.typography.headlineMedium)
        budgetStatus?.let { status ->
            val amount = localPrice(status.amount.toDouble())
            Text(
                stringResource(if (status.over) R.string.plan_over_budget else R.string.plan_under_budget, amount),
                style = TravelTheme.typography.labelLarge,
                color = if (status.over) TravelTheme.extendedColors.warning else TravelTheme.extendedColors.success,
            )
        }
        Spacer(Modifier.height(TravelTheme.spacing.md))
        HorizontalDivider()
        Spacer(Modifier.height(TravelTheme.spacing.md))
        SectionTitle(stringResource(R.string.plan_cost_breakdown))
        Spacer(Modifier.height(TravelTheme.spacing.sm))
        val described =
            parts.map { p ->
                Triple(p, TravelTheme.sectionAccents[p.category.section()].bar, (p.amount / total * PERCENT).roundToInt())
            }
        val barDescription =
            described.map { (p, _, pct) ->
                stringResource(R.string.plan_cost_part_a11y, p.category.label(), localPrice(p.amount), percentText(pct))
            }
        Row(
            Modifier
                .fillMaxWidth()
                .height(CostBarHeight)
                .clip(CircleShape)
                .clearAndSetSemantics { contentDescription = barDescription.joinToString(", ") },
        ) {
            described.forEach { (p, color, _) -> Box(Modifier.weight(p.amount.toFloat()).fillMaxHeight().background(color)) }
        }
        Spacer(Modifier.height(TravelTheme.spacing.sm))
        described.forEach { (p, color, pct) -> LegendRow(p.category.label(), color, localPrice(p.amount), pct) }
        if (hasTransport) {
            Spacer(Modifier.height(TravelTheme.spacing.xs))
            Text(stringResource(R.string.plan_cost_transport_note), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendRow(label: String, color: Color, amount: String, percent: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = TravelTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(LegendDot).clip(CircleShape).background(color))
        Spacer(Modifier.width(TravelTheme.spacing.sm))
        Text(label, style = TravelTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(amount, style = TravelTheme.typography.bodyMedium)
        Spacer(Modifier.width(TravelTheme.spacing.sm))
        Text(
            java.text.NumberFormat.getPercentInstance().format(percent / PERCENT.toDouble()),
            style = TravelTheme.typography.bodySmall,
            color = TravelTheme.colors.onSurfaceVariant,
        )
    }
}

private const val PERCENT = 100

/** Localised percentage ("42%", "42 %"). */
@Composable
private fun percentText(percent: Int): String =
    java.text.NumberFormat.getPercentInstance(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(percent / PERCENT.toDouble())
