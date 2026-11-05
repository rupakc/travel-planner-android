package io.github.rupakc.travelplanner.feature.home

import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.section.SectionId

/** Catalogue icon names (`FeatureDescriptor.iconName`) by feature. */
internal fun FeatureCatalogue.iconNames(): Map<FeatureId, String> = all.associate { it.id to it.iconName }

/** The results section a feature lives in (for its accent colour), or null for stand-alone tools. */
internal fun FeatureId.sectionId(): SectionId? =
    when (this) {
        FeatureId.PACKING_LIST -> SectionId.PACKING_LIST
        FeatureId.EMERGENCY_CARD -> SectionId.EMERGENCY_CARD
        FeatureId.WALKING_TOURS -> SectionId.WALKING_TOURS
        FeatureId.EVENTS -> SectionId.EVENTS
        FeatureId.VISA_CHECKER -> SectionId.VISA
        FeatureId.FOREX -> SectionId.FOREX
        FeatureId.SIM -> SectionId.SIM
        FeatureId.TRIP_HEALTH_CHECK -> SectionId.STRESS_TEST
        FeatureId.ITINERARY_EDITOR -> SectionId.ITINERARY
        FeatureId.DEAL_ALERTS -> SectionId.FLIGHTS
        else -> null
    }
