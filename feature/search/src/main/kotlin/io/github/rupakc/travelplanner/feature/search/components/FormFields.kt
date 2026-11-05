package io.github.rupakc.travelplanner.feature.search.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.ACCESSIBILITY_IDS
import io.github.rupakc.travelplanner.core.domain.search.INTEREST_IDS
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.SerendipityStyle
import io.github.rupakc.travelplanner.core.domain.search.TravellerCounts
import io.github.rupakc.travelplanner.core.domain.search.TravellerKind
import io.github.rupakc.travelplanner.core.domain.search.parseIsoDate
import io.github.rupakc.travelplanner.core.domain.search.serendipityStyle
import io.github.rupakc.travelplanner.core.domain.search.toggle
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.feature.search.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Layout constants of the Search feature (defined once; everything else comes from the design system). */
internal object SearchDimens {
    /** Readable form width on tablets / foldables. */
    val FormMaxWidth = 720.dp

    /** Compare table width on expanded windows. */
    val TableMaxWidth = 1040.dp

    /** Compare row-label column on the expanded table. */
    val CompareLabelWidth = 176.dp

    /** Recent-search card width in the horizontal strip. */
    val RecentCardWidth = 256.dp

    /** Screenshot thumbnail. */
    val Thumbnail = 64.dp

    /** Route spine (dot + line) column. */
    val SpineWidth = 24.dp
    val SpineDot = 10.dp
    val SpineLine = 2.dp

    /** Import progress step badge. */
    val StepBadge = 28.dp

    /** Nights field next to a stop. */
    val NightsField = 96.dp

    /** Outline of the date boxes (matches the text fields). */
    val Hairline = 1.dp
}

/** Section label above a group of fields (heading semantics). */
@Composable
internal fun FieldGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TravelTheme.typography.titleSmall,
        color = TravelTheme.colors.onSurface,
        modifier = modifier.semantics { heading() },
    )
}

/** Error/warning text for a field or form, announced politely. */
@Composable
internal fun IssueText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TravelTheme.typography.bodySmall,
        color = TravelTheme.colors.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@StringRes
internal fun SearchFormIssue.messageRes(): Int =
    when (this) {
        SearchFormIssue.ORIGIN_REQUIRED -> R.string.search_issue_origin
        SearchFormIssue.DESTINATION_REQUIRED -> R.string.search_issue_destination
        SearchFormIssue.NATIONALITY_REQUIRED -> R.string.search_issue_nationality
        SearchFormIssue.STOP_CITY_REQUIRED -> R.string.search_issue_stop_city
        SearchFormIssue.STOP_NIGHTS_OUT_OF_RANGE -> R.string.search_issue_stop_nights
        SearchFormIssue.BUDGET_INVALID -> R.string.search_issue_budget
        SearchFormIssue.DEPARTURE_REQUIRED -> R.string.search_issue_departure_required
        SearchFormIssue.DEPARTURE_IN_PAST -> R.string.search_issue_departure_past
        SearchFormIssue.RETURN_BEFORE_DEPARTURE -> R.string.search_issue_return_before
    }

/** The message of [issue] when it's in [issues], else null. */
@Composable
internal fun issueMessage(issues: List<SearchFormIssue>, vararg issue: SearchFormIssue): String? =
    issue.firstOrNull { it in issues }?.let { stringResource(it.messageRes()) }

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Localised medium date ("12 Nov 2026"), or "" for a blank/invalid ISO date. */
internal fun formatDisplayDate(iso: String): String =
    parseIsoDate(iso)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
private class FromToday(today: LocalDate) : SelectableDates {
    private val min = today.utcMillis()

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= min

    override fun isSelectableYear(year: Int): Boolean = year >= Instant.ofEpochMilli(min).atZone(ZoneOffset.UTC).year
}

/**
 * Departure + return as two tappable boxes opening a Material 3 date range picker (min today,
 * return ≥ departure by construction; no end date = one-way).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripDatesField(
    departure: String,
    returnDate: String,
    today: LocalDate,
    onChange: (departure: String, returnDate: String) -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            DateBox(stringResource(R.string.search_departure), formatDisplayDate(departure), { open = true }, Modifier.weight(1f), errorText != null)
            DateBox(
                stringResource(R.string.search_return),
                formatDisplayDate(returnDate).ifEmpty { stringResource(R.string.search_one_way) },
                { open = true },
                Modifier.weight(1f),
                errorText != null,
            )
        }
        errorText?.let { IssueText(it) }
    }
    if (open) {
        val state =
            rememberDateRangePickerState(
                initialSelectedStartDateMillis = parseIsoDate(departure)?.takeIf { !it.isBefore(today) }?.utcMillis(),
                initialSelectedEndDateMillis = parseIsoDate(returnDate)?.utcMillis(),
                selectableDates = remember(today) { FromToday(today) },
            )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedStartDateMillis?.let { start ->
                            onChange(start.utcDate().toString(), state.selectedEndDateMillis?.utcDate()?.toString().orEmpty())
                        }
                        open = false
                    },
                    enabled = state.selectedStartDateMillis != null,
                ) { Text(stringResource(R.string.search_dates_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.search_dates_cancel)) } },
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.weight(1f),
                title = {
                    Text(
                        stringResource(R.string.search_dates_hint),
                        style = TravelTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = TravelTheme.spacing.xl, end = TravelTheme.spacing.md, top = TravelTheme.spacing.lg),
                    )
                },
            )
        }
    }
}

/** One date with a Material 3 date picker (min today) — the import review's "Leaving on". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SingleDateField(label: String, value: String, today: LocalDate, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    DateBox(label, formatDisplayDate(value), { open = true }, modifier, false)
    if (open) {
        val state =
            rememberDatePickerState(
                initialSelectedDateMillis = parseIsoDate(value)?.utcMillis(),
                selectableDates = remember(today) { FromToday(today) },
            )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(it.utcDate().toString()) }
                    open = false
                }) { Text(stringResource(R.string.search_dates_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.search_dates_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun DateBox(label: String, value: String, onClick: () -> Unit, modifier: Modifier, isError: Boolean) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget).semantics { contentDescription = "$label, $value" },
        shape = TravelShape.Input,
        border =
            BorderStroke(SearchDimens.Hairline, if (isError) TravelTheme.colors.error else TravelTheme.colors.outline),
    ) {
        Row(Modifier.padding(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DateRange, contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant)
            Spacer(Modifier.width(TravelTheme.spacing.sm))
            Column {
                Text(label, style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
                Text(value, style = TravelTheme.typography.bodyLarge)
            }
        }
    }
}

/** Numeric budget field; [label]/[placeholder] differ per mode (web copy). */
@Composable
internal fun BudgetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' }) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        prefix = { Text(stringResource(R.string.search_usd_prefix)) },
        singleLine = true,
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        shape = TravelShape.Input,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

@StringRes
private fun TravellerKind.labelRes(): Int =
    when (this) {
        TravellerKind.ADULTS -> R.string.search_adults
        TravellerKind.SENIORS -> R.string.search_seniors
        TravellerKind.CHILDREN -> R.string.search_children
        TravellerKind.INFANTS -> R.string.search_infants
    }

@StringRes
private fun TravellerKind.subRes(): Int =
    when (this) {
        TravellerKind.ADULTS -> R.string.search_adults_sub
        TravellerKind.SENIORS -> R.string.search_seniors_sub
        TravellerKind.CHILDREN -> R.string.search_children_sub
        TravellerKind.INFANTS -> R.string.search_infants_sub
    }

@StringRes
internal fun accessibilityLabelRes(id: String): Int =
    when (id) {
        "wheelchair" -> R.string.search_access_wheelchair
        "visual_impairment" -> R.string.search_access_visual
        "hearing_impairment" -> R.string.search_access_hearing
        else -> R.string.search_access_cognitive
    }

/** Accessibility emoji shown in the collapsed summary (web `ACCESS_EMOJI`). */
private fun accessibilityEmoji(id: String): String? =
    when (id) {
        "wheelchair" -> "♿"
        "visual_impairment" -> "👁️"
        "hearing_impairment" -> "🦻"
        "cognitive_disability" -> "🧠"
        else -> null
    }

/**
 * Web TravelerPanel as an inline expandable panel (no popover on a phone): steppers for the four
 * groups with the web's rules (never below 0, never nobody, ≤ 20 in total) and the four accessibility needs.
 */
@Composable
internal fun TravellersField(
    counts: TravellerCounts,
    accessibilityNeeds: List<String>,
    onCountsChange: (TravellerCounts) -> Unit,
    onAccessibilityChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val summary = pluralStringResource(R.plurals.search_traveler_count, counts.total, counts.total)
    val emojis = accessibilityNeeds.mapNotNull(::accessibilityEmoji).joinToString("")
    val expandedText = stringResource(if (open) R.string.search_expanded else R.string.search_collapsed)
    OutlinedCard(modifier = modifier.fillMaxWidth(), shape = TravelShape.Input) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget)
                .clickable(onClickLabel = stringResource(R.string.search_travelers_edit), role = Role.Button) { open = !open }
                .semantics { stateDescription = expandedText }
                .padding(TravelTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant)
            Spacer(Modifier.width(TravelTheme.spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.search_travelers), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
                Text(if (emojis.isEmpty()) summary else "$summary $emojis", style = TravelTheme.typography.bodyLarge)
            }
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(open) {
            Column(
                Modifier.padding(start = TravelTheme.spacing.md, end = TravelTheme.spacing.md, bottom = TravelTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
            ) {
                TravellerKind.entries.forEach { kind -> TravellerStepper(kind, counts, onCountsChange) }
                Text(
                    stringResource(R.string.search_accessibility_needs),
                    style = TravelTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = TravelTheme.spacing.sm).semantics { heading() },
                )
                ACCESSIBILITY_IDS.forEach { id ->
                    val checked = id in accessibilityNeeds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget)
                            .toggleable(checked, role = Role.Checkbox) { onAccessibilityChange(accessibilityNeeds.toggle(id)) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(TravelTheme.spacing.sm))
                        Text(stringResource(accessibilityLabelRes(id)), style = TravelTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TravellerStepper(kind: TravellerKind, counts: TravellerCounts, onCountsChange: (TravellerCounts) -> Unit) {
    val label = stringResource(kind.labelRes())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TravelTheme.typography.bodyLarge)
            Text(stringResource(kind.subRes()), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        IconButton(onClick = { onCountsChange(counts.change(kind, -1)) }, enabled = counts.canDecrement(kind)) {
            Icon(painterResource(R.drawable.search_ic_remove), contentDescription = stringResource(R.string.search_remove_one, label))
        }
        Text(
            counts[kind].toString(),
            style = TravelTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { contentDescription = "$label ${counts[kind]}" },
        )
        IconButton(onClick = { onCountsChange(counts.change(kind, 1)) }, enabled = counts.canIncrement(kind)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.search_add_one, label))
        }
    }
}

/** Web TagInput: type and press Enter (or a comma) to add; chips with a remove button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagInputField(
    label: String,
    values: List<String>,
    placeholder: String,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }

    fun add(raw: String) {
        val tag = raw.trim().trimEnd(',').trim()
        if (tag.isNotEmpty() && tag !in values) onChange(values + tag)
        input = ""
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        OutlinedTextField(
            value = input,
            onValueChange = { text -> if (text.endsWith(",")) add(text) else input = text },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            supportingText = { Text(stringResource(R.string.search_tag_hint)) },
            singleLine = true,
            shape = TravelShape.Input,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(input) }),
        )
        if (values.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                values.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onChange(values - tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.search_tag_remove, tag),
                                modifier = Modifier.padding(start = TravelTheme.spacing.xxs),
                            )
                        },
                        shape = TravelShape.Pill,
                        colors = InputChipDefaults.inputChipColors(),
                    )
                }
            }
        }
    }
}

@StringRes
internal fun interestLabelRes(id: String): Int =
    when (id) {
        "food" -> R.string.search_interest_food
        "history" -> R.string.search_interest_history
        "adventure" -> R.string.search_interest_adventure
        "culture" -> R.string.search_interest_culture
        "nature" -> R.string.search_interest_nature
        "shopping" -> R.string.search_interest_shopping
        "nightlife" -> R.string.search_interest_nightlife
        "wellness" -> R.string.search_interest_wellness
        "art" -> R.string.search_interest_art
        else -> R.string.search_interest_family
    }

/** The web's 10 interest chips (multi-select, aria-pressed parity via selected state). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterestChips(label: String, selected: List<String>, onChange: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        FieldGroupLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            INTEREST_IDS.forEach { id ->
                TpChip(label = stringResource(interestLabelRes(id)), selected = id in selected, onClick = { onChange(selected.toggle(id)) })
            }
        }
    }
}

/** Web "🎯 Discovery style" dial: 0–1 in 0.05 steps with the live bucket label. */
@Composable
internal fun SerendipityDial(value: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    val style =
        stringResource(
            when (serendipityStyle(value)) {
                SerendipityStyle.CLASSICS -> R.string.search_style_classics
                SerendipityStyle.BALANCED -> R.string.search_style_balanced
                SerendipityStyle.HIDDEN_GEMS -> R.string.search_style_gems
            },
        )
    val dialLabel = stringResource(R.string.search_serendipity_label)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldGroupLabel(stringResource(R.string.search_discovery_style), Modifier.weight(1f))
            Text(style, style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(Math.round(it * STEPS_PER_UNIT) / STEPS_PER_UNIT) },
            steps = SLIDER_STEPS,
            modifier =
                Modifier.semantics {
                    contentDescription = dialLabel
                    stateDescription = style
                },
        )
        Row {
            Text(stringResource(R.string.search_style_start), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.search_style_end), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

/** 0.05 steps → 19 intermediate stops. */
private const val SLIDER_STEPS = 19
private const val STEPS_PER_UNIT = 20.0

/** Trip pace (`pace` request field: relaxed / balanced / packed), as a radio group. */
@Composable
internal fun PaceSelector(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val options =
        listOf(
            TravelSearchRequest.PACE_RELAXED to R.string.search_pace_relaxed,
            TravelSearchRequest.PACE_BALANCED to R.string.search_pace_balanced,
            TravelSearchRequest.PACE_PACKED to R.string.search_pace_packed,
        )
    Column(modifier) {
        FieldGroupLabel(stringResource(R.string.search_pace))
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            options.forEach { (id, res) ->
                Row(
                    Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget)
                        .selectable(selected = value == id, role = Role.RadioButton) { onChange(id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = value == id, onClick = null)
                    Text(stringResource(res), style = TravelTheme.typography.bodyMedium)
                }
            }
        }
    }
}
