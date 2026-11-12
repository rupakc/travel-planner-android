package io.github.rupakc.travelplanner.feature.auth.login

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.data.repository.AuthProviderInfo
import io.github.rupakc.travelplanner.core.designsystem.component.SocialProvider
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeOauthRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Social sign-in feature detection and Privacy choices on the login screen. */
class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val consent = FakeConsentRepository()

    private val oauth = FakeOauthRepository()

    private fun vm(providers: Map<String, AuthProviderInfo> = emptyMap(), repo: FakeAuthProvidersRepository = FakeAuthProvidersRepository(providers)) =
        loginViewModel(FakeAuthRepository(), providers = repo, consent = consent, oauth = oauth)

    @Test
    fun `tapping Google shows coming soon for Google`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.effects.test {
                vm.onEvent(LoginEvent.SocialProviderTapped(SocialProvider.GOOGLE))
                assertEquals("Google", vm.state.value.comingSoonProvider)
                expectNoEvents()
            }
        }

    @Test
    fun `privacy choices opens the prompt`() {
        val vm = vm()
        vm.onEvent(LoginEvent.OpenPrivacyChoices)
        assertEquals(1, consent.openCount)
    }

    @Test
    fun `providers are asked once when the screen opens`() {
        val repo = FakeAuthProvidersRepository()
        val vm = vm(repo = repo)
        vm.onEvent(LoginEvent.SocialProviderTapped(SocialProvider.APPLE))
        vm.onEvent(LoginEvent.SocialProviderTapped(SocialProvider.GITHUB))
        assertEquals(1, repo.calls)
        assertEquals("GitHub", vm.state.value.comingSoonProvider)
    }

    @Test
    fun `configured provider with a safe start url opens it`() =
        runTest(timeout = 30.seconds) {
            val url = "https://voyager.example/api/auth/oauth/google/start"
            val vm = vm(mapOf("google" to AuthProviderInfo(configured = true, startUrl = url)))
            vm.onEvent(LoginEvent.SocialProviderTapped(SocialProvider.FACEBOOK))
            assertEquals("Facebook", vm.state.value.comingSoonProvider)
            vm.effects.test {
                vm.onEvent(LoginEvent.SocialProviderTapped(SocialProvider.GOOGLE))
                // The PKCE start URL comes from the repository, not from the server's `start_url` itself.
                assertEquals(LoginEffect.OpenUrl("https://example.test/api/auth/oauth/github/start"), awaitItem())
            }
            assertEquals(listOf("begin:google"), oauth.calls)
            assertNull(vm.state.value.comingSoonProvider)
        }

    @Test
    fun `configured provider with an offsite start url shows coming soon`() =
        runTest(timeout = 30.seconds) {
            // The repository resolves start URLs through SafeStartPath: an offsite URL arrives as null.
            val vm = vm(mapOf("github" to AuthProviderInfo(configured = true, startUrl = null)))
            vm.effects.test {
                vm.onEvent(LoginEvent.SocialProviderTapped(SocialProvider.GITHUB))
                assertEquals("GitHub", vm.state.value.comingSoonProvider)
                expectNoEvents()
            }
        }

    @Test
    fun `unconfigured provider with a start url shows coming soon`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(mapOf("apple" to AuthProviderInfo(configured = false, startUrl = "https://voyager.example/api/auth/oauth/apple/start")))
            vm.effects.test {
                vm.onEvent(LoginEvent.SocialProviderTapped(SocialProvider.APPLE))
                assertEquals("Apple", vm.state.value.comingSoonProvider)
                expectNoEvents()
            }
        }

    @Test
    fun `providers 404 shows coming soon`() =
        runTest(timeout = 30.seconds) {
            val vm = vm(emptyMap())
            vm.effects.test {
                vm.onEvent(LoginEvent.SocialProviderTapped(SocialProvider.GOOGLE))
                assertEquals("Google", vm.state.value.comingSoonProvider)
                expectNoEvents()
            }
        }
}
