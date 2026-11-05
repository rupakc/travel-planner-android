package io.github.rupakc.travelplanner.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.core.domain.globalsearch.SettingsEntry
import io.github.rupakc.travelplanner.core.domain.globalsearch.SettingsPage
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppearanceRoute
import io.github.rupakc.travelplanner.core.model.navigation.FeaturesRoute
import io.github.rupakc.travelplanner.core.model.navigation.NotificationSettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.PreferencesRoute
import io.github.rupakc.travelplanner.core.model.navigation.ServerRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsEntryRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.TasteProfileRoute
import io.github.rupakc.travelplanner.feature.settings.appearance.AppearanceRoute as AppearanceScreenRoute
import io.github.rupakc.travelplanner.feature.settings.features.FeaturesRoute as FeaturesScreenRoute
import io.github.rupakc.travelplanner.feature.settings.notifications.NotificationSettingsRoute as NotificationsScreenRoute
import io.github.rupakc.travelplanner.feature.settings.preferences.PreferencesRoute as PreferencesScreenRoute
import io.github.rupakc.travelplanner.feature.settings.root.SettingsRoute as SettingsRootRoute
import io.github.rupakc.travelplanner.feature.settings.server.ServerRoute as ServerScreenRoute
import io.github.rupakc.travelplanner.feature.settings.taste.TasteProfileRoute as TasteScreenRoute

/**
 * Settings root + sub-screens + [SettingsEntryRoute] (a row opened from global search).
 * [onRetakeQuiz] opens `OnboardingRoute(fromSettings = true)`; [onNavigate] opens sub-screens and
 * other routes; [onChangePassword] opens the voluntary
 * password change (`AccountChangePasswordRoute`, registered by feature:auth). Sign-out is done here via `AuthRepository.logout()`
 * (which unregisters the push token); [onSignOut] is called afterwards (the auth gate shows Login).
 */
fun NavGraphBuilder.settingsGraph(
    onBack: () -> Unit,
    onRetakeQuiz: () -> Unit,
    onSignOut: () -> Unit,
    onChangePassword: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
) {
    val screens = SettingsScreens(onBack, onRetakeQuiz, onSignOut, onChangePassword, onNavigate)
    composable<SettingsRoute> { screens.Page(SettingsPage.ROOT, highlight = null) }
    composable<PreferencesRoute> { screens.Page(SettingsPage.PREFERENCES, highlight = null) }
    composable<TasteProfileRoute> { screens.Page(SettingsPage.TASTE_PROFILE, highlight = null) }
    composable<FeaturesRoute> { screens.Page(SettingsPage.FEATURES, highlight = null) }
    composable<AppearanceRoute> { screens.Page(SettingsPage.APPEARANCE, highlight = null) }
    composable<ServerRoute> { screens.Page(SettingsPage.SERVER, highlight = null) }
    composable<NotificationSettingsRoute> { screens.Page(SettingsPage.NOTIFICATIONS, highlight = null) }
    composable<SettingsEntryRoute> { entry ->
        val key = entry.toRoute<SettingsEntryRoute>().entry
        val settingsEntry = SettingsEntry.fromKey(key)
        screens.Page(settingsEntry?.page ?: SettingsPage.ROOT, highlight = settingsEntry?.key)
    }
}

private class SettingsScreens(
    val onBack: () -> Unit,
    val onRetakeQuiz: () -> Unit,
    val onSignOut: () -> Unit,
    val onChangePassword: () -> Unit,
    val onNavigate: (AppRoute) -> Unit,
) {
    @Composable
    fun Page(page: SettingsPage, highlight: String?) {
        when (page) {
            SettingsPage.ROOT -> SettingsRootRoute(highlight, onBack, onRetakeQuiz, onChangePassword, onNavigate, onSignOut, hiltViewModel())
            SettingsPage.PREFERENCES -> PreferencesScreenRoute(highlight, onBack, onOpenTaste = { onNavigate(TasteProfileRoute) }, viewModel = hiltViewModel())
            SettingsPage.TASTE_PROFILE -> TasteScreenRoute(onBack, hiltViewModel())
            SettingsPage.FEATURES -> FeaturesScreenRoute(onBack, hiltViewModel())
            SettingsPage.APPEARANCE -> AppearanceScreenRoute(highlight, onBack, hiltViewModel())
            SettingsPage.SERVER -> ServerScreenRoute(onBack, hiltViewModel())
            SettingsPage.NOTIFICATIONS -> NotificationsScreenRoute(onBack, hiltViewModel())
        }
    }
}
