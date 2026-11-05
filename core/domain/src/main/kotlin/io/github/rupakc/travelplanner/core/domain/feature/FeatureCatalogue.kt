package io.github.rupakc.travelplanner.core.domain.feature

import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides

/**
 * Optional surfaces that can be shown/hidden per traveller type (spec §6.3).
 * Core surfaces (Search, Results, My Plan, Settings) are never hidden and are not listed.
 * [id] is the stable string stored in DataStore overrides.
 */
enum class FeatureId(val id: String) {
    DEAL_ALERTS("deal_alerts"),
    BEST_VALUE("best_value"),
    BUDGET_PLANNER("budget_planner"),
    COMPARE("compare"),
    SURPRISE_ME("surprise_me"),
    IMPORT_LINK("import_link"),
    CHAT("chat"),
    NEAR_ME("near_me"),
    PACKING_LIST("packing_list"),
    EMERGENCY_CARD("emergency_card"),
    WALKING_TOURS("walking_tours"),
    EVENTS("events"),
    VISA_CHECKER("visa_checker"),
    FOREX("forex"),
    SIM("sim"),
    TRIP_HEALTH_CHECK("trip_health_check"),
    ITINERARY_EDITOR("itinerary_editor"),
    ACCESSIBILITY_INFO("accessibility_info"),
    ;

    companion object {
        fun fromId(id: String): FeatureId? = entries.firstOrNull { it.id == id }
    }
}

/** Catalogue entry; [titleKey] (a string-resource name) and [iconName] are resolved by UI modules (domain stays Android-free). */
data class FeatureDescriptor(
    val id: FeatureId,
    val titleKey: String,
    val iconName: String,
    val defaultOnFor: Set<TravellerType>,
)

/** The feature catalogue (implemented by the onboarding/settings team in core:domain). */
interface FeatureCatalogue {
    val all: List<FeatureDescriptor>

    fun defaultsFor(types: Collection<TravellerType>): Set<FeatureId>

    /** visible = defaults(types) ∪ overrides.enabled − overrides.disabled (spec §6.3). */
    fun visible(types: Collection<TravellerType>, overrides: FeatureOverrides): Set<FeatureId> {
        val enabled = overrides.enabled.mapNotNull(FeatureId::fromId).toSet()
        val disabled = overrides.disabled.mapNotNull(FeatureId::fromId).toSet()
        return (defaultsFor(types) + enabled) - disabled
    }
}
