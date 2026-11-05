package io.github.rupakc.travelplanner.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.export.safeUrl
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.FlightLeg
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Width of the time column in itinerary rows and max content width (layout constants). */
private val TimeColumnWidth = 88.dp
private val MaxContentWidth = 640.dp

/**
 * The read-only shared trip (web SharePage): poster header, then one card per category,
 * "Save a copy" when signed in, "Plan your own trip" and the privacy footer.
 */
@Composable
internal fun ShareScreen(
    state: ShareUiState,
    onEvent: (ShareEvent) -> Unit,
    onBack: () -> Unit,
    onPlanOwn: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = { TpTopBar(stringResource(R.string.share_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val trip = state.trip
            when {
                trip != null -> {
                    TripBody(trip, state, onEvent, onPlanOwn, onOpenLink, onOpenSaved)
                }

                state.error is AppError.Network -> {
                    ErrorState(stringResource(R.string.share_offline), onRetry = { onEvent(ShareEvent.Retry) })
                }

                state.error != null -> {
                    EmptyState(
                        title = stringResource(R.string.share_unavailable_title),
                        message = stringResource(R.string.share_unavailable_message),
                        actionLabel = stringResource(R.string.share_plan_own),
                        onAction = onPlanOwn,
                    )
                }

                else -> {
                    SkeletonList(Modifier.padding(TravelTheme.spacing.gutter).widthIn(max = MaxContentWidth), lines = SKELETON_LINES)
                }
            }
        }
    }
    ShareMessages(state.message, snackbar, onEvent, onOpenSaved)
}

private const val SKELETON_LINES = 6

@Composable
private fun ShareMessages(message: ShareMessage?, snackbar: SnackbarHostState, onEvent: (ShareEvent) -> Unit, onOpenSaved: (Long) -> Unit) {
    val saved = stringResource(R.string.share_saved)
    val open = stringResource(R.string.share_open_saved)
    val failed = (message as? ShareMessage.SaveFailed)?.error?.message ?: stringResource(R.string.share_save_failed)
    LaunchedEffect(message) {
        when (message) {
            null -> {
                return@LaunchedEffect
            }

            is ShareMessage.Saved -> {
                val result = snackbar.showSnackbar(saved, actionLabel = open, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) onOpenSaved(message.planId)
            }

            is ShareMessage.SaveFailed -> {
                snackbar.showSnackbar(failed)
            }
        }
        onEvent(ShareEvent.MessageShown)
    }
}

@Composable
private fun TripBody(
    trip: SharedTrip,
    state: ShareUiState,
    onEvent: (ShareEvent) -> Unit,
    onPlanOwn: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenSaved: (Long) -> Unit,
) {
    val sel = trip.selections
    LazyColumn(
        Modifier.fillMaxSize().widthIn(max = MaxContentWidth),
        contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        item(key = "poster", contentType = "poster") { Poster(trip, onOpenLink) }
        if (trip.flights.isNotEmpty()) item(key = "flights", contentType = "card") { FlightsCard(trip) }
        sel.hotel?.let { hotel -> item(key = "hotel", contentType = "card") { HotelCard(hotel) } }
        if (trip.itinerary.isNotEmpty()) itinerary(trip)
        listCards(sel, onOpenLink)
        item(key = "actions", contentType = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                val savedId = state.savedPlanId
                if (savedId != null) {
                    // Saved once: open the copy instead of creating duplicates.
                    TpButton(stringResource(R.string.share_open_copy), onClick = { onOpenSaved(savedId) }, modifier = Modifier.fillMaxWidth())
                } else if (state.canSaveCopy) {
                    TpButton(
                        stringResource(if (state.isSaving) R.string.share_saving else R.string.share_save_copy),
                        onClick = { onEvent(ShareEvent.SaveCopy) },
                        loading = state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TpButton(
                    stringResource(R.string.share_plan_own),
                    onClick = onPlanOwn,
                    variant = if (state.canSaveCopy) TpButtonVariant.Secondary else TpButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.share_footer),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.sm),
                )
            }
        }
    }
}

private fun LocalDate.medium(): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(this)

/** The poster: the one bold moment of the page — teal block, destination set large in Sora. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Poster(trip: SharedTrip, onOpenLink: (String) -> Unit) {
    Surface(color = TravelTheme.colors.primary, contentColor = TravelTheme.colors.onPrimary, shape = TravelShape.LargeCard) {
        Column(Modifier.fillMaxWidth().padding(TravelTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(stringResource(R.string.share_brand), style = TravelTheme.typography.labelLarge)
            Text(
                trip.destination ?: stringResource(R.string.share_destination_fallback),
                style = TravelTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            trip.origin?.let { Text(stringResource(R.string.share_from, it), style = TravelTheme.typography.bodyMedium) }
            val link = safeUrl(trip.importUrl)
            (trip.importTitle ?: link)?.let { title ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.share_inspired_by, title), style = TravelTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (link != null) {
                        IconButton(onClick = { onOpenLink(link) }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.share_open_link_a11y, title))
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                trip.startDate?.let { start ->
                    val end = trip.endDate
                    Text(
                        if (end == null || end == start) start.medium() else stringResource(R.string.share_dates, start.medium(), end.medium()),
                        style = TravelTheme.typography.labelLarge,
                    )
                }
                Text(pluralStringResource(R.plurals.share_travellers, trip.travellers, trip.travellers), style = TravelTheme.typography.labelLarge)
                if (trip.plannedUsd > 0) Text(stringResource(R.string.share_planned, localPrice(trip.plannedUsd)), style = TravelTheme.typography.labelLarge)
            }
            if (trip.interests.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                ) {
                    trip.interests.forEach { interest ->
                        Surface(color = TravelTheme.colors.onPrimary.copy(alpha = CHIP_ALPHA), shape = TravelShape.Pill) {
                            Text(
                                interest,
                                style = TravelTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xxs),
                            )
                        }
                    }
                }
            }
            if (trip.name.isNotBlank()) Text(trip.name, style = TravelTheme.typography.bodySmall)
        }
    }
}

private const val CHIP_ALPHA = 0.2f

/** A card with a section accent bar and a heading. */
@Composable
private fun DetailCard(title: String, section: SectionId, content: @Composable () -> Unit) {
    val accent = TravelTheme.sectionAccents[section]
    TpCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { heading() }) {
            Box(Modifier.width(TravelTheme.spacing.accentBar).height(TravelTheme.spacing.lg).background(accent.bar))
            Spacer(Modifier.width(TravelTheme.spacing.sm))
            Text(title, style = TravelTheme.typography.titleMedium, color = accent.text)
        }
        Spacer(Modifier.height(TravelTheme.spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) { content() }
    }
}

@Composable
private fun FlightsCard(trip: SharedTrip) {
    DetailCard(stringResource(R.string.share_flights), SectionId.FLIGHTS) {
        trip.flights.forEach { row ->
            val tag =
                when (val t = row.tag) {
                    FlightTag.Depart -> stringResource(R.string.share_depart)
                    FlightTag.Return -> stringResource(R.string.share_return)
                    FlightTag.Single -> stringResource(R.string.share_flight)
                    is FlightTag.Leg -> stringResource(R.string.share_leg, t.number)
                }
            FlightLine(tag, row.leg, row.route, row.date, row.priceUsd)
        }
        trip.legsTotalUsd?.let {
            Text(stringResource(R.string.share_flights_total, localPrice(it)), style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.primary)
        }
    }
}

@Composable
private fun FlightLine(tag: String, leg: FlightLeg, route: String?, date: String?, priceUsd: Double?) {
    Column {
        Text(tag, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.secondary)
        val title = listOfNotNull(leg.airline ?: stringResource(R.string.share_flight), leg.flightNumber).joinToString(" ")
        Text(listOfNotNull(title, priceUsd?.takeIf { it > 0 }?.let { localPrice(it) }).joinToString(" · "), style = TravelTheme.typography.bodyLarge)
        listOfNotNull(route, listOfNotNull(date, leg.departureTime).joinToString(" ").ifEmpty { null }).forEach {
            Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun HotelCard(hotel: Hotel) {
    DetailCard(stringResource(R.string.share_hotel), SectionId.HOTELS) {
        Text(hotel.name ?: stringResource(R.string.share_unnamed), style = TravelTheme.typography.bodyLarge)
        val facts =
            listOfNotNull(
                hotel.starRating?.takeIf { it > 0 }?.let { stringResource(R.string.share_stars, kotlin.math.round(it).toInt()) },
                hotel.reviewScore?.let { stringResource(R.string.share_review, it.toString()) },
                hotel.location,
                hotel.pricePerNightUsd?.takeIf { it > 0 }?.let { stringResource(R.string.share_per_night, localPrice(it)) },
                hotel.totalPriceUsd?.takeIf { it > 0 }?.let { stringResource(R.string.share_total, localPrice(it)) },
            )
        facts.forEach { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
    }
}

private fun LazyListScope.itinerary(trip: SharedTrip) {
    item(key = "itinerary", contentType = "card") {
        val title =
            if (trip.fullItinerary) {
                stringResource(R.string.share_itinerary, pluralStringResource(R.plurals.share_days, trip.itinerary.size, trip.itinerary.size))
            } else {
                stringResource(R.string.share_itinerary_picks)
            }
        DetailCard(title, SectionId.ITINERARY) {
            trip.itinerary.forEach { day ->
                Column {
                    val heading = listOfNotNull(day.dayNumber?.let { stringResource(R.string.share_day, it) }, day.date, day.city).joinToString(", ")
                    Text(heading, style = TravelTheme.typography.titleSmall, color = TravelTheme.colors.primary, modifier = Modifier.semantics { heading() })
                    day.theme?.let {
                        Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = TravelTheme.colors.onSurfaceVariant)
                    }
                    day.slots.forEach { SlotLine(it) }
                }
            }
        }
    }
}

@Composable
private fun SlotLine(slot: SharedSlot) {
    Row(Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.xs)) {
        Column(Modifier.widthIn(min = TimeColumnWidth)) {
            val tod =
                when (slot.timeOfDay) {
                    "morning" -> stringResource(R.string.share_morning)
                    "afternoon" -> stringResource(R.string.share_afternoon)
                    "evening" -> stringResource(R.string.share_evening)
                    else -> slot.timeOfDay
                }
            tod?.let { Text(it, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant) }
            slot.timeRange?.let { Text(it, style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant) }
        }
        Column(Modifier.weight(1f)) {
            Text(slot.activity ?: stringResource(R.string.share_unnamed), style = TravelTheme.typography.bodyMedium)
            slot.location?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
            if (slot.fromLink) {
                Text(
                    stringResource(R.string.share_from_link),
                    style = TravelTheme.typography.labelSmall,
                    color = TravelTheme.extendedColors.warning,
                )
            }
            slot.note?.let { Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = TravelTheme.colors.primary) }
        }
    }
}

private fun LazyListScope.listCards(sel: PlanSelections, onOpenLink: (String) -> Unit) {
    if (sel.activities.isNotEmpty()) {
        item(key = "activities", contentType = "card") {
            DetailCard(stringResource(R.string.share_activities, sel.activities.size), SectionId.ACTIVITIES) {
                sel.activities.forEach { a -> ActivityLine(a) }
            }
        }
    }
    if (sel.events.isNotEmpty()) {
        item(key = "events", contentType = "card") {
            DetailCard(stringResource(R.string.share_events, sel.events.size), SectionId.EVENTS) {
                sel.events.forEach { e ->
                    val dates = listOfNotNull(e.startDate, e.endDate?.takeIf { it != e.startDate }).joinToString(" – ").ifEmpty { null }
                    Line(e.name, listOfNotNull(e.category, dates, e.price, e.location))
                }
            }
        }
    }
    if (sel.walkingTours.isNotEmpty()) {
        item(key = "tours", contentType = "card") {
            DetailCard(stringResource(R.string.share_tours, sel.walkingTours.size), SectionId.WALKING_TOURS) {
                sel.walkingTours.forEach { t -> Line(t.name, listOfNotNull(t.provider, t.city), safeUrl(t.url), onOpenLink) }
            }
        }
    }
    sel.sim?.let { sim ->
        item(key = "sim", contentType = "card") {
            DetailCard(stringResource(R.string.share_sim), SectionId.SIM) {
                val data = sim.dataGb?.let { stringResource(R.string.share_data_gb, it) } ?: stringResource(R.string.share_unlimited)
                Line(
                    listOfNotNull(sim.provider, sim.planName).joinToString(" — "),
                    listOfNotNull(
                        sim.priceUsd?.let { localPrice(it) },
                        data,
                        sim.validityDays?.let { pluralStringResource(R.plurals.share_validity, it, it) },
                        sim.networkQuality?.speed,
                    ),
                )
            }
        }
    }
    if (sel.gettingAround.isNotEmpty()) {
        item(key = "transport", contentType = "card") {
            DetailCard(stringResource(R.string.share_getting_around, sel.gettingAround.size), SectionId.GETTING_AROUND) {
                sel.gettingAround.forEach { o -> Line(o.name, listOfNotNull(o.type?.replace('_', ' '), o.priceInfo)) }
            }
        }
    }
    if (sel.tips.isNotEmpty()) {
        item(key = "tips", contentType = "card") {
            DetailCard(stringResource(R.string.share_tips, sel.tips.size), SectionId.TIPS) {
                sel.tips.forEach { t -> Line(t.title, listOfNotNull(t.body)) }
            }
        }
    }
}

@Composable
private fun ActivityLine(a: Activity) {
    val hours = a.durationHours?.let { stringResource(R.string.share_hours, it.toString().removeSuffix(".0")) }
    Line(a.name, listOfNotNull(a.city?.substringBefore(','), a.category, hours, a.priceUsd?.let { localPrice(it) }, a.location))
}

@Composable
private fun Line(title: String?, details: List<String>, link: String? = null, onOpenLink: ((String) -> Unit)? = null) {
    val name = title?.takeIf(String::isNotBlank) ?: stringResource(R.string.share_unnamed)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = TravelTheme.typography.bodyLarge)
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        if (link != null && onOpenLink != null) {
            IconButton(onClick = { onOpenLink(link) }) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.share_open_link_a11y, name))
            }
        }
    }
}

// --- previews --------------------------------------------------------------------------------

private val previewTrip =
    SharedTrip(
        name = "The Foodie Tokyo Journey",
        destination = "Tokyo, Japan",
        origin = "New York",
        startDate = LocalDate.of(2027, 4, 1),
        endDate = LocalDate.of(2027, 4, 8),
        travellers = 2,
        interests = listOf("food", "history"),
        plannedUsd = 2480.0,
        importTitle = null,
        importUrl = null,
        flights = emptyList(),
        legsTotalUsd = null,
        itinerary =
            listOf(
                SharedDay(1, "2027-04-01", "Tokyo", "Arrival", listOf(SharedSlot("morning", "09:00–11:00", "Tsukiji Outer Market", "Tsukiji", null, false))),
            ),
        fullItinerary = true,
        selections = PlanSelections(hotel = Hotel(name = "Park Hyatt Tokyo", starRating = 5.0, location = "Shinjuku")),
    )

@Preview(name = "Shared trip — light")
@Composable
private fun ShareLightPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { ShareScreen(ShareUiState(isLoading = false, trip = previewTrip, canSaveCopy = true), {}, {}, {}, {}, {}) }
}

@Preview(name = "Shared trip — dark")
@Composable
private fun ShareDarkPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { ShareScreen(ShareUiState(isLoading = false, trip = previewTrip), {}, {}, {}, {}, {}) }
}

@Preview(name = "Shared trip — unavailable")
@Composable
private fun ShareErrorPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { ShareScreen(ShareUiState(isLoading = false, error = AppError.NotFound()), {}, {}, {}, {}, {}) }
}
