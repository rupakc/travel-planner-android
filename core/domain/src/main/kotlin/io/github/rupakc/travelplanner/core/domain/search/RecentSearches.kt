package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.domain.place.placeName
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.preferences.RecentStop
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Expired recent searches move this far out from today (web `SHIFT_DAYS`, = the form's default departure). */
const val RECENT_SHIFT_DAYS = 7L

/**
 * Port of web `toRecentSearch`: a submitted request → the `POST /api/recent-searches` body.
 * `destinations` is journey-ordered with the final destination last, so every city before it is a stop.
 */
fun toRecentSearch(request: TravelSearchRequest): RecentSearchIn {
    val journey = request.destinations.orEmpty()
    val stops = journey.dropLast(1).mapIndexed { i, city -> RecentStop(city, request.destinationNights?.getOrNull(i)) }
    return RecentSearchIn(
        origin = request.origin,
        destination = request.destination,
        departureDate = request.departureDate,
        returnDate = request.returnDate?.ifBlank { null },
        stops = stops,
        nationality = request.nationality,
        residencePermits = request.residencePermits,
        existingVisas = request.existingVisas,
        interests = request.interests,
        budgetUsd = request.budgetUsd?.takeIf { it != 0.0 },
        adults = request.adults,
        children = request.children,
        seniors = request.seniors,
        infants = request.infants,
        accessibilityNeeds = request.accessibilityNeeds,
        serendipity = request.serendipity,
    )
}

/** [recentSearchToForm] result: the form fields, stops on the way, and whether past dates were moved. */
data class RestoredSearch(val form: SearchForm, val stops: List<ExtraStop>, val datesShifted: Boolean)

/**
 * Port of web `recentSearchToForm`: a saved search → Search form state. A departure before
 * [today] moves to today + 7 days keeping the trip length (a one-way trip stays one-way).
 * Fields the saved search doesn't hold (pace, import) come from [base].
 */
fun recentSearchToForm(search: RecentSearchIn, today: LocalDate, base: SearchForm = SearchForm()): RestoredSearch {
    var departure = search.departureDate
    var ret = search.returnDate.orEmpty()
    val departureDate = parseIsoDate(departure)
    val datesShifted = departureDate != null && departureDate.isBefore(today)
    if (datesShifted && departureDate != null) {
        val tripLength = parseIsoDate(ret)?.let { ChronoUnit.DAYS.between(departureDate, it) }
        val newDeparture = today.plusDays(RECENT_SHIFT_DAYS)
        departure = newDeparture.toString()
        ret = tripLength?.let { newDeparture.plusDays(it).toString() }.orEmpty()
    }
    val travellers =
        TravellerCounts.of(search.adults, search.children, search.seniors, search.infants) ?: TravellerCounts()
    val budget = search.budgetUsd?.takeIf { it != 0.0 }
    return RestoredSearch(
        form =
            base.copy(
                origin = search.origin,
                destination = search.destination,
                departureDate = departure,
                returnDate = ret,
                nationality = search.nationality,
                residencePermits = search.residencePermits,
                existingVisas = search.existingVisas,
                interests = search.interests,
                budgetUsd = budget?.let(::budgetText).orEmpty(),
                travellers = travellers,
                accessibilityNeeds = search.accessibilityNeeds,
                serendipity = search.serendipity,
                mustInclude = null,
                importSource = null,
            ),
        stops = search.stops.map { ExtraStop(it.city, it.nights?.takeIf { n -> n > 0 }?.toString().orEmpty()) },
        datesShifted = datesShifted,
    )
}

/** Short labels for a recent-search card (web `describeRecentSearch`). */
data class RecentSearchSummary(val route: String, val dates: String, val interests: List<String>, val travellers: Int)

/** Port of web `describeRecentSearch`; dates use [locale]'s month names ("Apr 1 – Apr 8, 2027" in English). */
fun describeRecentSearch(search: RecentSearchIn, locale: Locale = Locale.US, names: Map<String, String> = emptyMap()): RecentSearchSummary {
    // Airport codes ("LIS") read as the city [names] holds for them ("Lisbon"); unknown codes stay codes.
    val route = (listOf(search.origin) + search.stops.map { it.city } + search.destination).joinToString(" → ") { shortCity(placeName(it, names)) }
    return RecentSearchSummary(
        route = route,
        dates = formatTripDates(search.departureDate, search.returnDate, locale),
        interests = search.interests,
        travellers = (search.adults + search.children + search.seniors + search.infants).coerceAtLeast(1),
    )
}

/** Web `formatDates`: "Apr 1 – Apr 8, 2027", "Dec 30, 2027 – Jan 4, 2028", one-way "Dec 30, 2027". */
fun formatTripDates(departure: String, returnDate: String?, locale: Locale = Locale.US): String {
    val dep = parseIsoDate(departure) ?: return departure
    val ret = parseIsoDate(returnDate) ?: return formatDay(dep, true, locale)
    return "${formatDay(dep, dep.year != ret.year, locale)} – ${formatDay(ret, true, locale)}"
}

private fun formatDay(date: LocalDate, withYear: Boolean, locale: Locale): String =
    if (locale.language == Locale.ENGLISH.language) {
        // The web's exact en-US shape ("Apr 1", "Apr 8, 2027").
        date.format(DateTimeFormatter.ofPattern(if (withYear) "MMM d, yyyy" else "MMM d", locale))
    } else {
        // Other locales get their own medium date order ("1 abr 2027").
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    }

/** True when the saved search's departure is before [today] (card shows "· dates passed", rerun disabled). */
fun recentDatesPassed(search: RecentSearchIn, today: LocalDate): Boolean = parseIsoDate(search.departureDate)?.isBefore(today) == true
