package io.github.rupakc.travelplanner.core.model.section

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientString
import io.github.rupakc.travelplanner.core.model.json.LenientStringList
import io.github.rupakc.travelplanner.core.model.json.RawBacked
import io.github.rupakc.travelplanner.core.model.json.RawBackedListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/** §3.4.8 visa. The static Phase-0 version only has [requirement]. */
@Serializable
data class VisaData(
    val requirement: VisaRequirement? = null,
    val vaccinations: Vaccinations? = null,
    val customs: Customs? = null,
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class VisaRequirement(
    /** visa-free | visa-on-arrival | e-visa | visa-required */
    @SerialName("visa_type") val visaType: String? = null,
    @SerialName("max_stay_days") val maxStayDays: LenientInt = null,
    val requirements: LenientStringList = emptyList(),
    @SerialName("processing_time") val processingTime: LenientString = null,
    @SerialName("fee_usd") val feeUsd: LenientDouble = null,
    @SerialName("official_url") val officialUrl: String? = null,
    val confidence: String? = null,
    val notes: LenientString = null,
)

@Serializable
data class Vaccinations(
    val required: LenientStringList = emptyList(),
    val recommended: LenientStringList = emptyList(),
    @SerialName("covid_status") val covidStatus: LenientString = null,
    val notes: LenientString = null,
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
data class Customs(
    @SerialName("duty_free_allowances") val dutyFreeAllowances: DutyFreeAllowances? = null,
    @SerialName("prohibited_items") val prohibitedItems: LenientStringList = emptyList(),
    @SerialName("declaration_required") val declarationRequired: LenientStringList = emptyList(),
    val notes: LenientString = null,
    @SerialName("source_url") val sourceUrl: String? = null,
)

@Serializable
data class DutyFreeAllowances(
    val alcohol: LenientString = null,
    val tobacco: LenientString = null,
    val currency: LenientString = null,
    val gifts: LenientString = null,
)

/** §3.4.9 SIM / eSIM plans. */
@Serializable
data class SimData(
    @Serializable(with = SimPlanListSerializer::class) val plans: List<SimPlan> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class SimPlan(
    val provider: String? = null,
    @SerialName("plan_name") val planName: String? = null,
    @SerialName("data_gb") val dataGb: LenientString = null,
    @SerialName("validity_days") val validityDays: LenientInt = null,
    @SerialName("price_usd") val priceUsd: LenientDouble = null,
    @SerialName("purchase_location") val purchaseLocation: LenientString = null,
    val url: String? = null,
    val snippet: String? = null,
    @SerialName("network_quality") val networkQuality: NetworkQuality? = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<SimPlan> {
    override fun withRaw(raw: JsonObject): SimPlan = copy(raw = raw)
}

@Serializable
data class NetworkQuality(
    val speed: LenientString = null,
    /** excellent | good | moderate | … */
    @SerialName("coverage_rating") val coverageRating: String? = null,
    @SerialName("coverage_description") val coverageDescription: String? = null,
)

object SimPlanListSerializer : RawBackedListSerializer<SimPlan>(SimPlan.serializer())

/** §3.4.10 tips. */
@Serializable
data class TipsData(
    @Serializable(with = TipListSerializer::class) val tips: List<Tip> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class Tip(
    val title: String? = null,
    val body: String? = null,
    val category: String? = null,
    val severity: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<Tip> {
    override fun withRaw(raw: JsonObject): Tip = copy(raw = raw)
}

object TipListSerializer : RawBackedListSerializer<Tip>(Tip.serializer())

/** §3.4.11 emergency card. Multi-city uses [cities] with the same keys per city. */
@Serializable
data class EmergencyCardData(
    @SerialName("emergency_numbers") val emergencyNumbers: EmergencyNumbers? = null,
    val embassy: Embassy? = null,
    val hospitals: List<Hospital> = emptyList(),
    @SerialName("local_phrases") val localPhrases: List<LocalPhrase> = emptyList(),
    @SerialName("local_laws") val localLaws: List<LocalLaw> = emptyList(),
    @SerialName("home_country_note") val homeCountryNote: LenientString = null,
    val cities: List<EmergencyCity> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class EmergencyCity(
    val city: String? = null,
    @SerialName("emergency_numbers") val emergencyNumbers: EmergencyNumbers? = null,
    val embassy: Embassy? = null,
    val hospitals: List<Hospital> = emptyList(),
    @SerialName("local_phrases") val localPhrases: List<LocalPhrase> = emptyList(),
    @SerialName("local_laws") val localLaws: List<LocalLaw> = emptyList(),
    @SerialName("home_country_note") val homeCountryNote: LenientString = null,
)

@Serializable
data class EmergencyNumbers(
    val police: LenientString = null,
    val ambulance: LenientString = null,
    val fire: LenientString = null,
    @SerialName("tourist_police") val touristPolice: LenientString = null,
)

@Serializable
data class Embassy(
    val name: String? = null,
    val address: String? = null,
    val phone: LenientString = null,
    @SerialName("emergency_after_hours") val emergencyAfterHours: LenientString = null,
    val website: String? = null,
    val hours: LenientString = null,
    val note: LenientString = null,
)

@Serializable
data class Hospital(
    val name: String? = null,
    val phone: LenientString = null,
    val address: String? = null,
    val notes: LenientString = null,
)

@Serializable
data class LocalPhrase(val english: String? = null, val local: String? = null, val phonetic: String? = null)

@Serializable
data class LocalLaw(
    val law: String? = null,
    val detail: String? = null,
    /** critical | warning */
    val severity: String? = null,
)

/** §3.4.12 getting around. */
@Serializable
data class GettingAroundData(
    @Serializable(with = TransportOptionListSerializer::class) val options: List<TransportOption> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class TransportOption(
    val name: String? = null,
    val type: String? = null,
    /** intra_city | inter_city */
    val scope: String? = null,
    val description: String? = null,
    val coverage: LenientString = null,
    @SerialName("price_info") val priceInfo: LenientString = null,
    @SerialName("operating_hours") val operatingHours: LenientString = null,
    val tips: LenientString = null,
    @SerialName("booking_url") val bookingUrl: String? = null,
    @SerialName("tourist_pass") val touristPass: LenientString = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<TransportOption> {
    override fun withRaw(raw: JsonObject): TransportOption = copy(raw = raw)
}

object TransportOptionListSerializer : RawBackedListSerializer<TransportOption>(TransportOption.serializer())

/** §3.4.13 forex. */
@Serializable
data class ForexData(
    @SerialName("local_currency") val localCurrency: LocalCurrency? = null,
    @SerialName("exchange_rates") val exchangeRates: List<ExchangeRate> = emptyList(),
    @SerialName("exchange_locations") val exchangeLocations: List<ExchangeLocation> = emptyList(),
    @SerialName("card_acceptance") val cardAcceptance: CardAcceptance? = null,
    @SerialName("cash_advice") val cashAdvice: CashAdvice? = null,
    @SerialName("atm_info") val atmInfo: AtmInfo? = null,
    val tipping: Tipping? = null,
    @SerialName("money_tips") val moneyTips: List<MoneyTip> = emptyList(),
    @SerialName("source_urls") val sourceUrls: LenientStringList = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class LocalCurrency(val name: String? = null, val code: String? = null, val symbol: String? = null)

@Serializable
data class ExchangeRate(
    @SerialName("from_currency") val fromCurrency: String? = null,
    @SerialName("to_currency") val toCurrency: String? = null,
    val rate: LenientDouble = null,
    val description: String? = null,
    val trend: LenientString = null,
)

@Serializable
data class ExchangeLocation(
    val type: String? = null,
    val name: String? = null,
    val description: String? = null,
    val rating: LenientString = null,
    val fees: LenientString = null,
    val tip: LenientString = null,
)

@Serializable
data class CardAcceptance(
    @SerialName("visa_mastercard") val visaMastercard: LenientString = null,
    val amex: LenientString = null,
    val contactless: LenientString = null,
    @SerialName("digital_wallets") val digitalWallets: LenientString = null,
    val surcharges: LenientString = null,
)

@Serializable
data class CashAdvice(
    /** high | medium | low */
    @SerialName("cash_dependency") val cashDependency: String? = null,
    val recommendation: LenientString = null,
    val denominations: LenientString = null,
    @SerialName("bring_usd_eur") val bringUsdEur: LenientString = null,
)

@Serializable
data class AtmInfo(
    val availability: LenientString = null,
    val networks: LenientString = null,
    @SerialName("withdrawal_limit") val withdrawalLimit: LenientString = null,
    val fees: LenientString = null,
    @SerialName("best_option") val bestOption: LenientString = null,
)

@Serializable
data class Tipping(val expected: LenientBoolean = null, val description: String? = null)

@Serializable
data class MoneyTip(val title: String? = null, val body: String? = null)
