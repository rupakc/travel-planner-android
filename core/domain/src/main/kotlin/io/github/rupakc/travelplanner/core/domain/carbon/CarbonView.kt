package io.github.rupakc.travelplanner.core.domain.carbon

import io.github.rupakc.travelplanner.core.model.carbon.CarbonCategory
import io.github.rupakc.travelplanner.core.model.carbon.CarbonFootprint
import io.github.rupakc.travelplanner.core.model.carbon.CarbonHint
import io.github.rupakc.travelplanner.core.model.carbon.CarbonSource
import kotlin.math.roundToInt

/**
 * What the footprint card shows. [Hidden] renders nothing: `features.carbon` is off (an older backend), the
 * endpoint answered 404, or there is nothing to send yet (a plan without a flight or hotel).
 */
sealed interface CarbonCardState {
    /** The diet the request used (the picker's value). */
    val diet: CarbonDiet

    data class Hidden(override val diet: CarbonDiet = CarbonDiet.MIXED) : CarbonCardState

    data class Loading(override val diet: CarbonDiet) : CarbonCardState

    /** [refreshing] while a newer request (e.g. another diet) is on its way; the old numbers stay, dimmed. */
    data class Ready(val footprint: CarbonFootprint, override val diet: CarbonDiet, val refreshing: Boolean = false) : CarbonCardState

    /** Any failure but 404: "The footprint isn't available right now." */
    data class Failed(override val diet: CarbonDiet) : CarbonCardState
}

/** Pure presentation rules of the web `CarbonFootprint` card. */
object CarbonView {
    /** Category keys in the backend's fixed order. */
    val CATEGORY_KEYS = listOf("flights", "stays", "ground", "food", "activities")

    private const val PERCENT = 100
    private const val SMALL_SHARE = 0.005

    /** The categories with a known key and something counted (kg > 0), in order. */
    fun visibleCategories(footprint: CarbonFootprint): List<CarbonCategory> =
        footprint.categories.filter { it.key in CATEGORY_KEYS && (it.kg ?: 0.0) > 0 }

    /** The hint with the biggest positive saving, or null (web: first after sorting by saving, descending). */
    fun bestHint(footprint: CarbonFootprint): CarbonHint? =
        footprint.lowerCarbon.filter { (it.savingKg ?: 0.0) > 0 }.maxByOrNull { it.savingKg ?: 0.0 }

    /** Crawler sources that carry a name or an id (an older backend sends none). */
    fun liveSources(footprint: CarbonFootprint): List<CarbonSource> = footprint.crawlerSources.filter { it.name != null || it.sourceId != null }

    /** Web `pctLabel`: a share (0–1) as a whole percent; "<1%" for small non-zero shares, "0%" otherwise. */
    fun pctLabel(share: Double?): String =
        when {
            share == null || share.isNaN() || share <= 0 -> "0%"
            share < SMALL_SHARE -> "<1%"
            else -> "${(share * PERCENT).roundToInt()}%"
        }

    /** A URL that may be opened (http/https only), else null. */
    fun safeUrl(url: String?): String? = url?.trim()?.takeIf { HTTP.containsMatchIn(it) }

    /** "2026-11-19T08:00:00Z" → "2026-11-19" (web `day`); null when it does not start with a date. */
    fun day(asOf: String?): String? = asOf?.take(DATE_LENGTH)?.takeIf { DATE.matches(it) }

    private const val DATE_LENGTH = 10
    private val HTTP = Regex("^https?://", RegexOption.IGNORE_CASE)
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
}
