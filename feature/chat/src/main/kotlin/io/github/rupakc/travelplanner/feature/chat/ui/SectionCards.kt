package io.github.rupakc.travelplanner.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.ForexData
import io.github.rupakc.travelplanner.core.model.section.GettingAroundData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.core.model.section.TipsData
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.core.ui.PriceText
import io.github.rupakc.travelplanner.core.ui.SectionScaffold
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.feature.chat.R
import io.github.rupakc.travelplanner.feature.chat.model.ChatSection
import io.github.rupakc.travelplanner.feature.chat.model.ChatSectionStatus
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Sections the chat renders (web `PlanningMessage` order). */
internal val CHAT_SECTION_ORDER =
    listOf(
        SectionId.FLIGHTS,
        SectionId.HOTELS,
        SectionId.ACTIVITIES,
        SectionId.VISA,
        SectionId.SIM,
        SectionId.TIPS,
        SectionId.GETTING_AROUND,
        SectionId.FOREX,
        SectionId.ITINERARY,
    )

/** Plan selection callbacks for section cards. */
internal class PlanCallbacks(val selections: PlanSelections, val onToggle: (PlanItem) -> Unit, val onOpenLink: (String) -> Unit) {
    fun isSelected(item: PlanItem): Boolean = PlanSelectionRules.isSelected(selections, item)
}

internal fun ChatSectionStatus.toSectionStatus(): SectionStatus =
    when (this) {
        ChatSectionStatus.LOADING -> SectionStatus.LOADING
        ChatSectionStatus.ENHANCING -> SectionStatus.ENHANCING
        ChatSectionStatus.DONE -> SectionStatus.DONE
    }

/** A compact, collapsible section card (web `ChatSection`) with Add-to-plan on selectable items. */
@Composable
internal fun ChatSectionCard(section: ChatSection, plan: PlanCallbacks, modifier: Modifier = Modifier) {
    val id = section.id ?: return
    var expanded by rememberSaveable(section.name) { mutableStateOf(true) }
    SectionScaffold(
        section = id,
        status = section.status.toSectionStatus(),
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            SectionBody(section, plan)
        }
    }
}

@Composable
private fun ColumnScope.SectionBody(section: ChatSection, plan: PlanCallbacks) {
    val estimate = (section.raw["knowledge_estimate"] as? JsonPrimitive)?.booleanOrNull == true
    when (val data = section.data) {
        is FlightsData -> FlightsBody(data, estimate, plan)
        is HotelsData -> HotelsBody(data, estimate, plan)
        is ActivitiesData -> ActivitiesBody(data, plan)
        is VisaData -> VisaBody(data, plan)
        is SimData -> SimBody(data, plan)
        is TipsData -> TipsBody(data, plan)
        is GettingAroundData -> TransportBody(data, plan)
        is ForexData -> ForexBody(data)
        is ItineraryData -> ItineraryBody(data, plan)
        else -> Unit
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun EstimateBanner() {
    Text(stringResource(R.string.chat_estimate_banner), style = TravelTheme.typography.labelMedium, color = TravelTheme.extendedColors.warning)
}

/** Web "Show N more" / "Show less" over a list. */
@Composable
private fun <T> LimitedList(items: List<T>, limit: Int, key: String, row: @Composable (T) -> Unit) {
    var all by rememberSaveable(key) { mutableStateOf(false) }
    val visible = if (all) items else items.take(limit)
    visible.forEach { row(it) }
    val hidden = items.size - limit
    if (hidden > 0) {
        TpButton(
            text = if (all) stringResource(R.string.chat_show_less) else pluralStringResource(R.plurals.chat_show_more, hidden, hidden),
            onClick = { all = !all },
            variant = TpButtonVariant.Text,
        )
    }
}

/** One selectable result row: title, meta, price, links and an Add button. */
@Composable
private fun ItemRow(
    title: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    TpCard(modifier = modifier.fillMaxWidth(), selected = selected, accent = accent) {
        Text(title, style = TravelTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        content()
        if (onToggle != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AddToPlanButton(selected = selected, itemName = title, onToggle = onToggle)
            }
        }
    }
}

@Composable
private fun LinkButton(label: String, url: String?, onOpenLink: (String) -> Unit) {
    if (url.isNullOrBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) return
    TpButton(label, onClick = { onOpenLink(url) }, variant = TpButtonVariant.Text)
}

@Composable
private fun ColumnScope.FlightsBody(data: FlightsData, estimate: Boolean, plan: PlanCallbacks) {
    if (data.results.isEmpty()) return Muted(stringResource(R.string.chat_no_flights))
    if (estimate) EstimateBanner()
    val accent = TravelTheme.sectionAccents[SectionId.FLIGHTS].icon
    LimitedList(data.results, ChatDimens.SECTION_LIMIT, "flights") { f ->
        val item = PlanItem.FlightItem(f)
        val title = listOfNotNull(f.outbound?.airline, f.outbound?.flightNumber).joinToString(" ").ifBlank { "—" }
        ItemRow(title, plan.isSelected(item), accent, onToggle = { plan.onToggle(item) }) {
            f.outbound?.let { leg ->
                val route = stringResource(R.string.chat_route, leg.origin.orEmpty(), leg.destination.orEmpty())
                Muted(if (f.returnLeg != null) "$route · ${stringResource(R.string.chat_round_trip)}" else route)
            }
            PriceText(f.priceUsd, isEstimate = f.isEstimate || estimate, style = TravelTheme.typography.titleSmall)
            LinkButton(stringResource(R.string.chat_book), f.bookingUrl, plan.onOpenLink)
        }
    }
}

@Composable
private fun ColumnScope.HotelsBody(data: HotelsData, estimate: Boolean, plan: PlanCallbacks) {
    if (data.results.isEmpty()) return Muted(stringResource(R.string.chat_no_hotels))
    if (estimate) EstimateBanner()
    val accent = TravelTheme.sectionAccents[SectionId.HOTELS].icon
    LimitedList(data.results, ChatDimens.SECTION_LIMIT, "hotels") { h ->
        val item = PlanItem.HotelItem(h)
        ItemRow(h.name.orEmpty(), plan.isSelected(item), accent, onToggle = { plan.onToggle(item) }) {
            h.location?.let { Muted(it) }
            h.starRating?.takeIf { it > 0 }?.let { Muted("★".repeat(it.toInt().coerceIn(1, MAX_STARS))) }
            localPrice(h.pricePerNightUsd).takeIf(String::isNotEmpty)?.let {
                Text(stringResource(R.string.chat_per_night, it), style = TravelTheme.typography.titleSmall)
            }
            LinkButton(stringResource(R.string.chat_book), h.bookingUrl, plan.onOpenLink)
        }
    }
}

@Composable
private fun ColumnScope.ActivitiesBody(data: ActivitiesData, plan: PlanCallbacks) {
    if (data.results.isEmpty()) return Muted(stringResource(R.string.chat_no_activities))
    val accent = TravelTheme.sectionAccents[SectionId.ACTIVITIES].icon
    LimitedList(data.results, ChatDimens.LIST_SECTION_LIMIT, "activities") { a ->
        val item = PlanItem.ActivityItem(a)
        ItemRow(a.name.orEmpty(), plan.isSelected(item), accent, onToggle = { plan.onToggle(item) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                PriceText(a.priceUsd, hideWhenUnknown = true, style = TravelTheme.typography.bodyMedium)
                a.durationHours?.let { Muted(stringResource(R.string.chat_hours, trimNumber(it))) }
            }
            LinkButton(stringResource(R.string.chat_book), a.bookingUrl, plan.onOpenLink)
        }
    }
}

@Composable
private fun ColumnScope.VisaBody(data: VisaData, plan: PlanCallbacks) {
    val req = data.requirement ?: return Muted(stringResource(R.string.chat_no_visa))
    req.visaType?.let { Text(it.replace('-', ' ').replaceFirstChar(Char::uppercase), style = TravelTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        req.maxStayDays?.let { Muted(pluralStringResource(R.plurals.chat_visa_max_stay, it, it)) }
        req.feeUsd?.let { fee ->
            Muted(stringResource(R.string.chat_visa_fee, if (fee == 0.0) stringResource(R.string.chat_free) else localPrice(fee)))
        }
        req.processingTime?.let { Muted(it) }
    }
    req.requirements.take(ChatDimens.VISA_MAX_REQUIREMENTS).forEach { Text("• $it", style = TravelTheme.typography.bodySmall) }
    LinkButton(stringResource(R.string.chat_official_source), req.officialUrl, plan.onOpenLink)
}

@Composable
private fun ColumnScope.SimBody(data: SimData, plan: PlanCallbacks) {
    if (data.plans.isEmpty()) return Muted(stringResource(R.string.chat_no_sim))
    val accent = TravelTheme.sectionAccents[SectionId.SIM].icon
    LimitedList(data.plans, ChatDimens.SECTION_LIMIT, "sim") { p ->
        val item = PlanItem.SimItem(p)
        val title = listOfNotNull(p.provider, p.planName).joinToString(" — ")
        ItemRow(title, plan.isSelected(item), accent, onToggle = { plan.onToggle(item) }) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalArrangement = Arrangement.Center) {
                PriceText(p.priceUsd, style = TravelTheme.typography.titleSmall)
                p.dataGb?.let { Muted(stringResource(R.string.chat_sim_data, it)) }
                p.validityDays?.let { Muted(pluralStringResource(R.plurals.chat_days, it, it)) }
            }
            LinkButton(stringResource(R.string.chat_get), p.url, plan.onOpenLink)
        }
    }
}

@Composable
private fun ColumnScope.TipsBody(data: TipsData, plan: PlanCallbacks) {
    if (data.tips.isEmpty()) return Muted(stringResource(R.string.chat_no_tips))
    val accent = TravelTheme.sectionAccents[SectionId.TIPS].icon
    LimitedList(data.tips, ChatDimens.LIST_SECTION_LIMIT, "tips") { tip ->
        val item = PlanItem.TipItem(tip)
        ItemRow(tip.title.orEmpty(), plan.isSelected(item), accent, onToggle = { plan.onToggle(item) }) {
            tip.body?.let { body ->
                val preview = if (body.length > ChatDimens.TIP_PREVIEW_CHARS) body.take(ChatDimens.TIP_PREVIEW_CHARS) + "…" else body
                Text(preview, style = TravelTheme.typography.bodySmall)
            }
            LinkButton(stringResource(R.string.chat_source), tip.sourceUrl, plan.onOpenLink)
        }
    }
}

@Composable
private fun ColumnScope.TransportBody(data: GettingAroundData, plan: PlanCallbacks) {
    if (data.options.isEmpty()) return Muted(stringResource(R.string.chat_no_transport))
    val accent = TravelTheme.sectionAccents[SectionId.GETTING_AROUND].icon
    LimitedList(data.options, ChatDimens.LIST_SECTION_LIMIT, "getting_around") { opt ->
        val item = PlanItem.TransportItem(opt)
        ItemRow(opt.name.orEmpty(), plan.isSelected(item), accent, onToggle = { plan.onToggle(item) }) {
            opt.type?.let { Muted(it.replace('_', ' ')) }
            opt.priceInfo?.let { Text(it, style = TravelTheme.typography.bodySmall) }
            opt.tips?.let { Muted(it.take(ChatDimens.TIP_PREVIEW_CHARS)) }
            LinkButton(stringResource(R.string.chat_info), opt.bookingUrl, plan.onOpenLink)
        }
    }
}

@Composable
private fun ColumnScope.ForexBody(data: ForexData) {
    val currency = data.localCurrency
    if (data.isError || (currency == null && data.exchangeRates.isEmpty())) return Muted(stringResource(R.string.chat_no_forex))
    currency?.let {
        Text(
            listOfNotNull(it.name, it.code?.let { c -> "($c)" }, it.symbol).joinToString(" "),
            style = TravelTheme.typography.titleSmall,
        )
    }
    data.exchangeRates.forEach { rate ->
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.Bottom) {
            rate.rate?.let { Text(trimNumber(it), style = TravelTheme.typography.titleSmall) }
            rate.description?.let { Muted(it) }
        }
    }
    data.exchangeLocations.take(ChatDimens.FOREX_MAX_LOCATIONS).forEach { loc ->
        Column {
            loc.name?.let { Text(it, style = TravelTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
            loc.description?.let { Muted(it.take(ChatDimens.TIP_PREVIEW_CHARS)) }
        }
    }
    data.tipping?.let { t ->
        val label = stringResource(if (t.expected == true) R.string.chat_tipping_expected else R.string.chat_tipping_not_expected)
        Text(listOfNotNull(label, t.description).joinToString(" — "), style = TravelTheme.typography.bodySmall)
    }
}

@Composable
private fun ColumnScope.ItineraryBody(data: ItineraryData, plan: PlanCallbacks) {
    if (data.days.isEmpty()) return Muted(stringResource(R.string.chat_no_itinerary))
    data.days.forEachIndexed { index, day ->
        val dayNumber = day.dayNumber ?: (index + 1)
        Text(
            listOfNotNull(stringResource(R.string.chat_day, dayNumber), day.date, day.city).joinToString(" · "),
            style = TravelTheme.typography.titleSmall,
        )
        day.slots.forEach { slot ->
            val tod = slot.timeOfDay.orEmpty()
            val key = "$dayNumber-$tod"
            // Web slot object: {key, day_number, time_of_day, activity, location, estimated_cost_usd}.
            val item = PlanItem.SlotItem(slot.copy(key = key, dayNumber = dayNumber, raw = slotRaw(slot.raw, key, dayNumber)))
            ItemRow(slot.activity.orEmpty(), plan.isSelected(item), TravelTheme.sectionAccents[SectionId.ITINERARY].icon, onToggle = { plan.onToggle(item) }) {
                Muted(listOfNotNull(tod.replaceFirstChar { it.uppercase() }, slot.location).joinToString(" · "))
                PriceText(slot.estimatedCostUsd, hideWhenUnknown = true, style = TravelTheme.typography.bodySmall)
            }
        }
    }
}

private fun slotRaw(raw: kotlinx.serialization.json.JsonObject, key: String, day: Int) =
    kotlinx.serialization.json.JsonObject(raw + mapOf("key" to JsonPrimitive(key), "day_number" to JsonPrimitive(day)))

/** 2.0 → "2", 2.5 → "2.5" (numbers exactly as received). */
internal fun trimNumber(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private const val MAX_STARS = 5
