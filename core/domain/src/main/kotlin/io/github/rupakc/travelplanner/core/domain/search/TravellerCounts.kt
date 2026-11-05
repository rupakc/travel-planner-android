package io.github.rupakc.travelplanner.core.domain.search

/** The four traveller groups of the web TravelerPanel, in its display order. */
enum class TravellerKind { ADULTS, SENIORS, CHILDREN, INFANTS }

/**
 * Traveller counts with the web TravelerPanel rules: no group below 0, never fewer than
 * 1 traveller in total. The backend caps each group and `num_travelers` at 20
 * (api-contract §1, preferences 0..20), so the total never goes above [MAX_TOTAL].
 */
data class TravellerCounts(val adults: Int = 1, val children: Int = 0, val seniors: Int = 0, val infants: Int = 0) {
    val total: Int get() = adults + children + seniors + infants

    operator fun get(kind: TravellerKind): Int =
        when (kind) {
            TravellerKind.ADULTS -> adults
            TravellerKind.SENIORS -> seniors
            TravellerKind.CHILDREN -> children
            TravellerKind.INFANTS -> infants
        }

    /** Web `change(key, delta)`: ignored when it would leave a group negative or nobody travelling (or exceed [MAX_TOTAL]). */
    fun change(kind: TravellerKind, delta: Int): TravellerCounts {
        val next = this[kind] + delta
        if (next < 0) return this
        val updated =
            when (kind) {
                TravellerKind.ADULTS -> copy(adults = next)
                TravellerKind.SENIORS -> copy(seniors = next)
                TravellerKind.CHILDREN -> copy(children = next)
                TravellerKind.INFANTS -> copy(infants = next)
            }
        return if (updated.total < 1 || updated.total > MAX_TOTAL) this else updated
    }

    fun canDecrement(kind: TravellerKind): Boolean = change(kind, -1) != this

    fun canIncrement(kind: TravellerKind): Boolean = change(kind, 1) != this

    companion object {
        const val MAX_TOTAL = 20

        /** Counts from stored values, or null when they don't describe a valid group (negative, nobody, over the cap). */
        fun of(adults: Int, children: Int, seniors: Int, infants: Int): TravellerCounts? =
            TravellerCounts(adults, children, seniors, infants).takeIf { c ->
                listOf(adults, children, seniors, infants).all { it >= 0 } && c.total in 1..MAX_TOTAL
            }
    }
}
