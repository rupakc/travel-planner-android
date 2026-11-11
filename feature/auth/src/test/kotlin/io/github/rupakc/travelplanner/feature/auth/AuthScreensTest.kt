package io.github.rupakc.travelplanner.feature.auth

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import io.github.rupakc.travelplanner.feature.auth.login.FakeAuthProvidersRepository
import io.github.rupakc.travelplanner.feature.auth.login.LoginRoute
import io.github.rupakc.travelplanner.feature.auth.login.LoginViewModel
import io.github.rupakc.travelplanner.feature.auth.password.ChangePasswordRoute
import io.github.rupakc.travelplanner.feature.auth.password.ChangePasswordViewModel
import io.github.rupakc.travelplanner.feature.auth.server.ServerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AuthScreensTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val settings = FakeSettingsRepository()

    private fun serverVm() = ServerViewModel(settings, FakeHealthRepository(), BUILD_INFO)

    @Test
    fun `server sheet opens from login and saves a new address`() {
        val vm = LoginViewModel(FakeAuthRepository(), SavedStateHandle(), FakeAuthProvidersRepository(), FakeConsentRepository())
        val server = serverVm()
        compose.setContent { TravelTheme { LoginRoute({}, vm, server) } }
        compose.onNodeWithText("Server: http://10.0.2.2:8001").performScrollTo().performClick()
        compose.onNodeWithText("Server address").performTextReplacement("http://192.168.1.20:8001/")
        compose.onNodeWithText("Test connection").performClick()
        compose.onNodeWithText("Connected. The server is reachable.").assertExists()
        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        assertEquals("http://192.168.1.20:8001", settings.state.value.baseUrlOverride)
        compose.onNodeWithText("Server: http://192.168.1.20:8001").assertExists()
    }

    @Test
    fun `unreachable server gets its own copy and a server action`() {
        val auth = FakeAuthRepository().apply { loginResult = AppResult.Failure(AppError.Network("java.net.ConnectException: failed")) }
        val vm = LoginViewModel(auth, SavedStateHandle(), FakeAuthProvidersRepository(), FakeConsentRepository())
        val server = serverVm()
        compose.setContent { TravelTheme { LoginRoute({}, vm, server) } }
        compose.onNodeWithText("Username").performTextInput("alice")
        compose.onNodeWithText("Password").performTextInput("pw")
        compose.onNodeWithText("Sign In").performScrollTo().performClick()
        compose.onNodeWithText("Can't reach the server. Check your connection, or the address under Server.").assertExists()
        compose.onNodeWithText("ConnectException", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Server").performScrollTo().performClick()
        compose.onNodeWithText("Server address").assertExists()
    }

    @Test
    fun `login error from the server is shown and sign in stays available`() {
        val auth = FakeAuthRepository().apply { loginResult = AppResult.Failure(AppError.Unauthorized("Invalid username or password")) }
        var loggedIn = 0
        val vm = LoginViewModel(auth, SavedStateHandle(), FakeAuthProvidersRepository(), FakeConsentRepository())
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { LoginRoute({ loggedIn++ }, vm, serverVm()) } }

        compose.onNodeWithText("Sign In").assertIsNotEnabled()
        compose.onNodeWithText("Username").performTextInput("alice")
        compose.onNodeWithText("Password").performTextInput("wrong")
        compose.onNodeWithText("Sign In").assertIsEnabled().performScrollTo().performClick()

        compose.onNodeWithText("Invalid username or password").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion).and(hasText("Invalid username or password"))).assertExists()
        assertEquals(0, loggedIn)
    }

    @Test
    fun `successful login reports once`() {
        val auth = FakeAuthRepository()
        var loggedIn = 0
        val vm = LoginViewModel(auth, SavedStateHandle(), FakeAuthProvidersRepository(), FakeConsentRepository())
        compose.setContent { TravelTheme(themeMode = ThemeMode.DARK) { LoginRoute({ loggedIn++ }, vm, serverVm()) } }
        compose.onNodeWithText("Username").performTextInput("alice")
        compose.onNodeWithText("Password").performTextInput("secret")
        compose.onNodeWithText("Sign In").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, loggedIn)
    }

    @Test
    fun `password toggle is labelled and describes its state`() {
        val vm = LoginViewModel(FakeAuthRepository(), SavedStateHandle(), FakeAuthProvidersRepository(), FakeConsentRepository())
        compose.setContent { TravelTheme { LoginRoute({}, vm, serverVm()) } }
        // Below the social sign-in buttons: scroll it into view before tapping.
        compose.onNodeWithContentDescription("Show password")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Password hidden"))
            .performScrollTo()
            .performClick()
        compose.onNodeWithContentDescription("Hide password")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Password shown"))
    }

    @Test
    fun `forced change greets the user and validates locally`() {
        val auth = FakeAuthRepository(Session("t", TestUser.copy(requiresPasswordChange = true)))
        val vm = ChangePasswordViewModel(auth)
        compose.setContent { TravelTheme { ChangePasswordRoute(forced = true, onDone = {}, onBack = {}, viewModel = vm) } }
        compose.onNodeWithText("Welcome, alice! Please set a new password to continue.").assertExists()
        compose.onNodeWithText("Temporary password").performTextInput("temp-pass")
        compose.onNodeWithText("New password").performTextInput("abc")
        compose.onNodeWithText("Confirm new password").performTextInput("abd")
        compose.onNodeWithText("Set password & continue").performScrollTo().performClick()
        compose.onNodeWithText("New passwords do not match").assertExists()
    }

    @Test
    fun `voluntary change confirms and goes back on Done`() {
        val auth = FakeAuthRepository(Session("t", TestUser))
        var done = 0
        var back = 0
        val vm = ChangePasswordViewModel(auth)
        compose.setContent {
            TravelTheme { ChangePasswordRoute(forced = false, onDone = { done++ }, onBack = { back++ }, viewModel = vm) }
        }
        compose.onNodeWithText("Current password").performTextInput("old-pass-1")
        compose.onNodeWithText("New password").performTextInput("new-pass-1")
        compose.onNodeWithText("Confirm new password").performTextInput("new-pass-1")
        compose.onNodeWithText("Update password").performScrollTo().performClick()
        compose.onNodeWithText("Password updated").assertExists()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        assertEquals(1, done)
        assertTrue(back >= 1)
    }
}
