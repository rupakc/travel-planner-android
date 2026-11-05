package io.github.rupakc.travelplanner.feature.results.sections.info

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.section.PackingCategory
import io.github.rupakc.travelplanner.core.model.section.PackingListData
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject

/**
 * Checked and custom packing items, shaped like the web's `selections.packing_list`
 * (`checked_items: {"<category>::<item>": bool}`, `custom_items: {"<category>": [item]}`).
 */
internal data class PackingState(val checked: Map<String, Boolean> = emptyMap(), val custom: Map<String, List<String>> = emptyMap()) {
    val checkedCount: Int get() = checked.values.count { it }

    fun isChecked(category: String, item: String): Boolean = checked[key(category, item)] == true

    /** Web `toggleItem`: flips one item. */
    fun toggle(category: String, item: String): PackingState {
        val k = key(category, item)
        return copy(checked = checked + (k to (checked[k] != true)))
    }

    /** Web `addCustomItem`: appends a trimmed, non-blank item to [category]. */
    fun addCustom(category: String, item: String): PackingState {
        val value = item.trim()
        if (value.isEmpty()) return this
        return copy(custom = custom + (category to custom[category].orEmpty() + value))
    }

    /** Web `totalItems`: the list's own total plus every custom item. */
    fun total(data: PackingListData): Int = (data.totalItems ?: 0) + custom.values.sumOf { it.size }

    /** The `selections.packing_list` object the web saves (`buildPlanState`). */
    fun toPlanJson(data: PackingListData): JsonObject =
        buildJsonObject {
            put("categories", TravelJson.encodeToJsonElement(ListSerializer(PackingCategory.serializer()), data.categories))
            data.luggageNote?.let { put("luggage_note", JsonPrimitive(it)) }
            putState(this@PackingState)
            put("total_items", JsonPrimitive(total(data)))
            put("checked_count", JsonPrimitive(checkedCount))
        }

    /** Compact text form for `rememberSaveable`. */
    fun encode(): String = TravelJson.encodeToString(JsonObject.serializer(), buildJsonObject { putState(this@PackingState) })

    companion object {
        fun key(category: String, item: String) = "$category::$item"

        /** Reads a saved `packing_list` (null/malformed → empty). */
        fun fromPlan(json: JsonObject?): PackingState {
            if (json == null) return PackingState()
            val checked =
                (json["checked_items"] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.booleanOrNull?.let { k to it } }?.toMap().orEmpty()
            val custom =
                (json["custom_items"] as? JsonObject)
                    ?.mapValues { (_, v) -> (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty() }
                    .orEmpty()
            return PackingState(checked, custom)
        }

        fun decode(text: String): PackingState =
            runCatching { fromPlan(TravelJson.parseToJsonElement(text) as? JsonObject) }.getOrDefault(PackingState())
    }
}

private fun JsonObjectBuilder.putState(state: PackingState) {
    put("checked_items", JsonObject(state.checked.mapValues { JsonPrimitive(it.value) }))
    put("custom_items", JsonObject(state.custom.mapValues { (_, items) -> JsonArray(items.map(::JsonPrimitive)) }))
}
