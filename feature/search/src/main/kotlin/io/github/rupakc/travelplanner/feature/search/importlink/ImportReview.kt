package io.github.rupakc.travelplanner.feature.search.importlink

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.DraftPlace
import io.github.rupakc.travelplanner.core.domain.search.DraftStop
import io.github.rupakc.travelplanner.core.domain.search.INTEREST_IDS
import io.github.rupakc.travelplanner.core.domain.search.ImportLimits
import io.github.rupakc.travelplanner.core.domain.search.ImportPlanIssue
import io.github.rupakc.travelplanner.core.domain.search.shortCity
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.feature.search.R
import io.github.rupakc.travelplanner.feature.search.components.AirportField
import io.github.rupakc.travelplanner.feature.search.components.BudgetField
import io.github.rupakc.travelplanner.feature.search.components.NationalityField
import io.github.rupakc.travelplanner.feature.search.components.SearchLookups
import io.github.rupakc.travelplanner.feature.search.components.SingleDateField
import io.github.rupakc.travelplanner.feature.search.components.TravellersField
import io.github.rupakc.travelplanner.feature.search.components.formatDisplayDate
import io.github.rupakc.travelplanner.feature.search.components.interestLabelRes
import java.text.NumberFormat

/**
 * Web ImportPreview without the map (no map SDK): what was found, the route with nights per stop,
 * places by stop (tick, move between stops), unplaced places (place by town or add to a stop),
 * the usual trip fields, then "Plan this trip (N places)".
 */
@Composable
internal fun ImportReview(state: ImportUiState, lookups: SearchLookups, onEvent: (ImportEvent) -> Unit, modifier: Modifier = Modifier) {
    val preview = state.preview ?: return
    val draft = state.draft ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
        FoundHeader(preview, draft.places.size + draft.unresolved.size)
        RouteStops(state, onEvent)
        val numbers = draft.places.mapIndexed { i, p -> p.id to i + 1 }.toMap()
        draft.includedStops.forEach { stop ->
            val rows = draft.places.filter { it.place.destination == stop.name }
            if (rows.isNotEmpty()) StopPlaces(stop, rows, numbers, draft.includedStops, onEvent)
        }
        if (draft.unresolved.isNotEmpty()) UnresolvedPlaces(state, draft.unresolved, draft.includedStops, onEvent)
        preview.warnings.filterNot { it.startsWith("We couldn't place") }.forEach { warning ->
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = TravelTheme.extendedColors.warning)
                Text(warning, style = TravelTheme.typography.bodySmall, color = TravelTheme.extendedColors.onWarningContainer)
            }
        }
        TripFields(state, lookups, onEvent)
        PlanFooter(state, onEvent)
    }
}

@Composable
private fun FoundHeader(preview: ImportPreview, placeCount: Int) {
    val openLink = rememberLinkOpener()
    val source = preview.source
    val heading =
        when (source.kind) {
            "url" -> source.title ?: source.siteName ?: stringResource(R.string.search_import_source_page)
            "images" -> stringResource(R.string.search_import_source_images)
            else -> stringResource(R.string.search_import_source_text)
        }
    val byline =
        listOfNotNull(source.siteName, source.author?.let { stringResource(R.string.search_import_by_author, it) })
            .joinToString(", ")
            .ifEmpty { stringResource(R.string.search_import_read_now) }
    val link = source.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    TpCard(modifier = Modifier.fillMaxWidth(), size = TpCardSize.Large, accent = TravelTheme.extendedColors.warning) {
        Text(stringResource(R.string.search_import_found), style = TravelTheme.typography.labelLarge, color = TravelTheme.extendedColors.warning)
        Text(preview.title, style = TravelTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        preview.summary?.let { Text(it, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant) }
        Row(
            Modifier.padding(top = TravelTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            Icon(
                painterResource(if (source.kind == "images") R.drawable.search_ic_image else R.drawable.search_ic_link),
                contentDescription = null,
                tint = TravelTheme.extendedColors.warning,
            )
            Column(Modifier.weight(1f)) {
                Text(heading, style = TravelTheme.typography.titleSmall, maxLines = 2)
                Text(
                    "$byline — " + pluralStringResource(R.plurals.search_import_places_found, placeCount, placeCount),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
            if (link != null) {
                TpButton(stringResource(R.string.search_import_open_source), { openLink(link) }, variant = TpButtonVariant.Text)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteStops(state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    val draft = state.draft ?: return
    val preview = state.preview ?: return
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(stringResource(R.string.search_import_your_route), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            draft.stops.forEach { stop -> StopChip(stop, canToggle = draft.stops.size > 1, onEvent) }
        }
        val summary =
            buildList {
                add(pluralStringResource(R.plurals.search_import_total_nights, state.nights, state.nights))
                if (preview.suggestedDays > 0) add(pluralStringResource(R.plurals.search_import_suggested_days, preview.suggestedDays, preview.suggestedDays))
                preview.seasonHint?.let { add(stringResource(R.string.search_import_best_time, it)) }
            }.joinToString(" ")
        Text(summary, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun StopChip(stop: DraftStop, canToggle: Boolean, onEvent: (ImportEvent) -> Unit) {
    val city = shortCity(stop.name)
    Surface(
        shape = TravelShape.Pill,
        color = if (stop.included) TravelTheme.colors.primaryContainer else TravelTheme.colors.surfaceVariant,
        contentColor = if (stop.included) TravelTheme.colors.onPrimaryContainer else TravelTheme.colors.onSurfaceVariant,
    ) {
        Row(Modifier.padding(start = TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(city, style = TravelTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (stop.included) {
                IconButton(onClick = { onEvent(ImportEvent.ChangeNights(stop.name, -1)) }, enabled = stop.nights > 1) {
                    Icon(painterResource(R.drawable.search_ic_remove), contentDescription = stringResource(R.string.search_import_less_night, city))
                }
                Text(pluralStringResource(R.plurals.search_import_nights, stop.nights, stop.nights), style = TravelTheme.typography.labelMedium)
                IconButton(onClick = { onEvent(ImportEvent.ChangeNights(stop.name, 1)) }, enabled = stop.nights < ImportLimits.MAX_STOP_NIGHTS) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.search_import_more_night, city))
                }
            } else {
                Text(" · " + stringResource(R.string.search_import_left_out), style = TravelTheme.typography.labelMedium)
            }
            if (canToggle) {
                IconButton(onClick = { onEvent(ImportEvent.ToggleStop(stop.name)) }) {
                    Icon(
                        if (stop.included) Icons.Filled.Close else Icons.Filled.Add,
                        contentDescription = stringResource(if (stop.included) R.string.search_import_leave_out else R.string.search_import_add_back, city),
                    )
                }
            }
        }
    }
}

@Composable
private fun StopPlaces(stop: DraftStop, rows: List<DraftPlace>, numbers: Map<String, Int>, stops: List<DraftStop>, onEvent: (ImportEvent) -> Unit) {
    val city = shortCity(stop.name)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                city,
                style = TravelTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(stop.destination.country, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            val allLabel = stringResource(R.string.search_import_select_all, city)
            val noneLabel = stringResource(R.string.search_import_select_none, city)
            TextButton(onClick = { onEvent(ImportEvent.SelectStopPlaces(stop.name, true)) }, modifier = Modifier.semantics { contentDescription = allLabel }) {
                Text(stringResource(R.string.search_import_all))
            }
            TextButton(onClick = {
                onEvent(ImportEvent.SelectStopPlaces(stop.name, false))
            }, modifier = Modifier.semantics { contentDescription = noneLabel }) {
                Text(stringResource(R.string.search_import_none))
            }
        }
        HorizontalDivider(color = TravelTheme.colors.outlineVariant)
        rows.forEach { place -> PlaceRow(place, numbers[place.id] ?: 0, stops, onEvent) }
    }
}

@StringRes
private fun kindLabelRes(kind: String): Int =
    when (kind) {
        "food" -> R.string.search_import_kind_food
        "drink" -> R.string.search_import_kind_drink
        "stay" -> R.string.search_import_kind_stay
        "activity" -> R.string.search_import_kind_activity
        "nature" -> R.string.search_import_kind_nature
        "shopping" -> R.string.search_import_kind_shopping
        "nightlife" -> R.string.search_import_kind_nightlife
        "event" -> R.string.search_import_kind_event
        "transport" -> R.string.search_import_kind_transport
        "area" -> R.string.search_import_kind_area
        else -> R.string.search_import_kind_sight
    }

@Composable
private fun PlaceRow(draft: DraftPlace, number: Int, stops: List<DraftStop>, onEvent: (ImportEvent) -> Unit) {
    val place = draft.place
    val town = place.city?.takeIf { it.isNotEmpty() && shortCity(place.destination) != it }
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(draft.selected, role = Role.Checkbox) { onEvent(ImportEvent.TogglePlace(place.id)) }
            .padding(vertical = TravelTheme.spacing.sm)
            .testTag("place_${place.id}"),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        Checkbox(checked = draft.selected, onCheckedChange = null)
        Surface(
            shape = CircleShape,
            color = if (place.approximate) TravelTheme.colors.surface else TravelTheme.colors.primary,
            contentColor = if (place.approximate) TravelTheme.colors.primary else TravelTheme.colors.onPrimary,
            border = BorderStroke(TravelTheme.spacing.xxs, TravelTheme.colors.primary),
            modifier = Modifier.size(TravelTheme.spacing.xl),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(NumberFormat.getIntegerInstance().format(number), style = TravelTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
            Text(place.name, style = TravelTheme.typography.titleSmall)
            val meta =
                listOfNotNull(
                    stringResource(kindLabelRes(place.kind)),
                    town?.let { stringResource(R.string.search_import_day_trip, it) },
                ).joinToString(" · ")
            Text(meta, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            if (place.verified) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = TravelTheme.extendedColors.success,
                        modifier = Modifier.size(TravelTheme.spacing.lg),
                    )
                    Text(
                        place.rating?.let { stringResource(R.string.search_import_on_map_rating, it.toString()) }
                            ?: stringResource(R.string.search_import_on_map),
                        style = TravelTheme.typography.labelSmall,
                        color = TravelTheme.extendedColors.success,
                    )
                }
            } else {
                val hint = stringResource(R.string.search_import_approximate_hint)
                Text(
                    stringResource(R.string.search_import_approximate),
                    style = TravelTheme.typography.labelSmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = hint },
                )
            }
            place.quote?.let { Text(stringResource(R.string.search_import_quote, it), style = TravelTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
            place.note?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
            if (stops.size > 1) {
                StopPicker(
                    label = stringResource(R.string.search_import_stop_for, place.name),
                    current = place.destination,
                    stops = stops,
                    allowLeaveOut = false,
                    onPick = { onEvent(ImportEvent.SetPlaceStop(place.id, it)) },
                )
            }
        }
    }
}

/** Stop selector (web `<select>`): current stop's city, a menu of included stops (+ "Leave out"). */
@Composable
private fun StopPicker(label: String, current: String?, stops: List<DraftStop>, allowLeaveOut: Boolean, onPick: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val leaveOut = stringResource(R.string.search_import_leave_out_option)
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.semantics { contentDescription = "$label: ${current?.let(::shortCity) ?: leaveOut}" }) {
            Icon(Icons.Filled.Place, contentDescription = null)
            Text(current?.let(::shortCity) ?: leaveOut)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowLeaveOut) {
                DropdownMenuItem(text = { Text(leaveOut) }, onClick = {
                    onPick(null)
                    open = false
                })
            }
            stops.forEach { stop ->
                DropdownMenuItem(text = { Text(shortCity(stop.name)) }, onClick = {
                    onPick(stop.name)
                    open = false
                })
            }
        }
    }
}

@Composable
private fun UnresolvedPlaces(state: ImportUiState, places: List<DraftPlace>, stops: List<DraftStop>, onEvent: (ImportEvent) -> Unit) {
    TpCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            pluralStringResource(R.plurals.search_import_unplaced_title, places.size, places.size),
            style = TravelTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.search_import_unplaced_sub), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        places.forEach { draft ->
            val place = draft.place
            val lookup = state.towns[place.id] ?: TownLookup(town = place.city.orEmpty())
            Column(Modifier.padding(top = TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                Text(place.name, style = TravelTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    val townLabel = stringResource(R.string.search_import_town_for, place.name)
                    OutlinedTextField(
                        value = lookup.town,
                        onValueChange = { onEvent(ImportEvent.TownChanged(place.id, it)) },
                        modifier = Modifier.weight(1f).semantics { contentDescription = townLabel },
                        placeholder = { Text(stringResource(R.string.search_import_which_town)) },
                        singleLine = true,
                        shape = TravelShape.Input,
                    )
                    TpButton(
                        stringResource(if (lookup.busy) R.string.search_import_placing else R.string.search_import_place_it),
                        {
                            if (state.towns[place.id] == null) onEvent(ImportEvent.TownChanged(place.id, lookup.town))
                            onEvent(ImportEvent.PlaceIt(place.id))
                        },
                        enabled = lookup.town.isNotBlank() && !lookup.busy,
                        loading = lookup.busy,
                        variant = TpButtonVariant.Tonal,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.search_import_or_add_to),
                        style = TravelTheme.typography.bodySmall,
                        color = TravelTheme.colors.onSurfaceVariant,
                    )
                    StopPicker(
                        label = stringResource(R.string.search_import_which_stop, place.name),
                        current = place.destination,
                        stops = stops,
                        allowLeaveOut = true,
                        onPick = { onEvent(ImportEvent.SetPlaceStop(place.id, it)) },
                    )
                }
                lookup.problem?.let { problem ->
                    val text =
                        when (problem) {
                            is TownProblem.Message -> problem.text ?: stringResource(R.string.search_import_cant_place)
                            is TownProblem.TooManyStops -> stringResource(R.string.search_import_too_many_stops, shortCity(problem.stopName))
                        }
                    Text(
                        text,
                        style = TravelTheme.typography.bodySmall,
                        color = TravelTheme.colors.error,
                        modifier =
                            Modifier.semantics {
                                liveRegion =
                                    LiveRegionMode.Polite
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun TripFields(state: ImportUiState, lookups: SearchLookups, onEvent: (ImportEvent) -> Unit) {
    val trip = state.trip
    val update: (io.github.rupakc.travelplanner.core.domain.search.SearchForm) -> Unit = { onEvent(ImportEvent.UpdateTrip(it)) }
    TpCard(modifier = Modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            AirportField(
                value = trip.origin,
                onValueChange = { update(trip.copy(origin = it)) },
                label = stringResource(R.string.search_from),
                lookups = lookups,
                leadingPainter = { Icon(painterResource(R.drawable.search_ic_flight), contentDescription = null) },
            )
            Column {
                SingleDateField(stringResource(R.string.search_import_leaving_on), trip.departureDate, state.today, { update(trip.copy(departureDate = it)) })
                state.built?.form?.returnDate?.takeIf(String::isNotEmpty)?.let { back ->
                    Text(
                        stringResource(R.string.search_import_back_on, formatDisplayDate(back)),
                        style = TravelTheme.typography.bodySmall,
                        color = TravelTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = TravelTheme.spacing.xs),
                    )
                }
            }
            NationalityField(value = trip.nationality, onValueChange = { update(trip.copy(nationality = it)) }, lookups = lookups)
            BudgetField(
                value = trip.budgetUsd,
                onValueChange = { update(trip.copy(budgetUsd = it)) },
                label = stringResource(R.string.search_budget),
                placeholder = stringResource(R.string.search_budget_total_placeholder),
            )
            TravellersField(
                counts = trip.travellers,
                accessibilityNeeds = trip.accessibilityNeeds,
                onCountsChange = { update(trip.copy(travellers = it)) },
                onAccessibilityChange = { update(trip.copy(accessibilityNeeds = it)) },
            )
            state.built?.form?.interests?.takeIf { it.isNotEmpty() }?.let { interests ->
                val labels = interests.map { id -> if (id in INTEREST_IDS) stringResource(interestLabelRes(id)) else id }
                Text(
                    stringResource(R.string.search_import_interests, labels.joinToString(", ")),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlanFooter(state: ImportUiState, onEvent: (ImportEvent) -> Unit) {
    val count = state.built?.form?.mustInclude?.size ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        state.planIssue?.let { issue ->
            val text =
                stringResource(
                    when (issue) {
                        ImportPlanIssue.ORIGIN_REQUIRED -> R.string.search_issue_origin
                        ImportPlanIssue.NATIONALITY_REQUIRED -> R.string.search_issue_nationality
                        ImportPlanIssue.NO_PLACES -> R.string.search_import_no_places
                        ImportPlanIssue.NO_STOPS -> R.string.search_import_no_stops
                        ImportPlanIssue.DEPARTURE_IN_PAST -> R.string.search_import_past
                    },
                )
            Text(
                text,
                style = TravelTheme.typography.bodyMedium,
                color = TravelTheme.extendedColors.onWarningContainer,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("import_plan_issue"),
            )
        }
        if (state.selectedTotal > ImportLimits.MAX_MUST_INCLUDE) {
            Text(
                pluralStringResource(R.plurals.search_import_first_25, ImportLimits.MAX_MUST_INCLUDE, ImportLimits.MAX_MUST_INCLUDE),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
        TpButton(
            pluralStringResource(R.plurals.search_import_plan, count, count),
            { onEvent(ImportEvent.Plan) },
            enabled = state.planIssue == null,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget).testTag("import_plan"),
        )
        TpButton(stringResource(R.string.search_import_start_over), {
            onEvent(ImportEvent.StartOver)
        }, variant = TpButtonVariant.Secondary, modifier = Modifier.fillMaxWidth())
    }
}
