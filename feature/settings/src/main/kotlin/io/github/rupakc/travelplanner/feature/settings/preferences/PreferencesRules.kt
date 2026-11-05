package io.github.rupakc.travelplanner.feature.settings.preferences

import io.github.rupakc.travelplanner.core.model.preferences.Preferences

/** The four traveller counts of the web TravelerPanel (in its display order). */
enum class TravellerField { ADULTS, SENIORS, CHILDREN, INFANTS }

/** Web interest ids (search + preferences chips), in web order. */
val InterestIds: List<String> = listOf("food", "history", "adventure", "culture", "nature", "shopping", "nightlife", "wellness", "art", "family")

/** Web accessibility ids (TravelerPanel checkboxes), in web order. */
val AccessibilityIds: List<String> = listOf("wheelchair", "visual_impairment", "hearing_impairment", "cognitive_disability")

/** Web budget categories (`budget_category`), in web order. */
val BudgetCategories: List<String> = listOf("low", "medium", "high")

/** Pure Preferences form rules, ported from the web (TravelerPanel, TagInput, AlertEmailField, PreferencesPage). */
object PreferencesRules {
    /** Backend cap: each count 0..20 and `num_travelers` 1..20, so the whole group is at most 20. */
    const val MAX_COUNT = 20

    // Mirrors the server's check: local@domain.tld, no spaces.
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")

    fun count(prefs: Preferences, field: TravellerField): Int =
        when (field) {
            TravellerField.ADULTS -> prefs.adults
            TravellerField.SENIORS -> prefs.seniors
            TravellerField.CHILDREN -> prefs.children
            TravellerField.INFANTS -> prefs.infants
        }

    fun total(prefs: Preferences): Int = prefs.adults + prefs.seniors + prefs.children + prefs.infants

    /** Web `change`: never below 0, never a group total below 1; the group total never above [MAX_COUNT]. */
    fun change(prefs: Preferences, field: TravellerField, delta: Int): Preferences {
        val next = (count(prefs, field) + delta).coerceIn(0, MAX_COUNT)
        val newTotal = total(prefs) - count(prefs, field) + next
        if (newTotal < 1 || newTotal > MAX_COUNT) return prefs
        return when (field) {
            TravellerField.ADULTS -> prefs.copy(adults = next)
            TravellerField.SENIORS -> prefs.copy(seniors = next)
            TravellerField.CHILDREN -> prefs.copy(children = next)
            TravellerField.INFANTS -> prefs.copy(infants = next)
        }
    }

    /** Web minus-button `disabled` rule. */
    fun canDecrease(prefs: Preferences, field: TravellerField): Boolean {
        val value = count(prefs, field)
        if (value == 0) return false
        return !(field == TravellerField.ADULTS && total(prefs) <= 1 && value <= 1)
    }

    fun canIncrease(prefs: Preferences): Boolean = total(prefs) < MAX_COUNT

    /** Web TagInput `addTag`: trimmed, non-empty, no duplicates. */
    fun addTag(tags: List<String>, raw: String): List<String> {
        val tag = raw.trim()
        return if (tag.isEmpty() || tag in tags) tags else tags + tag
    }

    /** Adds every comma-separated part (the web adds on Enter or comma). */
    fun addTags(tags: List<String>, raw: String): List<String> = raw.split(',').fold(tags, ::addTag)

    fun toggle(values: List<String>, id: String): List<String> = if (id in values) values - id else values + id

    /** Web save: `num_travelers = max(1, adults + children + seniors + infants)`. */
    fun forSave(prefs: Preferences): Preferences = prefs.copy(numTravelers = maxOf(1, total(prefs)))

    fun isEmail(value: String): Boolean = emailRegex.matches(value.trim())
}
