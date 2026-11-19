package io.github.rupakc.travelplanner.core.ui.carbon

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import io.github.rupakc.travelplanner.core.ui.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Numbers of the card in the app language (web `fmtNumber`). Pure. */
object CarbonFormat {
    private const val SMALL = 10

    /** Whole kilograms: "1,234". */
    fun kg0(value: Double, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value.roundToLong())

    /** One decimal under 10 kg ("3.4"), whole above (web `kg1`). */
    fun kg1(value: Double, locale: Locale): String = number(value, locale, if (value < SMALL) 1 else 0)

    /** A number with at most [maxFraction] decimals ("1,477", "2"). */
    fun number(value: Double, locale: Locale, maxFraction: Int = 1): String =
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = maxFraction }.format(value)

    /** "2026-11-19" → "19 Nov 2026" in [locale]; the input when it is not a date. */
    fun date(isoDay: String, locale: Locale): String =
        runCatching { LocalDate.parse(isoDay).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrDefault(isoDay)
}

@Composable
@ReadOnlyComposable
internal fun currentCarbonLocale(): Locale = LocalConfiguration.current.locales[0]

/** Line kinds the app translates, with their parameters in the string's argument order. */
private val LINES: Map<String, Pair<Int, List<String>>> =
    mapOf(
        "flight_tim" to (R.string.carbon_line_flight_tim to listOf("route")),
        "flight_factor" to (R.string.carbon_line_flight_factor to listOf("route", "km")),
        "hotel_nights" to (R.string.carbon_line_hotel_nights to listOf("city", "nights", "rooms")),
        "local_measured" to (R.string.carbon_line_local_measured to listOf("km")),
        "local_estimate" to (R.string.carbon_line_local_estimate to listOf("km")),
        "intercity" to (R.string.carbon_line_intercity to listOf("route", "mode", "km")),
        "meals" to (R.string.carbon_line_meals to listOf("diet", "days", "travellers")),
        "activity_estimate" to (R.string.carbon_line_activity_estimate to listOf("name", "km", "mode")),
    )

/** Assumption codes the app translates. */
private val ASSUMPTIONS: Map<String, Pair<Int, List<String>>> =
    mapOf(
        "travellers_default" to (R.string.carbon_assume_travellers_default to emptyList()),
        "nights_default" to (R.string.carbon_assume_nights_default to listOf("nights")),
        "flights_cheapest" to (R.string.carbon_assume_flights_cheapest to emptyList()),
        "flights_cabin" to (R.string.carbon_assume_flights_cabin to listOf("cabin")),
        "flights_none" to (R.string.carbon_assume_flights_none to emptyList()),
        "flight_desnz" to (R.string.carbon_assume_flight_desnz to emptyList()),
        "flight_unknown_airport" to (R.string.carbon_assume_flight_unknown_airport to listOf("route")),
        "rooms_default" to (R.string.carbon_assume_rooms_default to listOf("rooms")),
        "hotel_default" to (R.string.carbon_assume_hotel_default to listOf("city", "kg")),
        "stays_none" to (R.string.carbon_assume_stays_none to emptyList()),
        "ground_default_mix" to (R.string.carbon_assume_ground_default_mix to emptyList()),
        "ground_straight_line" to (R.string.carbon_assume_ground_straight_line to emptyList()),
        "ground_per_day" to (R.string.carbon_assume_ground_per_day to listOf("km_per_day")),
        "intercity_rail" to (R.string.carbon_assume_intercity_rail to emptyList()),
        "intercity_unknown" to (R.string.carbon_assume_intercity_unknown to listOf("route")),
        "food_basket" to (R.string.carbon_assume_food_basket to listOf("diet", "kg_per_day")),
        "activities_partial" to (R.string.carbon_assume_activities_partial to emptyList()),
        "activities_unmodelled" to (R.string.carbon_assume_activities_unmodelled to listOf("count")),
        "hotel_grid" to (R.string.carbon_assume_hotel_grid to listOf("city", "g_per_kwh", "kwh")),
        "factors_newer" to (R.string.carbon_assume_factors_newer to listOf("year")),
    )

private val MODES: Map<String, Int> =
    mapOf(
        "rail_national" to R.string.carbon_mode_rail_national,
        "rail_international" to R.string.carbon_mode_rail_international,
        "light_rail_tram" to R.string.carbon_mode_light_rail_tram,
        "metro" to R.string.carbon_mode_metro,
        "bus_local" to R.string.carbon_mode_bus_local,
        "coach" to R.string.carbon_mode_coach,
        "taxi_vehicle" to R.string.carbon_mode_taxi_vehicle,
        "car_average" to R.string.carbon_mode_car_average,
        "ferry_foot" to R.string.carbon_mode_ferry_foot,
    )

private val CABINS: Map<String, Int> =
    mapOf(
        "economy" to R.string.carbon_cabin_economy,
        "premium_economy" to R.string.carbon_cabin_premium_economy,
        "business" to R.string.carbon_cabin_business,
        "first" to R.string.carbon_cabin_first,
    )

/** A line in the app language; the backend's English [fallback] for an unknown kind or a missing parameter. */
@Composable
internal fun lineText(kind: String?, params: JsonObject?, fallback: String?): String = translated(LINES[kind], params, fallback)

/** An assumption in the app language; the backend's English [fallback] for an unknown code or a missing parameter. */
@Composable
internal fun assumptionText(code: String?, params: JsonObject?, fallback: String?): String = translated(ASSUMPTIONS[code], params, fallback)

@Composable
private fun translated(spec: Pair<Int, List<String>>?, params: JsonObject?, fallback: String?): String {
    spec ?: return fallback.orEmpty()
    val (res, keys) = spec
    // No arguments: the copy may hold a literal "%" (formatted="false"), so it must not go through String.format.
    if (keys.isEmpty()) return stringResource(res)
    val values = keys.mapNotNull { key -> (params?.get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull } }
    if (values.size != keys.size) return fallback.orEmpty()
    val args = keys.zip(values).map { (key, value) -> paramText(key, value) }
    // Every translated string takes one to three arguments (no spread: detekt SpreadOperator).
    return when (args.size) {
        1 -> stringResource(res, args[0])
        2 -> stringResource(res, args[0], args[1])
        else -> stringResource(res, args[0], args[1], args.getOrElse(2) { "" })
    }
}

@Composable
private fun paramText(key: String, value: JsonPrimitive): String {
    val text = value.content
    val diet = CarbonDiet.entries.firstOrNull { it.wire == text }
    val number = if (value.isString) null else value.doubleOrNull
    return when {
        key == "mode" -> MODES[text]?.let { stringResource(it) } ?: text
        key == "diet" && diet != null -> dietLabel(diet)
        key == "cabin" -> CABINS[text]?.let { stringResource(it) } ?: text
        key == "year" || key == "diet" || number == null -> text
        else -> CarbonFormat.number(number, currentCarbonLocale())
    }
}

/** "Mixed diet", "Vegan", … */
@Composable
internal fun dietLabel(diet: CarbonDiet): String =
    stringResource(
        when (diet) {
            CarbonDiet.MIXED -> R.string.carbon_diet_mixed
            CarbonDiet.MEAT_HEAVY -> R.string.carbon_diet_meat_heavy
            CarbonDiet.PESCATARIAN -> R.string.carbon_diet_pescatarian
            CarbonDiet.VEGETARIAN -> R.string.carbon_diet_vegetarian
            CarbonDiet.VEGAN -> R.string.carbon_diet_vegan
        },
    )

/** "Flights", "Stays", "Getting around", "Food", "Activities"; the key itself if unknown. */
@Composable
internal fun categoryLabel(key: String?): String {
    @StringRes val res =
        when (key) {
            "flights" -> R.string.carbon_cat_flights
            "stays" -> R.string.carbon_cat_stays
            "ground" -> R.string.carbon_cat_ground
            "food" -> R.string.carbon_cat_food
            "activities" -> R.string.carbon_cat_activities
            else -> return key.orEmpty()
        }
    return stringResource(res)
}

/** "Google TIM", "Factor", "Estimate"; null for an unknown method (no badge). */
@Composable
internal fun methodLabel(method: String?): String? =
    when (method) {
        "measured_tim" -> stringResource(R.string.carbon_method_measured_tim)
        "factor" -> stringResource(R.string.carbon_method_factor)
        "estimate" -> stringResource(R.string.carbon_method_estimate)
        else -> null
    }
