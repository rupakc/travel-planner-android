package io.github.rupakc.travelplanner.feature.auth.signup

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthFlags
import io.github.rupakc.travelplanner.core.model.auth.PasswordResetResponse
import io.github.rupakc.travelplanner.core.model.auth.SessionDto
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.auth.VerificationSentDto
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailResponse
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAppLanguage
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthConfigRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSignupRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.reset.ForgotPasswordEvent
import io.github.rupakc.travelplanner.feature.auth.reset.ForgotPasswordViewModel
import io.github.rupakc.travelplanner.feature.auth.reset.ResetPasswordEvent
import io.github.rupakc.travelplanner.feature.auth.reset.ResetPasswordViewModel
import io.github.rupakc.travelplanner.feature.auth.verify.CheckEmailEvent
import io.github.rupakc.travelplanner.feature.auth.verify.CheckEmailViewModel
import io.github.rupakc.travelplanner.feature.auth.verify.VerifyEmailEvent
import io.github.rupakc.travelplanner.feature.auth.verify.VerifyEmailViewModel
import io.github.rupakc.travelplanner.feature.auth.verify.VerifyPhase
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignupViewModelsTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeSignupRepository()
    private val telemetry = FakeTelemetry()
    private val config = FakeAuthConfigRepository(AuthFlags(signupAvailable = true, resetAvailable = true, minPasswordLength = 10))
    private val language = FakeAppLanguage("de")
    private val session = SessionDto("jwt", "bearer", 3600, "rt_1", User(username = "jane"))

    private fun signUp() = SignUpViewModel(repo, config, telemetry, language)

    private fun SignUpViewModel.fill(password: String = "correct horse battery") {
        onEvent(SignUpEvent.UsernameChanged("Jane_Doe"))
        onEvent(SignUpEvent.EmailChanged("jane@example.com"))
        onEvent(SignUpEvent.PasswordChanged(password))
    }

    // ---- sign-up

    @Test
    fun `min password length comes from config`() {
        val vm = signUp()
        vm.fill("123456789")
        assertEquals(10, vm.state.value.minPasswordLength)
        assertFalse(vm.state.value.canSubmit)
        vm.onEvent(SignUpEvent.PasswordChanged("1234567890x"))
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `an old backend without the setting uses the web's 10`() {
        config.flags = AuthFlags()
        val vm = signUp()
        vm.fill("123456789")
        assertFalse(vm.state.value.canSubmit)
        vm.onEvent(SignUpEvent.PasswordChanged("1234567890"))
        assertTrue(vm.state.value.canSubmit)
    }

    @Test
    fun `submit sends a lower-cased username, locale and client, then asks for the email check`() =
        runTest {
            repo.signupResult = AppResult.Success(VerificationSentDto(status = "verification_sent", email = "jane@example.com"))
            val vm = signUp()
            vm.fill()
            vm.effects.test {
                vm.onEvent(SignUpEvent.Submit)
                assertEquals(SignUpEffect.CheckEmail("jane@example.com"), awaitItem())
            }
            val request = repo.signupRequests.single()
            assertEquals("jane_doe", request.username)
            assertEquals("de", request.locale)
            assertEquals("android", request.client)
            assertNull(request.inviteCode)
            assertEquals("", vm.state.value.password)
        }

    @Test
    fun `invite code is required and sent only when the server asks for it`() =
        runTest {
            config.flags = AuthFlags(signupAvailable = true, inviteRequired = true, minPasswordLength = 10)
            val vm = signUp()
            vm.fill()
            assertFalse(vm.state.value.canSubmit)
            vm.onEvent(SignUpEvent.InviteChanged(" WELCOME "))
            vm.onEvent(SignUpEvent.Submit)
            assertEquals("WELCOME", repo.signupRequests.single().inviteCode)
        }

    @Test
    fun `a backend error code becomes our message and telemetry carries the code only`() {
        repo.signupResult = AppResult.Failure(AppError.Client(409, "x", "username_taken"))
        val vm = signUp()
        vm.fill()
        vm.onEvent(SignUpEvent.Submit)
        assertEquals(AuthMessage.Local(R.string.auth_err_username_taken), vm.state.value.error)
        assertFalse(vm.state.value.loading)
        val result = telemetry.events.single { it.first == "auth_result" }.second
        assertEquals(mapOf("method" to "password", "mode" to "signup", "result" to "error", "code" to "username_taken"), result)
    }

    @Test
    fun `telemetry never carries the email or username`() {
        val vm = signUp()
        vm.fill()
        vm.onEvent(SignUpEvent.Submit)
        val text = telemetry.events.toString()
        assertFalse(text.contains("jane"))
        assertEquals(listOf("signup_step", "auth_start", "auth_result", "signup_step"), telemetry.events.map { it.first })
    }

    // ---- check email

    private fun check(email: String? = "jane@example.com") =
        CheckEmailViewModel(SavedStateHandle(mapOf("email" to email)), repo, telemetry, language)

    @Test
    fun `resend starts in cooldown, waits 30 seconds, then sends once`() =
        runTest {
            val vm = check()
            assertEquals(30, vm.state.value.cooldownSeconds)
            vm.onEvent(CheckEmailEvent.Resend)
            assertTrue(repo.calls.none { it.startsWith("resend") })
            main.testDispatcher.scheduler.advanceTimeBy(29_001)
            assertEquals(1, vm.state.value.cooldownSeconds)
            assertFalse(vm.state.value.canResend)
            main.testDispatcher.scheduler.advanceTimeBy(1_000)
            assertEquals(0, vm.state.value.cooldownSeconds)
            vm.onEvent(CheckEmailEvent.Resend)
            assertEquals(listOf("resend:jane@example.com"), repo.calls)
            assertTrue(vm.state.value.resent)
            assertEquals(30, vm.state.value.cooldownSeconds)
            vm.onEvent(CheckEmailEvent.Resend)
            assertEquals(1, repo.calls.size)
        }

    @Test
    fun `resend failure shows the message and keeps the button usable after the cooldown`() =
        runTest {
            val vm = check()
            main.testDispatcher.scheduler.advanceTimeBy(30_001)
            repo.resendResult = AppResult.Failure(AppError.RateLimited("Slow down", 30, "rate_limited"))
            vm.onEvent(CheckEmailEvent.Resend)
            assertEquals(AuthMessage.Local(R.string.auth_err_rate_limited), vm.state.value.error)
            assertTrue(vm.state.value.canResend)
        }

    // ---- verify

    private val auth = FakeAuthRepository()

    private fun verify(token: String? = null) = VerifyEmailViewModel(SavedStateHandle(mapOf("token" to token)), repo, auth, telemetry, language)

    private fun signedInAs(name: String) {
        auth.sessionFlow.value = Session("t", User(username = name))
    }

    @Test
    fun `the signed-in user's own link is stored without logout or a question`() =
        runTest(timeout = 30.seconds) {
            signedInAs("jane")
            repo.verifyResult = AppResult.Success(VerifyEmailResponse("verified", session, GameDelta()))
            val vm = verify("abc")
            assertEquals(VerifyPhase.SignedIn, vm.state.value.phase)
            assertEquals(0, auth.logoutCalls)
            assertEquals(listOf("redeem:abc", "store:jane"), repo.calls)
        }

    @Test
    fun `another user's link waits for the choice and nothing is stored or cleared`() =
        runTest(timeout = 30.seconds) {
            signedInAs("bob")
            repo.verifyResult = AppResult.Success(VerifyEmailResponse("verified", session, GameDelta()))
            val vm = verify("abc")
            assertEquals(VerifyPhase.ConfirmSwitch, vm.state.value.phase)
            assertEquals("bob", vm.state.value.currentUsername)
            assertEquals("jane", vm.state.value.newUsername)
            assertEquals(0, auth.logoutCalls)
            assertEquals(listOf("redeem:abc"), repo.calls)
            vm.onEvent(VerifyEmailEvent.SignOutAndVerify)
            assertEquals(1, auth.logoutCalls)
            assertEquals(listOf("redeem:abc", "store:jane"), repo.calls)
            assertEquals(VerifyPhase.SignedIn, vm.state.value.phase)
        }

    @Test
    fun `staying signed in keeps the user and the email stays confirmed`() =
        runTest(timeout = 30.seconds) {
            signedInAs("bob")
            repo.verifyResult = AppResult.Success(VerifyEmailResponse("verified", session, GameDelta()))
            val vm = verify("abc")
            vm.onEvent(VerifyEmailEvent.CancelSwitch)
            assertEquals(VerifyPhase.Confirmed, vm.state.value.phase)
            assertEquals(0, auth.logoutCalls)
            assertFalse(repo.calls.any { it.startsWith("store") })
        }

    @Test
    fun `signed out the session is stored at once and a pasted link follows the same rule`() =
        runTest(timeout = 30.seconds) {
            repo.verifyResult = AppResult.Success(VerifyEmailResponse("verified", session, GameDelta()))
            assertEquals(VerifyPhase.SignedIn, verify("abc").state.value.phase)
            assertEquals(listOf("redeem:abc", "store:jane"), repo.calls)
            signedInAs("bob")
            val vm = verify(null)
            vm.onEvent(VerifyEmailEvent.PastedChanged("xyz"))
            vm.onEvent(VerifyEmailEvent.SubmitPasted)
            assertEquals(VerifyPhase.ConfirmSwitch, vm.state.value.phase)
        }

    @Test
    fun `a confirmation without a session never asks`() =
        runTest(timeout = 30.seconds) {
            signedInAs("bob")
            repo.verifyResult = AppResult.Success(VerifyEmailResponse("verified", null, GameDelta()))
            assertEquals(VerifyPhase.Confirmed, verify("abc").state.value.phase)
            assertEquals(0, auth.logoutCalls)
        }

    @Test
    fun `verify with a route token signs in and shows the XP`() {
        repo.verifyResult = AppResult.Success(VerifyEmailResponse("verified", session, GameDelta(xp = 50)))
        val vm = verify("https://h/verify?token=abc&x=1")
        assertEquals(VerifyPhase.SignedIn, vm.state.value.phase)
        assertTrue(repo.calls.contains("redeem:abc"))
        assertTrue(telemetry.events.contains("email_verify" to mapOf("result" to "ok")))
    }

    @Test
    fun `confirmation only shows its own state`() {
        repo.verifyResult = AppResult.Success(VerifyEmailResponse("verified", null, GameDelta()))
        val vm = verify("abc")
        assertEquals(VerifyPhase.Confirmed, vm.state.value.phase)
    }

    @Test
    fun `without a token the user pastes a link or a bare code`() {
        val vm = verify(null)
        assertEquals(VerifyPhase.NeedsToken, vm.state.value.phase)
        assertTrue(repo.calls.isEmpty())
        vm.onEvent(VerifyEmailEvent.PastedChanged("not a token!"))
        vm.onEvent(VerifyEmailEvent.SubmitPasted)
        assertEquals(AuthMessage.Local(R.string.auth_err_token_invalid), vm.state.value.error)
        assertTrue(repo.calls.isEmpty())
        vm.onEvent(VerifyEmailEvent.PastedChanged("https://h/verify?token=xyz"))
        vm.onEvent(VerifyEmailEvent.SubmitPasted)
        assertTrue(repo.calls.contains("redeem:xyz"))
    }

    @Test
    fun `expired link offers a resend for the typed address`() {
        repo.verifyResult = AppResult.Failure(AppError.Client(410, "x", "token_expired"))
        val vm = verify("abc")
        assertEquals(AuthMessage.Local(R.string.auth_err_token_expired), vm.state.value.error)
        assertTrue(vm.state.value.canResend)
        assertTrue(telemetry.events.contains("email_verify" to mapOf("result" to "expired")))
        vm.onEvent(VerifyEmailEvent.EmailChanged("jane@example.com"))
        vm.onEvent(VerifyEmailEvent.Resend)
        assertTrue(repo.calls.contains("resend:jane@example.com"))
    }

    // ---- forgot / reset

    @Test
    fun `forgot shows the neutral sent state and hides the form when reset is unavailable`() {
        val vm = ForgotPasswordViewModel(repo, config, telemetry, language)
        assertFalse(vm.state.value.unavailable)
        vm.onEvent(ForgotPasswordEvent.EmailChanged("jane@example.com"))
        vm.onEvent(ForgotPasswordEvent.Submit)
        assertTrue(vm.state.value.sent)
        assertTrue(telemetry.events.contains("password_reset" to mapOf("step" to "request", "result" to "ok")))
        config.flags = AuthFlags()
        assertTrue(ForgotPasswordViewModel(repo, config, telemetry, language).state.value.unavailable)
    }

    private fun reset(token: String? = "tok") =
        ResetPasswordViewModel(SavedStateHandle(mapOf("token" to token)), repo, config, telemetry)

    @Test
    fun `reset needs matching passwords of the configured length`() {
        val vm = reset()
        vm.onEvent(ResetPasswordEvent.PasswordChanged("123456789"))
        vm.onEvent(ResetPasswordEvent.ConfirmChanged("123456789"))
        assertFalse(vm.state.value.canSubmit)
        vm.onEvent(ResetPasswordEvent.PasswordChanged("1234567890"))
        assertTrue(vm.state.value.mismatch)
        vm.onEvent(ResetPasswordEvent.ConfirmChanged("1234567890"))
        assertTrue(vm.state.value.canSubmit)
        vm.onEvent(ResetPasswordEvent.Submit)
        assertTrue(vm.state.value.done)
        assertTrue(telemetry.events.contains("password_reset" to mapOf("step" to "confirm", "result" to "ok")))
    }

    @Test
    fun `reset without a token takes a pasted link, then a server error code maps to copy`() {
        repo.confirmResetResult = AppResult.Failure(AppError.Client(422, "x", "password_common"))
        val vm = reset(null)
        assertNull(vm.state.value.token)
        vm.onEvent(ResetPasswordEvent.PastedChanged("https://h/reset-password?token=zzz"))
        vm.onEvent(ResetPasswordEvent.UsePasted)
        assertEquals("zzz", vm.state.value.token)
        vm.onEvent(ResetPasswordEvent.PasswordChanged("1234567890"))
        vm.onEvent(ResetPasswordEvent.ConfirmChanged("1234567890"))
        vm.onEvent(ResetPasswordEvent.Submit)
        assertEquals(AuthMessage.Local(R.string.auth_err_password_common), vm.state.value.error)
        assertFalse(vm.state.value.done)
        assertTrue(telemetry.events.contains("password_reset" to mapOf("step" to "confirm", "result" to "error")))
    }

    @Test
    fun `reset success keeps the session out of the view model`() {
        repo.confirmResetResult = AppResult.Success(PasswordResetResponse("password_reset", session))
        val vm = reset()
        vm.onEvent(ResetPasswordEvent.PasswordChanged("1234567890"))
        vm.onEvent(ResetPasswordEvent.ConfirmChanged("1234567890"))
        vm.onEvent(ResetPasswordEvent.Submit)
        assertEquals("", vm.state.value.password)
    }
}
