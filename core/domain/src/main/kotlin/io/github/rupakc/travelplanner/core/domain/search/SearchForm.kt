package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.domain.format.parseLocalizedAmount
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.search.ImportSource
import io.github.rupakc.travelplanner.core.model.search.MustIncludePlace
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.LocalDate
import java.util.Locale

/** The web's 10 interest ids, in the order the Search form shows them (SearchPage `INTERESTS`). */
val INTEREST_IDS: List<String> =
    listOf("food", "history", "adventure", "culture", "nature", "shopping", "nightlife", "wellness", "art", "family")

/** The web's 4 accessibility need ids (TravelerPanel `ACCESSIBILITY_OPTIONS`). */
val ACCESSIBILITY_IDS: List<String> = listOf("wheelchair", "visual_impairment", "hearing_impairment", "cognitive_disability")

/** At most this many stops on the way (web "+ Add stop", backend `stops` max 3). */
const val MAX_EXTRA_STOPS = 3

/** Per-stop nights bounds (web number input 1–30, backend `nights` 1..30). */
val STOP_NIGHTS_RANGE: IntRange = 1..30

/** Web form defaults: departure a week out, return two weeks out. */
const val DEFAULT_DEPARTURE_DAYS = 7L
const val DEFAULT_RETURN_DAYS = 14L

/** Serendipity dial thresholds and default (web: <0.33 classics, >0.66 hidden gems). */
const val SERENDIPITY_DEFAULT = 0.5
const val SERENDIPITY_CLASSICS_BELOW = 0.33
const val SERENDIPITY_GEMS_ABOVE = 0.66

/**
 * The known-destination Search form, field for field as the web keeps it (dates are ISO
 * `YYYY-MM-DD`; a blank [returnDate] is a one-way trip; [budgetUsd] is the raw text typed).
 * [origin]/[destination] hold what the API gets: an IATA code after a typeahead pick, else the text typed.
 */
data class SearchForm(
    val origin: String = "",
    val destination: String = "",
    val departureDate: String = "",
    val returnDate: String = "",
    val nationality: String = "",
    val residencePermits: List<String> = emptyList(),
    val existingVisas: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val budgetUsd: String = "",
    val travellers: TravellerCounts = TravellerCounts(),
    val accessibilityNeeds: List<String> = emptyList(),
    val serendipity: Double = SERENDIPITY_DEFAULT,
    val pace: String = TravelSearchRequest.PACE_BALANCED,
    val mustInclude: List<MustIncludePlace>? = null,
    val importSource: ImportSource? = null,
) {
    companion object {
        /** The web's initial form: departure today+7, return today+14, everything else empty. */
        fun initial(today: LocalDate): SearchForm =
            SearchForm(
                departureDate = today.plusDays(DEFAULT_DEPARTURE_DAYS).toString(),
                returnDate = today.plusDays(DEFAULT_RETURN_DAYS).toString(),
            )
    }
}

/** A stop on the way to the final destination; [nights] is the raw text (blank = split automatically). */
data class ExtraStop(val city: String = "", val nights: String = "")

/** Serendipity dial label bucket (web: "Famous classics" / "Balanced mix" / "Hidden gems"). */
enum class SerendipityStyle { CLASSICS, BALANCED, HIDDEN_GEMS }

fun serendipityStyle(value: Double): SerendipityStyle =
    when {
        value < SERENDIPITY_CLASSICS_BELOW -> SerendipityStyle.CLASSICS
        value > SERENDIPITY_GEMS_ABOVE -> SerendipityStyle.HIDDEN_GEMS
        else -> SerendipityStyle.BALANCED
    }

/** Web `BUDGET_TO_USD`: preference budget category → the USD the form starts with. */
fun budgetCategoryToUsd(category: String?): String =
    when (category) {
        "low" -> "1000"
        "medium" -> "3000"
        "high" -> "8000"
        else -> ""
    }

/** Web `USD_TO_BUDGET`: blank/unparseable → medium; ≤ 1500 low; ≤ 5000 medium; else high. [usd] is read in [locale]. */
fun usdToBudgetCategory(usd: String?, locale: Locale): String {
    val n = parseLocalizedAmount(usd, locale)
    return when {
        n == null -> "medium"
        n <= LOW_BUDGET_MAX -> "low"
        n <= MEDIUM_BUDGET_MAX -> "medium"
        else -> "high"
    }
}

private const val LOW_BUDGET_MAX = 1500.0
private const val MEDIUM_BUDGET_MAX = 5000.0

/** Web `budget_usd ? parseFloat(budget_usd) : null` (negative or junk → null), read in [locale] ("1.500" is 1500 in Spanish). */
fun parseBudget(text: String?, locale: Locale): Double? = parseLocalizedAmount(text, locale)?.takeIf { it > 0.0 }

/** A budget number back as form text, like JS `String(n)`: 3000.0 → "3000", 2500.5 → "2500.5". */
fun budgetText(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

/** Web `parseNights`: a positive whole number, else null (split automatically). */
fun parseNights(text: String?): Int? = text?.trim()?.toIntOrNull()?.takeIf { it > 0 }

/**
 * Web `SearchPage.startSearch`: the form + stops on the way → the TravelSearchRequest.
 * Journey order: stops first, the To city last; `destination_nights` aligned, null last.
 */
fun buildSearchRequest(form: SearchForm, stops: List<ExtraStop>, locale: Locale): TravelSearchRequest {
    val kept = stops.map { it.copy(city = it.city.trim()) }.filter { it.city.isNotEmpty() }
    return TravelSearchRequest(
        origin = form.origin.trim(),
        destination = form.destination.trim(),
        departureDate = form.departureDate,
        returnDate = form.returnDate.ifBlank { null },
        interests = form.interests,
        nationality = form.nationality.trim(),
        residencePermits = form.residencePermits,
        existingVisas = form.existingVisas,
        budgetUsd = parseBudget(form.budgetUsd, locale),
        numTravelers = form.travellers.total.coerceAtLeast(1),
        adults = form.travellers.adults,
        children = form.travellers.children,
        seniors = form.travellers.seniors,
        infants = form.travellers.infants,
        accessibilityNeeds = form.accessibilityNeeds,
        destinations = if (kept.isEmpty()) null else kept.map { it.city } + form.destination.trim(),
        destinationNights = if (kept.isEmpty()) null else kept.map { parseNights(it.nights) } + null,
        pace = form.pace,
        serendipity = form.serendipity,
        mustInclude = form.mustInclude,
        importSource = form.importSource,
    )
}

/**
 * Two-way binding (web `startSearch` → `updatePreferences`): the preference fields the search
 * writes back. [interests] defaults to the form's (the import flow writes the known form's instead).
 * Everything else in [current] (username, current residence) is kept: PUT replaces all fields.
 */
fun preferencesFromForm(current: Preferences, form: SearchForm, locale: Locale, interests: List<String> = form.interests): Preferences =
    current.copy(
        nationality = form.nationality.trim(),
        residencePermits = form.residencePermits,
        existingVisas = form.existingVisas,
        interests = interests,
        numTravelers = form.travellers.total.coerceAtLeast(1),
        budgetCategory = usdToBudgetCategory(form.budgetUsd, locale),
        adults = form.travellers.adults,
        children = form.travellers.children,
        seniors = form.travellers.seniors,
        infants = form.travellers.infants,
        accessibilityNeeds = form.accessibilityNeeds,
    )

/** True when [preferencesFromForm] would change anything (skip the PUT otherwise). */
fun preferencesChanged(current: Preferences, form: SearchForm, locale: Locale, interests: List<String> = form.interests): Boolean =
    preferencesFromForm(current, form, locale, interests) != current

/**
 * Web pre-fill from saved preferences: non-empty preference values replace the form's,
 * the budget comes from the category, traveller counts always come from preferences.
 */
fun applyPreferences(form: SearchForm, prefs: Preferences): SearchForm =
    form.copy(
        nationality = prefs.nationality.ifBlank { form.nationality },
        residencePermits = prefs.residencePermits.ifEmpty { form.residencePermits },
        existingVisas = prefs.existingVisas.ifEmpty { form.existingVisas },
        interests = prefs.interests.ifEmpty { form.interests },
        budgetUsd = budgetCategoryToUsd(prefs.budgetCategory).ifEmpty { form.budgetUsd },
        travellers = TravellerCounts.of(prefs.adults, prefs.children, prefs.seniors, prefs.infants) ?: form.travellers,
        accessibilityNeeds = prefs.accessibilityNeeds.ifEmpty { form.accessibilityNeeds },
    )

/** Toggles [id] in [list] (web chip toggles keep insertion order). */
fun List<String>.toggle(id: String): List<String> = if (id in this) this - id else this + id

/** A problem that blocks submitting the known-destination form. */
enum class SearchFormIssue {
    ORIGIN_REQUIRED,
    DESTINATION_REQUIRED,
    NATIONALITY_REQUIRED,
    STOP_CITY_REQUIRED,
    STOP_NIGHTS_OUT_OF_RANGE,
    BUDGET_INVALID,
    DEPARTURE_REQUIRED,
    DEPARTURE_IN_PAST,
    RETURN_BEFORE_DEPARTURE,
}

/**
 * The web form's `required`/`min` rules, as explicit issues (the web relies on the browser's
 * native messages). Empty list = ready to submit.
 */
fun validateSearchForm(form: SearchForm, stops: List<ExtraStop>, today: LocalDate, locale: Locale): List<SearchFormIssue> =
    buildList {
        if (form.origin.isBlank()) add(SearchFormIssue.ORIGIN_REQUIRED)
        if (form.destination.isBlank()) add(SearchFormIssue.DESTINATION_REQUIRED)
        if (stops.any { it.city.isBlank() }) add(SearchFormIssue.STOP_CITY_REQUIRED)
        if (stops.any { it.nights.isNotBlank() && it.nights.trim().toIntOrNull() !in STOP_NIGHTS_RANGE }) {
            add(SearchFormIssue.STOP_NIGHTS_OUT_OF_RANGE)
        }
        addAll(validateTripDates(form.departureDate, form.returnDate, today))
        if (form.nationality.isBlank()) add(SearchFormIssue.NATIONALITY_REQUIRED)
        if (form.budgetUsd.isNotBlank() && parseLocalizedAmount(form.budgetUsd, locale)?.takeIf { it >= 0 } == null) {
            add(SearchFormIssue.BUDGET_INVALID)
        }
    }

/** Date rules shared by every mode: departure required and ≥ today (web `min=today`); return ≥ departure (web `min=departure`). */
fun validateTripDates(departure: String, returnDate: String, today: LocalDate): List<SearchFormIssue> {
    val dep = parseIsoDate(departure) ?: return listOf(SearchFormIssue.DEPARTURE_REQUIRED)
    val ret = parseIsoDate(returnDate)
    return buildList {
        if (dep.isBefore(today)) add(SearchFormIssue.DEPARTURE_IN_PAST)
        if (ret != null && ret.isBefore(dep)) add(SearchFormIssue.RETURN_BEFORE_DEPARTURE)
    }
}

/** `YYYY-MM-DD` → date, or null when blank/invalid. */
fun parseIsoDate(value: String?): LocalDate? = value?.trim()?.takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Short city name for route summaries (web `split(',')[0]`). */
fun shortCity(place: String?): String = place.orEmpty().substringBefore(',').trim()
