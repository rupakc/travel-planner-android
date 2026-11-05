package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.AtmInfo
import io.github.rupakc.travelplanner.core.model.section.CardAcceptance
import io.github.rupakc.travelplanner.core.model.section.CashAdvice
import io.github.rupakc.travelplanner.core.model.section.ExchangeLocation
import io.github.rupakc.travelplanner.core.model.section.ExchangeRate
import io.github.rupakc.travelplanner.core.model.section.ForexData
import io.github.rupakc.travelplanner.core.model.section.GettingAroundData
import io.github.rupakc.travelplanner.core.model.section.LocalCurrency
import io.github.rupakc.travelplanner.core.model.section.MoneyTip
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.Tipping
import io.github.rupakc.travelplanner.core.model.section.TransportOption
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.feature.results.R

// ---- Getting around -----------------------------------------------------------------------

/** Web typeIcons: short type tag shown in the cyan square ("?" for unknown types). */
internal fun transportTag(type: String?): String =
    when (type) {
        "metro" -> "MRT"
        "bus" -> "BUS"
        "tram" -> "TRAM"
        "taxi" -> "TAXI"
        "rideshare" -> "RIDE"
        "bike" -> "BIKE"
        "scooter" -> "MOTO"
        "walking" -> "WALK"
        "water_transport" -> "BOAT"
        "tourist_transport" -> "TOUR"
        "train" -> "RAIL"
        "long_distance_bus" -> "COACH"
        "domestic_flight" -> "FLY"
        "car_rental" -> "RENT"
        "ferry" -> "FERRY"
        else -> "?"
    }

/** Groups in the web's order: In the City (intra_city), Between Cities (inter_city), Other. */
internal fun transportGroups(options: List<TransportOption>): List<Pair<Int, List<TransportOption>>> =
    listOf(
        R.string.transport_in_city to options.filter { it.scope == "intra_city" },
        R.string.transport_between_cities to options.filter { it.scope == "inter_city" },
        R.string.transport_other to options.filter { it.scope != "intra_city" && it.scope != "inter_city" },
    ).filter { it.second.isNotEmpty() }

@Composable
internal fun GettingAroundBody(
    data: GettingAroundData,
    isSelected: (PlanItem) -> Boolean,
    onToggle: (PlanItem) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (data.options.isEmpty()) {
        EmptyState(stringResource(R.string.transport_empty), modifier)
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
        transportGroups(data.options).forEach { (title, options) ->
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                SubHeading(stringResource(title))
                AdaptiveGrid(options) { option, cell ->
                    val item = PlanItem.TransportItem(option)
                    TransportCard(option, isSelected(item), { onToggle(item) }, onOpenLink, cell)
                }
            }
        }
    }
}

@Composable
private fun TransportCard(option: TransportOption, selected: Boolean, onToggle: () -> Unit, onOpenLink: (String) -> Unit, modifier: Modifier) {
    val accent = TravelTheme.sectionAccents[SectionId.GETTING_AROUND]
    TpCard(modifier.fillMaxWidth(), selected = selected, accent = accent.bar) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                val type = option.type?.replace('_', ' ')
                Text(
                    transportTag(option.type),
                    style = TravelTheme.typography.labelMedium,
                    color = accent.icon,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .defaultMinSize(minWidth = InfoDimens.TypeTagMinWidth)
                            .background(accent.tint, TravelShape.Input)
                            .padding(TravelTheme.spacing.sm)
                            .semantics { contentDescription = type.orEmpty() },
                )
                Column(Modifier.weight(1f)) {
                    Text(option.name.orEmpty(), style = TravelTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), itemVerticalAlignment = Alignment.CenterVertically) {
                        type?.takeIf { it.isNotBlank() }?.let {
                            InfoPill(
                                it,
                                tone = Tone.Accent,
                                section =
                                    if (option.scope ==
                                        "inter_city"
                                    ) {
                                        SectionId.FLIGHTS
                                    } else {
                                        SectionId.GETTING_AROUND
                                    },
                            )
                        }
                        CityChip(cityOf(option.raw))
                    }
                }
            }
            option.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.bodyMedium) }
            LabeledLine(stringResource(R.string.transport_coverage), option.coverage)
            LabeledLine(stringResource(R.string.transport_price), option.priceInfo)
            LabeledLine(stringResource(R.string.transport_hours), option.operatingHours)
            LabeledLine(stringResource(R.string.transport_tourist_pass), option.touristPass, valueColor = accent.icon)
            option.tips?.takeIf {
                it.isNotBlank()
            }?.let { Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = TravelTheme.colors.onSurfaceVariant) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                LinkButton(stringResource(R.string.transport_more_info), option.bookingUrl, onOpenLink)
                AddToPlanButton(selected, option.name.orEmpty(), onToggle)
            }
        }
    }
}

// ---- Forex --------------------------------------------------------------------------------

/** Web ratingColors for exchange locations. */
internal fun exchangeRatingTone(rating: String?): Tone =
    when (rating) {
        "excellent" -> Tone.Success
        "good" -> Tone.Info
        "fair" -> Tone.Warning
        "poor" -> Tone.Danger
        else -> Tone.Neutral
    }

/** Web locTypeIcons (decorative). */
internal fun exchangeEmoji(type: String?): String =
    when (type) {
        "atm" -> "🏧"
        "bank" -> "🏦"
        "exchange_bureau" -> "💱"
        "hotel" -> "🏨"
        "airport" -> "✈️"
        "online" -> "🌐"
        else -> "💰"
    }

/** Web `isHome`: a rate from neither USD nor EUR is the traveller's own currency. */
internal fun isHomeRate(rate: ExchangeRate): Boolean = rate.fromCurrency != "USD" && rate.fromCurrency != "EUR"

private fun ForexData.isBlank(): Boolean =
    localCurrency == null && exchangeRates.isEmpty() && exchangeLocations.isEmpty() && cardAcceptance == null && cashAdvice == null &&
        atmInfo == null && tipping == null && moneyTips.isEmpty()

@Composable
internal fun ForexBody(data: ForexData, onOpenLink: (String) -> Unit, modifier: Modifier = Modifier) {
    if (data.isError || data.isBlank()) {
        EmptyState(stringResource(R.string.forex_unavailable), modifier)
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
        data.localCurrency?.let { CurrencyHeader(it, data.exchangeRates) }
        if (data.exchangeLocations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                SubHeading(stringResource(R.string.forex_where))
                AdaptiveGrid(data.exchangeLocations) { loc, cell -> ExchangeLocationCard(loc, cell) }
            }
        }
        val blocks = listOfNotNull(data.cardAcceptance?.let { Block.Cards(it) }, data.cashAdvice?.let { Block.Cash(it) })
        if (blocks.isNotEmpty()) {
            AdaptiveGrid(blocks) { block, cell ->
                when (block) {
                    is Block.Cards -> CardsBlock(block.value, cell)
                    is Block.Cash -> CashBlock(block.value, cell)
                }
            }
        }
        data.atmInfo?.let { AtmBlock(it) }
        data.tipping?.let { TippingLine(it) }
        if (data.moneyTips.isNotEmpty()) MoneyTips(data.moneyTips)
        if (data.sourceUrls.isNotEmpty()) {
            FlowRow {
                data.sourceUrls.forEachIndexed { i, url -> LinkButton(stringResource(R.string.info_source_n, i + 1), url, onOpenLink) }
            }
        }
    }
}

private sealed interface Block {
    data class Cards(val value: CardAcceptance) : Block

    data class Cash(val value: CashAdvice) : Block
}

@Composable
private fun Panel(tone: Tone, modifier: Modifier = Modifier, section: SectionId? = null, content: @Composable () -> Unit) {
    val colors = toneColors(tone, section)
    Surface(
        modifier.fillMaxWidth(),
        shape = TravelShape.ItemCard,
        color = colors.container,
        border = BorderStroke(InfoDimens.Border, colors.content.copy(alpha = PANEL_BORDER_ALPHA)),
    ) {
        Column(Modifier.padding(TravelTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) { content() }
    }
}

private const val PANEL_BORDER_ALPHA = 0.25f

@Composable
private fun CurrencyHeader(currency: LocalCurrency, rates: List<ExchangeRate>) {
    Panel(Tone.Accent, section = SectionId.FOREX) {
        SubHeading(
            listOfNotNull(
                currency.name,
                currency.code?.let {
                    "($it)"
                },
                currency.symbol,
            ).joinToString(" "),
            color = TravelTheme.sectionAccents[SectionId.FOREX].icon,
        )
        if (rates.isNotEmpty()) {
            AdaptiveGrid(rates) { rate, cell -> RateTile(rate, cell) }
        }
    }
}

@Composable
private fun RateTile(rate: ExchangeRate, modifier: Modifier) {
    val home = isHomeRate(rate)
    val colors = if (home) toneColors(Tone.Warning) else ToneColors(TravelTheme.colors.surface, TravelTheme.sectionAccents[SectionId.FOREX].icon)
    Column(modifier.background(colors.container, TravelShape.Input).padding(TravelTheme.spacing.md)) {
        if (home) Text(stringResource(R.string.forex_your_currency), style = TravelTheme.typography.labelSmall, color = colors.content)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), itemVerticalAlignment = Alignment.CenterVertically) {
            if (rate.rate != null) {
                Text(rate.rate!!.plain(), style = TravelTheme.typography.titleLarge, color = colors.content)
            } else {
                Text(stringResource(R.string.forex_rate_loading), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            rate.description?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
        }
        rate.trend?.takeIf { it.isNotBlank() && it != "Live rate loading..." }?.let { MetaText(it) }
    }
}

@Composable
private fun ExchangeLocationCard(loc: ExchangeLocation, modifier: Modifier) {
    TpCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(exchangeEmoji(loc.type), style = TravelTheme.typography.titleLarge)
                Column {
                    Text(loc.name.orEmpty(), style = TravelTheme.typography.titleSmall)
                    loc.rating?.takeIf { it.isNotBlank() }?.let { InfoPill(it, tone = exchangeRatingTone(it)) }
                }
            }
            loc.description?.let { Text(it, style = TravelTheme.typography.bodySmall) }
            LabeledLine(stringResource(R.string.forex_fees), loc.fees)
            loc.tip?.takeIf {
                it.isNotBlank()
            }?.let { Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = TravelTheme.extendedColors.success) }
        }
    }
}

@Composable
private fun CardsBlock(cards: CardAcceptance, modifier: Modifier) {
    Panel(Tone.Info, modifier) {
        SubHeading(stringResource(R.string.forex_cards))
        LabeledLine(stringResource(R.string.forex_visa_mc), cards.visaMastercard)
        LabeledLine(stringResource(R.string.forex_amex), cards.amex)
        LabeledLine(stringResource(R.string.forex_contactless), cards.contactless)
        LabeledLine(stringResource(R.string.forex_digital), cards.digitalWallets)
        LabeledLine(stringResource(R.string.forex_surcharges), cards.surcharges, valueColor = TravelTheme.extendedColors.warning)
    }
}

@Composable
private fun CashBlock(cash: CashAdvice, modifier: Modifier) {
    Panel(Tone.Warning, modifier) {
        SubHeading(stringResource(R.string.forex_cash))
        cash.cashDependency?.takeIf { it.isNotBlank() }?.let {
            val tone =
                when (it) {
                    "high" -> Tone.Danger
                    "medium" -> Tone.Warning
                    else -> Tone.Success
                }
            InfoPill(stringResource(R.string.forex_cash_dependency, it), tone = tone)
        }
        cash.recommendation?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.bodySmall) }
        LabeledLine(stringResource(R.string.forex_denominations), cash.denominations)
        LabeledLine(stringResource(R.string.forex_bring_usd_eur), cash.bringUsdEur)
    }
}

@Composable
private fun AtmBlock(atm: AtmInfo) {
    Panel(Tone.Accent, section = SectionId.HOTELS) {
        SubHeading(stringResource(R.string.forex_atm))
        LabeledLine(stringResource(R.string.forex_availability), atm.availability)
        LabeledLine(stringResource(R.string.forex_networks), atm.networks)
        LabeledLine(stringResource(R.string.forex_limit), atm.withdrawalLimit)
        LabeledLine(stringResource(R.string.forex_fees), atm.fees)
        atm.bestOption?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.extendedColors.success) }
    }
}

@Composable
private fun TippingLine(tipping: Tipping) {
    val expected = tipping.expected == true
    val head = stringResource(if (expected) R.string.forex_tipping_expected else R.string.forex_tipping_not_expected)
    Callout(listOfNotNull(head, tipping.description?.takeIf { it.isNotBlank() }).joinToString(" — "), tone = if (expected) Tone.Warning else Tone.Success)
}

@Composable
private fun MoneyTips(tips: List<MoneyTip>) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        SubHeading(stringResource(R.string.forex_money_tips))
        tips.forEach { tip ->
            Panel(Tone.Success) {
                Text(tip.title.orEmpty(), style = TravelTheme.typography.titleSmall)
                tip.body?.let { Text(it, style = TravelTheme.typography.bodySmall) }
            }
        }
    }
}

private val previewTransport =
    GettingAroundData(
        options =
            listOf(
                TransportOption(name = "Lisbon Metro", type = "metro", scope = "intra_city", priceInfo = "€1.85 per ride", operatingHours = "06:30–01:00"),
                TransportOption(name = "CP Alfa Pendular", type = "train", scope = "inter_city", bookingUrl = "https://www.cp.pt"),
            ),
    )

private val previewForex =
    ForexData(
        localCurrency = LocalCurrency("Euro", "EUR", "€"),
        exchangeRates = listOf(ExchangeRate("USD", "EUR", 0.92, "1 USD"), ExchangeRate("GBP", "EUR", null, "1 GBP")),
        cashAdvice = CashAdvice("low", "Cards work almost everywhere."),
        tipping = Tipping(false, "Round up in cafés."),
        sourceUrls = listOf("https://example.org/fx"),
    )

@Preview(name = "Getting around and forex – light")
@Composable
private fun TransportForexLightPreview() =
    PreviewSurface(ThemeMode.LIGHT) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            GettingAroundBody(previewTransport, { false }, {}, {})
            ForexBody(previewForex, {})
        }
    }

@Preview(name = "Getting around and forex – dark")
@Composable
private fun TransportForexDarkPreview() =
    PreviewSurface(ThemeMode.DARK) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            GettingAroundBody(previewTransport, { true }, {}, {})
            ForexBody(previewForex, {})
        }
    }
