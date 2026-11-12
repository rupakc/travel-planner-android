package io.github.rupakc.travelplanner.feature.auth

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import io.github.rupakc.travelplanner.feature.auth.login.FakeAuthProvidersRepository
import io.github.rupakc.travelplanner.feature.auth.login.LoginEvent
import io.github.rupakc.travelplanner.feature.auth.login.LoginViewModel
import io.github.rupakc.travelplanner.feature.auth.login.loginViewModel
import io.github.rupakc.travelplanner.feature.auth.password.ChangePasswordEvent
import io.github.rupakc.travelplanner.feature.auth.password.ChangePasswordViewModel
import io.github.rupakc.travelplanner.feature.auth.password.PasswordField
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = loginViewModel(auth, saved)

    private fun LoginViewModel.fill(user: String = "alice", pass: String = "secret") {
        onEvent(LoginEvent.UsernameChanged(user))
        onEvent(LoginEvent.PasswordChanged(pass))
    }

    @Test
    fun `submit is disabled until both fields are filled`() {
        val vm = vm()
        assertFalse(vm.state.value.canSubmit)
        vm.onEvent(LoginEvent.UsernameChanged("alice"))
        assertFalse(vm.state.value.canSubmit)
        vm.onEvent(LoginEvent.PasswordChanged("x"))
        assertTrue(vm.state.value.canSubmit)
        vm.onEvent(LoginEvent.UsernameChanged("   "))
        vm.onEvent(LoginEvent.Submit)
        assertNull(auth.sessionFlow.value)
    }

    @Test
    fun `successful login stores the session and clears the password`() =
        runTest {
            val vm = vm()
            vm.fill(user = "  alice ")
            vm.onEvent(LoginEvent.Submit)
            val state = vm.state.value
            assertTrue(state.signedIn)
            assertFalse(state.loading)
            assertEquals("", state.password)
            assertEquals("alice", auth.sessionFlow.value?.user?.username)
        }

    @Test
    fun `server detail is shown as is`() =
        runTest {
            auth.loginResult = AppResult.Failure(AppError.Unauthorized("Invalid username or password"))
            val vm = vm()
            vm.fill()
            vm.state.test {
                skipItems(1)
                vm.onEvent(LoginEvent.Submit)
                var failed = awaitItem()
                if (failed.loading) failed = awaitItem()
                assertFalse(failed.loading)
                assertEquals(AuthMessage.Server("Invalid username or password"), failed.error)
                assertFalse(failed.signedIn)
            }
        }

    @Test
    fun `rate limit, network and blank errors map to the right copy`() {
        val limited = AppError.RateLimited("Too many requests — please wait 30 seconds and try again.", 30)
        assertEquals(AuthMessage.Server(limited.message.orEmpty()), limited.toLoginMessage())
        assertEquals(AuthMessage.Local(R.string.auth_error_network, suggestServer = true), AppError.Network("timeout").toLoginMessage())
        assertEquals(AuthMessage.Local(R.string.auth_error_login_failed), AppError.Server(500, " ").toLoginMessage())
    }

    @Test
    fun `typing clears the error and the toggle flips visibility`() {
        auth.loginResult = AppResult.Failure(AppError.Unauthorized(null))
        val vm = vm()
        vm.fill()
        vm.onEvent(LoginEvent.Submit)
        assertEquals(AuthMessage.Local(R.string.auth_error_login_failed), vm.state.value.error)
        vm.onEvent(LoginEvent.PasswordChanged("other"))
        assertNull(vm.state.value.error)
        vm.onEvent(LoginEvent.TogglePasswordVisibility)
        assertTrue(vm.state.value.passwordVisible)
    }

    @Test
    fun `username survives process death but the password does not`() {
        val saved = SavedStateHandle()
        vm(saved).fill()
        val restored = vm(saved).state.value
        assertEquals("alice", restored.username)
        assertEquals("", restored.password)
    }
}

class ChangePasswordViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository(Session("t", TestUser.copy(requiresPasswordChange = true)))

    private fun ChangePasswordViewModel.fill(current: String = "temp-pass", new: String = "brand-new-1", confirm: String = new) {
        onEvent(ChangePasswordEvent.Changed(PasswordField.CURRENT, current))
        onEvent(ChangePasswordEvent.Changed(PasswordField.NEW, new))
        onEvent(ChangePasswordEvent.Changed(PasswordField.CONFIRM, confirm))
    }

    @Test
    fun `shows the signed-in username`() {
        assertEquals("alice", ChangePasswordViewModel(auth).state.value.username)
    }

    @Test
    fun `mismatch is checked before length, and nothing is sent`() {
        auth.changePasswordResult = AppResult.Failure(AppError.Unknown("must not be called"))
        val vm = ChangePasswordViewModel(auth)
        vm.fill(new = "short", confirm = "shorter")
        vm.onEvent(ChangePasswordEvent.Submit)
        assertEquals(AuthMessage.Local(R.string.auth_error_mismatch), vm.state.value.error)
        vm.fill(new = "short")
        vm.onEvent(ChangePasswordEvent.Submit)
        assertEquals(AuthMessage.Local(R.string.auth_error_too_short), vm.state.value.error)
        assertTrue(auth.sessionFlow.value!!.user.requiresPasswordChange)
    }

    @Test
    fun `success clears the forced flag and the fields`() {
        val vm = ChangePasswordViewModel(auth)
        vm.fill()
        vm.onEvent(ChangePasswordEvent.Submit)
        val state = vm.state.value
        assertTrue(state.done)
        assertEquals("", state.new)
        assertFalse(auth.sessionFlow.value!!.user.requiresPasswordChange)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `server errors show detail, else fallbacks`() {
        auth.changePasswordResult = AppResult.Failure(AppError.Client(400, "Current password is incorrect"))
        val vm = ChangePasswordViewModel(auth)
        vm.fill()
        vm.onEvent(ChangePasswordEvent.Submit)
        assertEquals(AuthMessage.Server("Current password is incorrect"), vm.state.value.error)
        assertFalse(vm.state.value.done)

        assertEquals(AuthMessage.Local(R.string.auth_error_change_failed), AppError.Server(500, null).toChangePasswordMessage())
        assertEquals(AuthMessage.Local(R.string.auth_error_generic), AppError.Unknown(null).toChangePasswordMessage())
        assertEquals(AuthMessage.Local(R.string.auth_error_network, suggestServer = true), AppError.Network().toChangePasswordMessage())
    }

    @Test
    fun `visibility is per field`() {
        val vm = ChangePasswordViewModel(auth)
        vm.onEvent(ChangePasswordEvent.ToggleVisibility(PasswordField.NEW))
        assertEquals(setOf(PasswordField.NEW), vm.state.value.visible)
        vm.onEvent(ChangePasswordEvent.ToggleVisibility(PasswordField.NEW))
        assertEquals(emptySet<PasswordField>(), vm.state.value.visible)
    }
}
