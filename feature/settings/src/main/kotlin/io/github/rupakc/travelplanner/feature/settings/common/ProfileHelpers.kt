package io.github.rupakc.travelplanner.feature.settings.common

import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType

/** Parses stored type names (`TravellerProfile.primaryType` / `secondaryTypes`), dropping unknown ones. */
internal fun travellerTypesOf(primary: String?, secondary: List<String>): List<TravellerType> =
    (listOfNotNull(primary) + secondary).distinct().mapNotNull { name -> TravellerType.entries.firstOrNull { it.name == name } }
