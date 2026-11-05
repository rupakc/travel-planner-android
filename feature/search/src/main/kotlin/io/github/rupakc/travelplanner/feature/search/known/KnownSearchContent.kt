package io.github.rupakc.travelplanner.feature.search.known

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.ExtraStop
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.shortCity
import io.github.rupakc.travelplanner.feature.search.R
import io.github.rupakc.travelplanner.feature.search.SearchFormEvent
import io.github.rupakc.travelplanner.feature.search.SearchUiState
import io.github.rupakc.travelplanner.feature.search.components.AirportField
import io.github.rupakc.travelplanner.feature.search.components.BudgetField
import io.github.rupakc.travelplanner.feature.search.components.InterestChips
import io.github.rupakc.travelplanner.feature.search.components.IssueText
import io.github.rupakc.travelplanner.feature.search.components.NationalityField
import io.github.rupakc.travelplanner.feature.search.components.PaceSelector
import io.github.rupakc.travelplanner.feature.search.components.SearchDimens
import io.github.rupakc.travelplanner.feature.search.components.SearchLookups
import io.github.rupakc.travelplanner.feature.search.components.SerendipityDial
import io.github.rupakc.travelplanner.feature.search.components.TagInputField
import io.github.rupakc.travelplanner.feature.search.components.TravellersField
import io.github.rupakc.travelplanner.feature.search.components.TripDatesField
import io.github.rupakc.travelplanner.feature.search.components.issueMessage
import io.github.rupakc.travelplanner.feature.search.components.messageRes

/** The known-destination form (web "I know where I'm going"). */
@Composable
internal fun KnownSearchForm(state: SearchUiState, lookups: SearchLookups, onEvent: (SearchFormEvent) -> Unit, modifier: Modifier = Modifier) {
    val form = state.form
    val update: (io.github.rupakc.travelplanner.core.domain.search.SearchForm) -> Unit = { onEvent(SearchFormEvent.UpdateForm(it)) }
    TpCard(modifier = modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            RouteSpine(state, lookups, onEvent)
            TripDatesField(
                departure = form.departureDate,
                returnDate = form.returnDate,
                today = state.today,
                onChange = { dep, ret -> update(form.copy(departureDate = dep, returnDate = ret)) },
                errorText =
                    issueMessage(
                        state.issues,
                        SearchFormIssue.DEPARTURE_REQUIRED,
                        SearchFormIssue.DEPARTURE_IN_PAST,
                        SearchFormIssue.RETURN_BEFORE_DEPARTURE,
                    ),
            )
            NationalityField(
                value = form.nationality,
                onValueChange = { update(form.copy(nationality = it)) },
                lookups = lookups,
                errorText = issueMessage(state.issues, SearchFormIssue.NATIONALITY_REQUIRED),
            )
            BudgetField(
                value = form.budgetUsd,
                onValueChange = { update(form.copy(budgetUsd = it)) },
                label = stringResource(R.string.search_budget),
                placeholder = stringResource(R.string.search_budget_placeholder),
                errorText = issueMessage(state.issues, SearchFormIssue.BUDGET_INVALID),
            )
            TravellersField(
                counts = form.travellers,
                accessibilityNeeds = form.accessibilityNeeds,
                onCountsChange = { update(form.copy(travellers = it)) },
                onAccessibilityChange = { update(form.copy(accessibilityNeeds = it)) },
            )
            TagInputField(
                label = stringResource(R.string.search_residence_permits),
                values = form.residencePermits,
                placeholder = stringResource(R.string.search_residence_placeholder),
                onChange = { update(form.copy(residencePermits = it)) },
            )
            TagInputField(
                label = stringResource(R.string.search_existing_visas),
                values = form.existingVisas,
                placeholder = stringResource(R.string.search_visas_placeholder),
                onChange = { update(form.copy(existingVisas = it)) },
            )
            InterestChips(stringResource(R.string.search_interests), form.interests, { update(form.copy(interests = it)) })
            SerendipityDial(form.serendipity, { update(form.copy(serendipity = it)) })
            PaceSelector(form.pace, { update(form.copy(pace = it)) })
            if (state.issues.isNotEmpty()) {
                Column(
                    Modifier.semantics { liveRegion = LiveRegionMode.Assertive }.testTag("search_issues"),
                    verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs),
                ) {
                    state.issues.forEach { Text(stringResource(it.messageRes()), color = TravelTheme.colors.error, style = TravelTheme.typography.bodyMedium) }
                }
            }
            TpButton(
                text = stringResource(R.string.search_submit),
                onClick = { onEvent(SearchFormEvent.Submit) },
                modifier = Modifier.fillMaxWidth().testTag("search_submit"),
                leadingIcon = Icons.Filled.Search,
            )
        }
    }
}

/**
 * The route as a journey: origin, stops on the way and the final destination down a dotted spine
 * (plane → dots → pin), in the order the trip is flown. This is the form's one signature element.
 */
@Composable
private fun RouteSpine(state: SearchUiState, lookups: SearchLookups, onEvent: (SearchFormEvent) -> Unit) {
    val form = state.form
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        SpineRow(marker = SpineMarker.ORIGIN) {
            AirportField(
                value = form.origin,
                onValueChange = { onEvent(SearchFormEvent.UpdateForm(form.copy(origin = it))) },
                label = stringResource(R.string.search_from),
                lookups = lookups,
                errorText = issueMessage(state.issues, SearchFormIssue.ORIGIN_REQUIRED),
            )
        }
        state.stops.forEachIndexed { index, stop ->
            SpineRow(marker = SpineMarker.STOP) { StopRow(index, stop, state, lookups, onEvent) }
        }
        if (state.canAddStop) {
            SpineRow(marker = SpineMarker.NONE) {
                TpButton(
                    text = stringResource(R.string.search_add_stop),
                    onClick = { onEvent(SearchFormEvent.AddStop) },
                    variant = TpButtonVariant.Text,
                    leadingIcon = Icons.Filled.Add,
                )
            }
        }
        SpineRow(marker = SpineMarker.DESTINATION, last = true) {
            AirportField(
                value = form.destination,
                onValueChange = { onEvent(SearchFormEvent.UpdateForm(form.copy(destination = it))) },
                label = stringResource(R.string.search_to),
                lookups = lookups,
                errorText = issueMessage(state.issues, SearchFormIssue.DESTINATION_REQUIRED),
            )
        }
        if (state.stops.isNotEmpty()) {
            val route =
                listOf(
                    shortCity(form.origin).ifEmpty { stringResource(R.string.search_route_origin) },
                    state.stops.map {
                        shortCity(it.city)
                    }.filter(String::isNotEmpty).joinToString(" → ").ifEmpty { stringResource(R.string.search_route_stops) },
                    shortCity(form.destination).ifEmpty { stringResource(R.string.search_route_destination) },
                ).joinToString(" → ")
            Text(
                stringResource(R.string.search_route_summary, route),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(start = SearchDimens.SpineWidth + TravelTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun StopRow(index: Int, stop: ExtraStop, state: SearchUiState, lookups: SearchLookups, onEvent: (SearchFormEvent) -> Unit) {
    val number = index + 1
    val cityError = if (stop.city.isBlank()) issueMessage(state.issues, SearchFormIssue.STOP_CITY_REQUIRED) else null
    val nightsError =
        if (stop.nights.isNotBlank() &&
            stop.nights.trim().toIntOrNull() !in 1..30
        ) {
            issueMessage(state.issues, SearchFormIssue.STOP_NIGHTS_OUT_OF_RANGE)
        } else {
            null
        }
    Column {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            AirportField(
                value = stop.city,
                onValueChange = { onEvent(SearchFormEvent.UpdateStop(index, stop.copy(city = it))) },
                label = stringResource(R.string.search_stop_label, number),
                placeholder = stringResource(R.string.search_stopover_city),
                lookups = lookups,
                modifier = Modifier.weight(1f),
                errorText = cityError,
            )
            OutlinedTextField(
                value = stop.nights,
                onValueChange = { v -> onEvent(SearchFormEvent.UpdateStop(index, stop.copy(nights = v.filter(Char::isDigit).take(2)))) },
                modifier = Modifier.width(SearchDimens.NightsField),
                label = { Text(stringResource(R.string.search_stop_nights)) },
                placeholder = { Text(stringResource(R.string.search_stop_nights_placeholder)) },
                singleLine = true,
                isError = nightsError != null,
                shape = TravelShape.Input,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            IconButton(onClick = { onEvent(SearchFormEvent.RemoveStop(index)) }, modifier = Modifier.padding(top = TravelTheme.spacing.sm)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_remove_stop, number))
            }
        }
        nightsError?.let { IssueText(it) }
    }
}

private enum class SpineMarker { ORIGIN, STOP, DESTINATION, NONE }

/** One row of the route spine: a marker column (dot / icon, dotted line below) + the field. */
@Composable
private fun SpineRow(marker: SpineMarker, last: Boolean = false, content: @Composable () -> Unit) {
    val lineColor = TravelTheme.colors.primary.copy(alpha = SPINE_ALPHA)
    val dotColor = TravelTheme.colors.primary
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Box(Modifier.width(SearchDimens.SpineWidth).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!last) {
                Canvas(Modifier.fillMaxHeight().width(SearchDimens.SpineLine)) {
                    drawLine(
                        color = lineColor,
                        start = Offset(size.width / 2, if (marker == SpineMarker.NONE) 0f else SPINE_TOP),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = size.width,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
                    )
                }
            }
            when (marker) {
                SpineMarker.ORIGIN -> {
                    Icon(
                        painterResource(R.drawable.search_ic_flight),
                        contentDescription = null,
                        tint = dotColor,
                        modifier = Modifier.padding(top = TravelTheme.spacing.lg),
                    )
                }

                SpineMarker.DESTINATION -> {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = dotColor, modifier = Modifier.padding(top = TravelTheme.spacing.lg))
                }

                SpineMarker.STOP -> {
                    Canvas(Modifier.padding(top = TravelTheme.spacing.xl).width(SearchDimens.SpineDot).height(SearchDimens.SpineDot)) {
                        drawCircle(dotColor)
                    }
                }

                SpineMarker.NONE -> {
                    Unit
                }
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

private const val SPINE_ALPHA = 0.45f
private const val SPINE_TOP = 64f
private const val DASH_ON = 8f
private const val DASH_OFF = 8f
