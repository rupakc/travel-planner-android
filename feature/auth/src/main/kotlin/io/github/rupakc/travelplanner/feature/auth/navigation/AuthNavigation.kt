package io.github.rupakc.travelplanner.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.AccountChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.CheckEmailRoute
import io.github.rupakc.travelplanner.core.model.navigation.ForgotPasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.OauthUsernameRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResetPasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.SignUpRoute
import io.github.rupakc.travelplanner.core.model.navigation.VerifyEmailRoute
import io.github.rupakc.travelplanner.feature.auth.login.LoginRoute as LoginScreenRoute
import io.github.rupakc.travelplanner.feature.auth.oauth.OauthUsernameRoute as OauthUsernameScreenRoute
import io.github.rupakc.travelplanner.feature.auth.password.ChangePasswordRoute as ChangePasswordScreenRoute
import io.github.rupakc.travelplanner.feature.auth.reset.ForgotPasswordRoute as ForgotPasswordScreenRoute
import io.github.rupakc.travelplanner.feature.auth.reset.ResetPasswordRoute as ResetPasswordScreenRoute
import io.github.rupakc.travelplanner.feature.auth.signup.SignUpRoute as SignUpScreenRoute
import io.github.rupakc.travelplanner.feature.auth.verify.CheckEmailRoute as CheckEmailScreenRoute
import io.github.rupakc.travelplanner.feature.auth.verify.VerifyEmailRoute as VerifyEmailScreenRoute

/**
 * How the account screens move between each other. The app turns these into navigation (Task 17); every default
 * is a no-op so an older graph call keeps compiling. The screens only ever pass plain strings: the address a
 * confirmation link went to, never a token.
 */
class AuthNavActions(
    val toSignUp: () -> Unit = {},
    val toForgotPassword: () -> Unit = {},
    val toCheckEmail: (email: String) -> Unit = {},
    /** Back to Login (pop to it, or navigate there). */
    val toLogin: () -> Unit = {},
    /** Request a new reset link from the Reset screen. */
    val toRequestNewReset: () -> Unit = toForgotPassword,
    /** A first social sign-in needs a username ([OauthUsernameRoute]). */
    val toOauthUsername: () -> Unit = {},
)

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
    actions: AuthNavActions = AuthNavActions(),
) {
    composable<LoginRoute> {
        LoginScreenRoute(
            onLoggedIn = onLoggedIn,
            onSignUp = actions.toSignUp,
            onForgotPassword = actions.toForgotPassword,
            onNeedsUsername = actions.toOauthUsername,
        )
    }
    // Account screens. Verify and Reset take their token from the route (a deep link or null = paste a link).
    composable<SignUpRoute> { SignUpScreenRoute(onCheckEmail = actions.toCheckEmail, onSignIn = actions.toLogin) }
    composable<OauthUsernameRoute> { OauthUsernameScreenRoute(onCancel = actions.toLogin) }
    composable<CheckEmailRoute> { CheckEmailScreenRoute(onBackToLogin = actions.toLogin) }
    composable<VerifyEmailRoute> { VerifyEmailScreenRoute(onDone = actions.toLogin) }
    composable<ForgotPasswordRoute> { ForgotPasswordScreenRoute(onBackToLogin = actions.toLogin) }
    composable<ResetPasswordRoute> { ResetPasswordScreenRoute(onDone = actions.toLogin, onRequestNew = actions.toRequestNewReset) }
    composable<ChangePasswordRoute> { ChangePasswordScreenRoute(forced = true, onDone = onPasswordChanged, onBack = {}) }
    composable<AccountChangePasswordRoute> {
        ChangePasswordScreenRoute(forced = false, onDone = onPasswordChanged, onBack = onBack)
    }
}
