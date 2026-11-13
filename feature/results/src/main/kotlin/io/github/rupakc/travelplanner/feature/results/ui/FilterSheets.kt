package io.github.rupakc.travelplanner.feature.results.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.TpRangeSlider
import io.github.rupakc.travelplanner.core.designsystem.component.TpSlider
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.component.tpToggleable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.search.ActivityFilters
import io.github.rupakc.travelplanner.core.model.search.FlightFilters
import io.github.rupakc.travelplanner.core.model.search.HotelFilters
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.sections.commerce.ChipRow
import io.github.rupakc.travelplanner.feature.results.sections.commerce.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val HOURS = 24f
private const val FLIGHT_PRICE_FLOOR = 100f
private const val FLIGHT_PRICE_DEFAULT = 5000f
private const val HOTEL_PRICE_FLOOR = 20f
private const val HOTEL_PRICE_DEFAULT = 1000f
private const val ACTIVITY_PRICE_DEFAULT = 500f
private const val PRICE_HEADROOM = 1.5
private const val DISTANCE_MIN = 0.5f
private const val DISTANCE_MAX = 20f
private const val BEDS_PLUS = 4

/** The web's 10 interest ids for the activity filter. */
val ACTIVITY_INTERESTS = listOf("food", "history", "adventure", "culture", "nature", "shopping", "nightlife", "wellness", "art", "family")

/** Which filter sheet is open. */
enum class FilterSheet { Flights, Hotels, Activities }

/** Web `priceCeil`: 1.5 × the highest price, rounded up to [step]; [fallback] without prices. */
internal fun priceCeil(prices: List<Double>, step: Double, fallback: Float): Float =
    prices.filter { it > 0 }.maxOrNull()?.let { (ceil(it * PRICE_HEADROOM / step) * step).toFloat() } ?: fallback

private fun hour(h: Float): String = "%02d:00".format(h.roundToInt())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetScaffold(
    title: String,
    activeCount: Int,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onApply: () -> Unit,
    applyEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TravelTheme.spacing.gutter)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
        ) {
            Text(title, style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
            Row(Modifier.fillMaxWidth().padding(bottom = TravelTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                TpButton(
                    stringResource(R.string.results_filter_clear),
                    onClick = onClear,
                    variant = TpButtonVariant.Text,
                    enabled = activeCount > 0,
                    tid = "results.results_filter_clear",
                )
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TpButton(
                    text =
                        if (activeCount >
                            0
                        ) {
                            stringResource(R.string.results_filter_apply_count, activeCount)
                        } else {
                            stringResource(R.string.results_filter_apply)
                        },
                    onClick = onApply,
                    enabled = applyEnabled,
                    modifier = Modifier.testTag("apply_filters"),
                    tid = "results.results_filter_apply_count",
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = TravelTheme.typography.titleSmall)

/** Web flight filter modal: stops, max price per person, departure/arrival time ranges. */
@Composable
internal fun FlightFilterSheet(current: FlightFilters?, data: FlightsData?, onApply: (FlightFilters) -> Unit, onDismiss: () -> Unit) {
    val ceilPrice = priceCeil(data?.results?.mapNotNull { it.priceUsd }.orEmpty(), 100.0, FLIGHT_PRICE_DEFAULT)
    var stops by rememberSaveable { mutableStateOf(current?.maxStops) }
    var maxPrice by rememberSaveable { mutableStateOf(current?.maxPriceUsd?.toFloat()) }
    var dep by rememberSaveable { mutableStateOf(parseRange(current?.departureTimeEarliest, current?.departureTimeLatest)) }
    var arr by rememberSaveable { mutableStateOf(parseRange(current?.arrivalTimeEarliest, current?.arrivalTimeLatest)) }
    val active = listOf(stops != null, maxPrice != null, dep != FULL_DAY, arr != FULL_DAY).count { it }
    SheetScaffold(
        title = stringResource(R.string.results_filter_flights_title),
        activeCount = active,
        onDismiss = onDismiss,
        onClear = { onApply(FlightFilters()) },
        onApply = {
            onApply(
                FlightFilters(
                    maxStops = stops,
                    maxPriceUsd = maxPrice?.toDouble(),
                    departureTimeEarliest = dep.first.takeIf { it > 0 }?.let(::hour),
                    departureTimeLatest = dep.second.takeIf { it < HOURS }?.let(::hour),
                    arrivalTimeEarliest = arr.first.takeIf { it > 0 }?.let(::hour),
                    arrivalTimeLatest = arr.second.takeIf { it < HOURS }?.let(::hour),
                ),
            )
        },
    ) {
        Label(stringResource(R.string.results_filter_stops))
        ChipRow {
            listOf(
                null to R.string.results_filter_any,
                0 to R.string.results_filter_nonstop,
                1 to R.string.results_filter_one_stop,
                2 to R.string.results_filter_two_stops,
            )
                .forEach { (value, label) -> TpChip(stringResource(label), selected = stops == value, onClick = { stops = value }) }
        }
        PriceSlider(stringResource(R.string.results_filter_max_price_pp), maxPrice, FLIGHT_PRICE_FLOOR, ceilPrice) { maxPrice = it }
        TimeRange(stringResource(R.string.results_filter_departure), dep) { dep = it }
        TimeRange(stringResource(R.string.results_filter_arrival), arr) { arr = it }
    }
}

private val FULL_DAY = 0f to HOURS

private fun parseRange(from: String?, to: String?): Pair<Float, Float> =
    (from?.take(2)?.toFloatOrNull() ?: 0f) to (to?.take(2)?.toFloatOrNull() ?: HOURS)

@Composable
private fun TimeRange(label: String, value: Pair<Float, Float>, onChange: (Pair<Float, Float>) -> Unit) {
    val text = stringResource(R.string.results_filter_time_range, hour(value.first), hour(value.second))
    Column {
        Row {
            Label(label)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(text, style = TravelTheme.typography.labelLarge)
        }
        TpRangeSlider(
            value = value.first..value.second,
            onValueChange = { r -> if (r.endInclusive - r.start >= 1f) onChange(r.start.roundToInt().toFloat() to r.endInclusive.roundToInt().toFloat()) },
            valueRange = 0f..HOURS,
            steps = HOURS.toInt() - 1,
            modifier = Modifier.semantics { stateDescription = "$label $text" },
            tid = "results.filter_sheets_range_slider",
        )
    }
}

@Composable
private fun PriceSlider(label: String, value: Float?, floor: Float, ceil: Float, onChange: (Float) -> Unit) {
    val shown = value ?: ceil
    val text = localPrice(shown.toDouble())
    Column {
        Row {
            Label(label)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(text, style = TravelTheme.typography.labelLarge)
        }
        TpSlider(
            value = shown.coerceIn(floor, ceil.coerceAtLeast(floor + 1)),
            onValueChange = { onChange(it.roundToInt().toFloat()) },
            valueRange = floor..ceil.coerceAtLeast(floor + 1),
            modifier = Modifier.semantics { stateDescription = "$label $text" },
            tid = "results.filter_sheets_slider",
        )
    }
}

/** Web hotel filter modal: beds, max price per night, Wi-Fi quality, distance from centre, private washroom. */
@Composable
internal fun HotelFilterSheet(current: HotelFilters?, data: HotelsData?, onApply: (HotelFilters) -> Unit, onDismiss: () -> Unit) {
    val ceilPrice = priceCeil(data?.results?.mapNotNull { it.pricePerNightUsd }.orEmpty(), 50.0, HOTEL_PRICE_DEFAULT)
    var beds by rememberSaveable { mutableStateOf(current?.numBeds) }
    var maxPrice by rememberSaveable { mutableStateOf(current?.maxPricePerNightUsd?.toFloat()) }
    var wifi by rememberSaveable { mutableStateOf(current?.wifiQuality) }
    var distance by rememberSaveable { mutableStateOf(current?.maxDistanceFromCenterKm?.toFloat()) }
    var washroom by rememberSaveable { mutableStateOf(current?.privateWashroom == true) }
    val active = listOf(beds != null, maxPrice != null, wifi != null, distance != null, washroom).count { it }
    SheetScaffold(
        title = stringResource(R.string.results_filter_hotels_title),
        activeCount = active,
        onDismiss = onDismiss,
        onClear = { onApply(HotelFilters()) },
        onApply = {
            onApply(HotelFilters(beds, maxPrice?.toDouble(), wifi, distance?.toDouble(), washroom.takeIf { it }))
        },
    ) {
        Label(stringResource(R.string.results_filter_beds))
        ChipRow {
            TpChip(stringResource(R.string.results_filter_any), selected = beds == null, onClick = { beds = null })
            for (n in 1 until BEDS_PLUS) {
                TpChip(pluralStringResource(R.plurals.results_filter_beds_n, n, n), selected = beds == n, onClick = { beds = n })
            }
            TpChip(stringResource(R.string.results_filter_beds_plus, BEDS_PLUS), selected = beds == BEDS_PLUS, onClick = { beds = BEDS_PLUS })
        }
        PriceSlider(stringResource(R.string.results_filter_max_night), maxPrice, HOTEL_PRICE_FLOOR, ceilPrice) { maxPrice = it }
        Label(stringResource(R.string.results_filter_wifi))
        ChipRow {
            listOf(
                null to R.string.results_filter_any,
                "basic" to R.string.results_filter_wifi_basic,
                "good" to R.string.results_filter_wifi_good,
                "excellent" to R.string.results_filter_wifi_excellent,
            )
                .forEach { (value, label) -> TpChip(stringResource(label), selected = wifi == value, onClick = { wifi = value }) }
        }
        val km = distance ?: DISTANCE_MAX
        val kmText = stringResource(R.string.results_filter_km, "%.1f".format(km))
        Column {
            Row {
                Label(stringResource(R.string.results_filter_distance))
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(kmText, style = TravelTheme.typography.labelLarge)
            }
            TpSlider(
                value = km,
                onValueChange = { distance = (it * 2).roundToInt() / 2f },
                valueRange = DISTANCE_MIN..DISTANCE_MAX,
                modifier = Modifier.semantics { stateDescription = kmText },
                tid = "results.filter_sheets_slider_2",
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(
                min = TravelTheme.spacing.minTouchTarget,
            ).tpToggleable(tid = "results.filter_sheets_toggleable", washroom, role = Role.Switch, onValueChange = {
                washroom = it
            }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(stringResource(R.string.results_filter_washroom))
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (washroom) {
                Text(
                    stringResource(R.string.results_filter_required),
                    style = TravelTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = TravelTheme.spacing.sm),
                )
            }
            Switch(checked = washroom, onCheckedChange = null)
        }
    }
}

/** Web activity filter modal: interests, max cost per person, availability dates, minimum rating. */
@Composable
internal fun ActivityFilterSheet(
    current: ActivityFilters?,
    data: ActivitiesData?,
    request: TravelSearchRequest?,
    onApply: (ActivityFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val ceilPrice = priceCeil(data?.results?.mapNotNull { it.priceUsd }.orEmpty(), 10.0, ACTIVITY_PRICE_DEFAULT)
    var interests by rememberSaveable { mutableStateOf(current?.filterInterests.orEmpty()) }
    var maxPrice by rememberSaveable { mutableStateOf(current?.maxPriceUsd?.toFloat()) }
    var from by rememberSaveable { mutableStateOf(current?.availableFrom ?: request?.departureDate.orEmpty()) }
    var to by rememberSaveable { mutableStateOf(current?.availableTo ?: request?.returnDate.orEmpty()) }
    var rating by rememberSaveable { mutableStateOf(current?.minRating) }
    val active = listOf(interests.isNotEmpty(), maxPrice != null, from.isNotBlank() || to.isNotBlank(), rating != null).count { it }
    val today = remember { LocalDate.now() }
    val datesValid = availabilityValid(from, to)
    SheetScaffold(
        title = stringResource(R.string.results_filter_activities_title),
        activeCount = active,
        onDismiss = onDismiss,
        onClear = { onApply(ActivityFilters()) },
        applyEnabled = datesValid,
        onApply = {
            onApply(
                ActivityFilters(
                    interests.takeIf {
                        it.isNotEmpty()
                    },
                    maxPrice?.toDouble(),
                    from.takeIf(String::isNotBlank),
                    to.takeIf(String::isNotBlank),
                    rating,
                ),
            )
        },
    ) {
        Label(stringResource(R.string.results_filter_interests))
        ChipRow {
            ACTIVITY_INTERESTS.forEach { id ->
                TpChip(interestLabel(id), selected = id in interests, onClick = { interests = if (id in interests) interests - id else interests + id })
            }
        }
        PriceSlider(stringResource(R.string.results_filter_max_cost_pp), maxPrice, 0f, ceilPrice) { maxPrice = it }
        Label(stringResource(R.string.results_filter_dates))
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            val fromDate = parseIso(from)
            DateField(stringResource(R.string.results_filter_from), from, today, parseIso(to), Modifier.weight(1f)) { from = it }
            DateField(stringResource(R.string.results_filter_to), to, maxOf(today, fromDate ?: today), null, Modifier.weight(1f)) { to = it }
        }
        if (!datesValid) {
            Text(stringResource(R.string.results_filter_dates_invalid), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.error)
        }
        Label(stringResource(R.string.results_filter_rating))
        ChipRow {
            listOf(null, 3.0, 3.5, 4.0, 4.5).forEach { r ->
                TpChip(
                    r?.let { stringResource(R.string.results_filter_rating_plus, if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()) }
                        ?: stringResource(R.string.results_filter_any),
                    selected = rating == r,
                    onClick = { rating = r },
                )
            }
        }
    }
}

internal fun parseIso(value: String): LocalDate? = value.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Web parity: both dates optional, each a real date, and "to" never before "from". */
internal fun availabilityValid(from: String, to: String): Boolean {
    val f = parseIso(from)
    val t = parseIso(to)
    if (from.isNotBlank() && f == null) return false
    if (to.isNotBlank() && t == null) return false
    return f == null || t == null || !t.isBefore(f)
}

/** Read-only date field; the Material 3 date picker (locale-aware, accessible) keeps dates within [min]..[max]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, min: LocalDate, max: LocalDate?, modifier: Modifier, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val pick = stringResource(R.string.results_filter_pick_date, label)
    TpOutlinedTextField(
        value = formatDate(value.takeIf(String::isNotBlank), currentLocale()).orEmpty(),
        onValueChange = {},
        readOnly = true,
        modifier = modifier,
        singleLine = true,
        shape = TravelShape.Input,
        label = { Text(label) },
        trailingIcon = {
            TpIconButton(onClick = { open = true }, tid = "results.filter_sheets_icon_button") { Icon(Icons.Filled.DateRange, contentDescription = pick) }
        },
        tid = "results.date_range_outlined_text_field",
    )
    if (!open) return
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = parseIso(value)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        return !d.isBefore(min) && (max == null || !d.isAfter(max))
                    }

                    override fun isSelectableYear(year: Int): Boolean = year >= min.year
                },
        )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TpTextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                },
                tid = "results.filter_sheets_text_button",
            ) { Text(stringResource(R.string.results_ok)) }
        },
        dismissButton = { TpTextButton(onClick = { open = false }, tid = "results.results_cancel") { Text(stringResource(R.string.results_cancel)) } },
    ) { DatePicker(state) }
}

@Composable
internal fun interestLabel(id: String): String =
    stringResource(
        when (id) {
            "food" -> R.string.results_interest_food
            "history" -> R.string.results_interest_history
            "adventure" -> R.string.results_interest_adventure
            "culture" -> R.string.results_interest_culture
            "nature" -> R.string.results_interest_nature
            "shopping" -> R.string.results_interest_shopping
            "nightlife" -> R.string.results_interest_nightlife
            "wellness" -> R.string.results_interest_wellness
            "art" -> R.string.results_interest_art
            else -> R.string.results_interest_family
        },
    )
