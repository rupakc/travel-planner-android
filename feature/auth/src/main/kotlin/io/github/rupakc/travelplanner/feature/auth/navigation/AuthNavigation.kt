package io.github.rupakc.travelplanner.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.AccountChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.feature.auth.login.LoginRoute as LoginScreenRoute
import io.github.rupakc.travelplanner.feature.auth.password.ChangePasswordRoute as ChangePasswordScreenRoute

/**
 * Login, forced password change and the voluntary change from Settings.
 * The app's auth gate decides between Login / [ChangePasswordRoute] (session → `requires_password_change`)
 * and moves on by itself after success, so [onLoggedIn] / [onPasswordChanged] are informational.
 * [AccountChangePasswordRoute] is not a gate screen: it shows "Password updated" with a Done button
 * that calls [onBack].
 */
fun NavGraphBuilder.authGraph(
    onLoggedIn: () -> Unit,
    onPasswordChanged: () -> Unit,
    onBack: () -> Unit = {},
) {
    composable<LoginRoute> { LoginScreenRoute(onLoggedIn = onLoggedIn) }
    composable<ChangePasswordRoute> { ChangePasswordScreenRoute(forced = true, onDone = onPasswordChanged, onBack = {}) }
    composable<AccountChangePasswordRoute> {
        ChangePasswordScreenRoute(forced = false, onDone = onPasswordChanged, onBack = onBack)
    }
}
