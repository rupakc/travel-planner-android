package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.search.FareCalendarDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Port of `frontend/src/utils/fareCalendar.js` (dates as ISO strings, UTC day math).
 * Text formatting lives in the UI (localized); these return numbers and data.
 */
object FareCalendarRules {
    private fun date(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Both trip dates moved by [offset] days (trip length unchanged). */
    fun shiftDates(departure: String, returnDate: String?, offset: Long): Pair<String, String?> =
        (date(departure)?.plusDays(offset)?.toString() ?: departure) to returnDate?.let { date(it)?.plusDays(offset)?.toString() ?: it }

    /** Whole days from [from] to [to] (negative when [to] is earlier); 0 when unparseable. */
    fun dayOffset(from: String, to: String): Long {
        val a = date(from) ?: return 0
        val b = date(to) ?: return 0
        return ChronoUnit.DAYS.between(a, b)
    }

    /** Departure date of the cheapest priced entry, or null. Ties keep the first. */
    fun cheapestDeparture(dates: List<FareCalendarDate>): String? {
        var best: FareCalendarDate? = null
        for (d in dates) {
            val p = d.cheapestUsd?.takeIf { it.isFinite() } ?: continue
            if (best == null || p < best.cheapestUsd!!) best = d
        }
        return best?.departureDate
    }

    /** Price difference of [entry] vs [selected], or null when either is unpriced. */
    fun fareDelta(entry: FareCalendarDate?, selected: FareCalendarDate?): Double? {
        val a = entry?.cheapestUsd?.takeIf { it.isFinite() } ?: return null
        val b = selected?.cheapestUsd?.takeIf { it.isFinite() } ?: return null
        return a - b
    }

    /** Rounded delta for display: 0 = "same"; sign drives "−$45" / "+$30". Null for unknown. */
    fun roundedDelta(delta: Double?): Long? = delta?.takeIf { it.isFinite() }?.let { jsRound(it).toLong() }

    /** Web `formatUsd`: whole dollars with thousands separators ("$1,480"). */
    fun formatUsd(amount: Double): String = "$" + "%,d".format(Locale.US, jsRound(amount).toLong())

    /** Web `formatDelta`: "−$45" (real minus sign) / "+$30" / "same"; "" for unknown. */
    fun formatDelta(delta: Double?): String {
        val rounded = roundedDelta(delta) ?: return ""
        if (rounded == 0L) return "same"
        return (if (rounded < 0) "−" else "+") + formatUsd(kotlin.math.abs(rounded).toDouble())
    }

    /** Web `formatDay`: "Tue 3 Nov" (the UI passes the user's locale). */
    fun formatDay(iso: String, locale: Locale = Locale.ENGLISH): String =
        date(iso)?.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) ?: iso

    /** Web `formatTripRange`: "Tue 3 Nov – Tue 10 Nov", just the departure for one-way trips. */
    fun formatTripRange(departure: String, returnDate: String?, locale: Locale = Locale.ENGLISH): String =
        if (returnDate != null) "${formatDay(departure, locale)} – ${formatDay(returnDate, locale)}" else formatDay(departure, locale)

    /** Web `replanPrompt` (English; the app shows the localized `results_fare_replan_prompt`). */
    fun replanPrompt(departure: String, returnDate: String?): String =
        "Re-plan for ${formatTripRange(departure, returnDate)}? This runs a new search."

    /** Web `priceInsightsChip(...).text` in English: "Fares are low right now · typical $420–$610". */
    fun priceInsightsText(insight: PriceInsight): String {
        val range =
            if (insight.typicalLow != null &&
                insight.typicalHigh != null
            ) {
                " · typical ${formatUsd(insight.typicalLow)}–${formatUsd(insight.typicalHigh)}"
            } else {
                ""
            }
        return "Fares are ${insight.level.wire} right now$range"
    }

    /** Google Flights' own verdict (`low|typical|high`) and its typical range, or null when unknown. */
    fun priceInsights(insights: JsonObject?): PriceInsight? {
        val level = (insights?.get("price_level") as? JsonPrimitive)?.contentOrNull ?: return null
        val kind = PriceLevel.entries.firstOrNull { it.wire == level } ?: return null
        val range = (insights["typical_price_range"] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.doubleOrNull }
        val typical = range?.takeIf { it.size == 2 && it.all { v -> v != null && v.isFinite() } }
        return PriceInsight(kind, typical?.get(0), typical?.get(1))
    }

    /** Calendar days from [today] to [iso]; 0 for past, missing or unparseable. */
    fun daysUntil(iso: String?, today: LocalDate): Long {
        val d = date(iso) ?: return 0
        return max(0L, ChronoUnit.DAYS.between(today, d))
    }
}

enum class PriceLevel(val wire: String) { LOW("low"), TYPICAL("typical"), HIGH("high") }

/** "Fares are {level} right now · typical $lo–$hi". */
data class PriceInsight(val level: PriceLevel, val typicalLow: Double?, val typicalHigh: Double?)
