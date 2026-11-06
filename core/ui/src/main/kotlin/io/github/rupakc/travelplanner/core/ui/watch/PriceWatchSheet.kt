package io.github.rupakc.travelplanner.core.ui.watch

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.DiffReason
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogController
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogState
import io.github.rupakc.travelplanner.core.domain.alerts.RowBadge
import io.github.rupakc.travelplanner.core.domain.alerts.SwapError
import io.github.rupakc.travelplanner.core.domain.alerts.Tone
import io.github.rupakc.travelplanner.core.domain.alerts.TripPart
import io.github.rupakc.travelplanner.core.domain.alerts.TripRow
import io.github.rupakc.travelplanner.core.domain.alerts.WatchedOutcome
import io.github.rupakc.travelplanner.core.domain.alerts.estimateCount
import io.github.rupakc.travelplanner.core.domain.alerts.estimateInfo
import io.github.rupakc.travelplanner.core.domain.alerts.formatDay
import io.github.rupakc.travelplanner.core.domain.alerts.liveCount
import io.github.rupakc.travelplanner.core.domain.alerts.matchedElsewhere
import io.github.rupakc.travelplanner.core.domain.alerts.nightsBetween
import io.github.rupakc.travelplanner.core.domain.alerts.perPerson
import io.github.rupakc.travelplanner.core.domain.alerts.providerName
import io.github.rupakc.travelplanner.core.domain.alerts.rowBadge
import io.github.rupakc.travelplanner.core.domain.alerts.titleDiffers
import io.github.rupakc.travelplanner.core.domain.alerts.totalDifference
import io.github.rupakc.travelplanner.core.domain.alerts.tripRows
import io.github.rupakc.travelplanner.core.domain.alerts.watchCurrency
import io.github.rupakc.travelplanner.core.model.alerts.PriceSuggestion
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.R
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.core.ui.format.localUsd
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Forwards [event] to a price controller; [onWatched] says which spec was watched (and whether via a suggestion). */
fun WatchSheetEvent.applyTo(controller: PriceWatchDialogController, scope: CoroutineScope, onWatched: (WatchedOutcome) -> Unit = {}) {
    when (this) {
        is WatchSheetEvent.SetMode -> {
            controller.setMode(mode)
        }

        is WatchSheetEvent.SetTarget -> {
            controller.setTarget(text, locale)
        }

        is WatchSheetEvent.SetEmail -> {
            controller.setEmail(on)
        }

        WatchSheetEvent.Submit -> {
            scope.launch { controller.submit()?.let(onWatched) }
        }

        is WatchSheetEvent.PickSuggestion -> {
            scope.launch {
                controller.state.value.noMatch?.suggestions?.getOrNull(index)?.let { controller.pickSuggestion(it)?.let(onWatched) }
            }
        }

        WatchSheetEvent.ToggleAlternatives -> {
            controller.toggleAlternatives()
        }

        is WatchSheetEvent.SwapTo -> {
            scope.launch { controller.swapTo(index)?.let(onWatched) }
        }

        is WatchSheetEvent.SetFlexible -> {
            Unit
        }
    }
}

/**
 * "Watch this hotel / activity / trip" bottom sheet (web `PriceWatchDialog`). Build the controller
 * with `PriceWatchesRepository::create` / `::delete`; mark your own card as watched only when
 * [WatchedOutcome.viaSuggestion] is false.
 *
 * Requests run in this composable's scope, so closing the sheet mid-request cancels the callback
 * (the watch may still be created server-side). Hosts that must always learn the outcome should
 * own the controller in their ViewModel and call `submit()` / `pickSuggestion()` / `swapTo()` from
 * `viewModelScope`, rendering the stateless `*SheetContent` themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceWatchSheet(
    controller: PriceWatchDialogController,
    userEmail: String?,
    onDismiss: () -> Unit,
    onSeeAlerts: (PriceWatch) -> Unit,
    onWatched: (WatchedOutcome) -> Unit = {},
    /** A host that owns [controller] passes its own handler (e.g. `applyTo(controller, viewModelScope)`). */
    onEvent: ((WatchSheetEvent) -> Unit)? = null,
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    val handle = onEvent ?: { event: WatchSheetEvent -> event.applyTo(controller, scope, onWatched) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PriceWatchSheetContent(state, userEmail, handle, onDismiss, onSeeAlerts)
    }
}

@Composable
private fun kindNoun(kind: String): String =
    stringResource(
        when (kind) {
            PriceWatchKinds.HOTEL -> R.string.watch_noun_hotel
            PriceWatchKinds.ACTIVITY -> R.string.watch_noun_activity
            PriceWatchKinds.TRIP -> R.string.watch_noun_trip
            else -> R.string.watch_noun_price
        },
    )

/** Stateless body of [PriceWatchSheet]. */
@Composable
fun PriceWatchSheetContent(
    state: PriceWatchDialogState,
    userEmail: String?,
    onEvent: (WatchSheetEvent) -> Unit,
    onDismiss: () -> Unit,
    onSeeAlerts: (PriceWatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noun = kindNoun(state.kind)
    val done = state.done
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TravelTheme.spacing.xl)
            .padding(bottom = TravelTheme.spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        val title =
            when {
                done == null -> stringResource(R.string.watch_price_title, noun)
                done.alreadyWatching == true -> stringResource(R.string.watch_price_title_already, noun)
                else -> stringResource(R.string.watch_price_title_done, noun)
            }
        Text(title, style = TravelTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        val subtitle = (if (done != null) listOf(done.title, done.subtitle) else listOf(state.preview.title, state.preview.subtitle)).filterNotNull()
        if (subtitle.isNotEmpty()) {
            Text(subtitle.joinToString(" · "), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        }
        if (done == null) PriceForm(state, noun, userEmail, onEvent, onDismiss) else PriceDone(state, done, onEvent, onDismiss, onSeeAlerts)
    }
}

@Composable
private fun PriceForm(state: PriceWatchDialogState, noun: String, userEmail: String?, onEvent: (WatchSheetEvent) -> Unit, onDismiss: () -> Unit) {
    when (state.kind) {
        PriceWatchKinds.HOTEL -> HotelBefore(state)
        PriceWatchKinds.ACTIVITY -> ActivityBefore(state.usedSpec)
        PriceWatchKinds.TRIP -> TripBefore(state.preview.parts)
        else -> GenericBefore(state)
    }
    val locale = currentLocale()
    AlertModeGroup(
        mode = state.mode,
        onMode = { onEvent(WatchSheetEvent.SetMode(it)) },
        target = state.target,
        onTarget = { onEvent(WatchSheetEvent.SetTarget(it, locale)) },
        currency = state.currency,
        anyDropHint = stringResource(R.string.watch_price_any_drop_hint),
        targetError = targetErrorText(state.shownTargetError),
        helper = state.currencyAsk?.message ?: stringResource(R.string.watch_price_target_hint, currencyLabel(state.currency)),
    )
    if (state.emailAvailable) EmailOption(state.email, userEmail, { onEvent(WatchSheetEvent.SetEmail(it)) })
    state.noMatch?.let { noMatch ->
        ToneBox(Tone.WARN, live = true) {
            Text(noMatch.message ?: noMatch.detail.orEmpty(), style = TravelTheme.typography.titleSmall)
            noMatch.estimateInfo()?.let { e ->
                val label = e.label?.let { " $it" }.orEmpty()
                Text(stringResource(R.string.watch_no_live_estimate, localPrice(e.price, e.currency), label), style = TravelTheme.typography.bodySmall)
            }
            if (noMatch.suggestions.isNotEmpty()) {
                Text(stringResource(R.string.watch_suggestions_heading), style = TravelTheme.typography.labelLarge)
                noMatch.suggestions.forEachIndexed { i, s ->
                    SuggestionRow(s, busy = state.saving, onWatch = { onEvent(WatchSheetEvent.PickSuggestion(i)) })
                }
            }
        }
    }
    state.error?.let { err ->
        ToneBox(Tone.WARN, live = true) { Text(err.message ?: stringResource(R.string.watch_price_error, noun)) }
    }
    SheetButtons(
        primary = stringResource(if (state.saving) R.string.watch_price_saving else R.string.watch_start),
        onPrimary = { onEvent(WatchSheetEvent.Submit) },
        primaryEnabled = state.shownTargetError == null,
        loading = state.saving,
        secondary = stringResource(R.string.watch_cancel),
        onSecondary = onDismiss,
        showPrimary = state.noMatch == null,
    )
}

/** One live alternative: title, subtitle, price in its own currency, "Watch this instead". */
@Composable
internal fun SuggestionRow(s: PriceSuggestion, busy: Boolean, onWatch: () -> Unit) {
    Surface(shape = TravelShape.Input, color = TravelTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Text(s.title.orEmpty(), style = TravelTheme.typography.titleSmall, color = TravelTheme.colors.onSurface)
            s.subtitle?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                s.price?.let {
                    Text(
                        listOfNotNull(localPrice(it, s.currency), s.priceLabel).joinToString(" "),
                        style = TravelTheme.typography.titleSmall,
                        color = TravelTheme.colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                TpButton(stringResource(R.string.watch_this_instead), onWatch, enabled = !busy, variant = TpButtonVariant.Secondary)
            }
        }
    }
}

@Composable
private fun Muted(text: String) = Text(text, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject?.int(key: String): Int? = (this?.get(key) as? JsonPrimitive)?.intOrNull

@Composable
private fun HotelBefore(state: PriceWatchDialogState) {
    val spec = state.usedSpec
    val preview = state.preview
    ToneBox(Tone.NEUTRAL) {
        preview.estimate?.let { estimate ->
            val rooms = spec.int("rooms") ?: 1
            val note =
                if (rooms > 1) {
                    pluralStringResource(R.plurals.watch_hotel_estimate_rooms, rooms, rooms, localPrice(estimate / rooms, preview.currency))
                } else {
                    stringResource(R.string.watch_hotel_estimate_one_room)
                }
            PriceLead(stringResource(R.string.watch_shown_now), localPrice(estimate, preview.currency), note)
        }
        val city = spec.str("city")?.substringBefore(',')
        val name = spec.str("name") ?: stringResource(R.string.watch_noun_hotel)
        val stay = preview.subtitle ?: stringResource(R.string.watch_hotel_your_stay)
        Muted(
            if (city != null) {
                stringResource(R.string.watch_hotel_find, name, city, stay)
            } else {
                stringResource(R.string.watch_hotel_find_no_city, name, stay)
            },
        )
    }
}

@Composable
private fun ActivityBefore(spec: JsonObject) {
    val n = spec.int("travellers") ?: 1
    ToneBox(Tone.NEUTRAL) {
        val name = spec.str("name").orEmpty()
        val travellers = if (n > 1) ", " + pluralStringResource(R.plurals.watch_for_travellers, n, n) else ""
        if (spec.str("listing_url") != null) {
            Text(stringResource(R.string.watch_activity_listing, name, travellers))
        } else {
            Text(stringResource(R.string.watch_activity_find, name, spec.str("city").orEmpty(), travellers))
        }
        spec.str("date")?.let { Muted(stringResource(R.string.watch_activity_date, formatDay(it, currentLocale()) ?: it)) }
        spec.num("estimate_price")?.let { Muted(stringResource(R.string.watch_activity_estimate, localPrice(it, "USD"))) }
    }
}

@Composable
private fun TripBefore(parts: List<TripPart>) {
    ToneBox(Tone.NEUTRAL) {
        Text(stringResource(R.string.watch_trip_as_counted), style = TravelTheme.typography.titleSmall)
        parts.forEach { p ->
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(p.key.labelRes()), style = TravelTheme.typography.bodyMedium)
                    when (p.note) {
                        TripPart.Note.CHEAPEST_FARE -> Muted(stringResource(R.string.watch_trip_note_cheapest))
                        TripPart.Note.PER_PERSON -> Muted(stringResource(R.string.watch_trip_note_per_person, p.travellers))
                        null -> Unit
                    }
                }
                Text(localPrice(p.amount), style = TravelTheme.typography.bodyMedium)
            }
        }
        InfoNote(stringResource(R.string.watch_trip_how) + " " + stringResource(R.string.watch_trip_estimates_fixed))
    }
}

private fun TripPart.Key.labelRes(): Int =
    when (this) {
        TripPart.Key.FLIGHTS -> R.string.watch_trip_part_flights
        TripPart.Key.HOTEL -> R.string.watch_trip_part_hotel
        TripPart.Key.ACTIVITIES -> R.string.watch_trip_part_activities
        TripPart.Key.SIM -> R.string.watch_trip_part_sim
    }

@Composable
private fun GenericBefore(state: PriceWatchDialogState) {
    val price = state.preview.price ?: return
    ToneBox(Tone.NEUTRAL) {
        PriceLead(stringResource(R.string.watch_shown_now), localPrice(price, state.preview.currency), stringResource(R.string.watch_generic_lookup))
    }
}

@Composable
internal fun InfoNote(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant)
        Muted(text)
    }
}

@Composable
private fun PriceDone(
    state: PriceWatchDialogState,
    watch: PriceWatch,
    onEvent: (WatchSheetEvent) -> Unit,
    onDismiss: () -> Unit,
    onSeeAlerts: (PriceWatch) -> Unit,
) {
    ToneBox(Tone.GOOD, live = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                val price = watch.baselinePrice ?: watch.lastPrice
                Text(
                    if (price != null) {
                        stringResource(R.string.watch_price_done_price, localPrice(price, watch.currency))
                    } else {
                        stringResource(R.string.watch_price_done_pending)
                    },
                )
                if (watch.alreadyBelowTarget == true) Text(stringResource(R.string.watch_already_below_target), style = TravelTheme.typography.titleSmall)
            }
        }
    }
    when (state.kind) {
        PriceWatchKinds.HOTEL -> HotelTracked(state, watch, onEvent)
        PriceWatchKinds.ACTIVITY -> ActivityMatched(watch)
        PriceWatchKinds.TRIP -> TripTracked(watch, state.preview.planTotal)
        else -> watch.title?.let { Text(stringResource(R.string.watch_tracking, it)) }
    }
    SheetButtons(
        primary = stringResource(R.string.watch_done),
        onPrimary = onDismiss,
        secondary = stringResource(R.string.watch_see_alerts),
        onSecondary = { onSeeAlerts(watch) },
    )
}

@Composable
private fun HotelTracked(state: PriceWatchDialogState, watch: PriceWatch, onEvent: (WatchSheetEvent) -> Unit) {
    val s = watch.subject
    val total = watch.baselinePrice ?: watch.lastPrice
    val nights = nightsBetween(s.str("check_in"), s.str("check_out"))
    val unit = watch.lastObservation?.num("unit_price")
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(stringResource(R.string.watch_hotel_tracking, watch.title.orEmpty()))
        val parts =
            listOfNotNull(
                total?.let { localPrice(it, watch.currency) + (nights?.let { n -> " " + pluralStringResource(R.plurals.watch_for_nights, n, n) } ?: "") },
                unit?.let { stringResource(R.string.watch_hotel_nightly, localPrice(it, watch.currency)) },
                watch.subtitle,
            )
        if (parts.isNotEmpty()) Muted(parts.joinToString(" · "))
        if (matchedElsewhere(watch)) Muted(stringResource(R.string.watch_hotel_listed_as, s.str("requested_name").orEmpty(), watch.title.orEmpty()))
        val alternatives = state.alternatives
        if (alternatives.isNotEmpty()) {
            val expanded = stringResource(if (state.swap.open) R.string.watch_expanded else R.string.watch_collapsed)
            TpButton(
                stringResource(R.string.watch_not_this_one),
                { onEvent(WatchSheetEvent.ToggleAlternatives) },
                variant = TpButtonVariant.Text,
                modifier =
                    Modifier.semantics {
                        stateDescription = expanded
                    },
            )
            if (state.swap.open) {
                alternatives.forEachIndexed { i, alt ->
                    SuggestionRow(alt, busy = state.swap.busyIndex != null, onWatch = { onEvent(WatchSheetEvent.SwapTo(i)) })
                }
            }
        }
        state.swap.error?.let { e ->
            ToneBox(Tone.WARN, live = true) {
                Text(stringResource(if (e == SwapError.KEPT_FIRST) R.string.watch_swap_kept_first else R.string.watch_swap_lost_first))
            }
        }
    }
}

@Composable
private fun ActivityMatched(watch: PriceWatch) {
    val subject = watch.subject
    val observed = watch.lastObservation?.get("observed") as? JsonObject
    val currency = watchCurrency(watch)
    val provider = providerName(subject.str("provider") ?: observed?.str("provider")) ?: stringResource(R.string.watch_activity_the_listing)
    val title = subject.str("listing_title") ?: watch.title
    val n = subject.int("travellers") ?: 1
    val unit = perPerson(watch)
    val total = watch.lastPrice ?: watch.baselinePrice
    val link = watch.link ?: watch.lastObservation?.str("link") ?: subject.str("listing_url")
    val open = rememberLinkOpener()
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(provider, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
        Text(title.orEmpty(), style = TravelTheme.typography.titleSmall)
        observed?.num("rating")?.let { r ->
            val reviews = observed.int("reviews")
            Muted(
                stringResource(R.string.watch_activity_rating, r.toString()) +
                    (reviews?.let { " · " + pluralStringResource(R.plurals.watch_reviews, it, it) } ?: ""),
            )
        }
        if (unit != null) {
            val perPersonText = stringResource(R.string.watch_activity_from_pp, localPrice(unit, currency))
            Text(
                if (n > 1 && total != null) {
                    perPersonText + " × " + pluralStringResource(R.plurals.watch_travellers, n, n) + " = " + localPrice(total, currency)
                } else {
                    perPersonText
                },
            )
        }
        if (titleDiffers(subject.str("name"), title)) {
            Text(stringResource(R.string.watch_activity_closest, subject.str("name").orEmpty()), color = TravelTheme.extendedColors.warning)
        }
        Muted(
            stringResource(R.string.watch_activity_source, provider) +
                if (currency != "USD") " " + stringResource(R.string.watch_activity_currency, currency) else "",
        )
        if (link != null) TpButton(stringResource(R.string.watch_activity_see_on, provider), { open(link) }, variant = TpButtonVariant.Text)
    }
}

@Composable
private fun TripTracked(watch: PriceWatch, planTotal: Double?) {
    val rows = tripRows(watch)
    val total = watch.lastPrice ?: watch.baselinePrice
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        val estimates = estimateCount(rows)
        Text(
            stringResource(
                R.string.watch_trip_watching_caption,
                pluralStringResource(R.plurals.watch_live_count, liveCount(rows), liveCount(rows)),
                pluralStringResource(R.plurals.watch_estimates, estimates, estimates),
            ),
            style = TravelTheme.typography.labelLarge,
        )
        rows.forEach { TripRowLine(it) }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.watch_trip_total), style = TravelTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(localPrice(total), style = TravelTheme.typography.titleSmall)
        }
        totalDifference(watch, planTotal)?.let { diff ->
            val reasons = diff.reasons.map { reasonText(it, diff.travellers) }
            val base =
                stringResource(
                    if (diff.delta < 0) R.string.watch_trip_diff_less else R.string.watch_trip_diff_more,
                    localPrice(abs(diff.delta)),
                    localPrice(diff.plan),
                )
            Muted(if (reasons.isEmpty()) "$base." else "$base: ${reasons.joinToString("; ")}.")
        }
        InfoNote(stringResource(R.string.watch_trip_estimates_fixed) + " " + stringResource(R.string.watch_trip_alert_rule, localUsd(TRIP_ALERT_STEP_USD)))
    }
}

@Composable
private fun reasonText(reason: DiffReason, travellers: Int): String =
    when (reason) {
        DiffReason.CHEAPEST_FARE -> stringResource(R.string.watch_trip_reason_fare)
        DiffReason.LIVE_HOTEL -> stringResource(R.string.watch_trip_reason_hotel)
        DiffReason.ACTIVITIES_PER_PERSON -> stringResource(R.string.watch_trip_reason_per_person, travellers)
        DiffReason.LIVE_ACTIVITIES -> stringResource(R.string.watch_trip_reason_activities)
    }

/** One part of a watched trip with its price and a Live / Estimate badge (text, not colour only). */
@Composable
fun TripRowLine(row: TripRow, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Column(Modifier.weight(1f)) {
            Text(row.label, style = TravelTheme.typography.bodyMedium)
            row.detail?.let { Muted(it) }
        }
        TripBadge(rowBadge(row))
        Column(horizontalAlignment = Alignment.End) {
            Text(localPrice(row.price, row.currency), style = TravelTheme.typography.bodyMedium)
            if (!row.inTotal) Muted(stringResource(R.string.watch_trip_not_in_total))
        }
    }
}

/** Live / Estimate (or degraded) badge for one part of a watched trip (web `TripBadge`). */
@Composable
fun TripBadge(badge: RowBadge, modifier: Modifier = Modifier) {
    val (bg, fg) = toneColors(badge.tone)
    val (label, title) =
        when (badge) {
            RowBadge.ESTIMATE -> R.string.watch_badge_estimate to R.string.watch_badge_estimate_hint
            RowBadge.UNAVAILABLE -> R.string.watch_badge_unavailable to R.string.watch_badge_unavailable_hint
            RowBadge.NOT_CHECKED -> R.string.watch_badge_not_checked to R.string.watch_badge_not_checked_hint
            RowBadge.LIVE_EARLIER -> R.string.watch_badge_live_earlier to R.string.watch_badge_live_earlier_hint
            RowBadge.LIVE -> R.string.watch_badge_live to R.string.watch_badge_live_hint
        }
    val hint = stringResource(title)
    Surface(
        color = bg,
        contentColor = fg,
        shape = TravelShape.Pill,
        modifier = modifier.semantics { stateDescription = hint },
    ) {
        Text(
            stringResource(label),
            style = TravelTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
        )
    }
}

@Preview(name = "Hotel sheet · light")
@Composable
private fun HotelSheetPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        Surface {
            PriceWatchSheetContent(
                PriceWatchDialogState(
                    kind = PriceWatchKinds.HOTEL,
                    spec = JsonObject(mapOf("name" to JsonPrimitive("Lumen Hotel"), "city" to JsonPrimitive("Lisbon"))),
                    preview =
                        io.github.rupakc.travelplanner.core.domain.alerts.PricePreview(
                            title = "Lumen Hotel",
                            subtitle = "10–14 Dec · 4 nights · 2 guests · 1 room",
                            estimate = 410.0,
                        ),
                ),
                null,
                {},
                {},
                {},
            )
        }
    }
}

@Preview(name = "Activity sheet · dark, done")
@Composable
private fun ActivitySheetPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) {
        Surface {
            PriceWatchSheetContent(
                PriceWatchDialogState(
                    kind = PriceWatchKinds.ACTIVITY,
                    spec = JsonObject(emptyMap()),
                    done =
                        PriceWatch(
                            id = 1,
                            kind = PriceWatchKinds.ACTIVITY,
                            title = "Lisbon Sunset Sail",
                            baselinePrice = 46.0,
                            currency = "EUR",
                            subject = JsonObject(mapOf("provider" to JsonPrimitive("getyourguide"), "travellers" to JsonPrimitive(2))),
                        ),
                ),
                null,
                {},
                {},
                {},
            )
        }
    }
}
