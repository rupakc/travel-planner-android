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
 * [onLinkProvider] is the hook for linking another sign-in method (Task 4): it receives the provider id
 * (`google`, `github`, `facebook`) and starts the browser flow (`OauthRepository.linkTicket` then the same
 * Custom Tab hand-off). While it is null the Link buttons read "Coming soon". After the flow returns the
 * shell can simply re-enter this screen: it reloads the account (and its linked methods) on every entry.
 */
fun NavGraphBuilder.accountGraph(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenSessions: () -> Unit,
    onSignedOut: () -> Unit,
    onLinkProvider: ((String) -> Unit)? = null,
) {
    composable<AccountRoute> { AccountScreenRoute(onBack, onChangePassword, onOpenSessions, onSignedOut, onLinkProvider) }
    composable<SessionsRoute> { SessionsScreenRoute(onBack, onSignedOut) }
}
