package io.github.rupakc.travelplanner.feature.search.compare

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonBlock
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.CompareErrorReason
import io.github.rupakc.travelplanner.core.domain.search.CompareRow
import io.github.rupakc.travelplanner.core.domain.search.DASH
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.fmtDuration
import io.github.rupakc.travelplanner.core.domain.search.fmtUsd
import io.github.rupakc.travelplanner.core.model.search.CompareEase
import io.github.rupakc.travelplanner.core.model.search.CompareEstimate
import io.github.rupakc.travelplanner.core.model.search.CompareFlight
import io.github.rupakc.travelplanner.core.model.search.CompareHotel
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.CompareVisa
import io.github.rupakc.travelplanner.core.model.search.CompareWeather
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.search.R
import io.github.rupakc.travelplanner.feature.search.components.AirportField
import io.github.rupakc.travelplanner.feature.search.components.BudgetField
import io.github.rupakc.travelplanner.feature.search.components.InterestChips
import io.github.rupakc.travelplanner.feature.search.components.IssueText
import io.github.rupakc.travelplanner.feature.search.components.NationalityField
import io.github.rupakc.travelplanner.feature.search.components.SearchDimens
import io.github.rupakc.travelplanner.feature.search.components.SearchLookups
import io.github.rupakc.travelplanner.feature.search.components.TravellersField
import io.github.rupakc.travelplanner.feature.search.components.TripDatesField
import io.github.rupakc.travelplanner.feature.search.components.issueMessage
import kotlin.math.roundToInt

/** Web Compare form: From, Destination 1..3 (remove / "Add another"), dates, nationality, budget, travellers, interests. */
@Composable
internal fun CompareForm(state: CompareUiState, lookups: SearchLookups, onEvent: (CompareEvent) -> Unit, modifier: Modifier = Modifier) {
    val form = state.form ?: return
    val update: (io.github.rupakc.travelplanner.core.domain.search.SearchForm) -> Unit = { onEvent(CompareEvent.UpdateForm(it)) }
    TpCard(modifier = modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            Text(
                stringResource(R.string.search_compare_intro),
                style = TravelTheme.typography.bodyMedium,
                color = TravelTheme.colors.onSurfaceVariant,
            )
            AirportField(
                value = form.origin,
                onValueChange = { update(form.copy(origin = it)) },
                label = stringResource(R.string.search_from),
                lookups = lookups,
                leadingPainter = { Icon(painterResource(R.drawable.search_ic_flight), contentDescription = null) },
                errorText = if (state.issue == CompareIssue.ORIGIN_REQUIRED) stringResource(R.string.search_issue_origin) else null,
            )
            state.destinations.forEachIndexed { i, dest ->
                Row(verticalAlignment = Alignment.Top) {
                    AirportField(
                        value = dest,
                        onValueChange = { onEvent(CompareEvent.UpdateDestination(i, it)) },
                        label = stringResource(R.string.search_compare_destination, i + 1),
                        lookups = lookups,
                        leadingIcon = Icons.Filled.Place,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.canRemoveDestination) {
                        TpIconButton(onClick = {
                            onEvent(CompareEvent.RemoveDestination(i))
                        }, modifier = Modifier.padding(top = TravelTheme.spacing.sm), tid = "search.search_compare_remove") {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_compare_remove, i + 1))
                        }
                    }
                }
            }
            if (state.canAddDestination) {
                TpButton(stringResource(R.string.search_compare_add), {
                    onEvent(CompareEvent.AddDestination)
                }, variant = TpButtonVariant.Text, leadingIcon = Icons.Filled.Add, tid = "search.search_compare_add")
            }
            TripDatesField(
                departure = form.departureDate,
                returnDate = form.returnDate,
                today = state.today,
                onChange = { dep, ret -> update(form.copy(departureDate = dep, returnDate = ret)) },
                errorText =
                    issueMessage(
                        state.dateIssues,
                        SearchFormIssue.DEPARTURE_REQUIRED,
                        SearchFormIssue.DEPARTURE_IN_PAST,
                        SearchFormIssue.RETURN_BEFORE_DEPARTURE,
                    ),
            )
            NationalityField(
                value = form.nationality,
                onValueChange = { update(form.copy(nationality = it)) },
                lookups = lookups,
                errorText = if (state.issue == CompareIssue.NATIONALITY_REQUIRED) stringResource(R.string.search_issue_nationality) else null,
            )
            BudgetField(
                value = form.budgetUsd,
                onValueChange = { update(form.copy(budgetUsd = it)) },
                label = stringResource(R.string.search_budget),
                placeholder = stringResource(R.string.search_budget_placeholder),
            )
            TravellersField(
                counts = form.travellers,
                accessibilityNeeds = form.accessibilityNeeds,
                onCountsChange = { update(form.copy(travellers = it)) },
                onAccessibilityChange = { update(form.copy(accessibilityNeeds = it)) },
            )
            InterestChips(stringResource(R.string.search_interests), form.interests, { update(form.copy(interests = it)) })
            if (state.issue == CompareIssue.NEED_TWO_DESTINATIONS) {
                IssueText(stringResource(R.string.search_compare_need_two), Modifier.fillMaxWidth().testTag("compare_issue"))
            }
            TpButton(
                text = stringResource(if (state.loading) R.string.search_compare_busy else R.string.search_compare_submit),
                onClick = { onEvent(CompareEvent.Run) },
                enabled = !state.loading,
                loading = state.loading,
                leadingIcon = Icons.Filled.Search,
                modifier = Modifier.fillMaxWidth().testTag("compare_submit"),
                tid = "search.search_compare_busy",
            )
        }
    }
}

@StringRes
private fun CompareRow.labelRes(): Int =
    when (this) {
        CompareRow.ESTIMATE -> R.string.search_compare_row_estimate
        CompareRow.FLIGHT -> R.string.search_compare_row_flight
        CompareRow.HOTEL -> R.string.search_compare_row_hotel
        CompareRow.WEATHER -> R.string.search_compare_row_weather
        CompareRow.VISA -> R.string.search_compare_row_visa
        CompareRow.EASE -> R.string.search_compare_row_ease
        CompareRow.SAFETY -> R.string.search_compare_row_safety
        CompareRow.ENGLISH -> R.string.search_compare_row_english
        CompareRow.DAILY -> R.string.search_compare_row_daily
    }

/**
 * Web CompareTable. [wide] (expanded windows) = a real table, rows × destinations; otherwise one card
 * per destination with every row (web < md). "Best" badges from the ported `bestByRow`.
 */
@Composable
internal fun CompareTable(
    columns: List<CompareColumn>,
    best: Map<CompareRow, Set<Int>>,
    wide: Boolean,
    onPlan: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (wide) {
        WideTable(columns, best, onPlan, onRetry, modifier)
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            columns.forEachIndexed { i, col -> CompareCard(i, col, best, onPlan, onRetry) }
        }
    }
}

@Composable
private fun CompareCard(index: Int, col: CompareColumn, best: Map<CompareRow, Set<Int>>, onPlan: (Int) -> Unit, onRetry: (Int) -> Unit) {
    TpCard(modifier = Modifier.fillMaxWidth().testTag("compare_column_$index")) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            ColumnHeader(index, col, onRetry)
            CompareRow.entries.forEach { row ->
                HorizontalDivider(color = TravelTheme.colors.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
                    Text(
                        stringResource(row.labelRes()),
                        style = TravelTheme.typography.labelMedium,
                        color = TravelTheme.colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Cell(col, row, best[row]?.contains(index) == true, TextAlign.End) }
                }
            }
            if (col.status == CompareColumn.Status.DONE) PlanButton(index, col, onPlan)
        }
    }
}

@Composable
private fun WideTable(columns: List<CompareColumn>, best: Map<CompareRow, Set<Int>>, onPlan: (Int) -> Unit, onRetry: (Int) -> Unit, modifier: Modifier) {
    val tableLabel = stringResource(R.string.search_compare_table)
    TpCard(modifier = modifier.fillMaxWidth().semantics { contentDescription = tableLabel }, size = TpCardSize.Large) {
        Row {
            Spacer(Modifier.width(SearchDimens.CompareLabelWidth))
            columns.forEachIndexed { i, col -> Column(Modifier.weight(1f).padding(horizontal = TravelTheme.spacing.sm)) { ColumnHeader(i, col, onRetry) } }
        }
        CompareRow.entries.forEach { row ->
            HorizontalDivider(Modifier.padding(vertical = TravelTheme.spacing.sm), color = TravelTheme.colors.outlineVariant)
            Row {
                Text(
                    stringResource(row.labelRes()),
                    style = TravelTheme.typography.labelMedium,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.width(SearchDimens.CompareLabelWidth).semantics { heading() },
                )
                columns.forEachIndexed { i, col ->
                    Column(Modifier.weight(1f).padding(horizontal = TravelTheme.spacing.sm)) { Cell(col, row, best[row]?.contains(i) == true, TextAlign.Start) }
                }
            }
        }
        Row(Modifier.padding(top = TravelTheme.spacing.md)) {
            Spacer(Modifier.width(SearchDimens.CompareLabelWidth))
            columns.forEachIndexed { i, col ->
                Column(Modifier.weight(1f).padding(horizontal = TravelTheme.spacing.sm)) {
                    if (col.status ==
                        CompareColumn.Status.DONE
                    ) {
                        PlanButton(i, col, onPlan)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(index: Int, col: CompareColumn, onRetry: (Int) -> Unit) {
    val title = col.summary?.label ?: col.destination
    Column {
        Text(title, style = TravelTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        when (col.status) {
            CompareColumn.Status.ERROR -> {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(R.string.search_compare_couldnt_load), color = TravelTheme.colors.error, style = TravelTheme.typography.bodySmall)
                    col.error?.let { Text(it.text(), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
                    val retryLabel = stringResource(R.string.search_compare_retry_for, title)
                    TpButton(
                        stringResource(R.string.search_compare_retry),
                        { onRetry(index) },
                        variant = TpButtonVariant.Text,
                        modifier = Modifier.semantics { contentDescription = retryLabel }.testTag("compare_retry_$index"),
                        tid = "search.search_compare_retry",
                    )
                }
            }

            CompareColumn.Status.DONE -> {
                if (col.summary?.missing?.isNotEmpty() == true) {
                    Text(
                        stringResource(R.string.search_compare_missing, col.summary.missing.joinToString(", ")),
                        style = TravelTheme.typography.bodySmall,
                        color = TravelTheme.extendedColors.warning,
                    )
                }
            }

            CompareColumn.Status.LOADING -> {
                Unit
            }
        }
    }
}

@Composable
private fun CompareErrorReason.text(): String =
    when (this) {
        is CompareErrorReason.Detail -> text
        CompareErrorReason.CheckDetails -> stringResource(R.string.search_compare_check_details)
        CompareErrorReason.Unavailable -> stringResource(R.string.search_compare_unavailable)
    }

@Composable
private fun PlanButton(index: Int, col: CompareColumn, onPlan: (Int) -> Unit) {
    val label = stringResource(R.string.search_plan_trip_to, col.summary?.label ?: col.destination)
    TpButton(
        stringResource(R.string.search_plan_this_trip),
        { onPlan(index) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }.testTag("compare_plan_$index"),
        tid = "search.search_plan_this_trip",
    )
}

@Composable
private fun Cell(col: CompareColumn, row: CompareRow, isBest: Boolean, align: TextAlign) {
    when {
        col.status == CompareColumn.Status.LOADING -> {
            val loading = stringResource(R.string.search_compare_loading, col.destination)
            SkeletonBlock(Modifier.width(SearchDimens.Thumbnail).semantics { contentDescription = loading })
        }

        col.status == CompareColumn.Status.ERROR || col.summary == null -> {
            Text(DASH, color = TravelTheme.colors.outline)
        }

        else -> {
            CellContent(col.summary, row, isBest, align)
        }
    }
}

/** A value line with optional badges and a secondary line (web cell renderers). */
@Composable
private fun CellContent(s: CompareSummary, row: CompareRow, isBest: Boolean, align: TextAlign) {
    val value = rowValue(s, row)
    if (value == null) {
        Text(DASH, color = TravelTheme.colors.onSurfaceVariant)
        return
    }
    Column(horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
        BadgeLine(value.main, value.badges + listOfNotNull(Badge(stringResource(R.string.search_compare_best), Tone.BEST).takeIf { isBest }))
        value.sub?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant, textAlign = align) }
    }
}

private enum class Tone { NEUTRAL, WARN, GOOD, BAD, BEST }

private data class Badge(val text: String, val tone: Tone)

private data class CellValue(val main: String, val badges: List<Badge> = emptyList(), val sub: String? = null)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BadgeLine(main: String, badges: List<Badge>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), verticalArrangement = Arrangement.Center) {
        Text(main, style = TravelTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        badges.forEach { BadgePill(it) }
    }
}

@Composable
private fun BadgePill(badge: Badge) {
    val ext = TravelTheme.extendedColors
    val (container, content) =
        when (badge.tone) {
            Tone.NEUTRAL -> TravelTheme.colors.surfaceVariant to TravelTheme.colors.onSurfaceVariant
            Tone.WARN -> ext.warningContainer to ext.onWarningContainer
            Tone.GOOD, Tone.BEST -> ext.successContainer to ext.onSuccessContainer
            Tone.BAD -> TravelTheme.colors.errorContainer to TravelTheme.colors.onErrorContainer
        }
    Surface(shape = TravelShape.Pill, color = container, contentColor = content) {
        Text(
            badge.text,
            style = TravelTheme.typography.labelSmall,
            fontWeight = if (badge.tone == Tone.BEST) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = TravelTheme.spacing.xs, vertical = TravelTheme.spacing.xxs),
        )
    }
}

/** Web ROWS[].render: the value, its badges and the secondary line for [row], or null when unknown. */
@Composable
private fun rowValue(s: CompareSummary, row: CompareRow): CellValue? =
    when (row) {
        CompareRow.ESTIMATE -> estimateValue(s.estimate)
        CompareRow.FLIGHT -> flightValue(s.flight)
        CompareRow.HOTEL -> hotelValue(s.hotel)
        CompareRow.WEATHER -> weatherValue(s.weather)
        CompareRow.VISA -> visaValue(s.visa)
        CompareRow.EASE -> easeValue(s.ease)
        CompareRow.SAFETY -> scoreValue(s.ease?.safety)
        CompareRow.ENGLISH -> scoreValue(s.ease?.english)
        CompareRow.DAILY -> dailyValue(s.dailyCostUsd)
    }

@Composable
private fun scoreValue(score: Int?): CellValue? = score?.let { CellValue(stringResource(R.string.search_compare_out_of_20, it)) }

@Composable
private fun dailyValue(usd: Double?): CellValue? = usd?.let { CellValue(stringResource(R.string.search_compare_per_day, fmtUsd(it, currentLocale()))) }

@Composable
private fun estimateValue(e: CompareEstimate?): CellValue? {
    if (e == null) return null
    val total = e.totalUsd ?: return null
    return CellValue(
        fmtUsd(total, currentLocale()),
        listOfNotNull(
            Badge(stringResource(R.string.search_compare_estimate), Tone.NEUTRAL),
            Badge(stringResource(R.string.search_compare_under_budget), Tone.GOOD).takeIf { e.budgetStatus == "under" },
            Badge(stringResource(R.string.search_compare_over_budget), Tone.BAD).takeIf { e.budgetStatus == "over" },
        ),
        stringResource(R.string.search_compare_partial).takeIf { e.partial == true },
    )
}

@Composable
private fun flightValue(f: CompareFlight?): CellValue? {
    if (f == null) return null
    val stops =
        f.stops?.let { n ->
            if (n ==
                0
            ) {
                stringResource(R.string.search_compare_nonstop)
            } else {
                pluralStringResource(R.plurals.search_compare_stops, n, n)
            }
        }
    return CellValue(
        fmtUsd(f.priceUsd, currentLocale()),
        listOfNotNull(Badge(stringResource(R.string.search_compare_estimate), Tone.WARN).takeIf { f.source == "estimate" }),
        listOfNotNull(stops, fmtDuration(f.durationMinutes), f.airline).joinToString(" · ").ifEmpty { null },
    )
}

@Composable
private fun hotelValue(h: CompareHotel?): CellValue? {
    if (h == null) return null
    return CellValue(
        stringResource(R.string.search_compare_typical, fmtUsd(h.typicalNightlyUsd, currentLocale())),
        sub = h.cheapestGoodNightlyUsd?.let { stringResource(R.string.search_compare_from_reviewed, fmtUsd(it, currentLocale())) },
    )
}

@Composable
private fun weatherValue(w: CompareWeather?): CellValue? {
    if (w == null) return null
    val temp: (Double?) -> String = { v -> v?.let { "${it.roundToInt()}°" } ?: DASH }
    val unit = if (w.avgHighC != null || w.avgLowC != null) stringResource(R.string.search_compare_celsius_suffix) else ""
    return CellValue(
        stringResource(R.string.search_compare_temps, temp(w.avgHighC), temp(w.avgLowC)) + unit,
        listOfNotNull(Badge(stringResource(R.string.search_compare_climate_estimate), Tone.WARN).takeIf { w.source != "open-meteo" }),
        stringResource(R.string.search_compare_poor_days, w.poorDays?.toString() ?: DASH, w.totalDays?.toString() ?: DASH),
    )
}

@Composable
private fun visaValue(v: CompareVisa?): CellValue? {
    if (v == null) return null
    val fee =
        v.feeUsd?.let {
            if (it ==
                0.0
            ) {
                stringResource(R.string.search_compare_no_fee)
            } else {
                stringResource(R.string.search_compare_fee, fmtUsd(it, currentLocale()))
            }
        }
    val stay = v.maxStayDays?.let { pluralStringResource(R.plurals.search_compare_max_stay, it, it) }
    val badge =
        if (v.verified == true) {
            Badge(stringResource(R.string.search_compare_verified), Tone.GOOD)
        } else {
            Badge(stringResource(R.string.search_compare_ai_check), Tone.WARN)
        }
    return CellValue(v.visaType ?: DASH, listOf(badge), listOfNotNull(fee, stay).joinToString(" · ").ifEmpty { null })
}

@Composable
private fun easeValue(ease: CompareEase?): CellValue? {
    if (ease == null) return null
    val total = ease.total ?: return null
    val grade =
        when (ease.grade) {
            "green" -> Badge(stringResource(R.string.search_compare_easy), Tone.GOOD)
            "amber" -> Badge(stringResource(R.string.search_compare_moderate), Tone.WARN)
            "red" -> Badge(stringResource(R.string.search_compare_hard), Tone.BAD)
            else -> null
        }
    return CellValue(stringResource(R.string.search_compare_ease_value, total), listOfNotNull(grade))
}

/** Remembers [CompareUiState.best] per columns list (bestByRow is cheap but recomposition is frequent). */
@Composable
internal fun rememberBest(state: CompareUiState): Map<CompareRow, Set<Int>> = remember(state.columns) { state.best }
