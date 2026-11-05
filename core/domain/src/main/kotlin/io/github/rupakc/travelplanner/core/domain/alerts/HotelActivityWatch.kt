package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.ActivityWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.HotelWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

// Port of frontend/src/utils/hotelWatch.js and activityWatch.js.

/** Google Hotels guests: every traveller is a guest; one room per 4 (web `hotelGuests`). */
data class HotelGuests(val adults: Int, val rooms: Int)

fun hotelGuests(request: TravelSearchRequest?): HotelGuests {
    val counted = (request?.adults ?: 0) + (request?.seniors ?: 0) + (request?.children ?: 0)
    val guests = (counted.takeIf { it > 0 } ?: request?.numTravelers ?: 1).coerceIn(1, MAX_ADULTS)
    val rooms = if (guests <= GUESTS_PER_ROOM) 1 else ceil(guests / GUESTS_PER_ROOM.toDouble()).toInt()
    return HotelGuests(guests, rooms)
}

/** The stay a hotel card is for. */
data class HotelStay(val checkIn: String, val checkOut: String, val city: String) {
    val nights: Int get() = nightsBetween(checkIn, checkOut) ?: 0
}

/**
 * The stay a hotel card is for, or null when the dates can't be told (no return date, a
 * multi-city hotel not placeable in one of the trip's cities) or check-in is [today] or past.
 */
fun hotelStay(hotel: Hotel?, request: TravelSearchRequest?, today: LocalDate): HotelStay? {
    if (hotel == null || request == null) return null
    val dep = parseIsoDate(request.departureDate) ?: return null
    val ret = parseIsoDate(request.returnDate) ?: return null
    val stops = request.destinations.orEmpty()
    val stay =
        if (stops.size > 1) {
            val at = stops.indexOfFirst { hotel.city != null && cityKey(it) == cityKey(hotel.city) }
            if (at < 0) return null
            val total = maxOf(stops.size, ChronoUnit.DAYS.between(dep, ret).toInt())
            val alloc = allocateDays(stops.size, total, request.destinationNights)
            val start = alloc.take(at).sum()
            HotelStay(dep.plusDays(start.toLong()).toString(), dep.plusDays((start + alloc[at]).toLong()).toString(), hotel.city.orEmpty())
        } else {
            if (hotel.city != null && request.destination.isNotBlank() && cityKey(hotel.city) != cityKey(request.destination)) return null
            HotelStay(dep.toString(), ret.toString(), hotel.city ?: request.destination)
        }
    if (stay.city.isBlank() || stay.nights < 1) return null
    return stay.takeIf { LocalDate.parse(it.checkIn).isAfter(today) }
}

/** The hotel agent prices one room: its whole-stay price, or null. */
fun perRoomEstimate(hotel: Hotel?, nights: Int): Double? {
    hotel?.totalPriceUsd?.takeIf { it > 0 }?.let { return it }
    return hotel?.pricePerNightUsd?.takeIf { it > 0 }?.let { (it * nights).roundToLong().toDouble() }
}

/** `hotel` spec for a hotel card, or null if it can't be watched; `estimate_total` scales the AI estimate to the rooms. */
fun hotelWatchSpec(hotel: Hotel?, request: TravelSearchRequest?, today: LocalDate): HotelWatchSpec? {
    val name = hotel?.name?.trim().orEmpty()
    if (name.isEmpty()) return null
    val stay = hotelStay(hotel, request, today) ?: return null
    val guests = hotelGuests(request)
    val estimate = perRoomEstimate(hotel, stay.nights)?.let { it * guests.rooms }
    return HotelWatchSpec(name, stay.city, stay.checkIn, stay.checkOut, guests.adults, guests.rooms, estimateTotal = estimate)
}

/** True when Google Hotels lists the watched property under another name than the card's. */
fun matchedElsewhere(watch: PriceWatch): Boolean {
    val asked = watch.subject.string("requested_name")
    return !asked.isNullOrBlank() && !watch.title.isNullOrBlank() && fold(asked) != fold(watch.title)
}

fun nightsBetween(checkIn: String?, checkOut: String?): Int? {
    val a = parseIsoDate(checkIn) ?: return null
    val b = parseIsoDate(checkOut) ?: return null
    return ChronoUnit.DAYS.between(a, b).toInt()
}

/** "GetYourGuide" / "Viator" (text only); unknown providers as given; null when none. */
fun providerName(provider: String?): String? =
    when (provider) {
        "getyourguide" -> "GetYourGuide"
        "viator" -> "Viator"
        else -> provider?.takeIf(String::isNotBlank)
    }

/** True when the listing's title isn't just the AI's name for it. */
fun titleDiffers(name: String?, title: String?): Boolean = !title.isNullOrBlank() && !name.isNullOrBlank() && fold(name) != fold(title)

/** Travellers on the search, 1–20 (web `travellersFrom`). */
fun travellersFrom(request: TravelSearchRequest?): Int {
    val n = request?.numTravelers ?: 0
    val total = if (n >= 1) n else listOf(request?.adults, request?.children, request?.seniors).sumOf { (it ?: 0).coerceAtLeast(0) }
    return total.coerceIn(1, MAX_TRAVELLERS)
}

/** The itinerary day an activity is scheduled on (ISO), or null. */
fun activityDate(itinerary: ItineraryData?, name: String?): String? {
    val target = fold(name)
    if (target.isEmpty()) return null
    for (day in itinerary?.days.orEmpty()) {
        val hit =
            day.slots.any { slot ->
                val text = fold(slot.activity)
                text == target || (target.length >= MIN_CONTAINED && text.contains(target))
            }
        if (hit && day.date != null && ISO_DAY.matches(day.date!!)) return day.date
    }
    return null
}

/**
 * The spec to watch an activity card's price, or null without a name or city (a multi-city
 * activity needs its own city). A date not after tomorrow is left out (the watch then runs 180 days).
 */
fun activityWatchSpec(activity: Activity?, request: TravelSearchRequest?, itinerary: ItineraryData?, today: LocalDate): ActivityWatchSpec? {
    val name = activity?.name?.trim().orEmpty()
    val destination = if ((request?.destinations?.size ?: 0) > 1) "" else request?.destination.orEmpty().substringBefore(',')
    val city = (activity?.city ?: destination).trim()
    if (name.isEmpty() || city.isEmpty()) return null
    val date = activityDate(itinerary, name)?.takeIf { LocalDate.parse(it).isAfter(today.plusDays(1)) }
    val estimate = activity?.priceUsd?.takeIf { it.isFinite() && it > 0 }
    return ActivityWatchSpec(name = name, city = city, travellers = travellersFrom(request), date = date, estimatePrice = estimate)
}

/** The watch's currency (the listing's own). */
fun watchCurrency(watch: PriceWatch): String = watch.currency ?: watch.subject.string("currency") ?: "USD"

/** Per-person "from" price of the latest look (or the baseline). */
fun perPerson(watch: PriceWatch): Double? {
    watch.lastObservation.double("unit_price")?.let { return it }
    val total = watch.lastPrice ?: watch.baselinePrice ?: return null
    val n = watch.subject.int("travellers")?.takeIf { it > 0 } ?: 1
    return total / n
}

/** Lower-case, accent-free, punctuation as single spaces (web `fold`). */
internal fun fold(text: String?): String =
    Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

/** First part of a place name, folded (web `cityKey`). */
internal fun cityKey(text: String?): String =
    Normalizer.normalize(text.orEmpty().substringBefore(',').lowercase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .trim()

/** Days per stop of a multi-city trip (web `allocateDays`, client mirror of backend must_include). */
internal fun allocateDays(n: Int, totalDays: Int, nights: List<Int?>?): List<Int> {
    val requested = List(n) { i -> nights?.getOrNull(i)?.takeIf { it > 0 } }
    val fixed = requested.sumOf { it ?: 0 }
    val free = requested.count { it == null }
    val remaining = totalDays - fixed
    val fits = remaining >= free && (free > 0 || remaining == 0)
    if (requested.any { it != null } && fits) {
        if (free == 0) return requested.map { it!! }
        val bounds = List(free + 1) { i -> jsRound(i * remaining / free.toDouble()).toInt() }
        var k = 0
        return requested.map { d ->
            d ?: (bounds[k + 1] - bounds[k]).also { k++ }
        }
    }
    val bounds = List(n + 1) { i -> jsRound(i * totalDays / n.toDouble()).toInt() }
    return List(n) { i -> bounds[i + 1] - bounds[i] }
}

private const val MAX_ADULTS = 16
private const val GUESTS_PER_ROOM = 4
private const val MAX_TRAVELLERS = 20
private const val MIN_CONTAINED = 6
private val ISO_DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")
