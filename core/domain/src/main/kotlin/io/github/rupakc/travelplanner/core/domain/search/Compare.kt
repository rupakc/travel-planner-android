package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/** Compare takes 2–3 destinations (web Compare mode). */
const val MIN_COMPARE_DESTINATIONS = 2
const val MAX_COMPARE_DESTINATIONS = 3

/**
 * Port of web `buildCompareRequests` (utils/compare.js): one request per destination,
 * sharing every field of [shared] (built like the known search, so "Plan this trip" can
 * run the exact compared request). Blank and case-insensitive duplicate destinations are
 * dropped, names are trimmed, multi-city fields are cleared.
 */
fun buildCompareRequests(shared: SearchForm, destinations: List<String>, locale: Locale): List<TravelSearchRequest> {
    val seen = HashSet<String>()
    val unique =
        destinations.map(String::trim).filter { it.isNotEmpty() && seen.add(it.lowercase(Locale.ROOT)) }
    val base = buildSearchRequest(shared.copy(mustInclude = null, importSource = null), emptyList(), locale)
    return unique.map { base.copy(destination = it, destinations = null, destinationNights = null) }
}

/** The CompareTable rows, in display order; [direction] says which value is best. */
enum class CompareRow(val direction: Direction?) {
    ESTIMATE(Direction.MIN),
    FLIGHT(Direction.MIN),
    HOTEL(Direction.MIN),
    WEATHER(Direction.MIN),
    VISA(null),
    EASE(Direction.MAX),
    SAFETY(Direction.MAX),
    ENGLISH(Direction.MAX),
    DAILY(Direction.MIN),
    ;

    enum class Direction { MIN, MAX }

    /** Web `ROW_RULES` extractor: the comparable number, or null. A partial total is never comparable. */
    fun value(summary: CompareSummary): Double? =
        when (this) {
            ESTIMATE -> summary.estimate?.takeUnless { it.partial == true }?.totalUsd
            FLIGHT -> summary.flight?.priceUsd
            HOTEL -> summary.hotel?.typicalNightlyUsd
            WEATHER -> summary.weather?.poorDays?.toDouble()
            VISA -> null
            EASE -> summary.ease?.total?.toDouble()
            SAFETY -> summary.ease?.safety?.toDouble()
            ENGLISH -> summary.ease?.english?.toDouble()
            DAILY -> summary.dailyCostUsd
        }?.takeIf { it.isFinite() }
}

/**
 * Port of web `bestByRow`: for each numeric row, the column indexes holding the best value
 * (ties mark every tied column). Rows with fewer than two known values are omitted.
 * [summaries] is indexed by column; null = not loaded / failed.
 */
fun bestByRow(summaries: List<CompareSummary?>): Map<CompareRow, Set<Int>> =
    CompareRow.entries.mapNotNull { row ->
        val direction = row.direction ?: return@mapNotNull null
        val values = summaries.mapIndexedNotNull { i, s -> s?.let(row::value)?.let { i to it } }
        if (values.size < MIN_COMPARE_DESTINATIONS) return@mapNotNull null
        val best = if (direction == CompareRow.Direction.MIN) values.minOf { it.second } else values.maxOf { it.second }
        row to values.filter { it.second == best }.map { it.first }.toSet()
    }.toMap()

/** Web `fmtUsd`: "$1,234" (rounded; "1234 US$" for a Spanish [locale]), "—" when unknown. */
fun fmtUsd(value: Double?, locale: Locale = Locale.US): String {
    if (value == null || !value.isFinite()) return DASH
    return io.github.rupakc.travelplanner.core.domain.format.formatWholeUsd(value, locale)
}

/** Web `fmtDuration`: "2h 5m", null when unknown or not positive. */
fun fmtDuration(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    return "${floor(minutes / MINUTES_PER_HOUR).toInt()}h ${minutes % MINUTES_PER_HOUR.toInt()}m"
}

const val DASH = "—"
private const val MINUTES_PER_HOUR = 60.0

/** Why a compare column failed (web `compareErrorReason`); the UI turns the generic cases into its own copy. */
sealed interface CompareErrorReason {
    /** The API's string `detail`, shown as-is. */
    data class Detail(val text: String) : CompareErrorReason

    /** A 4xx without a readable detail: "Please check the trip details". */
    data object CheckDetails : CompareErrorReason

    /** 5xx, network or anything else: "Service unavailable — try again". */
    data object Unavailable : CompareErrorReason
}

/**
 * The backend's plain string `detail`, as the web shows it (utils/httpError.js: only a non-blank string
 * detail). Never a FastAPI validation list (the mapper puts its first message in `Client.message`), a
 * structured `{code, message}` detail, or exception text from decoding / unknown failures.
 */
fun backendDetail(error: AppError?): String? {
    val text =
        when (error) {
            is AppError.Client -> error.message.takeIf { error.fieldErrors.isEmpty() && error.code == null }
            is AppError.Server -> error.message.takeIf { error.code == null }
            is AppError.NotFound -> error.message.takeIf { error.code == null }
            is AppError.RateLimited, is AppError.Forbidden, is AppError.Unauthorized -> error.message
            else -> null
        }
    return text?.trim()?.ifEmpty { null }
}

/** Port of web `compareErrorReason`: string detail first, then by status class (4xx → check details, else unavailable). */
fun compareErrorReason(error: AppError?): CompareErrorReason {
    backendDetail(error)?.let { return CompareErrorReason.Detail(it) }
    return when (error) {
        is AppError.Client, is AppError.NotFound, is AppError.Forbidden, is AppError.Unauthorized, is AppError.RateLimited -> {
            CompareErrorReason.CheckDetails
        }

        else -> {
            CompareErrorReason.Unavailable
        }
    }
}
