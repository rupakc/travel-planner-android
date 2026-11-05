package io.github.rupakc.travelplanner.feature.search

import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.model.navigation.SearchModes

/** The four Search modes (web segmented toggle), in display order; [feature] = the optional surface that hides the chip. */
enum class SearchMode(val routeValue: String, val feature: FeatureId?) {
    KNOWN(SearchModes.KNOWN, null),
    SURPRISE(SearchModes.SURPRISE, FeatureId.SURPRISE_ME),
    COMPARE(SearchModes.COMPARE, FeatureId.COMPARE),
    IMPORT(SearchModes.IMPORT, FeatureId.IMPORT_LINK),
    ;

    companion object {
        /** Route `mode` → mode; unknown or null = known destination (web "discover" is accepted as Surprise me). */
        fun fromRoute(value: String?): SearchMode =
            when (value) {
                "discover" -> SURPRISE
                else -> entries.firstOrNull { it.routeValue == value } ?: KNOWN
            }
    }
}

/**
 * Mode chips that show (spec §6.3): a chip hides when its feature isn't visible for the user,
 * except the [current] mode. [visibleFeatures] null = the catalogue isn't available → show all.
 */
fun visibleModes(visibleFeatures: Set<FeatureId>?, current: SearchMode): List<SearchMode> =
    SearchMode.entries.filter { mode ->
        mode == current || mode.feature == null || visibleFeatures == null || mode.feature in visibleFeatures
    }
