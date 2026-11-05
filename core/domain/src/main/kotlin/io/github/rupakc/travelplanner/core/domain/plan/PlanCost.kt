package io.github.rupakc.travelplanner.core.domain.plan

import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Hotel
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * Port of `frontend/src/utils/planHelpers.js` (cost, budget status, plan name).
 * Prices are LLM output: anything not a finite positive number counts as 0.
 */

/** Cost categories of [computePlanCostBreakdown] (web keys `flights`, `hotel`, `activities`, `sim`). */
enum class CostCategory(val key: String) { FLIGHTS("flights"), HOTEL("hotel"), ACTIVITIES("activities"), SIM("sim") }

/** One category's share of the plan cost (USD). Labels are resolved by the UI. */
data class CostPart(val category: CostCategory, val amount: Double)

/** Hotels priced per night with unknown dates assume a week (web parity). */
const val DEFAULT_TRIP_NIGHTS = 7

private fun price(value: Double?): Double = if (value != null && value.isFinite() && value > 0) value else 0.0

/** Whole days between two ISO dates, or null when either is missing/unparseable. */
internal fun daysBetween(from: String?, to: String?): Long? {
    if (from.isNullOrBlank() || to.isNullOrBlank()) return null
    val start = runCatching { LocalDate.parse(from) }.getOrNull() ?: return null
    val end = runCatching { LocalDate.parse(to) }.getOrNull() ?: return null
    return ChronoUnit.DAYS.between(start, end)
}

private fun hotelCost(hotel: Hotel?, search: TravelSearchRequest?): Double = hotelCost(hotel, search?.departureDate, search?.returnDate)

private fun hotelCost(hotel: Hotel?, departureDate: String?, returnDate: String?): Double {
    if (hotel == null) return 0.0
    price(hotel.totalPriceUsd).takeIf { it > 0 }?.let { return it }
    val hasDates = !returnDate.isNullOrBlank() && !departureDate.isNullOrBlank()
    val nights =
        if (hasDates) {
            // Web: NaN for unparseable dates, which then drops the hotel from the breakdown.
            daysBetween(departureDate, returnDate)?.coerceAtLeast(1) ?: return 0.0
        } else {
            DEFAULT_TRIP_NIGHTS.toLong()
        }
    return price(hotel.pricePerNightUsd) * nights
}

/** Per-category split of the plan's cost, biggest first; only categories with a priced item. */
fun computePlanCostBreakdown(selections: PlanSelections?, search: TravelSearchRequest? = null): List<CostPart> =
    computePlanCostBreakdown(selections, search?.departureDate, search?.returnDate)

/** [computePlanCostBreakdown] from the trip dates alone (saved / shared plans keep only `search_data` JSON). */
fun computePlanCostBreakdown(selections: PlanSelections?, departureDate: String?, returnDate: String?): List<CostPart> {
    if (selections == null) return emptyList()
    val parts =
        listOf(
            CostPart(CostCategory.FLIGHTS, price(selections.flight?.priceUsd) + selections.flights.sumOf { price(it.priceUsd) }),
            CostPart(CostCategory.HOTEL, hotelCost(selections.hotel, departureDate, returnDate)),
            CostPart(CostCategory.ACTIVITIES, selections.activities.sumOf { price(it.priceUsd) }),
            CostPart(CostCategory.SIM, price(selections.sim?.priceUsd)),
        )
    return parts.filter { it.amount > 0 }.sortedByDescending { it.amount }
}

/** Total estimated plan cost in USD (web `computePlanCost`). */
fun computePlanCost(selections: PlanSelections?, search: TravelSearchRequest? = null): Double =
    computePlanCostBreakdown(selections, search).sumOf { it.amount }

/** [computePlanCost] from the trip dates alone. */
fun computePlanCost(selections: PlanSelections?, departureDate: String?, returnDate: String?): Double =
    computePlanCostBreakdown(selections, departureDate, returnDate).sumOf { it.amount }

/** Web `getBudgetStatus`: [amount] is the rounded absolute difference. */
data class BudgetStatus(val over: Boolean, val amount: Long)

/** Null without a positive budget or a positive cost. */
fun getBudgetStatus(cost: Double, budgetUsd: Double?): BudgetStatus? {
    val budget = budgetUsd?.takeIf { it != 0.0 && it.isFinite() } ?: return null
    if (cost <= 0) return null
    val diff = budget - cost
    return if (diff >= 0) BudgetStatus(over = false, amount = diff.roundToLong()) else BudgetStatus(over = true, amount = abs(diff).roundToLong())
}

private val THEMES =
    mapOf(
        "food" to listOf("Foodie", "Culinary", "Gourmet", "Tasty"),
        "history" to listOf("Historic", "Ancient", "Timeless", "Heritage"),
        "adventure" to listOf("Wild", "Epic", "Daring", "Thrilling"),
        "culture" to listOf("Cultural", "Artistic", "Vibrant", "Soulful"),
        "nature" to listOf("Green", "Natural", "Serene", "Wilderness"),
        "shopping" to listOf("Shopaholic", "Boutique", "Bazaar", "Market"),
        "nightlife" to listOf("Midnight", "Neon", "After-Dark", "Starlit"),
        "wellness" to listOf("Zen", "Blissful", "Peaceful", "Tranquil"),
        "art" to listOf("Artsy", "Creative", "Gallery", "Canvas"),
        "family" to listOf("Family", "Joyful", "Sunny", "Playful"),
    )

private val TRIP_NOUNS = listOf("Adventure", "Escape", "Journey", "Odyssey", "Expedition", "Quest", "Voyage", "Getaway", "Trail", "Safari")

private val SEASONAL =
    mapOf(
        "spring" to listOf("Blossom", "Fresh", "Springtime"),
        "summer" to listOf("Sunny", "Sizzling", "Tropical"),
        "fall" to listOf("Golden", "Autumn", "Amber"),
        "winter" to listOf("Frosty", "Cozy", "Snowbound"),
    )

private val GENERIC = listOf("Grand", "Dreamy", "Cosmic", "Wanderlust")

@Suppress("MagicNumber")
private fun season(date: String?): String? {
    val month = date?.let { runCatching { LocalDate.parse(it.take(10)).monthValue }.getOrNull() } ?: return null
    return when (month) {
        in 3..5 -> "spring"
        in 6..8 -> "summer"
        in 9..11 -> "fall"
        else -> "winter"
    }
}

/**
 * Web `generatePlanName`: "The {Adj} {Destination} {Noun}". The adjective comes
 * from the first interest's theme, else the departure season, else a generic list.
 * English by design (the web stores the generated name; it is user-editable).
 */
fun generatePlanName(
    destination: String?,
    interests: List<String> = emptyList(),
    departureDate: String? = null,
    random: kotlin.random.Random = kotlin.random.Random.Default,
): String {
    val adjectives = interests.firstOrNull()?.let(THEMES::get) ?: season(departureDate)?.let(SEASONAL::get) ?: GENERIC
    val dest = destination?.takeIf(String::isNotBlank) ?: "Mystery"
    return "The ${adjectives.random(random)} $dest ${TRIP_NOUNS.random(random)}"
}
