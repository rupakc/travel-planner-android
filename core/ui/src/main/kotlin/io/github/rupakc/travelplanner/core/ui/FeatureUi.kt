package io.github.rupakc.travelplanner.core.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.section.SectionId

/** Display title of a catalogue feature (Home tiles, Settings → Features, onboarding picker). */
@StringRes
fun FeatureId.titleRes(): Int =
    when (this) {
        FeatureId.DEAL_ALERTS -> R.string.feature_deal_alerts
        FeatureId.BEST_VALUE -> R.string.feature_best_value
        FeatureId.BUDGET_PLANNER -> R.string.feature_budget_planner
        FeatureId.COMPARE -> R.string.feature_compare
        FeatureId.SURPRISE_ME -> R.string.feature_surprise_me
        FeatureId.IMPORT_LINK -> R.string.feature_import_link
        FeatureId.CHAT -> R.string.feature_chat
        FeatureId.NEAR_ME -> R.string.feature_near_me
        FeatureId.PACKING_LIST -> R.string.feature_packing_list
        FeatureId.EMERGENCY_CARD -> R.string.feature_emergency_card
        FeatureId.WALKING_TOURS -> R.string.feature_walking_tours
        FeatureId.EVENTS -> R.string.feature_events
        FeatureId.VISA_CHECKER -> R.string.feature_visa_checker
        FeatureId.FOREX -> R.string.feature_forex
        FeatureId.SIM -> R.string.feature_sim
        FeatureId.TRIP_HEALTH_CHECK -> R.string.feature_trip_health_check
        FeatureId.ITINERARY_EDITOR -> R.string.feature_itinerary_editor
        FeatureId.ACCESSIBILITY_INFO -> R.string.feature_accessibility_info
    }

/** One-line description of what the feature does. */
@StringRes
fun FeatureId.descriptionRes(): Int =
    when (this) {
        FeatureId.DEAL_ALERTS -> R.string.feature_deal_alerts_desc
        FeatureId.BEST_VALUE -> R.string.feature_best_value_desc
        FeatureId.BUDGET_PLANNER -> R.string.feature_budget_planner_desc
        FeatureId.COMPARE -> R.string.feature_compare_desc
        FeatureId.SURPRISE_ME -> R.string.feature_surprise_me_desc
        FeatureId.IMPORT_LINK -> R.string.feature_import_link_desc
        FeatureId.CHAT -> R.string.feature_chat_desc
        FeatureId.NEAR_ME -> R.string.feature_near_me_desc
        FeatureId.PACKING_LIST -> R.string.feature_packing_list_desc
        FeatureId.EMERGENCY_CARD -> R.string.feature_emergency_card_desc
        FeatureId.WALKING_TOURS -> R.string.feature_walking_tours_desc
        FeatureId.EVENTS -> R.string.feature_events_desc
        FeatureId.VISA_CHECKER -> R.string.feature_visa_checker_desc
        FeatureId.FOREX -> R.string.feature_forex_desc
        FeatureId.SIM -> R.string.feature_sim_desc
        FeatureId.TRIP_HEALTH_CHECK -> R.string.feature_trip_health_check_desc
        FeatureId.ITINERARY_EDITOR -> R.string.feature_itinerary_editor_desc
        FeatureId.ACCESSIBILITY_INFO -> R.string.feature_accessibility_info_desc
    }

/** Decorative icon for the feature (material-icons-core only). */
fun FeatureId.icon(): ImageVector =
    when (this) {
        FeatureId.DEAL_ALERTS -> Icons.Filled.Notifications
        FeatureId.BEST_VALUE -> Icons.Filled.Star
        FeatureId.BUDGET_PLANNER -> Icons.Filled.Build
        FeatureId.COMPARE -> Icons.AutoMirrored.Filled.List
        FeatureId.SURPRISE_ME -> Icons.Filled.ThumbUp
        FeatureId.IMPORT_LINK -> Icons.Filled.Share
        FeatureId.CHAT -> Icons.AutoMirrored.Filled.Send
        FeatureId.NEAR_ME -> Icons.Filled.LocationOn
        FeatureId.PACKING_LIST -> Icons.Filled.ShoppingCart
        FeatureId.EMERGENCY_CARD -> Icons.Filled.Call
        FeatureId.WALKING_TOURS -> Icons.Filled.Place
        FeatureId.EVENTS -> Icons.Filled.DateRange
        FeatureId.VISA_CHECKER -> Icons.Filled.Lock
        FeatureId.FOREX -> Icons.Filled.Refresh
        FeatureId.SIM -> Icons.Filled.Phone
        FeatureId.TRIP_HEALTH_CHECK -> Icons.Filled.CheckCircle
        FeatureId.ITINERARY_EDITOR -> Icons.Filled.Edit
        FeatureId.ACCESSIBILITY_INFO -> Icons.Filled.Face
    }

/** The results section a feature lives in (for its accent colour), or null for stand-alone tools. */
fun FeatureId.sectionId(): SectionId? =
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

/** Short display name of a traveller type ("Culture seeker & foodie"). */
@StringRes
fun TravellerType.labelRes(): Int =
    when (this) {
        TravellerType.BUDGET_BACKPACKER -> R.string.traveller_type_budget_backpacker
        TravellerType.FAMILY -> R.string.traveller_type_family
        TravellerType.LUXURY -> R.string.traveller_type_luxury
        TravellerType.ADVENTURE -> R.string.traveller_type_adventure
        TravellerType.CULTURE_FOODIE -> R.string.traveller_type_culture_foodie
        TravellerType.BUSINESS -> R.string.traveller_type_business
        TravellerType.RELAXATION -> R.string.traveller_type_relaxation
        TravellerType.ACCESSIBLE -> R.string.traveller_type_accessible
    }

/** Parses stored type names (`TravellerProfile.primaryType` / `secondaryTypes`), dropping unknown ones. */
fun travellerTypesOf(primary: String?, secondary: List<String>): List<TravellerType> =
    (listOfNotNull(primary) + secondary).distinct().mapNotNull { name -> TravellerType.entries.firstOrNull { it.name == name } }
