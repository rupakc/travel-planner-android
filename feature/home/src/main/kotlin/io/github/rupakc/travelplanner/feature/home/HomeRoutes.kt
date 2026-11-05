package io.github.rupakc.travelplanner.feature.home

import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.globalsearch.SettingsEntry
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute
import io.github.rupakc.travelplanner.core.model.navigation.GlobalSearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchModes
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsEntryRoute
import io.github.rupakc.travelplanner.core.model.navigation.TasteProfileRoute

/** Features that open their own screen (the rest live inside trip results). */
internal val FeatureId.isTool: Boolean
    get() = this in setOf(FeatureId.DEAL_ALERTS, FeatureId.COMPARE, FeatureId.SURPRISE_ME, FeatureId.IMPORT_LINK, FeatureId.CHAT, FeatureId.NEAR_ME)

/**
 * Where a feature tile goes. Tools open their screen; results features open the
 * upcoming trip when there is one, else the Search form (they appear in trip results).
 */
internal fun FeatureId.destination(upcomingPlanId: Long?): AppRoute =
    when (this) {
        FeatureId.DEAL_ALERTS -> AlertsRoute()
        FeatureId.COMPARE -> SearchRoute(mode = SearchModes.COMPARE)
        FeatureId.SURPRISE_ME -> SearchRoute(mode = SearchModes.SURPRISE)
        FeatureId.IMPORT_LINK -> SearchRoute(mode = SearchModes.IMPORT)
        FeatureId.CHAT -> ChatRoute()
        FeatureId.NEAR_ME -> NearbyRoute
        else -> upcomingPlanId?.let(::PlanDetailRoute) ?: SearchRoute()
    }

/** Route for a Home navigation event, or null for events the ViewModel handles. */
internal fun HomeEvent.destination(upcomingPlanId: Long?): AppRoute? =
    when (this) {
        HomeEvent.OpenSearch -> GlobalSearchRoute
        HomeEvent.PlanTrip -> SearchRoute()
        HomeEvent.OpenTravellerType -> SettingsEntryRoute(SettingsEntry.TRAVELLER_TYPE.key)
        HomeEvent.OpenTasteGraph -> TasteProfileRoute
        is HomeEvent.OpenTrip -> PlanDetailRoute(planId)
        is HomeEvent.OpenFeature -> feature.destination(upcomingPlanId)
        is HomeEvent.OpenRecentSearch -> SearchRoute(recentSearchId = id)
        HomeEvent.Refresh, HomeEvent.DismissError -> null
    }
