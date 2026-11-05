package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.domain.plan.DEFAULT_TRIP_NIGHTS
import io.github.rupakc.travelplanner.core.domain.plan.daysBetween
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.Hotel
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Port of `frontend/src/utils/budgetPlanner.js`: splits the trip budget into
 * Flights / Stay / Activities / Everything else and checks the loaded results
 * against each share. Units follow My Plan: flight `price_usd` per person, hotel
 * nightly × nights, activity `price_usd`. Unknown prices never fit and are never "over".
 */
object BudgetPlanner {
    private const val ACTIVITY_SHARE = 0.15
    private const val STEP = 10.0
    private const val EPSILON = 1e-9
    private const val CENTS = 100.0

    private fun fitsWithin(amount: Double, share: Double) = amount <= share + EPSILON

    private fun ceilStep(n: Double) = ceil((n - EPSILON) / STEP) * STEP

    private fun floorStep(n: Double) = floor((n + EPSILON) / STEP) * STEP

    private fun roundStep(n: Double) = jsRound(n / STEP) * STEP

    private fun positive(v: Double?): Double? = v?.takeIf { it.isFinite() && it > 0 }

    /** Nights between the trip dates (min 1), default 7. */
    fun tripNights(search: TravelSearchRequest?): Int =
        daysBetween(search?.departureDate, search?.returnDate)?.let { max(1L, it).toInt() } ?: DEFAULT_TRIP_NIGHTS

    /** One flight leg's fares; a one-way/round-trip search is one leg. */
    private fun flightLegs(section: FlightsData?): List<List<Flight>> {
        if (section == null || section.isError) return emptyList()
        if (section.legs.isNotEmpty()) return section.legs.map { it.results }
        return listOf(section.results)
    }

    private fun cheapestFare(fares: List<Flight>): Double? = fares.mapNotNull { positive(it.priceUsd) }.minOrNull()

    private fun defaultsWithin(flights: Double, stay: Double, activities: Double, total: Double): BudgetAllocation {
        val f = min(flights, total)
        val s = min(stay, floorStep(total - f))
        val a = min(activities, floorStep(total - f - s))
        return BudgetAllocation(f, s, a, total - f - s - a)
    }

    /** Suggested split, or null without a positive budget. */
    fun defaultAllocation(search: TravelSearchRequest?, results: TripResults): BudgetAllocation? {
        val budget = positive(search?.budgetUsd) ?: return null
        val total = jsRound(budget)
        val legCheapest = flightLegs(results.flights).mapNotNull(::cheapestFare)
        val flights = if (legCheapest.isNotEmpty()) ceilStep(legCheapest.sum()) else 0.0
        val nightlies = results.hotels.okResults.mapNotNull { positive(it.pricePerNightUsd) }
        val stay = if (nightlies.isNotEmpty()) ceilStep(median(nightlies) * tripNights(search)) else 0.0
        return defaultsWithin(flights, stay, roundStep(total * ACTIVITY_SHARE), total)
    }

    /** Move one category; `other` absorbs the difference and never drops below 0. Total stays constant. */
    fun reallocate(allocation: BudgetAllocation, key: BudgetCategory, value: Double, total: Double = allocation.total): BudgetAllocation {
        val rest = BudgetCategory.entries.filter { it != key }.sumOf { allocation[it] }
        val v = min(max(0.0, jsRound(value)), max(0.0, total - rest))
        return allocation.with(key, v).copy(other = total - rest - v)
    }

    /** The user edited the total: `other` absorbs the change first, then categories scale down. */
    fun withTotal(allocation: BudgetAllocation, total: Double): BudgetAllocation {
        val t = max(0.0, if (total.isFinite()) jsRound(total) else 0.0)
        var (f, s, a) = Triple(allocation.flights, allocation.stay, allocation.activities)
        val used = f + s + a
        if (used > t) {
            val scale = t / used
            f = floor(f * scale)
            s = floor(s * scale)
            a = floor(a * scale)
        }
        return BudgetAllocation(f, s, a, t - (f + s + a))
    }

    private fun fitFlights(section: FlightsData?, share: Double): FitSet {
        val fits = linkedSetOf<String>()
        val over = linkedSetOf<String>()
        val legs = flightLegs(section)
        val multi = section?.legs?.isNotEmpty() == true
        val cheapest = legs.map(::cheapestFare)
        val cheapestTotal = cheapest.sumOf { it ?: 0.0 }
        legs.forEachIndexed { i, fares ->
            val legCheapest = cheapest[i] ?: return@forEachIndexed
            val cap = if (multi) share * legCheapest / cheapestTotal else share
            for (f in fares) {
                val p = positive(f.priceUsd) ?: continue
                (if (fitsWithin(p, cap)) fits else over).add(flightKey(f))
            }
        }
        return FitSet(fits, over)
    }

    private fun fitHotels(hotels: List<Hotel>, share: Double, nights: Int): FitSet {
        val fits = linkedSetOf<String>()
        val over = linkedSetOf<String>()
        for (h in hotels) {
            val name = h.name?.takeIf(String::isNotEmpty)
            val nightly = positive(h.pricePerNightUsd)
            if (name != null && nightly != null) (if (fitsWithin(nightly * nights, share)) fits else over).add(name)
        }
        return FitSet(fits, over)
    }

    private fun pickActivities(results: TripResults, share: Double): ActivityFit {
        data class Priced(val name: String, val index: Int, val price: Double, val score: Double?)
        val priced =
            results.activities.okResults.mapIndexedNotNull { i, a ->
                val name = a.name?.takeIf(String::isNotEmpty) ?: return@mapIndexedNotNull null
                val p = a.priceUsd?.takeIf { it.isFinite() && it >= 0 } ?: return@mapIndexedNotNull null
                Priced(name, i, p, a.similarityScore?.takeIf { it.isFinite() })
            }
        val sorted =
            priced.sortedWith { x, y ->
                when {
                    x.score != y.score && x.score == null -> 1
                    x.score != y.score && y.score == null -> -1
                    x.score != y.score -> y.score!!.compareTo(x.score!!)
                    else -> x.price.compareTo(y.price).takeIf { it != 0 } ?: x.index.compareTo(y.index)
                }
            }
        val picked = linkedSetOf<String>()
        var spent = 0.0
        for (x in sorted) {
            if (x.price == 0.0) {
                picked += x.name
            } else if (fitsWithin(spent + x.price, share)) {
                picked += x.name
                spent += x.price
            }
        }
        return ActivityFit(picked, jsRound(spent * CENTS) / CENTS)
    }

    /** Which loaded flights, hotels and activities fit [allocation]. */
    fun fitResults(results: TripResults, allocation: BudgetAllocation, search: TravelSearchRequest?): BudgetFit {
        val nights = tripNights(search)
        return BudgetFit(
            hotels = fitHotels(results.hotels.okResults, allocation.stay, nights),
            flights = fitFlights(results.flights, allocation.flights),
            activities = pickActivities(results, allocation.activities),
            nights = nights,
        )
    }

    /** Predicates for [BestValuePackage.build]'s caps. */
    fun capsFromFit(fit: BudgetFit): PackageCaps =
        PackageCaps(hotel = { it.name in fit.hotels.fits }, flight = { flightKey(it) in fit.flights.fits })
}

/** JavaScript `Math.round` (half rounds up, also for negatives). */
internal fun jsRound(v: Double): Double = floor(v + HALF)

private const val HALF = 0.5

enum class BudgetCategory { FLIGHTS, STAY, ACTIVITIES }

/** A budget split in USD per person; [total] = sum of all four. */
data class BudgetAllocation(val flights: Double, val stay: Double, val activities: Double, val other: Double) {
    val total: Double get() = flights + stay + activities + other

    operator fun get(key: BudgetCategory): Double =
        when (key) {
            BudgetCategory.FLIGHTS -> flights
            BudgetCategory.STAY -> stay
            BudgetCategory.ACTIVITIES -> activities
        }

    fun with(key: BudgetCategory, value: Double): BudgetAllocation =
        when (key) {
            BudgetCategory.FLIGHTS -> copy(flights = value)
            BudgetCategory.STAY -> copy(stay = value)
            BudgetCategory.ACTIVITIES -> copy(activities = value)
        }

    /** Whole dollars for display. */
    fun rounded(key: BudgetCategory): Long = this[key].roundToLong()
}

/** Items that fit / are over a share (keys: hotel name or [flightKey]). */
data class FitSet(val fits: Set<String>, val over: Set<String>) {
    val count: Int get() = fits.size
    val total: Int get() = fits.size + over.size
}

data class ActivityFit(val picked: Set<String>, val spent: Double)

data class BudgetFit(val hotels: FitSet, val flights: FitSet, val activities: ActivityFit, val nights: Int)

/** Optional pool limits for the best-value package (from the budget planner). */
data class PackageCaps(val hotel: ((Hotel) -> Boolean)? = null, val flight: ((Flight) -> Boolean)? = null)
