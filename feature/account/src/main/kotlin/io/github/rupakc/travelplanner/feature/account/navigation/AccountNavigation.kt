package io.github.rupakc.travelplanner.feature.account.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.AccountRoute
import io.github.rupakc.travelplanner.core.model.navigation.SessionsRoute
import io.github.rupakc.travelplanner.feature.account.AccountRoute as AccountScreenRoute
import io.github.rupakc.travelplanner.feature.account.SessionsRoute as SessionsScreenRoute

/**
 * Account area (`AccountRoute`) and its devices screen (`SessionsRoute`). The shell wires this (Task 17).
 * [onChangePassword] opens `AccountChangePasswordRoute`; [onSignedOut] runs after an account delete or
 * "Sign out everywhere" (the session is already cleared, the auth gate shows Login).
 *
 * [onLinkProvider] is the shell's browser hand-off for linking another sign-in method: the screen checks the provider
 * is configured (`/api/auth/providers`, else a "coming soon" note), takes a link ticket, then passes the safe
 * same-origin start URL here for the Custom Tab. The browser's return link is exchanged by the screen itself
 * and the account reloads. While it is null the Link buttons read "Coming soon".
 */
fun NavGraphBuilder.accountGraph(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenSessions: () -> Unit,
    onSignedOut: () -> Unit,
    /** Opens the given start URL in a Custom Tab. */
    onLinkProvider: ((String) -> Unit)? = null,
) {
    composable<AccountRoute> { AccountScreenRoute(onBack, onChangePassword, onOpenSessions, onSignedOut, onLinkProvider) }
    composable<SessionsRoute> { SessionsScreenRoute(onBack, onSignedOut) }
}
