package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.CreateFareWatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.FareWatchMatchRequest
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor

// Pure helpers for fare watches (port of frontend/src/utils/fareWatch.js). Wording lives in
// string resources: these return values the UI turns into text.

/** The trip fields a fare watch needs (web `tripFromSearch` / `tripFromPlan`). */
data class WatchTrip(
    val origin: String,
    val destination: String,
    val departureDate: String,
    val returnDate: String?,
    val numTravelers: Int,
    val destinations: List<String>? = null,
    val planId: Long? = null,
) {
    fun toCreateRequest(alertMode: String, targetPrice: Double?, flexible: Boolean, email: Boolean): CreateFareWatchRequest =
        CreateFareWatchRequest(
            planId = planId,
            origin = origin,
            destination = destination,
            destinations = destinations,
            departureDate = departureDate,
            returnDate = returnDate,
            numTravelers = numTravelers,
            alertMode = alertMode,
            targetPrice = targetPrice,
            flexibleDays = if (flexible) FLEXIBLE_DAYS else 0,
            emailEnabled = email,
        )

    fun toMatchRequest(): FareWatchMatchRequest =
        FareWatchMatchRequest(planId, origin, destination, destinations, departureDate, returnDate, numTravelers)

    companion object {
        const val FLEXIBLE_DAYS = 3

        fun from(request: TravelSearchRequest?): WatchTrip? =
            request?.let {
                WatchTrip(
                    origin = it.origin,
                    destination = it.destination,
                    departureDate = it.departureDate,
                    returnDate = it.returnDate?.takeIf(String::isNotBlank),
                    numTravelers = it.numTravelers.coerceAtLeast(1),
                    destinations = it.destinations,
                )
            }

        fun from(plan: Plan): WatchTrip? = from(plan.searchRequest)?.copy(planId = plan.id)
    }
}

/** Single-destination trip with both ends and a departure after [today]. */
fun isWatchableTrip(trip: WatchTrip?, today: LocalDate): Boolean {
    if (trip == null || trip.origin.isBlank() || trip.destination.isBlank()) return false
    if ((trip.destinations?.size ?: 0) > 1) return false
    val dep = parseIsoDate(trip.departureDate) ?: return false
    return dep.isAfter(today)
}

/** Cheapest positive flight price of a single-city flights result, or null (multi-city doesn't count). */
fun cheapestFlightPrice(flights: FlightsData?): Double? {
    if (flights == null || flights.legs.isNotEmpty()) return null
    return flights.results.mapNotNull { it.priceUsd }.filter { it.isFinite() && it > 0 }.minOrNull()
}

private fun cityOf(label: String?, code: String?): String =
    label.orEmpty().substringBefore(',').trim().ifEmpty { code.orEmpty().substringBefore(',') }

/** "Berlin → Lisbon". */
fun routeLabel(watch: FareWatch): String =
    "${cityOf(watch.originLabel, watch.originCode)} → ${cityOf(watch.destinationLabel, watch.destinationCode)}"

/** "Berlin → Lisbon" for a trip being watched (labels are what the search typed). */
fun routeLabel(trip: WatchTrip): String = "${cityOf(trip.origin, null)} → ${cityOf(trip.destination, null)}"

/** Card status chip (web `watchStatus`): shared by fare and price watches. */
enum class WatchStatusKey(val tone: Tone) {
    PENDING(Tone.INFO),
    WATCHING(Tone.INFO),
    DROPPED(Tone.GOOD),
    PAUSED(Tone.NEUTRAL),
    EXPIRED(Tone.NEUTRAL),
    PROBLEM(Tone.WARN),
}

/** Visual tone of a status or message; the UI maps it to design-system colours (never colour alone). */
enum class Tone { GOOD, NEUTRAL, WARN, INFO }

object WatchStatuses {
    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val ERROR = "error"
    const val EXPIRED = "expired"
}

fun watchStatus(status: String?, baseline: Double?, last: Double?): WatchStatusKey =
    when {
        status == WatchStatuses.PAUSED -> WatchStatusKey.PAUSED
        status == WatchStatuses.EXPIRED -> WatchStatusKey.EXPIRED
        status == WatchStatuses.ERROR -> WatchStatusKey.PROBLEM
        baseline == null -> WatchStatusKey.PENDING
        last != null && last < baseline - DROP_EPSILON -> WatchStatusKey.DROPPED
        else -> WatchStatusKey.WATCHING
    }

fun watchStatus(watch: FareWatch): WatchStatusKey = watchStatus(watch.status, watch.baselinePrice, watch.lastPrice)

fun watchStatus(watch: PriceWatch): WatchStatusKey = watchStatus(watch.status, watch.baselinePrice, watch.lastPrice)

enum class Direction { DOWN, UP, SAME }

/** Latest price vs the price when watching started; [amount] is the absolute change. */
data class PriceChange(val delta: Double, val direction: Direction) {
    val amount: Double get() = abs(delta)
}

/** Fare watches: whole dollars (web `priceChange`). */
fun priceChange(watch: FareWatch): PriceChange? {
    val base = watch.baselinePrice ?: return null
    val last = watch.lastPrice ?: return null
    val delta = jsRound(last - base).toDouble()
    return PriceChange(delta, directionOf(delta))
}

internal fun directionOf(delta: Double): Direction =
    when {
        delta == 0.0 -> Direction.SAME
        delta < 0 -> Direction.DOWN
        else -> Direction.UP
    }

/** A point of the sparkline: [x], [y] in the drawing box, and its [price]. */
data class SparkPoint(val x: Float, val y: Float, val price: Double)

/** Sparkline geometry (web `sparkGeometry`): higher price = higher on screen; [targetY] null without a target. */
data class SparkGeometry(val points: List<SparkPoint>, val min: Double, val max: Double, val targetY: Float?)

fun sparkGeometry(prices: List<Double?>, width: Float, height: Float, pad: Float = 2f, target: Double? = null): SparkGeometry? {
    val values = prices.filterNotNull().filter { it.isFinite() }
    if (values.isEmpty()) return null
    val scale = if (target != null && target.isFinite()) values + target else values
    val min = scale.min()
    val max = scale.max()
    val span = max - min
    val inner = height - pad * 2

    fun yOf(p: Double): Float = if (span == 0.0) height / 2 else round2(pad + (1 - (p - min) / span) * inner)
    val step = if (values.size > 1) width / (values.size - 1) else 0f
    val points =
        values.mapIndexed { i, price ->
            SparkPoint(x = if (values.size > 1) round2((i * step).toDouble()) else width, y = yOf(price), price = price)
        }
    return SparkGeometry(points, min, max, target?.takeIf { it.isFinite() }?.let(::yOf))
}

private fun round2(v: Double): Float = (jsRound(v * HUNDRED) / HUNDRED).toFloat()

/** Why a typed target was refused. */
enum class TargetError { NOT_A_PRICE, }

/**
 * Fare target check (web `checkTarget`): "$1,200" → 1200. [alreadyUnder] = today's [price] is
 * already at or under the target (note "Today's fare ($X) is already under this…").
 */
data class TargetCheck(val value: Double?, val error: TargetError?, val alreadyUnder: Boolean = false)

fun checkTarget(raw: String?, price: Double?): TargetCheck {
    val text = raw.orEmpty()
    val value = text.replace(Regex("[$,\\s]"), "").toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    if (text.isBlank() || value == null) return TargetCheck(null, TargetError.NOT_A_PRICE)
    return TargetCheck(value, null, alreadyUnder = price != null && value >= price)
}

/** Pre-filled target: Google's typical low if below today's price, else 10 % under today (rounded down to $5). */
fun defaultTarget(typicalLow: Double?, price: Double?): Double? {
    if (typicalLow != null && (price == null || typicalLow < price)) return roundDown5(typicalLow)
    return price?.let { roundDown5(it * TARGET_DISCOUNT) }
}

private fun roundDown5(v: Double): Double = floor(v / FIVE) * FIVE

/** Where today's price sits on Google's typical range, 0–100 (clamped); null without a usable range. */
fun rangePosition(low: Double?, high: Double?, price: Double?): Double? {
    if (low == null || high == null || price == null) return null
    if (high <= low) return null
    return ((price - low) / (high - low) * HUNDRED).coerceIn(0.0, HUNDRED)
}

/** Cheapest nearby dates from the latest check when they beat the trip's own (flexible watches). */
data class CheaperNearby(val departureDate: String, val saving: Double)

fun cheaperNearby(watch: FareWatch): CheaperNearby? {
    if ((watch.flexibleDays ?: 0) == 0) return null
    val latest = watch.history.lastOrNull { it.price != null } ?: return null
    val date = latest.cheapestDepartureDate ?: return null
    if (date == watch.departureDate) return null
    val price = latest.price ?: return null
    val cheapest = latest.cheapestPrice ?: return null
    if (cheapest >= price) return null
    return CheaperNearby(date, price - cheapest)
}

/**
 * A full search request for the watched trip (optionally its cheapest nearby dates), filled
 * from saved [preferences] (web `replanSearchData`). Null when the watch has no route or date.
 */
fun replanRequest(watch: FareWatch, preferences: Preferences?, cheapest: Boolean = false): TravelSearchRequest? {
    var dep = watch.departureDate
    var ret = watch.returnDate
    if (cheapest) {
        watch.history.lastOrNull { it.cheapestDepartureDate != null }?.let {
            dep = it.cheapestDepartureDate
            ret = it.cheapestReturnDate
        }
    }
    val origin = watch.originLabel?.takeIf(String::isNotBlank) ?: watch.originCode ?: return null
    val destination = watch.destinationLabel?.takeIf(String::isNotBlank) ?: watch.destinationCode ?: return null
    return replanFrom(origin, destination, dep ?: return null, ret, watch.numTravelers ?: 1, preferences)
}

internal fun replanFrom(
    origin: String,
    destination: String,
    departure: String,
    returnDate: String?,
    travellers: Int,
    preferences: Preferences?,
): TravelSearchRequest {
    val p = preferences ?: Preferences()
    val n = travellers.coerceAtLeast(1)
    return TravelSearchRequest(
        origin = origin,
        destination = destination,
        departureDate = departure,
        returnDate = returnDate,
        interests = p.interests,
        nationality = p.nationality,
        residencePermits = p.residencePermits,
        existingVisas = p.existingVisas,
        budgetUsd = null,
        numTravelers = n,
        adults = n,
        children = 0,
        seniors = 0,
        infants = 0,
        accessibilityNeeds = p.accessibilityNeeds,
    )
}

private const val DROP_EPSILON = 0.5
private const val HUNDRED = 100.0
private const val FIVE = 5.0
private const val TARGET_DISCOUNT = 0.9
