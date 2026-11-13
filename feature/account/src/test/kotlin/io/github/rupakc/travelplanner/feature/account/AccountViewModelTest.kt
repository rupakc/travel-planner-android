package io.github.rupakc.travelplanner.feature.account

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthProviderInfo
import io.github.rupakc.travelplanner.core.data.repository.AuthProvidersRepository
import io.github.rupakc.travelplanner.core.data.repository.OauthInbox
import io.github.rupakc.travelplanner.core.domain.auth.OauthCallback
import io.github.rupakc.travelplanner.core.model.auth.AccountDto
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.auth.IdentityDto
import io.github.rupakc.travelplanner.core.model.auth.LinkTicketResponse
import io.github.rupakc.travelplanner.core.model.auth.OauthResult
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAccountRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeOauthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeAccountRepository()
    private val consent = FakeConsentRepository(true)
    private val appSettings = FakeSettingsRepository()
    private val telemetry = FakeTelemetry()
    private val cache = File("/cache")

    private val jane = User(username = "jane", id = "u1", displayName = "Jane", email = "j@x.com", emailVerified = false, hasPassword = true)

    private val providers = FakeProviders()
    private val oauth = FakeOauthRepository()
    private val inbox = OauthInbox()

    private fun vm(): AccountViewModel =
        AccountViewModel(repo, consent, appSettings, telemetry, { AccountRules.exportTarget(cache) }, providers, oauth, inbox)

    private class FakeProviders(var map: Map<String, AuthProviderInfo> = emptyMap()) : AuthProvidersRepository {
        override suspend fun providers() = map
    }

    @Test fun `link with an unconfigured provider says coming soon and opens nothing`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            vm.onEvent(AccountEvent.LinkRequested("google"))
            assertEquals(AccountMessage(R.string.account_link_coming_soon, arg = "Google"), vm.state.value.message)
            assertTrue(oauth.calls.isEmpty())
            assertTrue(telemetry.events.contains("account_action" to mapOf("action" to "link")))
        }

    @Test fun `link with a configured provider takes a ticket and hands the url to the shell`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            providers.map = mapOf("github" to AuthProviderInfo(configured = true, startUrl = "/api/auth/oauth/github/start"))
            oauth.linkTicketResult = AppResult.Success(LinkTicketResponse("http://h/api/auth/oauth/github/start?link=T"))
            val vm = vm()
            vm.onEvent(AccountEvent.LinkRequested("github"))
            assertEquals(AccountEffect.OpenLink("http://h/api/auth/oauth/github/start?link=T"), vm.effects.first())
            assertEquals(listOf("linkTicket:github"), oauth.calls)
        }

    @Test fun `a returning link code reloads the account`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            oauth.pending = true
            oauth.exchangeResult = AppResult.Success(OauthResult(status = "linked"))
            repo.accountResult = account(identities = listOf(IdentityDto(provider = "github")))
            inbox.post(OauthCallback.Code("c1"))
            assertEquals(AccountMessage(R.string.account_link_done), vm.state.value.message)
            assertEquals(listOf("github"), vm.state.value.identities.mapNotNull { it.provider })
        }

    private fun account(user: User = jane, settings: AccountSettings = AccountSettings(), identities: List<IdentityDto> = emptyList()) =
        AppResult.Success(AccountDto(user = user, identities = identities, settings = settings))

    @Test fun `settings PATCH sends only the changed keys`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            repo.patchSettingsResult = AppResult.Success(AccountSettings(theme = "dark"))
            vm.onEvent(AccountEvent.Change { it.copy(theme = "dark") })
            assertEquals(1, repo.patches.size)
            assertEquals(setOf("theme"), repo.patches.single().keys)
            assertEquals(JsonPrimitive("dark"), repo.patches.single()["theme"])
            // The same value again is not sent.
            vm.onEvent(AccountEvent.Change { it.copy(theme = "dark") })
            assertEquals(1, repo.patches.size)
        }

    @Test fun `a failed patch rolls the setting back and shows a message`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            repo.patchSettingsResult = AppResult.Failure(AppError.Network())
            vm.onEvent(AccountEvent.Change { it.copy(units = "imperial") })
            assertEquals("metric", vm.state.value.settings.units)
            assertNotNull(vm.state.value.message)
        }

    @Test fun `telemetry opt-out is applied to the local consent`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            repo.patchSettingsResult = AppResult.Success(AccountSettings(telemetryOptOut = true))
            vm.onEvent(AccountEvent.Change { it.copy(telemetryOptOut = true) })
            assertEquals(listOf(ConsentChoice.DENIED), consent.chosen)
        }

    @Test fun `a server opt-out denies local consent on load`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account(settings = AccountSettings(telemetryOptOut = true))
            vm()
            assertEquals(listOf(ConsentChoice.DENIED), consent.chosen)
        }

    @Test fun `a local denial is sent to the server on load`() =
        runTest(timeout = 30.seconds) {
            consent.allowedValue = false
            repo.accountResult = account()
            vm()
            assertEquals(setOf("telemetry_opt_out"), repo.patches.single().keys)
        }

    @Test fun `delete with a password sends the password`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            vm.onEvent(AccountEvent.OpenDelete)
            vm.onEvent(AccountEvent.ConfirmDelete)
            assertTrue(repo.deleteRequests.isEmpty())
            assertNotNull(vm.state.value.delete?.error)
            vm.onEvent(AccountEvent.DeleteInput("secret"))
            vm.effects.test {
                vm.onEvent(AccountEvent.ConfirmDelete)
                assertEquals(AccountEffect.SignedOut, awaitItem())
            }
            assertEquals("secret", repo.deleteRequests.single().password)
            assertNull(repo.deleteRequests.single().confirmUsername)
        }

    @Test fun `delete without a password needs the username`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account(user = jane.copy(hasPassword = false))
            val vm = vm()
            vm.onEvent(AccountEvent.OpenDelete)
            vm.onEvent(AccountEvent.DeleteInput("wrong"))
            vm.onEvent(AccountEvent.ConfirmDelete)
            assertTrue(repo.deleteRequests.isEmpty())
            vm.onEvent(AccountEvent.DeleteInput("jane"))
            vm.onEvent(AccountEvent.ConfirmDelete)
            assertEquals("jane", repo.deleteRequests.single().confirmUsername)
            assertNull(repo.deleteRequests.single().password)
        }

    @Test fun `a server refusal stays in the delete dialog`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            repo.deleteAccountResult = AppResult.Failure(AppError.Forbidden(code = "password_incorrect"))
            vm.onEvent(AccountEvent.OpenDelete)
            vm.onEvent(AccountEvent.DeleteInput("nope"))
            vm.onEvent(AccountEvent.ConfirmDelete)
            assertEquals(R.string.account_err_password_incorrect, vm.state.value.delete?.error?.res)
            assertEquals(false, vm.state.value.delete?.busy)
        }

    @Test fun `export goes to the cache target and is then shared`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            vm.effects.test {
                vm.onEvent(AccountEvent.Export)
                val effect = awaitItem() as AccountEffect.ShareExport
                assertEquals(AccountRules.exportTarget(cache), effect.file)
            }
            assertTrue(repo.calls.contains("exportTo"))
        }

    @Test fun `an export rate limit is shown`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            repo.exportResult = AppResult.Failure(AppError.RateLimited("Slow down", 60))
            vm.onEvent(AccountEvent.Export)
            assertEquals("Slow down", vm.state.value.message?.detail)
            assertEquals(false, vm.state.value.exporting)
        }

    @Test fun `unlinking the last method shows the explanation`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account(identities = listOf(IdentityDto("github")))
            val vm = vm()
            repo.unlinkIdentityResult = AppResult.Failure(AppError.Client(409, "x", code = "last_sign_in_method"))
            vm.onEvent(AccountEvent.AskUnlink("github"))
            vm.onEvent(AccountEvent.ConfirmUnlink)
            assertEquals(R.string.account_err_last_method, vm.state.value.message?.res)
            assertEquals(1, vm.state.value.identities.size)
        }

    @Test fun `unlink removes the identity`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account(identities = listOf(IdentityDto("github"), IdentityDto("google")))
            val vm = vm()
            vm.onEvent(AccountEvent.AskUnlink("github"))
            vm.onEvent(AccountEvent.ConfirmUnlink)
            assertEquals(listOf("google"), vm.state.value.identities.map { it.provider })
        }

    @Test fun `already verified reloads the account`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            repo.requestEmailVerificationResult = AppResult.Failure(AppError.Client(409, "x", code = "already_verified"))
            repo.accountResult = account(user = jane.copy(emailVerified = true))
            vm.onEvent(AccountEvent.VerifyEmail)
            assertEquals(true, vm.state.value.user?.emailVerified)
            assertEquals(R.string.account_err_already_verified, vm.state.value.message?.res)
        }

    @Test fun `avatar upload sends jpeg bytes and updates the user`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            repo.uploadAvatarResult = AppResult.Success("https://x/a.webp?v=1")
            vm.onEvent(AccountEvent.AvatarPicked(ByteArray(3)))
            assertTrue(repo.calls.contains("uploadAvatar:3:image/jpeg"))
            assertEquals("https://x/a.webp?v=1", vm.state.value.user?.avatarUrl)
            vm.onEvent(AccountEvent.AvatarPicked(null))
            assertEquals(R.string.account_err_avatar_type, vm.state.value.message?.res)
        }

    @Test fun `telemetry carries only the action enum`() =
        runTest(timeout = 30.seconds) {
            repo.accountResult = account()
            val vm = vm()
            vm.onEvent(AccountEvent.SaveName)
            vm.onEvent(AccountEvent.EditName)
            vm.onEvent(AccountEvent.NameChanged("Jane D"))
            vm.onEvent(AccountEvent.SaveName)
            val account = telemetry.events.filter { it.first == "account_action" }
            assertEquals(listOf(mapOf<String, Any?>("action" to "name_change")), account.map { it.second })
            assertEquals("Jane D", repo.calls.first { it.startsWith("updateDisplayName") }.removePrefix("updateDisplayName:"))
        }
}
