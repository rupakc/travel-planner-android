package io.github.rupakc.travelplanner.feature.chat.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonBlock
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.ChatItineraryDay
import io.github.rupakc.travelplanner.core.model.chat.ChatSlot
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.feature.chat.R
import io.github.rupakc.travelplanner.feature.chat.model.ChatTripMap
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The planning result (web `TripPlanView`): the route as a vertical spine of numbered
 * stops (no map SDK — each stop opens in the Maps app), trip summary, intercity
 * options, day cards and "Save as plan". Every value shown comes from `trip_map` /
 * `comprehensive_itinerary`; missing values are simply not shown.
 */
@Composable
internal fun TripPlanView(
    tripMap: ChatTripMap,
    itinerary: ChatItinerary?,
    itineraryError: String?,
    generating: Boolean,
    saving: Boolean,
    onSave: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        RouteCard(tripMap, itinerary, generating)
        val intercity = itinerary?.intercityTravel.orEmpty()
        if (intercity.isNotEmpty()) IntercityRow(itinerary!!)
        if (itineraryError != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = TravelTheme.colors.error, modifier = Modifier.size(ChatDimens.SmallIcon))
                Text(stringResource(R.string.chat_itinerary_failed), color = TravelTheme.colors.error, style = TravelTheme.typography.bodyMedium)
            }
        }
        if (generating && itinerary == null) repeat(SKELETON_DAYS) { DayCardSkeleton() }
        val days = itinerary?.days.orEmpty()
        if (days.isNotEmpty()) DayCards(days, tripMap)
        if (days.isNotEmpty() && onSave != null) {
            TpButton(
                text = stringResource(R.string.chat_save_as_plan),
                onClick = onSave,
                loading = saving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RouteCard(tripMap: ChatTripMap, itinerary: ChatItinerary?, generating: Boolean) {
    val summary = itinerary?.tripSummary
    val context = LocalContext.current
    val nightsByCity = remember(summary) { summary?.cities.orEmpty().associate { it.name?.lowercase() to it.nights } }
    TpCard(size = TpCardSize.Large) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(tripMap.origin).plus(tripMap.cities.mapNotNull { it.city }).joinToString(" → "),
                    style = TravelTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                TripFacts(tripMap, itinerary)
            }
            summary?.totalCostUsd?.takeIf { it > 0 }?.let { total ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(localPrice(total), style = TravelTheme.typography.titleLarge, color = TravelTheme.colors.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.chat_est_per_person), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
        }
        if (generating && itinerary == null) {
            Text(stringResource(R.string.chat_plotting), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.primary)
        }
        // The route spine: departure, then numbered stops in order.
        Column(Modifier.padding(top = TravelTheme.spacing.md)) {
            tripMap.origin?.let { origin ->
                RouteStop(marker = null, title = origin, subtitle = stringResource(R.string.chat_departure_city), last = tripMap.cities.isEmpty()) {
                    openInMaps(context, origin, tripMap.originLat, tripMap.originLng)
                }
            }
            tripMap.cities.forEachIndexed { i, c ->
                val name = c.city ?: return@forEachIndexed
                val nights = nightsByCity[name.lowercase()]
                RouteStop(
                    marker = i + 1,
                    title = name,
                    subtitle = nights?.let { pluralStringResource(R.plurals.chat_trip_nights, it, it) },
                    last = i == tripMap.cities.lastIndex,
                ) { openInMaps(context, name, c.lat, c.lng) }
            }
        }
    }
}

@Composable
private fun TripFacts(tripMap: ChatTripMap, itinerary: ChatItinerary?) {
    val facts =
        buildList {
            val dates = listOfNotNull(formatDay(tripMap.departureDate), formatDay(tripMap.returnDate)).joinToString(" – ")
            if (dates.isNotEmpty()) add(dates)
            itinerary?.tripSummary?.totalNights?.takeIf { it > 0 }?.let { add(pluralStringResource(R.plurals.chat_trip_nights, it, it)) }
            val days = itinerary?.days.orEmpty()
            if (days.isNotEmpty()) {
                val full = days.count { it.isTravelDay != true }
                add(pluralStringResource(R.plurals.chat_trip_full_days, full, full))
            }
            tripMap.numTravelers?.takeIf { it > 1 }?.let { add(pluralStringResource(R.plurals.chat_trip_travelers, it, it)) }
        }
    if (facts.isNotEmpty()) {
        Text(facts.joinToString(" · "), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
    }
}

/** One stop on the spine: node (numbered for destinations), name, nights, and an "Open in Maps" button. */
@Composable
private fun RouteStop(marker: Int?, title: String, subtitle: String?, last: Boolean, onOpenMaps: () -> Unit) {
    val spine = TravelTheme.colors.primary
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(ChatDimens.Avatar).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (!last) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .width(ChatDimens.RouteSpine)
                        .fillMaxHeight(HALF)
                        .background(spine),
                )
            }
            if (marker != null) {
                Box(Modifier.align(Alignment.TopCenter).width(ChatDimens.RouteSpine).fillMaxHeight(HALF).background(spine))
                Surface(shape = TravelShape.Pill, color = spine, modifier = Modifier.size(ChatDimens.Avatar - TravelTheme.spacing.sm)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$marker", style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(Modifier.size(ChatDimens.RouteNode).clip(TravelShape.Pill).background(TravelTheme.colors.outline))
            }
        }
        Column(Modifier.weight(1f).padding(start = TravelTheme.spacing.sm, top = TravelTheme.spacing.xs, bottom = TravelTheme.spacing.xs)) {
            Text(title, style = TravelTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
        }
        IconButton(onClick = onOpenMaps) {
            Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.chat_open_in_maps, title), tint = TravelTheme.colors.primary)
        }
    }
}

@Composable
private fun IntercityRow(itinerary: ChatItinerary) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(stringResource(R.string.chat_getting_there), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            itinerary.intercityTravel.forEach { leg ->
                TpCard {
                    Text(stringResource(R.string.chat_route, leg.from.orEmpty(), leg.to.orEmpty()), style = TravelTheme.typography.titleSmall)
                    leg.options.take(ChatDimens.INTERCITY_MAX_OPTIONS).forEach { opt ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                            opt.mode?.let { Text(it, style = TravelTheme.typography.bodySmall) }
                            opt.duration?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
                            opt.priceUsd?.takeIf { it > 0 }?.let {
                                Text(
                                    localPrice(it),
                                    style = TravelTheme.typography.bodySmall,
                                    color = TravelTheme.colors.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCards(days: List<ChatItineraryDay>, tripMap: ChatTripMap) {
    var allOpen by rememberSaveable { mutableStateOf(true) }
    val overrides = remember(allOpen) { mutableStateMapOf<Int, Boolean>() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            pluralStringResource(R.plurals.chat_day_itinerary, days.size, days.size),
            style = TravelTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TpButton(
            text = stringResource(if (allOpen) R.string.chat_collapse_all else R.string.chat_expand_all),
            onClick = { allOpen = !allOpen },
            variant = TpButtonVariant.Text,
        )
    }
    val cityIndex = remember(tripMap) { tripMap.cities.mapIndexedNotNull { i, c -> c.city?.lowercase()?.let { it to i } }.toMap() }
    days.forEachIndexed { index, day ->
        val number = day.dayNumber ?: (index + 1)
        val open = overrides[number] ?: allOpen
        DayCard(day, number, open, cityIndex[day.city?.lowercase()]) { overrides[number] = !open }
    }
}

@Composable
private fun DayCard(day: ChatItineraryDay, number: Int, open: Boolean, cityIndex: Int?, onToggle: () -> Unit) {
    val travel = day.isTravelDay == true
    val stripe = if (travel) TravelTheme.extendedColors.warning else TravelTheme.sectionAccents[SectionId.ITINERARY].bar
    val state = stringResource(if (open) R.string.chat_day_expanded else R.string.chat_day_collapsed)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(TravelShape.ItemCard)
            .background(TravelTheme.colors.surface),
    ) {
        Box(Modifier.width(TravelTheme.spacing.accentBar).fillMaxHeight().background(stripe))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TravelTheme.spacing.minTouchTarget)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { stateDescription = state }
                    .padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(stringResource(R.string.chat_day, number), formatDay(day.date)).joinToString(" · "),
                        style = TravelTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    val place =
                        when {
                            travel -> stringResource(R.string.chat_travel_day)
                            day.city != null && day.country != null -> stringResource(R.string.chat_city_country, day.city!!, day.country!!)
                            else -> day.city
                        }
                    place?.let {
                        val prefix = cityIndex?.let { i -> "${i + 1} · " }.orEmpty()
                        Text(prefix + it, style = TravelTheme.typography.labelLarge, color = if (travel) stripe else TravelTheme.colors.primary)
                    }
                    if (open) day.theme?.let { Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic) }
                }
                day.dailyCostUsd?.takeIf {
                    it > 0
                }?.let { Text(localPrice(it), style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.primary) }
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
            if (open) DayBody(day)
        }
    }
}

@Composable
private fun DayBody(day: ChatItineraryDay) {
    Column(
        Modifier.padding(start = TravelTheme.spacing.md, end = TravelTheme.spacing.md, bottom = TravelTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        day.hotel?.takeIf { !it.name.isNullOrBlank() }?.let { hotel ->
            val accent = TravelTheme.sectionAccents[SectionId.HOTELS]
            Row(
                Modifier.fillMaxWidth().clip(TravelShape.Input).background(accent.tint).padding(TravelTheme.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_chat_hotel), contentDescription = null, tint = accent.icon, modifier = Modifier.size(ChatDimens.AvatarIcon))
                Column(Modifier.weight(1f)) {
                    val stars = hotel.stars?.takeIf { it > 0 }?.let { " " + "★".repeat(it.toInt().coerceIn(1, MAX_STARS)) }.orEmpty()
                    Text(hotel.name.orEmpty() + stars, style = TravelTheme.typography.titleSmall)
                    listOfNotNull(hotel.tier, hotel.neighbourhood).takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = TravelTheme.typography.bodySmall, color = accent.text)
                    }
                    hotel.highlight?.let { Text("“$it”", style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic) }
                }
                hotel.pricePerNightUsd?.takeIf { it > 0 }?.let {
                    Text(stringResource(R.string.chat_per_night, localPrice(it)), style = TravelTheme.typography.labelLarge, color = accent.text)
                }
            }
        }
        SlotLine(R.string.chat_morning, day.morning)
        SlotLine(R.string.chat_afternoon, day.afternoon)
        SlotLine(R.string.chat_evening, day.evening)
        if (day.placesToSee.isNotEmpty()) {
            PillGroup(
                stringResource(R.string.chat_must_see),
                day.placesToSee.mapNotNull { p ->
                    val name = p.name ?: return@mapNotNull null
                    val price = p.entryUsd?.let { if (it == 0.0) stringResource(R.string.chat_free) else localPrice(it) }
                    listOfNotNull(name, price).joinToString(" · ")
                },
            )
        }
        if (day.dining.isNotEmpty()) {
            PillGroup(
                stringResource(R.string.chat_dining),
                day.dining.mapNotNull { d ->
                    val name = d.name ?: return@mapNotNull null
                    listOfNotNull(name, d.cuisine, d.priceRange).joinToString(" · ")
                },
            )
        }
    }
}

@Composable
private fun SlotLine(labelRes: Int, slot: ChatSlot?) {
    val activity = slot?.activity?.takeIf(String::isNotBlank) ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        Text(
            stringResource(labelRes),
            style = TravelTheme.typography.labelMedium,
            color = TravelTheme.colors.onSurfaceVariant,
            modifier = Modifier.width(SLOT_LABEL_WIDTH),
        )
        Column(Modifier.weight(1f)) {
            Text(activity, style = TravelTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            slot.place?.takeIf { it != activity }?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
        }
        slot.costUsd?.takeIf { it > 0 }?.let { Text(localPrice(it), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.primary) }
    }
}

@Composable
private fun PillGroup(title: String, labels: List<String>) {
    if (labels.isEmpty()) return
    Text(title, style = TravelTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        labels.forEach { label ->
            Surface(shape = TravelShape.Pill, color = TravelTheme.colors.surfaceVariant) {
                Text(
                    label,
                    style = TravelTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun DayCardSkeleton() {
    TpCard {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            SkeletonBlock(Modifier.fillMaxWidth(SKELETON_TITLE))
            SkeletonBlock(Modifier.fillMaxWidth())
            SkeletonBlock(Modifier.fillMaxWidth(SKELETON_LINE))
        }
    }
}

/** "2026-04-01" → "Wed, Apr 1" in the device locale; unparseable dates are shown as received. */
internal fun formatDay(date: String?): String? {
    if (date.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())) }.getOrDefault(date)
}

/** Opens [label] in the Maps app (geo: intent); coordinates when the API gave them, else a place search. */
internal fun openInMaps(context: Context, label: String, lat: Double?, lng: Double?) {
    val uri =
        if (lat != null && lng != null) {
            "geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})".toUri()
        } else {
            "geo:0,0?q=${Uri.encode(label)}".toUri()
        }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No maps app: nothing to open.
    }
}

private const val SKELETON_DAYS = 3
private const val SKELETON_TITLE = 0.4f
private const val SKELETON_LINE = 0.7f
private const val HALF = 0.5f
private const val MAX_STARS = 5
private val SLOT_LABEL_WIDTH = ChatDimens.Avatar * 3
