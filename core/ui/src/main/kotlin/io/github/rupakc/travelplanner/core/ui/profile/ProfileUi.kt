package io.github.rupakc.travelplanner.core.ui.profile

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.rupakc.travelplanner.core.domain.feature.FeatureDescriptor
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.ui.R

/** Display name of a traveller type ("Culture Seeker & Foodie"). */
@StringRes
fun TravellerType.titleRes(): Int =
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

/** One-line description of a traveller type. */
@StringRes
fun TravellerType.descriptionRes(): Int =
    when (this) {
        TravellerType.BUDGET_BACKPACKER -> R.string.traveller_type_budget_backpacker_description
        TravellerType.FAMILY -> R.string.traveller_type_family_description
        TravellerType.LUXURY -> R.string.traveller_type_luxury_description
        TravellerType.ADVENTURE -> R.string.traveller_type_adventure_description
        TravellerType.CULTURE_FOODIE -> R.string.traveller_type_culture_foodie_description
        TravellerType.BUSINESS -> R.string.traveller_type_business_description
        TravellerType.RELAXATION -> R.string.traveller_type_relaxation_description
        TravellerType.ACCESSIBLE -> R.string.traveller_type_accessible_description
    }

/** Title of a catalogue feature (resource `feature_<id>_title`, = `FeatureDescriptor.titleKey`). */
@StringRes
fun FeatureId.titleRes(): Int =
    when (this) {
        FeatureId.DEAL_ALERTS -> R.string.feature_deal_alerts_title
        FeatureId.BEST_VALUE -> R.string.feature_best_value_title
        FeatureId.BUDGET_PLANNER -> R.string.feature_budget_planner_title
        FeatureId.COMPARE -> R.string.feature_compare_title
        FeatureId.SURPRISE_ME -> R.string.feature_surprise_me_title
        FeatureId.IMPORT_LINK -> R.string.feature_import_link_title
        FeatureId.CHAT -> R.string.feature_chat_title
        FeatureId.NEAR_ME -> R.string.feature_near_me_title
        FeatureId.PACKING_LIST -> R.string.feature_packing_list_title
        FeatureId.EMERGENCY_CARD -> R.string.feature_emergency_card_title
        FeatureId.WALKING_TOURS -> R.string.feature_walking_tours_title
        FeatureId.EVENTS -> R.string.feature_events_title
        FeatureId.VISA_CHECKER -> R.string.feature_visa_checker_title
        FeatureId.FOREX -> R.string.feature_forex_title
        FeatureId.SIM -> R.string.feature_sim_title
        FeatureId.TRIP_HEALTH_CHECK -> R.string.feature_trip_health_check_title
        FeatureId.ITINERARY_EDITOR -> R.string.feature_itinerary_editor_title
        FeatureId.ACCESSIBILITY_INFO -> R.string.feature_accessibility_info_title
    }

/** One-line description of a catalogue feature (resource `feature_<id>_description`). */
@StringRes
fun FeatureId.descriptionRes(): Int =
    when (this) {
        FeatureId.DEAL_ALERTS -> R.string.feature_deal_alerts_description
        FeatureId.BEST_VALUE -> R.string.feature_best_value_description
        FeatureId.BUDGET_PLANNER -> R.string.feature_budget_planner_description
        FeatureId.COMPARE -> R.string.feature_compare_description
        FeatureId.SURPRISE_ME -> R.string.feature_surprise_me_description
        FeatureId.IMPORT_LINK -> R.string.feature_import_link_description
        FeatureId.CHAT -> R.string.feature_chat_description
        FeatureId.NEAR_ME -> R.string.feature_near_me_description
        FeatureId.PACKING_LIST -> R.string.feature_packing_list_description
        FeatureId.EMERGENCY_CARD -> R.string.feature_emergency_card_description
        FeatureId.WALKING_TOURS -> R.string.feature_walking_tours_description
        FeatureId.EVENTS -> R.string.feature_events_description
        FeatureId.VISA_CHECKER -> R.string.feature_visa_checker_description
        FeatureId.FOREX -> R.string.feature_forex_description
        FeatureId.SIM -> R.string.feature_sim_description
        FeatureId.TRIP_HEALTH_CHECK -> R.string.feature_trip_health_check_description
        FeatureId.ITINERARY_EDITOR -> R.string.feature_itinerary_editor_description
        FeatureId.ACCESSIBILITY_INFO -> R.string.feature_accessibility_info_description
    }

/** Icon for a catalogue entry's `iconName` (material-icons-core only; unknown names → Info). Decorative: pair with the title. */
fun FeatureDescriptor.icon(): ImageVector = featureIcon(iconName)

/** Icon for a catalogue `iconName`. */
fun featureIcon(iconName: String): ImageVector =
    when (iconName) {
        "chat" -> Icons.AutoMirrored.Filled.Send
        "verified" -> Icons.Filled.CheckCircle
        "notifications" -> Icons.Filled.Notifications
        "thumb_up" -> Icons.Filled.ThumbUp
        "wallet" -> Icons.Filled.ShoppingCart
        "compare" -> Icons.AutoMirrored.Filled.List
        "surprise" -> Icons.Filled.Star
        "link" -> Icons.Filled.Share
        "near_me" -> Icons.Filled.LocationOn
        "edit_calendar" -> Icons.Filled.Edit
        "walk" -> Icons.Filled.Place
        "event" -> Icons.Filled.DateRange
        "luggage" -> Icons.Filled.Done
        "emergency" -> Icons.Filled.Call
        "health" -> Icons.Filled.Favorite
        "currency" -> Icons.Filled.Refresh
        "sim" -> Icons.Filled.Phone
        "accessibility" -> Icons.Filled.Person
        else -> Icons.Filled.Info
    }
