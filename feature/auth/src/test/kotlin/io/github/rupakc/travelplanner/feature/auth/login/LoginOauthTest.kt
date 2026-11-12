package io.github.rupakc.travelplanner.feature.auth.login

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthProviderInfo
import io.github.rupakc.travelplanner.core.data.repository.OauthInbox
import io.github.rupakc.travelplanner.core.designsystem.component.SocialProvider
import io.github.rupakc.travelplanner.core.domain.auth.OauthCallback
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.auth.OauthResult
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeOauthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The browser return of a social sign-in: the three results, errors, and callbacks without a pending attempt. */
class LoginOauthTest {
    @get:Rule val main = MainDispatcherRule()

    private val oauth = FakeOauthRepository()
    private val inbox = OauthInbox()
    private val telemetry = FakeTelemetry()
    private val google = mapOf("google" to AuthProviderInfo(configured = true, startUrl = "https://x.test/api/auth/oauth/google/start"))

    private fun vm() =
        loginViewModel(FakeAuthRepository(), providers = FakeAuthProvidersRepository(google), oauth = oauth, inbox = inbox, telemetry = telemetry)

    private fun LoginViewModel.startGoogle() = onEvent(LoginEvent.SocialProviderTapped(SocialProvider.GOOGLE))

    private fun signedIn() = OauthResult(status = "signed_in")

    @Test
    fun `signed_in marks the login done`() =
        runTest(timeout = 30.seconds) {
            oauth.exchangeResult = AppResult.Success(signedIn())
            val vm = vm()
            vm.startGoogle()
            inbox.post(OauthCallback.Code("abc"))
            assertTrue(vm.state.value.signedIn)
            assertEquals(listOf("begin:google", "exchange:abc"), oauth.calls)
            val result = telemetry.events.last { it.first == TelemetryEvents.AUTH_RESULT }.second
            assertEquals(mapOf("method" to "google", "mode" to "signin", "result" to "ok"), result)
        }

    @Test
    fun `needs_username keeps the draft and opens the username screen`() =
        runTest(timeout = 30.seconds) {
            val draft = OauthResult(status = "needs_username", signupToken = "tok", suggestedUsername = "jane")
            oauth.exchangeResult = AppResult.Success(draft)
            val vm = vm()
            vm.startGoogle()
            vm.effects.test {
                assertTrue(awaitItem() is LoginEffect.OpenUrl)
                inbox.post(OauthCallback.Code("abc"))
                assertEquals(LoginEffect.NeedsUsername, awaitItem())
            }
            assertEquals(draft, inbox.draft.value)
            assertFalse(vm.state.value.signedIn)
        }

    @Test
    fun `linked result is not a sign-in`() =
        runTest(timeout = 30.seconds) {
            oauth.exchangeResult = AppResult.Success(OauthResult(status = "linked", provider = "google"))
            val vm = vm()
            vm.startGoogle()
            inbox.post(OauthCallback.Code("abc"))
            assertFalse(vm.state.value.signedIn)
            assertFalse(vm.state.value.loading)
        }

    @Test
    fun `callback without a pending attempt is ignored`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            inbox.post(OauthCallback.Code("stale"))
            inbox.post(OauthCallback.Error("provider_error"))
            assertTrue(oauth.calls.isEmpty())
            assertNull(vm.state.value.error)
            assertFalse(vm.state.value.signedIn)
        }

    @Test
    fun `error callback shows its copy and ends the attempt`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.startGoogle()
            inbox.post(OauthCallback.Error("email_in_use_unverified"))
            assertEquals(AuthMessage.Local(R.string.auth_oauth_err_email_in_use_unverified), vm.state.value.error)
            assertFalse(oauth.hasPendingAttempt())
        }

    @Test
    fun `cancelled is reported as cancelled`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.startGoogle()
            inbox.post(OauthCallback.Error("cancelled"))
            assertEquals("cancelled", telemetry.events.last { it.first == TelemetryEvents.AUTH_RESULT }.second["result"])
        }

    @Test
    fun `code_invalid asks to start again`() =
        runTest(timeout = 30.seconds) {
            oauth.exchangeResult = AppResult.Failure(AppError.Client(status = 400, message = "Invalid code", code = "code_invalid"))
            val vm = vm()
            vm.startGoogle()
            inbox.post(OauthCallback.Code("abc"))
            assertEquals(AuthMessage.Local(R.string.auth_oauth_err_code_invalid), vm.state.value.error)
            assertFalse(vm.state.value.loading)
            assertFalse(oauth.hasPendingAttempt())
        }

    @Test
    fun `telemetry carries enums only`() =
        runTest(timeout = 30.seconds) {
            oauth.exchangeResult = AppResult.Success(signedIn())
            val vm = vm()
            vm.startGoogle()
            inbox.post(OauthCallback.Code("secret-code"))
            val flat = telemetry.events.flatMap { it.second.values }.map { it.toString() }
            assertTrue(flat.none { it.contains("secret-code") })
        }
}
