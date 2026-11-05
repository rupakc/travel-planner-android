package io.github.rupakc.travelplanner.feature.results.sections.commerce

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonBlock
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.results.FareCalendarRules
import io.github.rupakc.travelplanner.core.domain.results.PriceLevel
import io.github.rupakc.travelplanner.core.model.search.FareCalendarDate
import io.github.rupakc.travelplanner.core.model.section.PricingAdvisorData
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.results.FareCalendarUi
import io.github.rupakc.travelplanner.feature.results.LoadStatus
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.ResultsEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlinx.serialization.json.JsonObject

private val SPARK_WIDTH = 180.dp
private val SPARK_HEIGHT = 44.dp
private val SPARK_PAD = 6.dp
private val SPARK_STROKE = 1.5.dp
private val SPARK_DOT = 3.dp
private val FARE_CELL_WIDTH = 84.dp
private val SELECTED_BORDER = 2.dp
private const val SPARK_DEFAULT_DAYS = 90

/**
 * Pricing advisor (inside Flights): sparkline of `trend_data` with a "Now" marker,
 * status and recommendation pills, confidence, "Why?" details and the web's
 * "Historical pattern estimate" footer. Hidden without trend data (web parity).
 */
@Composable
fun PricingAdvisorBanner(data: PricingAdvisorData, daysUntilDeparture: Long, modifier: Modifier = Modifier) {
    val points = data.trendData.filter { it.daysBefore != null && it.relativePrice?.isFinite() == true }
    if (points.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val tone =
        when (data.priceStatus) {
            "above_average" -> PillTone.Warning
            "below_average" -> PillTone.Positive
            else -> PillTone.Neutral
        }
    val bookNow = data.recommendation == "book_now"
    OutlinedCard(modifier.fillMaxWidth(), shape = TravelShape.ItemCard) {
        Column(Modifier.fillMaxWidth().padding(TravelTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Sparkline(points.map { it.daysBefore!! to it.relativePrice!! }, daysUntilDeparture)
            ChipRow {
                data.priceStatusLabel?.let { Pill(it, tone = tone) }
                Pill(
                    stringResource(if (bookNow) R.string.results_advisor_book_now else R.string.results_advisor_wait),
                    tone = if (bookNow) PillTone.Positive else PillTone.Warning,
                )
                data.confidence?.let { Text(stringResource(R.string.results_advisor_confidence, it), style = TravelTheme.typography.labelSmall) }
            }
            if (expanded) {
                data.recommendationDetail?.let { Text(it, style = TravelTheme.typography.bodySmall) }
                data.optimalBookingWindow?.let { Text(stringResource(R.string.results_advisor_window, it), style = TravelTheme.typography.bodySmall) }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.results_less else R.string.results_advisor_why))
            }
            HorizontalDivider(color = TravelTheme.colors.outlineVariant)
            Text(
                listOfNotNull(stringResource(R.string.results_advisor_footer), data.disclaimer).joinToString(" "),
                style = TravelTheme.typography.labelSmall,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** Web `PricingSparkline`: x = days before departure (90 → now), y = relative price; "Now" = closest point. */
@Composable
private fun Sparkline(points: List<Pair<Int, Double>>, daysUntil: Long) {
    val sorted = remember(points) { points.sortedByDescending { it.first } }
    val line = TravelTheme.colors.secondary
    val description = stringResource(R.string.results_advisor_sparkline_a11y)
    Column {
        Canvas(Modifier.width(SPARK_WIDTH).height(SPARK_HEIGHT).semantics { contentDescription = description }) {
            val pad = SPARK_PAD.toPx()
            val maxDb = sorted.first().first.takeIf { it > 0 } ?: SPARK_DEFAULT_DAYS
            val prices = sorted.map { it.second }
            val minP = prices.min()
            val range = (prices.max() - minP).takeIf { it != 0.0 } ?: 0.1

            fun x(d: Int) = pad + (1 - d.toFloat() / maxDb) * (size.width - 2 * pad)

            fun y(p: Double) = (size.height - pad - ((p - minP) / range) * (size.height - 2 * pad)).toFloat()
            val path = Path()
            sorted.forEachIndexed { i, (d, p) -> if (i == 0) path.moveTo(x(d), y(p)) else path.lineTo(x(d), y(p)) }
            drawPath(path, line, style = Stroke(width = SPARK_STROKE.toPx(), cap = StrokeCap.Round))
            val now = sorted.minBy { abs(it.first - daysUntil) }
            drawCircle(line.copy(alpha = 0.25f), radius = SPARK_DOT.toPx() * 5 / 3, center = Offset(x(now.first), y(now.second)))
            drawCircle(line, radius = SPARK_DOT.toPx(), center = Offset(x(now.first), y(now.second)))
        }
        Row(Modifier.width(SPARK_WIDTH)) {
            Text(
                stringResource(R.string.results_advisor_90d),
                style = TravelTheme.typography.labelSmall,
                color = TravelTheme.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.results_advisor_now), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

/** Google Flights' own verdict for the searched dates ("Fares are low right now · typical $X–$Y · Google Flights"). */
@Composable
fun PriceInsightsChip(insights: JsonObject?, modifier: Modifier = Modifier) {
    val chip = FareCalendarRules.priceInsights(insights) ?: return
    val level =
        stringResource(
            when (chip.level) {
                PriceLevel.LOW -> R.string.results_insight_low
                PriceLevel.TYPICAL -> R.string.results_insight_typical
                PriceLevel.HIGH -> R.string.results_insight_high
            },
        )
    val range =
        if (chip.typicalLow != null && chip.typicalHigh != null) {
            stringResource(R.string.results_insight_range, localPrice(chip.typicalLow), localPrice(chip.typicalHigh))
        } else {
            null
        }
    val tone =
        when (chip.level) {
            PriceLevel.LOW -> PillTone.Positive
            PriceLevel.TYPICAL -> PillTone.Neutral
            PriceLevel.HIGH -> PillTone.Warning
        }
    Pill(
        listOfNotNull(stringResource(R.string.results_insight, level), range, stringResource(R.string.results_insight_source)).joinToString(" · "),
        modifier,
        tone,
    )
}

/**
 * Opt-in strip of the cheapest live fare for departure ±3 days (same trip length);
 * picking another date asks before re-planning.
 */
@Composable
fun FareCalendarStrip(state: FareCalendarUi, onEvent: (ResultsEvent) -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth(), shape = TravelShape.ItemCard) {
        Column(Modifier.padding(all = TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(stringResource(R.string.results_fare_calendar), style = TravelTheme.typography.titleSmall)
            Text(stringResource(R.string.results_fare_calendar_sub), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
            when (state.status) {
                LoadStatus.IDLE, LoadStatus.LOADING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                        repeat(FARE_SKELETON_CELLS) { SkeletonBlock(Modifier.width(FARE_CELL_WIDTH / 2), height = FARE_CELL_WIDTH * 3 / 4) }
                    }
                }

                LoadStatus.ERROR -> {
                    Row(
                        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            state.error?.message ?: stringResource(R.string.results_fare_calendar_error),
                            style = TravelTheme.typography.bodySmall,
                            color = TravelTheme.extendedColors.warning,
                            modifier = Modifier.weight(1f),
                        )
                        TpButton(stringResource(R.string.results_retry), onClick = { onEvent(ResultsEvent.RetryFareCalendar) }, variant = TpButtonVariant.Text)
                    }
                }

                LoadStatus.DONE -> {
                    if (state.dates.isEmpty()) {
                        Text(stringResource(R.string.results_fare_calendar_empty), style = TravelTheme.typography.bodySmall)
                    } else {
                        FareCells(state.dates, onPick = { onEvent(ResultsEvent.PickFareDate(it)) })
                    }
                }
            }
            state.pending?.let { pending ->
                val locale = currentLocale()
                val range =
                    listOfNotNull(
                        pending.departureDate?.let {
                            formatDay(it, locale)
                        },
                        pending.returnDate?.let { formatDay(it, locale) },
                    ).joinToString(" – ")
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                    Text(stringResource(R.string.results_fare_replan_prompt, range), style = TravelTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                        TpButton(stringResource(R.string.results_cancel), onClick = {
                            onEvent(ResultsEvent.PickFareDate(null))
                        }, variant = TpButtonVariant.Text)
                        TpButton(stringResource(R.string.results_fare_replan), onClick = { onEvent(ResultsEvent.ConfirmFareReplan) })
                    }
                }
            }
        }
    }
}

private const val FARE_SKELETON_CELLS = 7

@Composable
private fun FareCells(dates: List<FareCalendarDate>, onPick: (FareCalendarDate) -> Unit) {
    val selected = dates.firstOrNull { it.isSelected == true }
    val cheapest = FareCalendarRules.cheapestDeparture(dates)
    val locale = currentLocale()
    val cheapestLabel = stringResource(R.string.results_fare_cheapest)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        items(dates, key = { it.departureDate ?: it.hashCode().toString() }, contentType = { "fare" }) { entry ->
            val isSelected = entry.isSelected == true
            val isCheapest = entry.departureDate == cheapest
            val delta = if (isSelected) null else FareCalendarRules.roundedDelta(FareCalendarRules.fareDelta(entry, selected))
            val deltaText =
                when {
                    isSelected -> stringResource(R.string.results_fare_yours)
                    delta == null -> ""
                    delta == 0L -> stringResource(R.string.results_fare_same)
                    delta < 0 -> stringResource(R.string.results_fare_less, localPrice(abs(delta).toDouble()))
                    else -> stringResource(R.string.results_fare_more, localPrice(delta.toDouble()))
                }
            val price = entry.cheapestUsd?.let { localPrice(it) } ?: "—"
            val day = entry.departureDate?.let { formatDay(it, locale) }.orEmpty()
            // Cheapest is said and shown in words, not by the green fill alone.
            val a11y = listOf(day, price, deltaText, if (isCheapest) cheapestLabel else "").filter(String::isNotEmpty).joinToString(", ")
            OutlinedCard(
                onClick = { if (!isSelected) onPick(entry) },
                modifier =
                    Modifier.widthIn(min = FARE_CELL_WIDTH).heightIn(min = TravelTheme.spacing.minTouchTarget).semantics {
                        contentDescription = a11y
                        this.selected = isSelected
                    },
                shape = TravelShape.Input,
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = if (isCheapest) TravelTheme.extendedColors.successContainer else TravelTheme.colors.surface,
                    ),
                border =
                    BorderStroke(
                        if (isSelected) SELECTED_BORDER else 1.dp,
                        if (isSelected) TravelTheme.colors.secondary else TravelTheme.colors.outlineVariant,
                    ),
            ) {
                Column(Modifier.fillMaxWidth().padding(all = TravelTheme.spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day, style = TravelTheme.typography.labelSmall)
                    Text(price, style = TravelTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (isCheapest) {
                        Text(
                            cheapestLabel,
                            style = TravelTheme.typography.labelSmall,
                            color = TravelTheme.extendedColors.onSuccessContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        deltaText,
                        style = TravelTheme.typography.labelSmall,
                        color =
                            if (delta != null &&
                                delta < 0
                            ) {
                                TravelTheme.extendedColors.success
                            } else {
                                TravelTheme.colors.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}

/** "Tue 3 Nov" in [locale] (the UI passes `currentLocale()`). */
internal fun formatDay(iso: String, locale: java.util.Locale): String = FareCalendarRules.formatDay(iso, locale)

/** Medium localized date in [locale] ("Dec 10, 2026" / "10 dic 2026"). */
internal fun formatDate(iso: String?, locale: java.util.Locale): String? =
    iso?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }.getOrDefault(it) }
