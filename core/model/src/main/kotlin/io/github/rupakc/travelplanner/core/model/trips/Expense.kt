package io.github.rupakc.travelplanner.core.model.trips

import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The eight expense categories. */
object ExpenseCategories {
    val ALL: List<String> = listOf("flights", "lodging", "food", "transport", "activities", "shopping", "fees", "other")
}

/** `Expense`: [amount] is a decimal string in the currency's exponent (never a float). */
@Serializable
data class Expense(
    val id: Long,
    @SerialName("client_id") val clientId: String? = null,
    val amount: String = "0",
    val currency: String = "USD",
    val category: String = "other",
    val note: String? = null,
    @SerialName("spent_on") val spentOn: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** `POST /trips/{id}/expenses` (a fresh UUID [clientId] per expense). */
@Serializable
data class CreateExpenseRequest(
    @SerialName("client_id") val clientId: String,
    val amount: String,
    val currency: String,
    val category: String,
    val note: String? = null,
    @SerialName("spent_on") val spentOn: String,
)

/** `PATCH /trips/{id}/expenses/{eid}`: only the set fields are sent. */
@Serializable
data class UpdateExpenseRequest(
    val amount: String? = null,
    val currency: String? = null,
    val category: String? = null,
    val note: String? = null,
    @SerialName("spent_on") val spentOn: String? = null,
)

@Serializable
data class ExpenseTotals(
    @SerialName("by_currency") val byCurrency: Map<String, String> = emptyMap(),
    @SerialName("usd_estimate") val usdEstimate: String? = null,
    @SerialName("by_category_usd") val byCategoryUsd: Map<String, String> = emptyMap(),
    val unconverted: List<String> = emptyList(),
)

@Serializable
data class ExpenseRate(
    val local: String? = null,
    @SerialName("usd_to_local") val usdToLocal: LenientDouble = null,
    @SerialName("as_of") val asOf: String? = null,
)

/** `GET /trips/{id}/expenses`. */
@Serializable
data class ExpensesResponse(
    val expenses: List<Expense> = emptyList(),
    val totals: ExpenseTotals = ExpenseTotals(),
    val rate: ExpenseRate? = null,
)
