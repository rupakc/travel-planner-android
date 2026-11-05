package io.github.rupakc.travelplanner.feature.alerts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AlertMode
import io.github.rupakc.travelplanner.core.domain.alerts.AnyWatch
import io.github.rupakc.travelplanner.core.domain.alerts.Direction
import io.github.rupakc.travelplanner.core.domain.alerts.PriceChange
import io.github.rupakc.travelplanner.core.domain.alerts.Tone
import io.github.rupakc.travelplanner.core.domain.alerts.TripGroup
import io.github.rupakc.travelplanner.core.domain.alerts.WatchStatusKey
import io.github.rupakc.travelplanner.core.domain.alerts.WatchStatuses
import io.github.rupakc.travelplanner.core.domain.alerts.cheaperNearby
import io.github.rupakc.travelplanner.core.domain.alerts.checkPriceTarget
import io.github.rupakc.travelplanner.core.domain.alerts.checkTarget
import io.github.rupakc.travelplanner.core.domain.alerts.formatDateRange
import io.github.rupakc.travelplanner.core.domain.alerts.formatDay
import io.github.rupakc.travelplanner.core.domain.alerts.formatUsd
import io.github.rupakc.travelplanner.core.domain.alerts.groupTotals
import io.github.rupakc.travelplanner.core.domain.alerts.isPast
import io.github.rupakc.travelplanner.core.domain.alerts.isUnavailable
import io.github.rupakc.travelplanner.core.domain.alerts.matchedElsewhere
import io.github.rupakc.travelplanner.core.domain.alerts.nightsBetween
import io.github.rupakc.travelplanner.core.domain.alerts.perPerson
import io.github.rupakc.travelplanner.core.domain.alerts.percent
import io.github.rupakc.travelplanner.core.domain.alerts.priceChange
import io.github.rupakc.travelplanner.core.domain.alerts.priceWatchChange
import io.github.rupakc.travelplanner.core.domain.alerts.providerName
import io.github.rupakc.travelplanner.core.domain.alerts.routeLabel
import io.github.rupakc.travelplanner.core.domain.alerts.sparkGeometry
import io.github.rupakc.travelplanner.core.domain.alerts.timeAgo
import io.github.rupakc.travelplanner.core.domain.alerts.timeUntil
import io.github.rupakc.travelplanner.core.domain.alerts.tripRows
import io.github.rupakc.travelplanner.core.domain.alerts.watchCurrency
import io.github.rupakc.travelplanner.core.domain.alerts.watchStatus
import io.github.rupakc.travelplanner.core.domain.alerts.whatChanged
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import io.github.rupakc.travelplanner.core.ui.watch.AlertModeGroup
import io.github.rupakc.travelplanner.core.ui.watch.EmailOption
import io.github.rupakc.travelplanner.core.ui.watch.FlexibleOption
import io.github.rupakc.travelplanner.core.ui.watch.ToneBox
import io.github.rupakc.travelplanner.core.ui.watch.TripBadge
import io.github.rupakc.travelplanner.core.ui.watch.TripRowLine
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.core.ui.watch.toneColors
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Everything a card can ask for; the screen forwards to [AlertsViewModel]. */
internal data class CardCallbacks(
    val onCheck: () -> Unit,
    val onStatus: (active: Boolean) -> Unit,
    val onSave: (WatchEdit) -> Unit,
    val onDelete: () -> Unit,
    val onReplan: (cheapest: Boolean) -> Unit,
    val onOpenLink: (String) -> Unit,
)

private fun JsonObject?.str(k: String) = (this?.get(k) as? JsonPrimitive)?.contentOrNull

private fun JsonObject?.num(k: String) = (this?.get(k) as? JsonPrimitive)?.doubleOrNull

private fun JsonObject?.int(k: String) = (this?.get(k) as? JsonPrimitive)?.intOrNull

private fun JsonObject?.obj(k: String) = this?.get(k) as? JsonObject

/**
 * One watched price (web `WatchCard` / `PriceWatchCard` + kind cards). The live price is the
 * hero: big, tabular, with its change and the sparkline right under it; the rest stays quiet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WatchCard(
    item: AnyWatch,
    busy: CardAction?,
    message: CardMessage?,
    highlighted: Boolean,
    emailAvailable: Boolean,
    userEmail: String?,
    now: Instant,
    callbacks: CardCallbacks,
    modifier: Modifier = Modifier,
) {
    val past = isPast(item)
    val fare = (item as? AnyWatch.Fare)?.watch
    val price = (item as? AnyWatch.Price)?.watch
    val status = if (fare != null) watchStatus(fare) else watchStatus(price!!)
    val title = if (fare != null) routeLabel(fare) else price?.title.orEmpty()
    var editing by rememberSaveable(item.key) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(item.key) { mutableStateOf(false) }

    TpCard(modifier = modifier.fillMaxWidth(), size = TpCardSize.Large, selected = highlighted) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.Center) {
                Text(title, style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                StatusChip(status)
            }
            Text(subtitleOf(item), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
            Text(alertRuleOf(item), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)

            PriceHero(item, past)

            if (price != null && !past && isUnavailable(price)) {
                ToneBox(Tone.NEUTRAL) { Text(stringResource(unavailableText(price.kind))) }
            }
            when (price?.kind) {
                PriceWatchKinds.HOTEL -> HotelDetails(price)
                PriceWatchKinds.ACTIVITY -> ActivityDetails(price)
                PriceWatchKinds.TRIP -> TripDetails(price, past, callbacks)
            }

            val history = fare?.history?.map { it.price } ?: price?.history?.map { it.price }.orEmpty()
            val target = if ((fare?.alertMode ?: price?.alertMode) == AlertMode.BELOW_TARGET.wire) fare?.targetPrice ?: price?.targetPrice else null
            Sparkline(history, target) { v -> if (fare != null) formatUsd(v) else formatPrice(v, price?.currency) }
            MetaLine(item, now)

            if (item.status == WatchStatuses.ERROR) {
                ToneBox(Tone.WARN) {
                    val problem = fare?.problem ?: price?.problem
                    val hold = stringResource(if (fare != null) R.string.alerts_on_hold_fare else R.string.alerts_on_hold_price)
                    Text(listOfNotNull(problem, hold).joinToString(" "))
                    TpButton(
                        stringResource(R.string.alerts_retry),
                        { callbacks.onStatus(true) },
                        loading = busy == CardAction.STATUS,
                        variant = TpButtonVariant.Secondary,
                        leadingIcon = Icons.Filled.Refresh,
                    )
                }
            }

            message?.let { m ->
                val tone =
                    when (m) {
                        is CardMessage.Checked -> m.outcome.tone
                        is CardMessage.Failed -> Tone.WARN
                        else -> Tone.NEUTRAL
                    }
                val (_, fg) = toneColors(tone)
                Text(
                    messageText(m),
                    style = TravelTheme.typography.bodyMedium,
                    color = if (tone == Tone.NEUTRAL) TravelTheme.colors.onSurfaceVariant else fg,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            if (editing) {
                EditPanel(item, emailAvailable, userEmail, saving = busy == CardAction.SAVE, onCancel = { editing = false }) { edit ->
                    callbacks.onSave(edit)
                    editing = false
                }
            }

            HorizontalDivider(color = TravelTheme.colors.outlineVariant)
            CardActions(item, past, busy, editing, confirmDelete, callbacks, onEdit = { editing = true }, onConfirmDelete = { confirmDelete = it })
        }
    }
}

@Composable
private fun StatusChip(status: WatchStatusKey) {
    val (bg, fg) = toneColors(status.tone)
    Surface(color = bg, contentColor = fg, shape = TravelShape.Pill) {
        Text(
            statusLabel(status),
            style = TravelTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
        )
    }
}

@Composable
private fun subtitleOf(item: AnyWatch): String =
    when (item) {
        is AnyWatch.Fare -> {
            val w = item.watch
            val n = w.numTravelers ?: 1
            listOfNotNull(
                formatDateRange(w.departureDate, w.returnDate, currentLocale())?.let {
                    if (w.returnDate ==
                        null
                    ) {
                        it + " " + stringResource(R.string.alerts_one_way)
                    } else {
                        it
                    }
                },
                pluralStringResource(R.plurals.alerts_travellers, n, n),
                stringResource(R.string.alerts_flexible_short).takeIf { (w.flexibleDays ?: 0) > 0 },
            ).joinToString(" · ")
        }

        is AnyWatch.Price -> {
            item.watch.subtitle.orEmpty()
        }
    }

@Composable
private fun alertRuleOf(item: AnyWatch): String {
    val (mode, target, email) =
        when (item) {
            is AnyWatch.Fare -> Triple(item.watch.alertMode, item.watch.targetPrice?.let(::formatUsd), item.watch.emailEnabled)
            is AnyWatch.Price -> Triple(item.watch.alertMode, item.watch.targetPrice?.let { formatPrice(it, item.watch.currency) }, item.watch.emailEnabled)
        }
    val rule =
        if (mode == AlertMode.BELOW_TARGET.wire && target != null) {
            stringResource(R.string.alerts_rule_target, target)
        } else {
            stringResource(R.string.alerts_rule_drops)
        }
    return if (email == true) rule + " · " + stringResource(R.string.alerts_rule_email) else rule
}

@Composable
private fun PriceHero(item: AnyWatch, past: Boolean) {
    val fare = (item as? AnyWatch.Fare)?.watch
    val price = (item as? AnyWatch.Price)?.watch
    val amount = fare?.let { it.lastPrice ?: it.baselinePrice } ?: price?.let { it.lastPrice ?: it.baselinePrice }
    if (amount == null) {
        val text =
            when {
                fare != null && past -> R.string.alerts_no_price_found
                fare != null && fare.status != WatchStatuses.ERROR -> R.string.alerts_getting_price
                else -> R.string.alerts_no_price_yet
            }
        Text(stringResource(text), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        return
    }
    Column {
        Text(
            if (fare != null) formatUsd(amount) else formatPrice(amount, price?.currency),
            style = TravelTheme.typography.displaySmall,
            color = TravelTheme.colors.onSurface,
        )
        if (fare != null) {
            val n = fare.numTravelers ?: 1
            Text(
                pluralStringResource(R.plurals.alerts_total_for_travellers, n, n),
                style = TravelTheme.typography.labelSmall,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        } else {
            price?.lastObservation.num("unit_price")?.let { unit ->
                Text(
                    formatPrice(unit, price?.currency) + " " + stringResource(unitLabel(price?.kind)),
                    style = TravelTheme.typography.labelSmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
        }
        val change: PriceChange? = if (fare != null) priceChange(fare) else price?.let(::priceWatchChange)
        change?.let { c ->
            val formatted = if (fare != null) formatUsd(c.amount) else formatPrice(c.amount, price?.currency)
            val (text, color) =
                when (c.direction) {
                    Direction.DOWN -> stringResource(R.string.alerts_change_down, formatted) to TravelTheme.extendedColors.success
                    Direction.UP -> stringResource(R.string.alerts_change_up, formatted) to TravelTheme.colors.error
                    Direction.SAME -> stringResource(R.string.alerts_change_same) to TravelTheme.colors.onSurfaceVariant
                }
            Text(text, style = TravelTheme.typography.labelLarge, color = color)
        }
    }
}

private fun unitLabel(kind: String?): Int =
    when (kind) {
        PriceWatchKinds.HOTEL -> R.string.alerts_unit_night
        PriceWatchKinds.ACTIVITY -> R.string.alerts_unit_person
        else -> R.string.alerts_unit_generic
    }

private fun unavailableText(kind: String?): Int =
    when (kind) {
        PriceWatchKinds.HOTEL -> R.string.alerts_unavailable_hotel
        PriceWatchKinds.ACTIVITY -> R.string.alerts_unavailable_activity
        else -> R.string.alerts_unavailable_generic
    }

@Composable
private fun Muted(text: String) = Text(text, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)

@Composable
private fun MetaLine(item: AnyWatch, now: Instant) {
    val fare = (item as? AnyWatch.Fare)?.watch
    val price = (item as? AnyWatch.Price)?.watch
    val parts = mutableListOf<String>()
    val lowest = fare?.lowestPrice ?: price?.lowestPrice
    if (lowest != null) {
        val text = if (fare != null) formatUsd(lowest) else formatPrice(lowest, price?.currency)
        var lowestText = stringResource(R.string.alerts_lowest_seen, text)
        val otherDay = fare?.lowestPriceDate?.takeIf { (fare.flexibleDays ?: 0) > 0 && it != fare.departureDate }
        if (otherDay != null) {
            formatDay(otherDay, currentLocale())?.let { lowestText += " " + stringResource(R.string.alerts_lowest_leaving, it) }
        }
        parts += lowestText
    }
    fare?.googlePriceLevel?.let { level ->
        when (level) {
            "low" -> parts += stringResource(R.string.alerts_google_low)
            "typical" -> parts += stringResource(R.string.alerts_google_typical)
            "high" -> parts += stringResource(R.string.alerts_google_high)
        }
    }
    timeAgo(fare?.lastCheckedAt ?: price?.lastCheckedAt, now)?.let { parts += stringResource(R.string.alerts_checked, timeAgoText(it)) }
    if (item.status == WatchStatuses.ACTIVE) {
        timeUntil(fare?.nextCheckAt ?: price?.nextCheckAt, now)?.let { parts += stringResource(R.string.alerts_next_check, timeUntilText(it)) }
    }
    if (parts.isNotEmpty()) Muted(parts.joinToString(" · "))
}

/** Price history line with the target dashed (web `Sparkline`); one text summary for screen readers. */
@Composable
internal fun Sparkline(history: List<Double?>, target: Double?, format: (Double) -> String) {
    val values = history.filterNotNull()
    if (values.size < 2) {
        Muted(stringResource(if (values.isEmpty()) R.string.alerts_spark_none else R.string.alerts_spark_after_next))
        return
    }
    val summary =
        pluralStringResource(R.plurals.alerts_spark_summary, values.size, values.size, format(values.first()), format(values.last()), format(values.min())) +
            (target?.let { " " + stringResource(R.string.alerts_spark_target, format(it)) } ?: "")
    val down = values.last() < values.first()
    val line = if (down) TravelTheme.extendedColors.success else TravelTheme.colors.secondary
    val targetColor = TravelTheme.colors.outline
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(SPARK_HEIGHT)
            .semantics { contentDescription = summary },
    ) {
        val pad = 4.dp.toPx()
        val g = sparkGeometry(values, size.width, size.height, pad = pad, target = target) ?: return@Canvas
        g.targetY?.let { y ->
            drawLine(targetColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        val path = Path()
        g.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        val area =
            Path().apply {
                addPath(path)
                lineTo(g.points.last().x, size.height)
                lineTo(g.points.first().x, size.height)
                close()
            }
        drawPath(area, line.copy(alpha = AREA_ALPHA))
        drawPath(path, line, style = Stroke(width = 2.dp.toPx()))
        val last = g.points.last()
        drawCircle(line, radius = 3.5.dp.toPx(), center = Offset(last.x, last.y))
    }
}

@Composable
private fun HotelDetails(watch: PriceWatch) {
    val s = watch.subject
    val nights = nightsBetween(s.str("check_in"), s.str("check_out"))
    val taxes = (watch.lastObservation.obj("observed")?.get("taxes_included") as? JsonPrimitive)?.booleanOrNull
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
        if (nights != null) {
            val city = s.str("city")?.substringBefore(',')
            val base =
                if (city != null) {
                    pluralStringResource(R.plurals.alerts_hotel_total_city, nights, nights, city)
                } else {
                    pluralStringResource(R.plurals.alerts_hotel_total, nights, nights)
                }
            val suffix =
                when (taxes) {
                    true -> stringResource(R.string.alerts_hotel_taxes_in)
                    false -> stringResource(R.string.alerts_hotel_taxes_out)
                    null -> "."
                }
            Muted(base + suffix)
        }
        if (matchedElsewhere(watch)) Muted(stringResource(R.string.alerts_hotel_called, s.str("requested_name").orEmpty()))
        if (watch.status != WatchStatuses.EXPIRED && isUnavailable(watch)) {
            Text(stringResource(R.string.alerts_hotel_sold_out), style = TravelTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ActivityDetails(watch: PriceWatch) {
    val subject = watch.subject
    val observed = watch.lastObservation.obj("observed")
    val currency = watchCurrency(watch)
    val provider = providerName(subject.str("provider") ?: observed.str("provider")) ?: stringResource(R.string.alerts_the_listing)
    val n = subject.int("travellers") ?: 1
    val unit = perPerson(watch)
    val total = watch.lastPrice ?: watch.baselinePrice
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
        val rating = observed.num("rating")
        Muted(listOfNotNull(provider, rating?.let { "★ $it" + (observed.int("reviews")?.let { r -> " ($r)" } ?: "") }).joinToString(" · "))
        if (!isUnavailable(watch) && unit != null) {
            val pp = stringResource(R.string.alerts_from_pp, formatPrice(unit, currency))
            Text(
                if (n > 1 && total != null) pp + " × " + pluralStringResource(R.plurals.alerts_travellers, n, n) + " = " + formatPrice(total, currency) else pp,
                style = TravelTheme.typography.bodyMedium,
            )
        }
        if (isUnavailable(watch) && observed.str("reason") == "currency_changed") {
            Muted(stringResource(R.string.alerts_activity_currency_changed, observed.str("seen_currency").orEmpty(), currency))
        }
        Muted(stringResource(R.string.alerts_activity_source, provider))
    }
}

/** Flights / stay / activities / other as one stacked bar, then the rows with Live / Estimate badges. */
@Composable
private fun TripDetails(watch: PriceWatch, past: Boolean, callbacks: CardCallbacks) {
    val rows = tripRows(watch)
    val groups = groupTotals(rows)
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        if (groups.isNotEmpty()) {
            val label = groups.map { groupLabel(it.group) + " " + percent(it.share) }.joinToString(", ")
            val colors = groupColors()
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(BAR_HEIGHT))
                    .semantics { contentDescription = label },
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                groups.forEach { g -> Box(Modifier.weight(g.share.toFloat().coerceAtLeast(MIN_SHARE)).fillMaxHeight().background(colors.getValue(g.group))) }
            }
            Muted(label)
        }
        rows.forEach { TripRowLine(it) }
        if (rows.any { !it.live }) Muted(stringResource(R.string.alerts_trip_estimates_fixed))
        whatChanged(watch)?.let { Muted(tripChangeText(it)) }
        val replannable = !past && (watch.subject["components"] != null)
        if (replannable) {
            TpButton(stringResource(R.string.alerts_replan_trip), { callbacks.onReplan(false) }, variant = TpButtonVariant.Secondary)
        }
    }
}

@Composable
private fun groupColors(): Map<TripGroup, androidx.compose.ui.graphics.Color> {
    val a = TravelTheme.sectionAccents
    return mapOf(
        TripGroup.FLIGHTS to a[io.github.rupakc.travelplanner.core.model.section.SectionId.FLIGHTS].bar,
        TripGroup.STAY to a[io.github.rupakc.travelplanner.core.model.section.SectionId.HOTELS].bar,
        TripGroup.ACTIVITIES to a[io.github.rupakc.travelplanner.core.model.section.SectionId.ACTIVITIES].bar,
        TripGroup.OTHER to a[io.github.rupakc.travelplanner.core.model.section.SectionId.EVENTS].bar,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardActions(
    item: AnyWatch,
    past: Boolean,
    busy: CardAction?,
    editing: Boolean,
    confirmDelete: Boolean,
    callbacks: CardCallbacks,
    onEdit: () -> Unit,
    onConfirmDelete: (Boolean) -> Unit,
) {
    val fare = (item as? AnyWatch.Fare)?.watch
    val price = (item as? AnyWatch.Price)?.watch
    val title = fare?.let(::routeLabel) ?: price?.title.orEmpty()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        if (fare != null && !past) {
            TpButton(stringResource(R.string.alerts_replan_fare), { callbacks.onReplan(false) })
            cheaperNearby(fare)?.let { c ->
                TpButton(
                    stringResource(R.string.alerts_leave_and_save, formatDay(c.departureDate, currentLocale()).orEmpty(), formatUsd(c.saving)),
                    { callbacks.onReplan(true) },
                    variant = TpButtonVariant.Tonal,
                )
            }
            fare.bookingUrl?.let { url ->
                TpButton(stringResource(R.string.alerts_book_google_flights), {
                    callbacks.onOpenLink(url)
                }, variant = TpButtonVariant.Text, leadingIcon = Icons.AutoMirrored.Filled.ExitToApp)
            }
        }
        val link = price?.link ?: price?.lastObservation.str("link")
        if (price != null && !past && link != null) {
            val label =
                when (price.kind) {
                    PriceWatchKinds.HOTEL -> R.string.alerts_open_book
                    PriceWatchKinds.TRIP -> R.string.alerts_open
                    else -> R.string.alerts_open_listing
                }
            TpButton(stringResource(label), { callbacks.onOpenLink(link) }, variant = TpButtonVariant.Text, leadingIcon = Icons.AutoMirrored.Filled.ExitToApp)
        }
        if (!past && item.status != WatchStatuses.ERROR) {
            TpButton(
                stringResource(if (busy == CardAction.CHECK) R.string.alerts_checking else R.string.alerts_check_now),
                callbacks.onCheck,
                variant = TpButtonVariant.Text,
                loading = busy == CardAction.CHECK,
                leadingIcon = Icons.Filled.Refresh,
            )
        }
        if (item.status == WatchStatuses.ACTIVE) {
            TpButton(stringResource(R.string.alerts_pause), { callbacks.onStatus(false) }, variant = TpButtonVariant.Text, enabled = busy != CardAction.STATUS)
        }
        if (item.status == WatchStatuses.PAUSED) {
            TpButton(
                stringResource(R.string.alerts_resume),
                { callbacks.onStatus(true) },
                variant = TpButtonVariant.Text,
                enabled = busy != CardAction.STATUS,
                leadingIcon = Icons.Filled.PlayArrow,
            )
        }
        if (!past && !editing) {
            TpButton(stringResource(R.string.alerts_edit), onEdit, variant = TpButtonVariant.Text, leadingIcon = Icons.Filled.Edit)
        }
        if (!confirmDelete) {
            val a11y = stringResource(R.string.alerts_delete_a11y, title)
            TpButton(
                stringResource(R.string.alerts_delete),
                { onConfirmDelete(true) },
                variant = TpButtonVariant.Text,
                leadingIcon = Icons.Filled.Delete,
                modifier = Modifier.semantics { contentDescription = a11y },
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                Text(stringResource(R.string.alerts_stop_watching), style = TravelTheme.typography.bodyMedium)
                TpButton(stringResource(R.string.alerts_delete), callbacks.onDelete, variant = TpButtonVariant.Destructive)
                TpButton(stringResource(R.string.alerts_keep), { onConfirmDelete(false) }, variant = TpButtonVariant.Text)
            }
        }
    }
}

/** Alert settings (web EditPanel): mode, target (in the watch's currency), nearby dates for fares, email. */
@Composable
private fun EditPanel(
    item: AnyWatch,
    emailAvailable: Boolean,
    userEmail: String?,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: (WatchEdit) -> Unit,
) {
    val fare = (item as? AnyWatch.Fare)?.watch
    val price = (item as? AnyWatch.Price)?.watch
    val initialTarget =
        fare?.targetPrice?.let { kotlin.math.round(it).toLong().toString() }
            ?: price?.targetPrice?.let { if (it == kotlin.math.floor(it)) it.toLong().toString() else it.toString() }
            ?: ""
    var mode by remember(item.key) { mutableStateOf(AlertMode.of(fare?.alertMode ?: price?.alertMode)) }
    var target by remember(item.key) { mutableStateOf(initialTarget) }
    var touched by remember(item.key) { mutableStateOf(false) }
    var flexible by remember(item.key) { mutableStateOf((fare?.flexibleDays ?: 0) > 0) }
    var email by remember(item.key) { mutableStateOf((fare?.emailEnabled ?: price?.emailEnabled) == true) }
    val check = if (mode == AlertMode.BELOW_TARGET) (if (fare != null) checkTarget(target, fare.lastPrice) else checkPriceTarget(target)) else null
    val error = check?.error?.let { stringResource(R.string.alerts_target_error) }
    val currency = price?.currency ?: "USD"
    Surface(color = TravelTheme.colors.surfaceContainerLow, shape = TravelShape.ItemCard) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            AlertModeGroup(
                mode = mode,
                onMode = { mode = it },
                target = target,
                onTarget = {
                    target = it
                    touched = true
                },
                currency = currency,
                anyDropHint = stringResource(R.string.alerts_edit_any_drop),
                targetError = error.takeIf { touched },
            )
            if (fare != null) {
                FlexibleOption(flexible) { flexible = it }
            }
            if (emailAvailable) EmailOption(email, userEmail, { email = it })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm, Alignment.End)) {
                TpButton(stringResource(R.string.alerts_cancel), onCancel, variant = TpButtonVariant.Text)
                TpButton(
                    stringResource(R.string.alerts_save),
                    {
                        if (check?.error != null) {
                            touched = true
                        } else {
                            onSave(
                                WatchEdit(
                                    mode,
                                    check?.value,
                                    if (fare !=
                                        null
                                    ) {
                                        flexible
                                    } else {
                                        null
                                    },
                                    email && !userEmail.isNullOrBlank(),
                                    target != initialTarget,
                                ),
                            )
                        }
                    },
                    loading = saving,
                )
            }
        }
    }
}

private val SPARK_HEIGHT = 48.dp
private val BAR_HEIGHT = 10.dp
private const val AREA_ALPHA = 0.16f
private const val MIN_SHARE = 0.01f

/** Width for side-by-side cards on wide windows (grid minimum cell). */
internal val CARD_MIN_WIDTH = 360.dp
