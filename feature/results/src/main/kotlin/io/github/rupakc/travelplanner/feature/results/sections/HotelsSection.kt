package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.core.ui.BookingLinkButton
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.ResultsEvent
import io.github.rupakc.travelplanner.feature.results.sections.commerce.ChipRow
import io.github.rupakc.travelplanner.feature.results.sections.commerce.CityChip
import io.github.rupakc.travelplanner.feature.results.sections.commerce.FoundOnlineBadge
import io.github.rupakc.travelplanner.feature.results.sections.commerce.HiddenByBudgetRow
import io.github.rupakc.travelplanner.feature.results.sections.commerce.LocalCommerceContext
import io.github.rupakc.travelplanner.feature.results.sections.commerce.Pill
import io.github.rupakc.travelplanner.feature.results.sections.commerce.PillTone
import io.github.rupakc.travelplanner.feature.results.watch.WatchPriceAction

private const val AMENITIES_SHOWN = 4
private const val DIM = 0.6f
private val STAR_SIZE = 14.dp

/** Hotels section body: one card per hotel (luxury → budget tiers come from the backend). */
@Composable
internal fun HotelsSectionContent(data: HotelsData, actions: SectionActions, modifier: Modifier = Modifier) {
    val ctx = LocalCommerceContext.current
    if (data.results.isEmpty()) {
        Text(stringResource(R.string.results_hotels_empty), modifier, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        return
    }
    val budget = ctx.budget?.takeIf { it.cardsReact }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        var hidden = 0
        data.results.forEachIndexed { index, hotel ->
            val item = PlanItem.HotelItem(hotel)
            val selected = actions.isSelected(item)
            val over = hotel.name != null && budget?.fit?.hotels?.over?.contains(hotel.name) == true
            if (over && budget.showOnlyFits && !selected) {
                hidden++
            } else {
                key(hotel.name, index) {
                    HotelCard(hotel, selected, over, actions)
                }
            }
        }
        HiddenByBudgetRow(hidden, onShowAll = { ctx.onEvent(ResultsEvent.ShowOnlyFits(false)) })
    }
}

@Composable
private fun HotelCard(hotel: Hotel, selected: Boolean, over: Boolean, actions: SectionActions) {
    val ctx = LocalCommerceContext.current
    val name = hotel.name.orEmpty()
    val toggle = { actions.onToggle(PlanItem.HotelItem(hotel)) }
    TpCard(selected = selected, accent = TravelTheme.sectionAccents[SectionId.HOTELS].bar, onClick = toggle) {
        if (over) Pill(stringResource(R.string.results_budget_over_stay), tone = PillTone.Warning)
        Column(Modifier.alpha(if (over && !selected) DIM else 1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = TravelTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ChipRow {
                        hotel.location?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
                        CityChip(hotel.city)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    hotel.pricePerNightUsd?.let {
                        Text(
                            stringResource(R.string.results_hotel_per_night, formatPrice(it)),
                            style = TravelTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TravelTheme.sectionAccents[SectionId.HOTELS].icon,
                        )
                    }
                    hotel.totalPriceUsd?.let {
                        Text(
                            stringResource(R.string.results_hotel_total, formatPrice(it)),
                            style = TravelTheme.typography.labelSmall,
                            color = TravelTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
            Stars(hotel.starRating, hotel.reviewScore)
            if (hotel.amenities.isNotEmpty()) {
                ChipRow {
                    hotel.amenities.take(AMENITIES_SHOWN).forEach { Pill(it) }
                    if (hotel.amenities.size > AMENITIES_SHOWN) Pill(stringResource(R.string.results_more_count, hotel.amenities.size - AMENITIES_SHOWN))
                }
            }
            ChipRow {
                hotel.source?.let { Pill(it) }
                FoundOnlineBadge(hotel.linkVerified)
            }
            ChipRow {
                BookingLinkButton(hotel.bookingUrl)
                WatchPriceAction(
                    available = ctx.watches.priceAvailable && ctx.request?.returnDate != null,
                    itemName = name,
                    state = ctx.watches.hotels[name],
                    onWatch = { actions.onWatchHotel(hotel) },
                )
                AddToPlanButton(selected = selected, itemName = name, onToggle = toggle)
            }
        }
    }
}

/** Star icons + "{score}/10"; announced as one phrase. */
@Composable
private fun Stars(stars: Double?, review: Double?) {
    val count = stars?.let { Math.round(it).toInt() }?.coerceIn(0, MAX_STARS) ?: 0
    if (count == 0 && review == null) return
    val a11y =
        listOfNotNull(
            if (count > 0) pluralStringResource(R.plurals.results_hotel_stars_a11y, count, count) else null,
            review?.let { stringResource(R.string.results_hotel_review, formatScore(it)) },
        ).joinToString(", ")
    Row(Modifier.clearAndSetSemantics { contentDescription = a11y }, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { Icon(Icons.Filled.Star, contentDescription = null, tint = TravelTheme.extendedColors.warning, modifier = Modifier.size(STAR_SIZE)) }
        review?.let {
            Text(
                " " + stringResource(R.string.results_hotel_review, formatScore(it)),
                style = TravelTheme.typography.labelMedium,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_STARS = 5

/** "8.6" / "9" as the API sent it. */
internal fun formatScore(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
