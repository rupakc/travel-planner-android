package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.core.ui.BookingLinkButton
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.sections.commerce.ChipRow
import io.github.rupakc.travelplanner.feature.results.sections.commerce.CityChip
import io.github.rupakc.travelplanner.feature.results.sections.commerce.FoundOnlineBadge
import io.github.rupakc.travelplanner.feature.results.sections.commerce.LocalCommerceContext
import io.github.rupakc.travelplanner.feature.results.sections.commerce.Pill
import io.github.rupakc.travelplanner.feature.results.sections.commerce.PillTone
import io.github.rupakc.travelplanner.feature.results.watch.WatchPriceAction
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** Web `OUTDOOR_CATS`: categories that get the weather chips. */
private val OUTDOOR = listOf("adventure", "nature", "outdoor", "sports", "hiking", "beach")
private const val PERCENT = 100
private const val DESCRIPTION_LINES = 2

/** Activities section body: category, match %, meta row, badges, booking, watch and add. */
@Composable
internal fun ActivitiesSectionContent(data: ActivitiesData, actions: SectionActions, modifier: Modifier = Modifier) {
    if (data.results.isEmpty()) {
        ActivitiesEmpty(modifier)
        return
    }
    val rows = commerceRows(SectionId.ACTIVITIES, data, LocalCommerceContext.current.budget, currentSelections(actions))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        rows.forEach { row -> key(row.key) { CommerceRowContent(row, actions) } }
    }
}

@Composable
internal fun ActivitiesEmpty(modifier: Modifier = Modifier) {
    Text(stringResource(R.string.results_activities_empty), modifier, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
}

private fun Activity.rawBool(key: String): Boolean? = (raw[key] as? JsonPrimitive)?.booleanOrNull

private fun Activity.rawString(key: String): String? = (raw[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

@Suppress("CyclomaticComplexMethod")
@Composable
internal fun ActivityCard(activity: Activity, selected: Boolean, fits: Boolean, actions: SectionActions, modifier: Modifier = Modifier) {
    val ctx = LocalCommerceContext.current
    val item = PlanItem.ActivityItem(activity)
    val name = activity.name.orEmpty()
    val toggle = { actions.onToggle(item) }
    val fromLink = activity.fromLink == true
    val outdoor = OUTDOOR.any { activity.category.orEmpty().lowercase().contains(it) }
    val poorDays = ctx.weather?.poorWeatherDayCount ?: 0
    val weatherLoaded = ctx.weather?.days?.isNotEmpty() == true
    TpCard(modifier, selected = selected, accent = TravelTheme.sectionAccents[SectionId.ACTIVITIES].bar, onClick = toggle) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipRow(Modifier.weight(1f)) {
                    activity.category?.let { Pill(it, tone = PillTone.Accent) }
                    if (fromLink) {
                        Pill(
                            activity.rawString("link_quote")?.let { stringResource(R.string.results_from_link_quote, it) }
                                ?: stringResource(R.string.results_from_link),
                            tone = PillTone.Warning,
                        )
                    }
                }
                if (!fromLink) {
                    activity.similarityScore?.takeIf { it > 0 }?.let {
                        Text(
                            stringResource(R.string.results_match, (it * PERCENT).roundToInt()),
                            style = TravelTheme.typography.labelSmall,
                            color = TravelTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(name, style = TravelTheme.typography.titleMedium)
            activity.description?.let {
                Text(
                    it,
                    style = TravelTheme.typography.bodyMedium,
                    maxLines = DESCRIPTION_LINES,
                    overflow = TextOverflow.Ellipsis,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
            val meta =
                listOfNotNull(
                    activity.durationHours?.let { stringResource(R.string.results_hours, formatHours(it)) },
                    activity.priceUsd?.let { localPrice(it) },
                    activity.location,
                    activity.rating?.let { r ->
                        activity.reviewCount?.let { n -> stringResource(R.string.results_rating_reviews, formatScore(r), "%,d".format(n)) }
                            ?: stringResource(R.string.results_rating, formatScore(r))
                    },
                )
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            ChipRow {
                CityChip(activity.city)
                if (!fromLink) activity.source?.let { Pill(it) }
                FoundOnlineBadge(activity.rawBool("link_verified"))
                if (fits) Pill(stringResource(R.string.results_budget_fits), tone = PillTone.Positive)
                if (activity.hiddenGem == true) Pill(stringResource(R.string.results_badge_hidden_gem), tone = PillTone.Accent)
                if (activity.familyFriendly == true) Pill(stringResource(R.string.results_badge_family), tone = PillTone.Info)
                if (activity.accessibilityFeatures.isNotEmpty()) Pill(stringResource(R.string.results_badge_accessible), tone = PillTone.Info)
                if (outdoor && poorDays > 0) Pill(stringResource(R.string.results_badge_check_weather), tone = PillTone.Warning)
                if (outdoor && poorDays == 0 && weatherLoaded) Pill(stringResource(R.string.results_badge_good_weather), tone = PillTone.Positive)
            }
            ChipRow {
                BookingLinkButton(activity.bookingUrl, accessibilityLabel = stringResource(R.string.results_book_a11y, name))
                WatchPriceAction(
                    available = ctx.watches.priceAvailable,
                    itemName = name,
                    state = ctx.watches.activities[name],
                    onWatch = { actions.onWatchActivity(activity) },
                )
                AddToPlanButton(selected = selected, itemName = name, onToggle = toggle)
            }
        }
    }
}
