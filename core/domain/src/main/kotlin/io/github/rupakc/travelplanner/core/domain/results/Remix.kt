package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.LocalDate

/**
 * Port of `frontend/src/utils/remix.js`: one-click "what if" transforms of a
 * search request plus before/after metric snapshots.
 */
enum class RemixPreset(val id: String) {
    /** 📅 "A week later": shift the whole trip 7 days, same length. */
    WEEK_LATER("week_later"),

    /** 💸 "Half the budget". */
    HALF_BUDGET("half_budget"),

    /** ✨ "Make it luxe": double the budget. */
    LUXE("luxe"),

    /** 🧘 "Slow it down": pace relaxed. */
    SLOWER_PACE("slower_pace"),

    /** ⚡ "Long weekend": 3 nights from the same start date. */
    LONG_WEEKEND("long_weekend"),
}

object Remix {
    private const val WEEK = 7L
    private const val LONG_WEEKEND_NIGHTS = 3L

    private fun date(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** The remixed request, or null when the preset does not apply (the web disables the chip). */
    fun apply(search: TravelSearchRequest, preset: RemixPreset): TravelSearchRequest? =
        when (preset) {
            RemixPreset.WEEK_LATER -> {
                date(search.departureDate)?.let { dep ->
                    search.copy(
                        departureDate = dep.plusDays(WEEK).toString(),
                        returnDate = date(search.returnDate)?.plusDays(WEEK)?.toString() ?: search.returnDate,
                    )
                }
            }

            RemixPreset.HALF_BUDGET -> {
                search.budgetUsd?.takeIf { it != 0.0 }?.let { search.copy(budgetUsd = jsRound(it / 2)) }
            }

            RemixPreset.LUXE -> {
                search.budgetUsd?.takeIf { it != 0.0 }?.let { search.copy(budgetUsd = jsRound(it * 2)) }
            }

            RemixPreset.SLOWER_PACE -> {
                search.copy(pace = TravelSearchRequest.PACE_RELAXED)
            }

            RemixPreset.LONG_WEEKEND -> {
                date(search.departureDate)?.let { search.copy(returnDate = it.plusDays(LONG_WEEKEND_NIGHTS).toString()) }
            }
        }

    /** Web `applyRemix(searchData, presetId)`: unknown ids and inapplicable presets return [search] unchanged. */
    fun apply(search: TravelSearchRequest, presetId: String): TravelSearchRequest =
        RemixPreset.entries.firstOrNull { it.id == presetId }?.let { apply(search, it) } ?: search

    /** Web `formatMetricValue`: "$1,235" for money, the plain number for counts, "—" for unknown. */
    fun formatMetricValue(value: Double?, isMoney: Boolean): String =
        when {
            value == null -> "—"
            isMoney -> "$" + "%,d".format(java.util.Locale.US, jsRound(value).toLong())
            value == Math.floor(value) -> value.toLong().toString()
            else -> value.toString()
        }

    /** Metrics captured before a remix (web `snapshotMetrics`). */
    fun snapshot(results: TripResults): RemixMetrics =
        RemixMetrics(
            minFlightUsd = results.flights?.results?.mapNotNull { it.priceUsd?.takeIf { p -> p.isFinite() && p > 0 } }?.minOrNull(),
            minHotelNightlyUsd = results.hotels?.results?.mapNotNull { it.pricePerNightUsd?.takeIf { p -> p.isFinite() && p > 0 } }?.minOrNull(),
            itineraryTotalUsd = results.itinerary?.totalEstimatedCostUsd?.takeIf { it.isFinite() && it > 0 },
            poorWeatherDays = results.weather?.let { w -> w.days.count { it.isPoor == true }.toDouble() },
        )

    /** Rows for metrics known before and after (web `diffMetrics`); lower is better for all four. */
    fun diff(before: RemixMetrics, after: RemixMetrics): List<MetricDiff> =
        RemixMetric.entries.mapNotNull { metric ->
            val b = before[metric] ?: return@mapNotNull null
            val a = after[metric] ?: return@mapNotNull null
            val delta = a - b
            MetricDiff(metric, b, a, delta, improved = if (delta == 0.0) null else delta < 0)
        }
}

/** Remix metrics; [isMoney] = USD, else a count. */
enum class RemixMetric(val isMoney: Boolean) { MIN_FLIGHT(true), MIN_HOTEL_NIGHTLY(true), ITINERARY_TOTAL(true), POOR_WEATHER_DAYS(false) }

data class RemixMetrics(
    val minFlightUsd: Double? = null,
    val minHotelNightlyUsd: Double? = null,
    val itineraryTotalUsd: Double? = null,
    val poorWeatherDays: Double? = null,
) {
    operator fun get(metric: RemixMetric): Double? =
        when (metric) {
            RemixMetric.MIN_FLIGHT -> minFlightUsd
            RemixMetric.MIN_HOTEL_NIGHTLY -> minHotelNightlyUsd
            RemixMetric.ITINERARY_TOTAL -> itineraryTotalUsd
            RemixMetric.POOR_WEATHER_DAYS -> poorWeatherDays
        }
}

/** One before/after row; [improved] null when unchanged. */
data class MetricDiff(val metric: RemixMetric, val before: Double, val after: Double, val delta: Double, val improved: Boolean?) {
    /** Web `direction`: same / down / up. */
    val direction: String get() =
        if (delta == 0.0) {
            "same"
        } else if (delta < 0) {
            "down"
        } else {
            "up"
        }
}
