package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.alerts.ActivityWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.CreateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.HotelWatchSpec
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Ports of the web watch spec builders used by the Results "Watch" buttons:
 * `fareWatch.js` (tripFromSearch, isWatchableTrip, cheapestFlightPrice),
 * `hotelWatch.js` (hotelGuests, hotelStay, hotelWatchSpec), `activityWatch.js`
 * (travellersFrom, activityDate, activitySpec) and `mustInclude.js` `allocateDays`.
 */
object WatchSpecs {
    private const val MAX_ADULTS = 16
    private const val GUESTS_PER_ROOM = 4
    private const val MAX_TRAVELLERS = 20
    private const val MIN_FUZZY_NAME = 6

    /** "LIS" style code (the web's `isAirportCode`). */
    fun isAirportCode(value: String?): Boolean = value != null && Regex("^[A-Z]{3}$").matches(value.trim())

    // ── fare watch ──────────────────────────────────────────────────────────

    /** The trip fields a fare watch needs (web `tripFromSearch`), as a create request with defaults. */
    fun fareTrip(search: TravelSearchRequest): CreateFareWatchRequest =
        CreateFareWatchRequest(
            origin = search.origin,
            destination = search.destination,
            destinations = search.destinations,
            departureDate = search.departureDate,
            returnDate = search.returnDate,
            numTravelers = search.numTravelers.takeIf { it > 0 } ?: 1,
        )

    /** Single-destination trip with both ends and a departure after [today] (web `isWatchableTrip`). */
    fun isWatchableTrip(search: TravelSearchRequest?, today: LocalDate): Boolean {
        if (search == null) return false
        if (listOf(search.origin, search.destination, search.departureDate).any(String::isBlank)) return false
        if ((search.destinations?.size ?: 0) > 1) return false
        return search.departureDate > today.toString()
    }

    /** Cheapest priced flight of a single-city result (the watch's "today" price), or null. */
    fun cheapestFlightPrice(flights: FlightsData?): Double? {
        if (flights == null || flights.legs.isNotEmpty()) return null
        return flights.results.mapNotNull { it.priceUsd?.takeIf { p -> p.isFinite() && p > 0 } }.minOrNull()
    }

    // ── hotel watch ─────────────────────────────────────────────────────────

    /** Web `foldText` + `cityKey` (mustInclude.js): diacritics folded, first comma segment. */
    fun foldedCityKey(text: String?): String = fold(text.orEmpty().split(',').first()).trim()

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase().replace("ß", "ss"), Normalizer.Form.NFKD).replace(Regex("[\\u0300-\\u036f]"), "")

    /** Web `allocateDays`: nights per stop, honouring requested nights when they fit. */
    fun allocateDays(n: Int, totalDays: Int, nights: List<Int?>?): List<Int> {
        val requested = List(n) { i -> nights?.getOrNull(i)?.takeIf { it > 0 } }
        val fixed = requested.sumOf { it ?: 0 }
        val free = requested.count { it == null }
        val remaining = totalDays - fixed
        val honoured = requested.any { it != null } && remaining >= free
        if (honoured && (free > 0 || remaining == 0)) {
            if (free == 0) return requested.map { it!! }
            val bounds = List(free + 1) { i -> jsRound(i.toDouble() * remaining / free).toInt() }
            var k = 0
            return requested.map { d -> d ?: (bounds[k + 1] - bounds[k]).also { k++ } }
        }
        val bounds = List(n + 1) { i -> jsRound(i.toDouble() * totalDays / n).toInt() }
        return List(n) { i -> bounds[i + 1] - bounds[i] }
    }

    /** Google Hotels guests: everyone counts, 1 room up to 4, then one per 4 (web `hotelGuests`). */
    fun hotelGuests(search: TravelSearchRequest?): Pair<Int, Int> {
        val counted = (search?.adults ?: 0) + (search?.seniors ?: 0) + (search?.children ?: 0)
        val base = if (counted > 0) counted else search?.numTravelers?.takeIf { it > 0 } ?: 1
        val guests = min(MAX_ADULTS, max(1, base))
        val rooms = if (guests <= GUESTS_PER_ROOM) 1 else ceil(guests.toDouble() / GUESTS_PER_ROOM).toInt()
        return guests to rooms
    }

    /** The stay a hotel card is for (check-in, check-out, city), or null (web `hotelStay`). */
    @Suppress("ReturnCount")
    fun hotelStay(hotel: Hotel, search: TravelSearchRequest?, today: LocalDate): Triple<String, String, String>? {
        val dep = search?.departureDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val ret = search.returnDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val stops = search.destinations.orEmpty()
        val stay: Triple<LocalDate, LocalDate, String?> =
            if (stops.size > 1) {
                val at = stops.indexOfFirst { hotel.city != null && foldedCityKey(it) == foldedCityKey(hotel.city) }
                if (at < 0) return null
                val total = max(stops.size, ChronoUnit.DAYS.between(dep, ret).toInt())
                val alloc = allocateDays(stops.size, total, search.destinationNights)
                val start = alloc.take(at).sum()
                Triple(dep.plusDays(start.toLong()), dep.plusDays((start + alloc[at]).toLong()), hotel.city)
            } else {
                if (hotel.city != null && search.destination.isNotBlank() && foldedCityKey(hotel.city) != foldedCityKey(search.destination)) return null
                Triple(dep, ret, hotel.city ?: search.destination)
            }
        val city = stay.third?.takeIf(String::isNotEmpty) ?: return null
        if (ChronoUnit.DAYS.between(stay.first, stay.second) < 1) return null
        return if (stay.first > today) Triple(stay.first.toString(), stay.second.toString(), city) else null
    }

    /**
     * `POST /price-watches` hotel spec for a hotel card, or null when it can't be watched.
     * `estimate_total` is the AI estimate (per room, whole stay) × rooms.
     * Pass [hotel]/[search] with airport codes already turned into city names.
     */
    fun hotelWatchSpec(hotel: Hotel, search: TravelSearchRequest?, today: LocalDate): HotelWatchSpec? {
        val name = hotel.name?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val (checkIn, checkOut, city) = hotelStay(hotel, search, today) ?: return null
        val (adults, rooms) = hotelGuests(search)
        val nights = ChronoUnit.DAYS.between(LocalDate.parse(checkIn), LocalDate.parse(checkOut))
        val perRoom =
            hotel.totalPriceUsd?.takeIf { it > 0 }
                ?: hotel.pricePerNightUsd?.takeIf { it > 0 }?.let { (it * nights).roundToLong().toDouble() }
        return HotelWatchSpec(name, city, checkIn, checkOut, adults, rooms, estimateTotal = perRoom?.let { it * rooms })
    }

    // ── activity watch ──────────────────────────────────────────────────────

    /** Travellers on the search, 1–20 (web `travellersFrom`). */
    fun travellersFrom(search: TravelSearchRequest?): Int {
        var total = search?.numTravelers?.takeIf { it >= 1 } ?: 0
        if (total == 0) total = listOf(search?.adults, search?.children, search?.seniors).sumOf { (it ?: 0).coerceAtLeast(0) }
        return min(max(if (total > 0) total else 1, 1), MAX_TRAVELLERS)
    }

    private fun foldName(s: String?): String = fold(s.orEmpty()).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** The itinerary day (ISO) an activity is scheduled on, or null (web `activityDate`). */
    fun activityDate(itinerary: ItineraryData?, name: String?): String? {
        val target = foldName(name)
        if (target.isEmpty()) return null
        for (day in itinerary?.days.orEmpty()) {
            val hit =
                day.slots.any { slot ->
                    val text = foldName(slot.activity)
                    text == target || (target.length >= MIN_FUZZY_NAME && text.contains(target))
                }
            val date = day.date
            if (hit && date != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date)) return date
        }
        return null
    }

    /**
     * Spec to watch an activity's price, or null (no name / city; a multi-city
     * activity without its own city is never guessed). A date not after tomorrow is left out.
     */
    fun activitySpec(activity: Activity, search: TravelSearchRequest?, itinerary: ItineraryData?, today: LocalDate): ActivityWatchSpec? {
        val name = activity.name?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val destination = if ((search?.destinations?.size ?: 0) > 1) "" else search?.destination.orEmpty().split(',').first()
        val city = (activity.city ?: destination).trim().takeIf(String::isNotEmpty) ?: return null
        val date = activityDate(itinerary, name)?.takeIf { it > today.plusDays(1).toString() }
        val estimate = activity.priceUsd?.takeIf { it.isFinite() && it > 0 }
        return ActivityWatchSpec(name, city, travellersFrom(search), date = date, estimatePrice = estimate)
    }
}
