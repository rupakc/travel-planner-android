package io.github.rupakc.travelplanner.feature.auth.login

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthFlags
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthConfigRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSignupRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Sign-up / reset entry points on Login are config-gated; `403 account_pending` offers a resend. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class LoginAccountsTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private val signup = FakeSignupRepository()
    private val telemetry = FakeTelemetry()
    private val auth = FakeAuthRepository()

    private fun vm(flags: AuthFlags) = loginViewModel(auth, signup = signup, config = FakeAuthConfigRepository(flags), telemetry = telemetry)

    @Test
    fun `flags off, no create account, no forgot password`() {
        val vm = vm(AuthFlags())
        assertFalse(vm.state.value.signupAvailable)
        assertFalse(vm.state.value.resetAvailable)
        compose.setContent {
            TravelTheme { LoginScreen(vm.state.value, vm::onEvent) }
        }
        compose.onNodeWithText("Create account").assertDoesNotExist()
        compose.onNodeWithText("Forgot password?").assertDoesNotExist()
    }

    @Test
    fun `flags on, both buttons show and call their lambdas`() {
        val vm = vm(AuthFlags(signupAvailable = true, resetAvailable = true))
        var signUp = 0
        var forgot = 0
        compose.setContent {
            TravelTheme { LoginScreen(vm.state.value, vm::onEvent, onSignUp = { signUp++ }, onForgotPassword = { forgot++ }) }
        }
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.onNodeWithText("Forgot password?").performScrollTo().performClick()
        assertEquals(1, signUp)
        assertEquals(1, forgot)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExist() = assertDoesNotExist()

    private fun loginPending(vm: LoginViewModel) {
        auth.loginResult =
            AppResult.Failure(AppError.Forbidden("Confirm your email", "account_pending", mapOf("email_hint" to "j•••@example.com")))
        vm.onEvent(LoginEvent.UsernameChanged("jane"))
        vm.onEvent(LoginEvent.PasswordChanged("secret"))
        vm.onEvent(LoginEvent.Submit)
    }

    @Test
    fun `account_pending shows the hint card instead of an error`() {
        val vm = vm(AuthFlags())
        loginPending(vm)
        val pending = vm.state.value.pending
        assertNotNull(pending)
        assertEquals("j•••@example.com", pending!!.hint)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
        assertTrue(telemetry.events.contains("auth_result" to mapOf("method" to "password", "mode" to "signin", "result" to "pending")))
    }

    @Test
    fun `pending resend needs the full address and then waits 30 seconds`() {
        val vm = vm(AuthFlags())
        loginPending(vm)
        vm.onEvent(LoginEvent.ResendPending)
        assertTrue(signup.calls.isEmpty())
        vm.onEvent(LoginEvent.PendingEmailChanged("jane@example.com"))
        vm.onEvent(LoginEvent.ResendPending)
        assertEquals(listOf("resend:jane@example.com"), signup.calls)
        assertTrue(vm.state.value.pending!!.sent)
        assertEquals(30, vm.state.value.pending!!.cooldownSeconds)
        vm.onEvent(LoginEvent.ResendPending)
        assertEquals(1, signup.calls.size)
        main.testDispatcher.scheduler.advanceTimeBy(30_001)
        assertEquals(0, vm.state.value.pending!!.cooldownSeconds)
    }
}
