package io.github.rupakc.travelplanner.feature.plan.model

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.model.plan.Plan
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Where a trip sits relative to today (drives the Upcoming / Past filter and the date badge). */
internal enum class TripTiming { UPCOMING, ONGOING, PAST, UNDATED }

/**
 * A saved plan as My trips shows it. Everything is read from the plan's own `search_data`
 * and `selections` (raw JSON, so plans saved from chat or the web with other keys still work).
 */
@Immutable
internal data class TripSummary(
    val id: Long,
    val name: String,
    val destination: String?,
    val origin: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val travellers: Int?,
    val budgetUsd: Double?,
    val interests: List<String>,
    val shareToken: String?,
    val updatedAt: LocalDateTime?,
    val itemCount: Int,
    val totalCostUsd: Double,
    val timing: TripTiming,
) {
    val isShared: Boolean get() = shareToken != null

    companion object {
        private val SQLITE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun from(plan: Plan, today: LocalDate): TripSummary {
            val sd = plan.searchData
            val start = sd.string("departure_date")?.let(::parseDate)
            val end = sd.string("return_date")?.let(::parseDate) ?: start
            return TripSummary(
                id = plan.id,
                name = plan.name,
                destination = destinationLabel(sd),
                origin = sd.string("origin"),
                startDate = start,
                endDate = end,
                travellers = sd.number("num_travelers")?.toInt()?.takeIf { it > 0 },
                budgetUsd = sd.number("budget_usd")?.takeIf { it > 0 },
                interests = (sd["interests"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty(),
                shareToken = plan.shareToken,
                updatedAt = plan.updatedAt?.let { runCatching { LocalDateTime.parse(it, SQLITE_TIME) }.getOrNull() },
                itemCount = plan.selections.count,
                totalCostUsd = PlanCost.total(plan.selections, sd.string("departure_date"), sd.string("return_date")),
                timing = timing(start, end, today),
            )
        }

        /** Web share page `destinationLabel`: multi-city stops joined with arrows, else the destination. */
        fun destinationLabel(sd: JsonObject): String? {
            val stops = (sd["destinations"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
            return if (stops.size > 1) stops.joinToString(" → ") else sd.string("destination")
        }

        fun timing(start: LocalDate?, end: LocalDate?, today: LocalDate): TripTiming =
            when {
                start == null -> TripTiming.UNDATED
                start.isAfter(today) -> TripTiming.UPCOMING
                (end ?: start).isBefore(today) -> TripTiming.PAST
                else -> TripTiming.ONGOING
            }

        private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.take(10)) }.getOrNull()

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank)

        private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.intOrNull?.toDouble() }
    }
}

/** My trips filter chips. */
internal enum class TripFilter { UPCOMING, PAST, ALL }

/** My trips sort order. */
internal enum class TripSort { TRIP_DATE, UPDATED }

/** Pure search / filter / sort for My trips (unit-tested). */
internal object TripQuery {
    fun apply(trips: List<TripSummary>, query: String, filter: TripFilter, sharedOnly: Boolean, sort: TripSort): List<TripSummary> {
        val needle = fold(query.trim())
        val matching =
            trips.filter { trip ->
                (needle.isEmpty() || fold(trip.name).contains(needle) || fold(trip.destination.orEmpty()).contains(needle)) &&
                    (!sharedOnly || trip.isShared) &&
                    when (filter) {
                        TripFilter.UPCOMING -> trip.timing == TripTiming.UPCOMING || trip.timing == TripTiming.ONGOING
                        TripFilter.PAST -> trip.timing == TripTiming.PAST
                        TripFilter.ALL -> true
                    }
            }
        return when (sort) {
            TripSort.UPDATED -> {
                matching.sortedWith(compareByDescending<TripSummary> { it.updatedAt }.thenByDescending { it.id })
            }

            // Upcoming: soonest first. Past / All: most recent trip first; undated trips last.
            TripSort.TRIP_DATE -> {
                if (filter == TripFilter.UPCOMING) {
                    matching.sortedWith(compareBy<TripSummary, LocalDate?>(nullsLast()) { it.startDate }.thenByDescending { it.id })
                } else {
                    matching.sortedWith(compareByDescending<TripSummary, LocalDate?>(nullsFirst()) { it.startDate }.thenByDescending { it.id })
                }
            }
        }
    }

    /** Case- and accent-insensitive ("Malaga" finds "Málaga"). */
    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

    private val MARKS = Regex("\\p{Mn}+")
}
