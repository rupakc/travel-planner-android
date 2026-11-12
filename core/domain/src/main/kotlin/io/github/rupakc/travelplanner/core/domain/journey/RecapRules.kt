package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.model.trips.Recap
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong

/** Pure rules of the trip recap screen. */
object RecapRules {
    /** Spent against a budget: [over] with the rounded absolute [amount] of the difference. */
    data class Spend(val spent: Double, val budget: Double?, val over: Boolean, val amount: Long)

    /** A point of the route scaled into the unit square (y grows downwards). */
    data class MapPoint(val x: Float, val y: Float)

    /** The distance, or null when the server has none (no coordinates) or it is not a positive number. */
    fun distanceKm(recap: Recap): Double? = recap.distanceKm?.takeIf { it.isFinite() && it > 0 }

    /** Average stars, or null when nothing was rated. */
    fun averageStars(recap: Recap): Double? = recap.avgStars?.takeIf { it.isFinite() && it > 0 && recap.ratedCount > 0 }

    /** The decimal-string amount (never a float on the wire) as a double; null when blank or malformed. */
    fun amount(text: String?): Double? = text?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { BigDecimal(it).toDouble() }.getOrNull() }

    /**
     * Spent against the budget. [spent] defaults to the server's `spent_usd`; null when there is no spend. Without a
     * positive budget nothing is over.
     */
    fun spend(recap: Recap): Spend? {
        val spent = amount(recap.spend.spentUsd) ?: return null
        val budget = recap.spend.budgetUsd?.takeIf { it.isFinite() && it > 0 }
        val diff = (budget ?: spent) - spent
        return Spend(spent, budget, over = budget != null && diff < 0, amount = abs(diff).roundToLong())
    }

    /** Only a trip with an end date can be completed, and only once. The server still decides (`409 recap_not_ready`). */
    fun showComplete(recap: Recap): Boolean = recap.completedAt == null && !recap.endDate.isNullOrBlank()

    /** The route to draw: the walked `route` when it has two points or more, else the planned places with coordinates. */
    fun routePoints(recap: Recap): List<Pair<Double, Double>> {
        val walked = recap.route.mapNotNull { p -> if (p.size >= 2 && valid(p[0], p[1])) p[0] to p[1] else null }
        if (walked.size >= 2) return walked
        val planned =
            recap.places.mapNotNull { p ->
                val lat = p.lat
                val lng = p.lng
                if (lat != null && lng != null && valid(lat, lng)) lat to lng else null
            }
        return if (planned.size >= 2) planned else walked.ifEmpty { planned }
    }

    private fun valid(lat: Double, lng: Double) = lat.isFinite() && lng.isFinite() && abs(lat) <= MAX_LAT && abs(lng) <= MAX_LNG

    /**
     * Fits [points] (lat, lng) into the unit square with equal scale on both axes (longitude shrunk by cos of the
     * mean latitude), centred; the result is empty for no points and the centre for a single one.
     */
    fun project(points: List<Pair<Double, Double>>): List<MapPoint> {
        if (points.isEmpty()) return emptyList()
        val k = cos(Math.toRadians(points.map { it.first }.average())).coerceAtLeast(MIN_COS)
        val xs = points.map { it.second * k }
        val ys = points.map { -it.first }
        val w = xs.max() - xs.min()
        val h = ys.max() - ys.min()
        val scale = maxOf(w, h)
        if (scale <= 0.0) return points.map { CENTRE }
        val ox = (1.0 - w / scale) / 2
        val oy = (1.0 - h / scale) / 2
        return points.indices.map { i -> MapPoint((ox + (xs[i] - xs.min()) / scale).toFloat(), (oy + (ys[i] - ys.min()) / scale).toFloat()) }
    }

    private const val MIN_COS = 0.05
    private val CENTRE = MapPoint(0.5f, 0.5f)
    private const val MAX_LAT = 90.0
    private const val MAX_LNG = 180.0
}
