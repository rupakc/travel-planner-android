package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.domain.plan.BudgetStatus
import io.github.rupakc.travelplanner.core.domain.plan.computePlanCost
import io.github.rupakc.travelplanner.core.domain.plan.daysBetween
import io.github.rupakc.travelplanner.core.domain.plan.getBudgetStatus
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.PlaceToSee
import io.github.rupakc.travelplanner.core.model.section.SimPlan
import io.github.rupakc.travelplanner.core.model.section.WalkingTour
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Why an item was picked (the UI renders the web's English copy, localized). */
sealed interface PickReason {
    data object CheapestFare : PickReason

    /** "Fewer stops for $[extra] more than the cheapest fare". */
    data class FewerStops(val extra: Long) : PickReason

    /** "Saves [minutes] of travel for $[extra] more than the cheapest fare". */
    data class SavesTime(val minutes: Int, val extra: Long) : PickReason

    /** Hotel: [stars] 3 → "Mid-range (3★)…", other > 0 → "N★…", null/0 → "Best-reviewed…"; [city] on multi-city. */
    data class BestReviewed(val stars: Int?, val city: String?) : PickReason

    data object CheaperStay : PickReason

    /** Activity: free ([price] 0), plain price, or above the $30 floor ([typical]); [matchesInterests] adds the interest note. */
    data class ActivityPrice(val price: Double, val typical: Boolean, val matchesInterests: Boolean) : PickReason

    /** Walking tour: the backend's own `price_note` (shown as-is) or null → "Free walking tour". */
    data class TourNote(val priceNote: String?) : PickReason

    data object FreeEntry : PickReason

    data class CoversTrip(val days: Int) : PickReason

    data object CheapestPlan : PickReason
}

/** Web package item types (`type` keys of the web). */
enum class PackageItemType { FLIGHT, FLIGHTS, HOTEL, ACTIVITIES, WALKING_TOURS, PLACES_TO_SEE, SIM }

/** One row of the package: a real listed item ([item]) and why it was picked. */
data class PackageItem(val type: PackageItemType, val item: PlanItem, val reason: PickReason, val city: String? = null)

/** The deterministic best-value package. [selections] holds only the package keys. */
data class BestValue(val items: List<PackageItem>, val selections: PlanSelections, val total: Double, val budget: BudgetStatus?)

/** Which current picks applying would replace (web labels "flight", "hotel", "SIM"). */
enum class PackageConflict { FLIGHT, HOTEL, SIM }

/**
 * Port of `frontend/src/utils/bestValuePackage.js`: a deterministic "best-value
 * package" over the streamed results. Every pick is a real listed item; any
 * non-finite number counts as unknown.
 */
object BestValuePackage {
    private const val FLIGHT_PRICE_WINDOW = 1.15
    private const val ACTIVITY_PRICE_FLOOR = 30.0
    private const val MIN_TIME_SAVED_MINUTES = 90
    private const val MIN_CHEAP_HOTEL_REVIEW = 7.0
    private const val ACTIVITIES_PER_CITY = 2
    private const val ACTIVITIES = 4
    private const val WALKING_TOURS = 3
    private const val PLACES_PER_CITY = 2
    private const val PLACES = 4
    private const val INTEREST_MATCH = 0.5
    private const val MID_RANGE_STARS = 3

    private fun Double?.known(): Double? = this?.takeIf { it.isFinite() }

    /** Comparator: unknown (null) sorts last. */
    private fun <T> asc(get: (T) -> Double?) =
        Comparator<T> { a, b ->
            val x = get(a)
            val y = get(b)
            when {
                x == null && y == null -> 0
                x == null -> 1
                y == null -> -1
                else -> x.compareTo(y)
            }
        }

    /** Comparator: larger first, unknown (null) still last. */
    private fun <T> desc(get: (T) -> Double?) =
        Comparator<T> { a, b ->
            val x = get(a)
            val y = get(b)
            when {
                x == null && y == null -> 0
                x == null -> 1
                y == null -> -1
                else -> y.compareTo(x)
            }
        }

    /** Stable multi-key sort (Kotlin's sortedWith is stable). */
    private fun <T> List<T>.sortBy(vararg cmps: Comparator<T>): List<T> =
        sortedWith { a, b ->
            cmps.firstNotNullOfOrNull { c -> c.compare(a, b).takeIf { it != 0 } }
                ?: 0
        }

    private fun <T> capped(items: List<T>, allow: ((T) -> Boolean)?): List<T> {
        if (allow == null) return items
        val kept = items.filter(allow)
        return kept.ifEmpty { items }
    }

    // ---- flights ----

    private fun price(f: Flight) = f.priceUsd.known()

    private fun stops(f: Flight) = (f.outboundStops ?: 0) + (f.returnLeg?.stops ?: 0)

    private fun duration(f: Flight): Double? {
        val out = f.outboundMinutes ?: return null
        return (out + (f.returnLeg?.durationMinutes ?: 0)).toDouble()
    }

    private data class Pick<T>(val item: T, val reason: PickReason)

    private fun pickFlight(flights: List<Flight>, allow: ((Flight) -> Boolean)?): Pick<Flight>? {
        var cands = capped(flights.filter { (price(it) ?: 0.0) > 0 }, allow)
        if (cands.any { it.source != "estimate" }) cands = cands.filter { it.source != "estimate" }
        if (cands.isEmpty()) return null
        val cheapestOverall = cands.sortBy(asc(::price), asc { stops(it).toDouble() }).first()
        val cheapestPrice = price(cheapestOverall)!!
        val pool = cands.filter { price(it)!! <= cheapestPrice * FLIGHT_PRICE_WINDOW }
        val fewest = pool.minOf(::stops)
        val group = pool.filter { stops(it) == fewest }
        val base = group.sortBy(asc(::price), asc(::duration)).first()
        val baseDuration = duration(base)
        val faster =
            group.filter {
                val d = duration(it)
                d != null && baseDuration != null && price(it)!! > price(base)!! && baseDuration - d >= MIN_TIME_SAVED_MINUTES
            }
        val best = if (faster.isNotEmpty()) faster.sortBy(asc(::duration), asc(::price)).first() else base
        val extra = jsRound(price(best)!! - cheapestPrice).toLong()
        val reason =
            when {
                extra <= 0 -> PickReason.CheapestFare
                stops(best) < stops(cheapestOverall) -> PickReason.FewerStops(extra)
                else -> PickReason.SavesTime(jsRound(baseDuration!! - duration(best)!!).toInt(), extra)
            }
        return Pick(best, reason)
    }

    private data class LegPick(val item: Flight, val reason: PickReason, val city: String?)

    private fun withLegIndex(f: Flight, legIndex: Int): Flight =
        if (f.legIndex ==
            legIndex
        ) {
            f
        } else {
            f.copy(legIndex = legIndex, raw = if (f.raw.isEmpty()) f.raw else JsonObject(f.raw + ("leg_index" to JsonPrimitive(legIndex))))
        }

    private fun pickFlights(section: FlightsData?, allow: ((Flight) -> Boolean)?): Pair<Pick<Flight>?, List<LegPick>> {
        if (section == null || section.isError) return null to emptyList()
        if (section.legs.isNotEmpty()) {
            val legs =
                section.legs.mapIndexedNotNull { i, leg ->
                    val pick = pickFlight(leg.results, allow) ?: return@mapIndexedNotNull null
                    val legIndex = pick.item.legIndex ?: leg.legIndex ?: i
                    LegPick(withLegIndex(pick.item, legIndex), pick.reason, pick.item.city ?: leg.to)
                }
            return null to legs.sortedBy { it.item.legIndex }
        }
        return pickFlight(section.okResults, allow) to emptyList()
    }

    // ---- hotels ----

    private fun nightly(h: Hotel) = h.pricePerNightUsd.known()

    private fun review(h: Hotel) = h.reviewScore.known()

    private fun firstStop(results: TripResults, search: TravelSearchRequest?): String? {
        val destinations = search?.destinations ?: return null
        if (destinations.size <= 1) return null
        val to = results.flights?.legs?.firstOrNull()?.to
        return if (!to.isNullOrBlank()) to else destinations.first()
    }

    private fun hotelCandidates(results: TripResults, first: String?): List<Hotel> {
        var cands = results.hotels.okResults.filter { !it.name.isNullOrEmpty() && (nightly(it) ?: 0.0) > 0 }
        if (first != null) {
            var inCity = cands.filter { cityKey(it.city) == cityKey(first) }
            if (inCity.isEmpty()) {
                val tagged = cands.firstOrNull { cityKey(it.city).isNotEmpty() }
                if (tagged != null) inCity = cands.filter { cityKey(it.city) == cityKey(tagged.city) }
            }
            if (inCity.isNotEmpty()) cands = inCity
        }
        return cands
    }

    private fun stars(h: Hotel): Int? = h.starRating.known()?.let { jsRound(it).toInt() }

    private fun pickHotel(cands: List<Hotel>, first: String?): Pick<Hotel>? {
        if (cands.isEmpty()) return null
        var pool = cands.filter { stars(it) == MID_RANGE_STARS }
        if (pool.isEmpty()) pool = cands.filter { stars(it) == MID_RANGE_STARS + 1 }
        if (pool.isEmpty()) pool = cands
        val best = pool.sortBy(desc(::review), asc(::nightly)).first()
        val city = if (first != null) best.city?.takeIf(String::isNotEmpty) ?: first else null
        return Pick(best, PickReason.BestReviewed(stars(best)?.takeIf { it > 0 }, city))
    }

    // ---- activities / tours / places ----

    private fun <T> takePerCity(sorted: List<T>, perCity: Int, total: Int, city: (T) -> String?): List<T> {
        val counts = mutableMapOf<String, Int>()
        val out = mutableListOf<T>()
        for (x in sorted) {
            if (out.size >= total) break
            val k = cityKey(city(x))
            val seen = counts[k] ?: 0
            if (seen < perCity) {
                counts[k] = seen + 1
                out += x
            }
        }
        return out
    }

    private fun pickActivities(results: TripResults): List<Pick<Activity>> {
        val priced = results.activities.okResults.filter { !it.name.isNullOrEmpty() && (it.priceUsd.known()?.let { p -> p >= 0 } == true) }
        val cap = priced.groupBy { cityKey(it.city) }.mapValues { (_, list) -> maxOf(ACTIVITY_PRICE_FLOOR, median(list.map { it.priceUsd!! })) }
        val cands = priced.filter { it.priceUsd!! <= cap.getValue(cityKey(it.city)) }
        val sorted = cands.sortBy(desc { it.similarityScore.known() }, asc { it.priceUsd })
        return takePerCity(sorted, ACTIVITIES_PER_CITY, ACTIVITIES) { it.city }.map {
            val p = it.priceUsd!!
            val match = (it.similarityScore.known() ?: 0.0) >= INTEREST_MATCH
            Pick(it, PickReason.ActivityPrice(p, typical = p > ACTIVITY_PRICE_FLOOR, matchesInterests = match))
        }
    }

    private fun pickWalkingTours(results: TripResults): List<Pick<WalkingTour>> {
        val section = results.walkingTours
        val list = listOrEmpty(section?.isError, section?.results).filter { !it.url.isNullOrEmpty() }
        val topFirst = Comparator<WalkingTour> { a, b -> (if (b.topPick == true) 1 else 0) - (if (a.topPick == true) 1 else 0) }
        val sorted = list.sortBy(topFirst, desc { it.rating.known() })
        val perCity = takePerCity(sorted, 1, Int.MAX_VALUE) { it.city }
        val ranked = perCity.sortBy(topFirst, desc { it.rating.known() })
        return ranked.take(WALKING_TOURS).map { Pick(it, PickReason.TourNote(it.priceNote?.takeIf(String::isNotEmpty))) }
    }

    private fun pickPlaces(results: TripResults): List<Pick<PlaceToSee>> {
        val section = results.placesToSee
        val free = listOrEmpty(section?.isError, section?.results).filter { !it.name.isNullOrEmpty() && it.admissionFeeUsd == 0.0 }
        val sorted = free.sortBy(desc { it.rating.known() })
        return takePerCity(sorted, PLACES_PER_CITY, PLACES, ::placeCity).map { Pick(it, PickReason.FreeEntry) }
    }

    /** Places carry `city` on multi-city trips; the model keeps it only in the raw JSON. */
    private fun placeCity(p: PlaceToSee): String? = (p.raw["city"] as? JsonPrimitive)?.takeIf { it.isString }?.content

    // ---- SIM ----

    private fun tripDays(search: TravelSearchRequest?): Int? = daysBetween(search?.departureDate, search?.returnDate)?.let { it.toInt() + 1 }

    private fun pickSim(results: TripResults, search: TravelSearchRequest?): Pick<SimPlan>? {
        val section = results.sim
        val plans = listOrEmpty(section?.isError, section?.plans)
        val priced = plans.filter { (it.provider != null || it.planName != null) && (it.priceUsd.known() ?: 0.0) > 0 }
        if (priced.isEmpty()) return null
        val days = tripDays(search)
        val cheapest = { list: List<SimPlan> -> list.sortBy(asc { it.priceUsd }).first() }
        if (days != null) {
            val covering = priced.filter { (it.validityDays ?: Int.MAX_VALUE) >= days }
            if (covering.isNotEmpty()) return Pick(cheapest(covering), PickReason.CoversTrip(days))
        }
        return Pick(cheapest(priced), PickReason.CheapestPlan)
    }

    // ---- builder ----

    /** Builds the package, or null without any flight or hotel. [caps] (budget planner) limit the hotel/flight pools. */
    fun build(results: TripResults, search: TravelSearchRequest?, caps: PackageCaps? = null): BestValue? {
        val (single, legs) = pickFlights(results.flights, caps?.flight)
        val first = firstStop(results, search)
        val hotelCands = capped(hotelCandidates(results, first), caps?.hotel)
        var hotel = pickHotel(hotelCands, first)
        if (single == null && legs.isEmpty() && hotel == null) return null

        val activities = pickActivities(results)
        val tours = pickWalkingTours(results)
        val places = pickPlaces(results)
        val sim = pickSim(results, search)

        fun selectionsFor(h: Pick<Hotel>?) =
            PlanSelections(
                flight = single?.item,
                flights = legs.map { it.item },
                hotel = h?.item,
                activities = activities.map { it.item },
                walkingTours = tours.map { it.item },
                placesToSee = places.map { it.item },
                sim = sim?.item,
            )

        var selections = selectionsFor(hotel)
        var total = computePlanCost(selections, search)

        // Budget fit: only the hotel is ever swapped
        val budget = search?.budgetUsd.known()
        val overBudget = budget != null && budget > 0 && total > budget
        if (hotel != null && overBudget) {
            val cost = { h: Hotel -> computePlanCost(PlanSelections(hotel = h), search) }
            val cheaper = hotelCands.filter { review(it) == null || review(it)!! >= MIN_CHEAP_HOTEL_REVIEW }.sortBy(asc { cost(it) }).firstOrNull()
            if (cheaper != null && cost(cheaper) < cost(hotel.item)) {
                hotel = Pick(cheaper, PickReason.CheaperStay)
                selections = selectionsFor(hotel)
                total = computePlanCost(selections, search)
            }
        }

        val items =
            buildList {
                single?.let { add(PackageItem(PackageItemType.FLIGHT, PlanItem.FlightItem(it.item), it.reason)) }
                legs.forEach { add(PackageItem(PackageItemType.FLIGHTS, PlanItem.FlightItem(it.item), it.reason, it.city)) }
                hotel?.let { add(PackageItem(PackageItemType.HOTEL, PlanItem.HotelItem(it.item), it.reason)) }
                activities.forEach { add(PackageItem(PackageItemType.ACTIVITIES, PlanItem.ActivityItem(it.item), it.reason, it.item.city)) }
                tours.forEach { add(PackageItem(PackageItemType.WALKING_TOURS, PlanItem.WalkingTourItem(it.item), it.reason, it.item.city)) }
                places.forEach { add(PackageItem(PackageItemType.PLACES_TO_SEE, PlanItem.PlaceItem(it.item), it.reason, placeCity(it.item))) }
                sim?.let { add(PackageItem(PackageItemType.SIM, PlanItem.SimItem(it.item), it.reason)) }
            }
        return BestValue(items, selections, total, getBudgetStatus(total, search?.budgetUsd))
    }

    // ---- apply / compare ----

    private fun sameHotel(a: Hotel?, b: Hotel?) = a?.name != null && a.name == b?.name

    private fun sameSim(a: SimPlan?, b: SimPlan?) =
        a != null && b != null && (a.provider != null || a.planName != null) && a.provider == b.provider && a.planName == b.planName

    private fun <T> addMissing(existing: List<T>, incoming: List<T>, same: (T, T) -> Boolean): List<T> {
        if (incoming.isEmpty()) return existing
        val added = mutableListOf<T>()
        for (x in incoming) if ((existing + added).none { same(it, x) }) added += x
        return existing + added
    }

    private fun <T> keyed(key: (T) -> String?): (T, T) -> Boolean = { a, b -> key(a) != null && key(a) == key(b) }

    /** Adds the package to [selections]: replaces flight/hotel/SIM and covered legs, adds list items without duplicates. */
    fun apply(selections: PlanSelections, pkg: BestValue): PlanSelections {
        val p = pkg.selections
        var next = selections
        if (p.flight != null) next = next.copy(flight = p.flight)
        if (p.flights.isNotEmpty()) {
            val covered = p.flights.map { it.legIndex }.toSet()
            next = next.copy(flights = (selections.flights.filter { it.legIndex !in covered } + p.flights).sortedBy { it.legIndex ?: 0 })
        }
        if (p.hotel != null) next = next.copy(hotel = p.hotel)
        if (p.sim != null) next = next.copy(sim = p.sim)
        return next.copy(
            activities = addMissing(selections.activities, p.activities, keyed { it.name }),
            placesToSee = addMissing(selections.placesToSee, p.placesToSee, keyed { it.name }),
            walkingTours = addMissing(selections.walkingTours, p.walkingTours, keyed { it.url }),
        )
    }

    /** True when every package item is already in [selections]. */
    @Suppress("ReturnCount")
    fun isApplied(selections: PlanSelections, pkg: BestValue): Boolean {
        val p = pkg.selections
        val s = selections
        if (p.flight != null && !sameFlight(s.flight, p.flight)) return false
        if (!p.flights.all { f -> s.flights.any { sameFlight(it, f) } }) return false
        if (p.hotel != null && !sameHotel(s.hotel, p.hotel)) return false
        if (p.sim != null && !sameSim(s.sim, p.sim)) return false
        val nameSame = keyed<Activity> { it.name }
        val placeSame = keyed<PlaceToSee> { it.name }
        val tourSame = keyed<WalkingTour> { it.url }
        return p.activities.all { x -> s.activities.any { nameSame(it, x) } } &&
            p.placesToSee.all { x -> s.placesToSee.any { placeSame(it, x) } } &&
            p.walkingTours.all { x -> s.walkingTours.any { tourSame(it, x) } }
    }

    /** Picks the user already made that applying would replace. */
    fun conflicts(selections: PlanSelections, pkg: BestValue): List<PackageConflict> {
        val p = pkg.selections
        val s = selections
        val flightClash =
            (p.flight != null && s.flight != null && !sameFlight(s.flight, p.flight)) ||
                p.flights.any { f -> s.flights.any { it.legIndex == f.legIndex && !sameFlight(it, f) } }
        return buildList {
            if (flightClash) add(PackageConflict.FLIGHT)
            if (p.hotel != null && s.hotel != null && !sameHotel(s.hotel, p.hotel)) add(PackageConflict.HOTEL)
            if (p.sim != null && s.sim != null && !sameSim(s.sim, p.sim)) add(PackageConflict.SIM)
        }
    }
}
