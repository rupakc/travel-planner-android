package io.github.rupakc.travelplanner.feature.plan.model

import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

// TODO(integration): replace with core/domain plan cost (Team C ports planHelpers.js into core/domain/.../plan/).
// This file is a faithful, minimal port of computePlanCostBreakdown / computePlanCost / getBudgetStatus /
// generatePlanName so My Plan and My trips can show totals before that port is merged.

/** One cost category of a plan (web `computePlanCostBreakdown` keys). */
internal enum class CostCategory { FLIGHTS, HOTEL, ACTIVITIES, SIM }

internal data class CostPart(val category: CostCategory, val amount: Double)

/** Web `getBudgetStatus`: rounded difference to the budget. */
internal data class BudgetStatus(val over: Boolean, val amount: Long)

internal object PlanCost {
    private const val DEFAULT_NIGHTS = 7.0

    /** Prices from AI agents may be junk: only positive finite numbers count. */
    private fun price(value: Double?): Double = value?.takeIf { it.isFinite() && it > 0 } ?: 0.0

    private fun nights(departureDate: String?, returnDate: String?): Double {
        val from = departureDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val to = returnDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (from == null || to == null) return DEFAULT_NIGHTS
        return maxOf(1.0, ChronoUnit.DAYS.between(from, to).toDouble())
    }

    /** Per-category split, biggest first; only categories with a numeric price. */
    fun breakdown(selections: PlanSelections, departureDate: String?, returnDate: String?): List<CostPart> {
        val hotel = selections.hotel
        val hotelCost =
            when {
                hotel == null -> 0.0
                price(hotel.totalPriceUsd) > 0 -> price(hotel.totalPriceUsd)
                else -> price(hotel.pricePerNightUsd) * nights(departureDate, returnDate)
            }
        return listOf(
            CostPart(CostCategory.FLIGHTS, price(selections.flight?.priceUsd) + selections.flights.sumOf { price(it.priceUsd) }),
            CostPart(CostCategory.HOTEL, hotelCost),
            CostPart(CostCategory.ACTIVITIES, selections.activities.sumOf { price(it.priceUsd) }),
            CostPart(CostCategory.SIM, price(selections.sim?.priceUsd)),
        ).filter { it.amount > 0 }.sortedByDescending { it.amount }
    }

    fun total(selections: PlanSelections, departureDate: String?, returnDate: String?): Double =
        breakdown(selections, departureDate, returnDate).sumOf { it.amount }

    fun budgetStatus(cost: Double, budgetUsd: Double?): BudgetStatus? {
        if (budgetUsd == null || budgetUsd <= 0 || cost <= 0) return null
        val diff = budgetUsd - cost
        return BudgetStatus(over = diff < 0, amount = abs(diff).roundToLong())
    }

    private val themes =
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
    private val nouns = listOf("Adventure", "Escape", "Journey", "Odyssey", "Expedition", "Quest", "Voyage", "Getaway", "Trail", "Safari")
    private val seasonal =
        mapOf(
            "spring" to listOf("Blossom", "Fresh", "Springtime"),
            "summer" to listOf("Sunny", "Sizzling", "Tropical"),
            "fall" to listOf("Golden", "Autumn", "Amber"),
            "winter" to listOf("Frosty", "Cozy", "Snowbound"),
        )

    @Suppress("MagicNumber") // month boundaries, as in the web
    private fun season(date: String?): String? {
        val month = date?.let { runCatching { LocalDate.parse(it.take(10)).monthValue }.getOrNull() } ?: return null
        return when (month) {
            in 3..5 -> "spring"
            in 6..8 -> "summer"
            in 9..11 -> "fall"
            else -> "winter"
        }
    }

    /** Web `generatePlanName`: "The {Adj} {Destination} {Noun}" (English, like the web; it is saved as plan data). */
    fun generateName(destination: String?, interests: List<String>, departureDate: String?, random: Random = Random.Default): String {
        val adjectives =
            interests.firstOrNull()?.let(themes::get) ?: season(departureDate)?.let(seasonal::get) ?: listOf("Grand", "Dreamy", "Cosmic", "Wanderlust")
        val dest = destination?.takeIf(String::isNotBlank) ?: "Mystery"
        return "The ${adjectives.random(random)} $dest ${nouns.random(random)}"
    }
}
