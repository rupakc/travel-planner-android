package io.github.rupakc.travelplanner.core.domain.telemetry

import io.github.rupakc.travelplanner.core.model.navigation.AccountChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppearanceRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute
import io.github.rupakc.travelplanner.core.model.navigation.FeaturesRoute
import io.github.rupakc.travelplanner.core.model.navigation.GlobalSearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.MyPlanRoute
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute
import io.github.rupakc.travelplanner.core.model.navigation.NotificationSettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.NotificationsRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlansRoute
import io.github.rupakc.travelplanner.core.model.navigation.PreferencesRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResultsRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.ServerRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsEntryRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.core.model.navigation.TasteProfileRoute
import kotlin.reflect.KClass

/**
 * Route templates for the telemetry `screen` field (web `frontend/src/telemetry/routes.js`): the screen's
 * template, never ids, tokens or arguments. The web name is used where the web has the same screen.
 */
object TelemetryRoutes {
    /** Screen name for anything without a template. */
    const val OTHER = "/other"

    val table: List<Pair<KClass<out AppRoute>, String>> =
        listOf(
            LoginRoute::class to "/login",
            ChangePasswordRoute::class to "/change-password",
            AccountChangePasswordRoute::class to "/change-password",
            OnboardingRoute::class to "/onboarding",
            HomeRoute::class to "/home",
            SearchRoute::class to "/",
            ResultsRoute::class to "/results",
            PlansRoute::class to "/plans",
            PlanDetailRoute::class to "/plans/:id",
            MyPlanRoute::class to "/my-plan",
            ChatRoute::class to "/chat",
            AlertsRoute::class to "/alerts",
            NotificationsRoute::class to "/notifications",
            GlobalSearchRoute::class to "/global-search",
            SettingsRoute::class to "/settings",
            SettingsEntryRoute::class to "/settings/:entry",
            PreferencesRoute::class to "/preferences",
            TasteProfileRoute::class to "/settings/taste",
            FeaturesRoute::class to "/settings/features",
            AppearanceRoute::class to "/settings/appearance",
            ServerRoute::class to "/settings/server",
            NotificationSettingsRoute::class to "/settings/notifications",
            NearbyRoute::class to "/nearby",
            ShareRoute::class to "/share/:token",
        )
}
