package io.github.rupakc.travelplanner.core.domain.telemetry

import io.github.rupakc.travelplanner.core.model.navigation.AccountChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.AccountRoute
import io.github.rupakc.travelplanner.core.model.navigation.AlertPrefsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppearanceRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute
import io.github.rupakc.travelplanner.core.model.navigation.CheckEmailRoute
import io.github.rupakc.travelplanner.core.model.navigation.ConnectionsRoute
import io.github.rupakc.travelplanner.core.model.navigation.ExpensesRoute
import io.github.rupakc.travelplanner.core.model.navigation.FeaturesRoute
import io.github.rupakc.travelplanner.core.model.navigation.ForgotPasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.GlobalSearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.IdeaRoute
import io.github.rupakc.travelplanner.core.model.navigation.JournalRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.MyPlanRoute
import io.github.rupakc.travelplanner.core.model.navigation.MyProfileRoute
import io.github.rupakc.travelplanner.core.model.navigation.MySharesRoute
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute
import io.github.rupakc.travelplanner.core.model.navigation.NotificationSettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.NotificationsRoute
import io.github.rupakc.travelplanner.core.model.navigation.OauthUsernameRoute
import io.github.rupakc.travelplanner.core.model.navigation.OfflinePackRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlansRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlayRoute
import io.github.rupakc.travelplanner.core.model.navigation.PreferencesRoute
import io.github.rupakc.travelplanner.core.model.navigation.ProfileRoute
import io.github.rupakc.travelplanner.core.model.navigation.PublishIdeaRoute
import io.github.rupakc.travelplanner.core.model.navigation.RecapRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResetPasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResultsRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.ServerRoute
import io.github.rupakc.travelplanner.core.model.navigation.SessionsRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsEntryRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareCreateRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareOpenLinkRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.core.model.navigation.SignUpRoute
import io.github.rupakc.travelplanner.core.model.navigation.SocialRoute
import io.github.rupakc.travelplanner.core.model.navigation.TasteProfileRoute
import io.github.rupakc.travelplanner.core.model.navigation.TodayRoute
import io.github.rupakc.travelplanner.core.model.navigation.TripRoute
import io.github.rupakc.travelplanner.core.model.navigation.TripsRoute
import io.github.rupakc.travelplanner.core.model.navigation.VerifyEmailRoute
import io.github.rupakc.travelplanner.core.model.navigation.YouRoute
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
            is AlertPrefsRoute -> AlertPrefsRoute::class
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
            is ShareCreateRoute -> ShareCreateRoute::class
            is MySharesRoute -> MySharesRoute::class
            is ShareOpenLinkRoute -> ShareOpenLinkRoute::class
            is SocialRoute -> SocialRoute::class
            is IdeaRoute -> IdeaRoute::class
            is PublishIdeaRoute -> PublishIdeaRoute::class
            is MyProfileRoute -> MyProfileRoute::class
            is ProfileRoute -> ProfileRoute::class
            is ConnectionsRoute -> ConnectionsRoute::class
            is CheckEmailRoute -> CheckEmailRoute::class
            is VerifyEmailRoute -> VerifyEmailRoute::class
            is ResetPasswordRoute -> ResetPasswordRoute::class
            is TripRoute -> TripRoute::class
            is TodayRoute -> TodayRoute::class
            is OfflinePackRoute -> OfflinePackRoute::class
            is ExpensesRoute -> ExpensesRoute::class
            is JournalRoute -> JournalRoute::class
            is RecapRoute -> RecapRoute::class
            is SignUpRoute -> SignUpRoute::class
            is ForgotPasswordRoute -> ForgotPasswordRoute::class
            is OauthUsernameRoute -> OauthUsernameRoute::class
            is AccountRoute -> AccountRoute::class
            is SessionsRoute -> SessionsRoute::class
            is PlayRoute -> PlayRoute::class
            is TripsRoute -> TripsRoute::class
            is YouRoute -> YouRoute::class
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
                AlertPrefsRoute,
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
                ShareCreateRoute("plan", 1),
                MySharesRoute,
                ShareOpenLinkRoute,
                SocialRoute,
                IdeaRoute(1),
                PublishIdeaRoute(1),
                MyProfileRoute,
                ProfileRoute("x"),
                ConnectionsRoute(),
                SignUpRoute,
                CheckEmailRoute("a@b.c"),
                VerifyEmailRoute(),
                ForgotPasswordRoute,
                ResetPasswordRoute(),
                OauthUsernameRoute,
                AccountRoute,
                SessionsRoute,
                PlayRoute,
                TripsRoute,
                TripRoute(1),
                TodayRoute(1),
                OfflinePackRoute(1),
                ExpensesRoute(1),
                JournalRoute(1),
                RecapRoute(1),
                YouRoute,
            ).map(::classOf)
        val table = TelemetryRoutes.table
        // Counted from the mapping itself, so parallel branches adding routes do not collide on a number.
        assertEquals(all.size, all.toSet().size)
        assertEquals(all.size, table.size)
        assertEquals(all.size, table.map { it.first }.toSet().size)
        assertEquals(all.toSet(), table.map { it.first }.toSet())
        table.forEach { assertTrue(it.second, TelemetryProps.isToken(it.second)) }
        assertTrue(TelemetryProps.isToken(TelemetryRoutes.OTHER))
    }
}
