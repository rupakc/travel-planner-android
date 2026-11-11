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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryRoutesTest {
    /** Exhaustive: adding an [AppRoute] breaks compilation here until it is mapped. */
    private fun classOf(route: AppRoute): KClass<out AppRoute> =
        when (route) {
            is LoginRoute -> LoginRoute::class
            is ChangePasswordRoute -> ChangePasswordRoute::class
            is AccountChangePasswordRoute -> AccountChangePasswordRoute::class
            is OnboardingRoute -> OnboardingRoute::class
            is HomeRoute -> HomeRoute::class
            is SearchRoute -> SearchRoute::class
            is ResultsRoute -> ResultsRoute::class
            is PlansRoute -> PlansRoute::class
            is PlanDetailRoute -> PlanDetailRoute::class
            is MyPlanRoute -> MyPlanRoute::class
            is ChatRoute -> ChatRoute::class
            is AlertsRoute -> AlertsRoute::class
            is NotificationsRoute -> NotificationsRoute::class
            is GlobalSearchRoute -> GlobalSearchRoute::class
            is SettingsRoute -> SettingsRoute::class
            is SettingsEntryRoute -> SettingsEntryRoute::class
            is PreferencesRoute -> PreferencesRoute::class
            is TasteProfileRoute -> TasteProfileRoute::class
            is FeaturesRoute -> FeaturesRoute::class
            is AppearanceRoute -> AppearanceRoute::class
            is ServerRoute -> ServerRoute::class
            is NotificationSettingsRoute -> NotificationSettingsRoute::class
            is NearbyRoute -> NearbyRoute::class
            is ShareRoute -> ShareRoute::class
        }

    @Test fun `every route class is mapped exactly once`() {
        val all =
            listOf<AppRoute>(
                LoginRoute,
                ChangePasswordRoute,
                AccountChangePasswordRoute,
                OnboardingRoute(),
                HomeRoute,
                SearchRoute(),
                ResultsRoute,
                PlansRoute,
                PlanDetailRoute(1),
                MyPlanRoute,
                ChatRoute(),
                AlertsRoute(),
                NotificationsRoute,
                GlobalSearchRoute,
                SettingsRoute,
                SettingsEntryRoute("x"),
                PreferencesRoute,
                TasteProfileRoute,
                FeaturesRoute,
                AppearanceRoute,
                ServerRoute,
                NotificationSettingsRoute,
                NearbyRoute,
                ShareRoute("t"),
            ).map(::classOf)
        val table = TelemetryRoutes.table
        assertEquals(24, all.toSet().size)
        assertEquals(24, table.size)
        assertEquals(24, table.map { it.first }.toSet().size)
        assertEquals(all.toSet(), table.map { it.first }.toSet())
        table.forEach { assertTrue(it.second, TelemetryProps.isToken(it.second)) }
        assertTrue(TelemetryProps.isToken(TelemetryRoutes.OTHER))
    }
}
