package io.github.rupakc.travelplanner.feature.alerts.story

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.alerts.AlertsErrorCode
import io.github.rupakc.travelplanner.core.model.alerts.PriceStory
import io.github.rupakc.travelplanner.core.model.alerts.StoryVerdict
import io.github.rupakc.travelplanner.core.model.alerts.alertsErrorCode

// Pure presentation helpers for the price story. The server decides the verdict, advice code and numbers; nothing here recomputes them.

/** A point with a known price. */
internal data class ChartPoint(val at: String?, val price: Double)

/** What the chart draws: the observed points (oldest first) and the value range including the band, target and lowest. */
internal data class ChartModel(
    val points: List<ChartPoint>,
    val min: Double,
    val max: Double,
    val typicalLow: Double?,
    val typicalHigh: Double?,
    val target: Double?,
    val lowest: Double?,
) {
    /** 0 (bottom) to 1 (top) for [value]; a flat series sits in the middle. */
    fun fraction(value: Double): Float = if (max <= min) HALF else ((value - min) / (max - min)).toFloat()

    /** 0 (left) to 1 (right) for point [index]. */
    fun xFraction(index: Int): Float = if (points.size <= 1) HALF else index.toFloat() / (points.size - 1)

    private companion object {
        const val HALF = 0.5f
    }
}

/** The chart for [story], or null with fewer than two observed points (a count line is shown instead). */
internal fun chartModel(story: PriceStory): ChartModel? {
    val points = story.points.mapNotNull { p -> p.price?.let { ChartPoint(p.at, it) } }
    if (points.size < 2) return null
    val typical = story.typical
    val low = typical?.low
    val high = typical?.high
    val extras = listOfNotNull(low, high, story.target, story.lowest?.price)
    val all = points.map { it.price } + extras
    return ChartModel(points, all.min(), all.max(), low, high.takeIf { low != null }, story.target, story.lowest?.price)
}

/** Number of observed points (those with a price). */
internal fun observedCount(story: PriceStory): Int = story.points.count { it.price != null }

/** The telemetry `verdict` enum: the server verdict, or `none`. */
internal fun telemetryVerdict(story: PriceStory): String = story.verdictKind.takeIf { it != StoryVerdict.UNKNOWN }?.wire ?: "none"

/** The point to start on and to jump to for "latest". */
internal fun latestIndex(model: ChartModel): Int = model.points.lastIndex

/** Steps [index] by [delta], staying inside the series. */
internal fun stepIndex(index: Int, delta: Int, size: Int): Int = (index + delta).coerceIn(0, (size - 1).coerceAtLeast(0))

/** The point under a touch at [x] of [width] pixels (nearest). */
internal fun indexAt(x: Float, width: Float, size: Int): Int =
    if (size <= 1 || width <= 0f) 0 else Math.round((x / width).coerceIn(0f, 1f) * (size - 1))

/** "Checked N days ago" shows only when the server gave an age. */
internal fun checkedAgeDays(story: PriceStory): Int? = story.checkedDaysAgo?.takeIf { it >= 0 }

/** Why an advice or story call failed, mapped from the server code; [UNKNOWN] shows the backend text. */
enum class TipFailure { LIMIT, BILLING, UNSUPPORTED, NO_DATA, FAILED, NOT_FOUND, OTHER }

/** The failure kind of [error] for the advice route. */
internal fun tipFailure(error: AppError): TipFailure =
    when (error.alertsErrorCode()) {
        AlertsErrorCode.AI_LIMIT -> TipFailure.LIMIT
        AlertsErrorCode.AI_BILLING -> TipFailure.BILLING
        AlertsErrorCode.ADVICE_UNSUPPORTED -> TipFailure.UNSUPPORTED
        AlertsErrorCode.ADVICE_NO_DATA -> TipFailure.NO_DATA
        AlertsErrorCode.ADVICE_FAILED -> TipFailure.FAILED
        AlertsErrorCode.WATCH_NOT_FOUND -> TipFailure.NOT_FOUND
        else -> TipFailure.OTHER
    }

/** A point's date in the UI [locale] ("12 Nov 2026"); empty when the server sent none or something unparseable. */
internal fun formatPointDate(at: String?, locale: java.util.Locale): String =
    runCatching {
        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
            .format(java.time.Instant.parse(at).atZone(java.time.ZoneOffset.UTC))
    }.getOrDefault("")
