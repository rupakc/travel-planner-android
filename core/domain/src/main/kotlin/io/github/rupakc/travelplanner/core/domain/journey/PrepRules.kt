package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.model.trips.PrepItem
import io.github.rupakc.travelplanner.core.model.trips.PrepSummary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Client side of the prep checklist rules (Trips-API "Prep checklist"): counts, optimistic toggles, due state, safe links. */
object PrepRules {
    const val STATUS_TODO = "todo"
    const val STATUS_DONE = "done"
    const val STATUS_NA = "na"

    /** How an open item's due date is presented. */
    enum class Due { None, Upcoming, Overdue }

    /** `total` counts items that are not `na`; `percent` = `floor(100 * done / total)` (0 without items); complete = `total > 0 && done == total`. */
    fun summarize(items: List<PrepItem>): PrepSummary {
        val live = items.filter { it.status != STATUS_NA }
        val done = live.count { it.status == STATUS_DONE }
        val total = live.size
        return PrepSummary(done = done, total = total, percent = if (total == 0) 0 else done * PERCENT / total, complete = total > 0 && done == total)
    }

    /** Auto items (the offline pack) and not-needed items have no checkbox. */
    fun canToggle(item: PrepItem): Boolean = !item.auto && item.status != STATUS_NA

    /** [items] with [id] set to done or todo, only when it can be toggled (the optimistic update). */
    fun applyToggle(items: List<PrepItem>, id: String, done: Boolean): List<PrepItem> =
        items.map { if (it.id == id && canToggle(it)) it.copy(status = if (done) STATUS_DONE else STATUS_TODO) else it }

    /** (active items, not-needed items), each in served order. */
    fun partition(items: List<PrepItem>): Pair<List<PrepItem>, List<PrepItem>> = items.partition { it.status != STATUS_NA }

    /** Due text applies to open items with a due date; the server's `overdue` decides the emphasis. */
    fun dueState(item: PrepItem): Due =
        when {
            item.due == null || item.status == STATUS_DONE || item.status == STATUS_NA -> Due.None
            item.overdue -> Due.Overdue
            else -> Due.Upcoming
        }

    /** The completeness meter's visa part: the visa item is done or not needed. */
    fun visaChecked(items: List<PrepItem>): Boolean = items.firstOrNull { it.id == "visa" }?.status.let { it == STATUS_DONE || it == STATUS_NA }

    /** The visa item's `official_url` when it is an http(s) URL, else null (never opened otherwise). */
    fun officialUrl(facts: JsonObject): String? =
        (facts["official_url"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    private const val PERCENT = 100
}
