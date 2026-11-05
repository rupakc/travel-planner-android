package io.github.rupakc.travelplanner.feature.auth.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.auth.R

/**
 * Login and forced password change. The app's auth gate decides which one shows
 * (session → user.requires_password_change); these callbacks are informational.
 */
fun NavGraphBuilder.authGraph(
    onLoggedIn: () -> Unit,
    onPasswordChanged: () -> Unit,
) {
    composable<LoginRoute> { LoginScreen(onLoggedIn = onLoggedIn) }
    composable<ChangePasswordRoute> { ChangePasswordScreen(onPasswordChanged = onPasswordChanged) }
}

@Composable
internal fun LoginScreen(onLoggedIn: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.auth_login_title),
        message = stringResource(R.string.auth_login_message),
        modifier = modifier,
        actions = listOf(PlaceholderAction(stringResource(R.string.auth_continue), onLoggedIn)),
    )
}

@Composable
internal fun ChangePasswordScreen(onPasswordChanged: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.auth_change_password_title),
        message = stringResource(R.string.auth_change_password_message),
        modifier = modifier,
        actions = listOf(PlaceholderAction(stringResource(R.string.auth_continue), onPasswordChanged)),
    )
}
