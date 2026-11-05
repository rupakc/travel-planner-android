package io.github.rupakc.travelplanner.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.model.preferences.TasteSignal
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import io.github.rupakc.travelplanner.feature.settings.appearance.AppearanceEvent
import io.github.rupakc.travelplanner.feature.settings.appearance.AppearanceViewModel
import io.github.rupakc.travelplanner.feature.settings.features.FeaturesEvent
import io.github.rupakc.travelplanner.feature.settings.features.FeaturesViewModel
import io.github.rupakc.travelplanner.feature.settings.notifications.NotificationSettingsViewModel
import io.github.rupakc.travelplanner.feature.settings.notifications.NotificationToggle
import io.github.rupakc.travelplanner.feature.settings.notifications.SetToggle
import io.github.rupakc.travelplanner.feature.settings.root.SettingsEvent
import io.github.rupakc.travelplanner.feature.settings.root.SettingsViewModel
import io.github.rupakc.travelplanner.feature.settings.server.ConnectionTest
import io.github.rupakc.travelplanner.feature.settings.server.ServerEvent
import io.github.rupakc.travelplanner.feature.settings.server.ServerViewModel
import io.github.rupakc.travelplanner.feature.settings.server.UrlProblem
import io.github.rupakc.travelplanner.feature.settings.taste.TasteEvent
import io.github.rupakc.travelplanner.feature.settings.taste.TasteProfileViewModel
import java.util.Optional
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelsTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository(Session("t", TestUser.copy(email = "a@b.co")))
    private val profile = FakeProfileRepository(TravellerProfile(onboardingCompleted = true, primaryType = "LUXURY"))
    private val settings = FakeSettingsRepository()
    private val locales = FakeAppLocales()
    private val catalogue = FakeCatalogue(setOf(FeatureId.CHAT, FeatureId.COMPARE))

    private fun root() = SettingsViewModel(auth, profile, settings, Optional.of(catalogue), locales, TestBuildInfo)

    @Test
    fun `root shows account, type, feature count, url and version`() =
        runTest {
            root().state.test {
                val s = expectMostRecentItem()
                assertEquals("alice", s.user?.username)
                assertEquals(listOf(TravellerType.LUXURY), s.travellerTypes)
                assertEquals(2, s.featuresOn)
                assertEquals("http://10.0.2.2:8001", s.effectiveBaseUrl)
                assertEquals("1.2.3", s.versionName)
            }
        }

    @Test
    fun `language is stored and applied per app`() =
        runTest {
            val vm = root()
            vm.state.test {
                vm.onEvent(SettingsEvent.ChooseLanguage)
                assertTrue(expectMostRecentItem().choosingLanguage)
                vm.onEvent(SettingsEvent.SetLanguage("es"))
                assertFalse(expectMostRecentItem().choosingLanguage)
                assertEquals("es", settings.state.value.localeTag)
                assertEquals(listOf<String?>("es"), locales.applied)
                vm.onEvent(SettingsEvent.SetLanguage(null))
                assertEquals(listOf("es", null), locales.applied)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sign out asks first, then logs out (which unregisters the device) and reports`() =
        runTest {
            val vm = root()
            vm.state.test {
                vm.onEvent(SettingsEvent.AskSignOut)
                assertTrue(expectMostRecentItem().confirmingSignOut)
                assertEquals(0, auth.logoutCalls)
                vm.onEvent(SettingsEvent.ConfirmSignOut)
                assertEquals(1, auth.logoutCalls)
                assertTrue(vm.signedOut.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `taste graph loads, resets after confirm and reloads`() =
        runTest {
            val repo =
                ScriptedPreferencesRepository(
                    tasteResult = AppResult.Success(TasteProfile(mapOf("interest" to listOf(TasteSignal("food", 2))), totalSignals = 2)),
                )
            val vm = TasteProfileViewModel(repo)
            assertTrue(vm.state.value.hasSignals)
            vm.onEvent(TasteEvent.AskReset)
            assertTrue(vm.state.value.confirmingReset)
            repo.tasteResult = AppResult.Success(TasteProfile(totalSignals = 0))
            vm.onEvent(TasteEvent.ConfirmReset)
            assertEquals(1, repo.resetCalls)
            assertFalse(vm.state.value.hasSignals)
            assertFalse(vm.state.value.confirmingReset)
        }

    @Test
    fun `taste reset failure is shown and load failure can retry`() =
        runTest {
            val repo = ScriptedPreferencesRepository(tasteResult = AppResult.Failure(AppError.Network()))
            val vm = TasteProfileViewModel(repo)
            assertEquals(AppError.Network(), vm.state.value.error)
            repo.tasteResult = AppResult.Success(TasteProfile(totalSignals = 1))
            vm.onEvent(TasteEvent.Retry)
            assertNull(vm.state.value.error)
            repo.resetResult = AppResult.Failure(AppError.Server(500, "boom"))
            vm.onEvent(TasteEvent.ConfirmReset)
            assertEquals("boom", vm.state.value.resetError?.message)
        }

    @Test
    fun `feature toggles store overrides only when they differ from the type default`() =
        runTest {
            val vm = FeaturesViewModel(profile, Optional.of(catalogue))
            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(FeatureId.entries.size, s.rows.size)
                assertTrue(s.rows.first { it.id == FeatureId.CHAT }.on)
                vm.onEvent(FeaturesEvent.Toggle(FeatureId.CHAT, false))
                vm.onEvent(FeaturesEvent.Toggle(FeatureId.NEAR_ME, true))
                val after = expectMostRecentItem()
                assertFalse(after.rows.first { it.id == FeatureId.CHAT }.on)
                assertTrue(after.rows.first { it.id == FeatureId.NEAR_ME }.on)
                assertEquals(setOf("chat"), profile.overridesState.value.disabled)
                vm.onEvent(FeaturesEvent.Toggle(FeatureId.CHAT, true))
                assertEquals(emptySet<String>(), profile.overridesState.value.disabled)
                vm.onEvent(FeaturesEvent.ResetToDefaults)
                assertFalse(expectMostRecentItem().hasOverrides)
            }
        }

    @Test
    fun `appearance and notification toggles write device settings`() =
        runTest {
            val appearance = AppearanceViewModel(settings)
            appearance.onEvent(AppearanceEvent.SetTheme(ThemeMode.DARK))
            appearance.onEvent(AppearanceEvent.SetDynamicColor(true))
            val notifications = NotificationSettingsViewModel(settings)
            notifications.onEvent(SetToggle(NotificationToggle.NEARBY, true))
            notifications.onEvent(SetToggle(NotificationToggle.PRICE_ALERTS, false))
            val s = settings.state.value
            assertEquals(ThemeMode.DARK, s.themeMode)
            assertTrue(s.dynamicColor)
            assertTrue(s.nearbyNotifications)
            assertFalse(s.priceAlertNotifications)
            assertTrue(s.tripNotifications)
        }

    @Test
    fun `server rejects invalid urls with the repository code`() =
        runTest {
            val base = settings
            val rejecting =
                object : SettingsRepository by base {
                    override suspend fun setBaseUrl(url: String?): AppResult<Unit> =
                        if (url != null && !url.startsWith("https")) AppResult.Failure(AppError.Client(0, null, "https_required")) else base.setBaseUrl(url)
                }
            val vm = ServerViewModel(SavedStateHandle(), rejecting, FakeHealthRepository(), TestBuildInfo)
            vm.onEvent(ServerEvent.InputChanged("http://plain"))
            vm.onEvent(ServerEvent.Save)
            assertEquals(UrlProblem.HTTPS_REQUIRED, vm.state.value.problem)
            vm.onEvent(ServerEvent.InputChanged("https://ok.example"))
            vm.onEvent(ServerEvent.Save)
            assertNull(vm.state.value.problem)
            assertTrue(vm.state.value.saved)
            assertEquals("https://ok.example", settings.state.value.baseUrlOverride)
            vm.onEvent(ServerEvent.UseDefault)
            assertNull(settings.state.value.baseUrlOverride)
            assertTrue(vm.state.value.usingDefault)
        }

    @Test
    fun `test connection saves a changed url then reports health flags or the failure`() =
        runTest {
            val health = FakeHealthRepository(Health(status = "ok", serpapiConfigured = true, pushConfigured = true))
            val vm = ServerViewModel(SavedStateHandle(), settings, health, TestBuildInfo)
            vm.onEvent(ServerEvent.InputChanged("http://192.168.1.2:8001"))
            vm.onEvent(ServerEvent.Test)
            assertEquals("http://192.168.1.2:8001", settings.state.value.baseUrlOverride)
            val ok = vm.state.value.test as ConnectionTest.Ok
            assertTrue(ok.health.serpapiConfigured)
            health.set(null)
            vm.onEvent(ServerEvent.Test)
            assertTrue(vm.state.value.test is ConnectionTest.Failed)
        }

    @Test
    fun `sign out finishes clearing caches even when the screen closes mid-logout`() =
        runTest {
            var cachesCleared = false
            lateinit var vm: SettingsViewModel
            val closingAuth =
                object : io.github.rupakc.travelplanner.core.data.repository.AuthRepository by auth {
                    override suspend fun logout() {
                        auth.sessionFlow.value = null
                        // The auth gate pops Settings here, cancelling its viewModelScope.
                        vm.viewModelScope.cancel()
                        yield()
                        cachesCleared = true
                    }
                }
            vm = SettingsViewModel(closingAuth, profile, settings, Optional.of(catalogue), locales, TestBuildInfo)
            vm.onEvent(SettingsEvent.ConfirmSignOut)
            assertTrue(cachesCleared)
        }

    @Test
    fun `language row follows the system per-app locale`() =
        runTest {
            locales.apply("es")
            root().state.test { assertEquals("es", expectMostRecentItem().languageTag) }
        }

    @Test
    fun `nearby always is its own setting`() =
        runTest {
            val notifications = NotificationSettingsViewModel(settings)
            notifications.onEvent(SetToggle(NotificationToggle.NEARBY_ALWAYS, true))
            assertTrue(settings.state.value.nearbyNotificationsAlways)
            assertFalse(settings.state.value.nearbyNotifications)
        }

    @Test
    fun `a toggle before the state is collected still compares against the type default`() =
        runTest {
            val vm = FeaturesViewModel(profile, Optional.of(catalogue))
            vm.onEvent(FeaturesEvent.Toggle(FeatureId.CHAT, true))
            assertTrue(profile.overridesState.value.enabled.isEmpty())
            vm.onEvent(FeaturesEvent.Toggle(FeatureId.NEAR_ME, true))
            assertEquals(setOf("near_me"), profile.overridesState.value.enabled)
        }

    @Test
    fun `feature groups cover every catalogue feature exactly once`() {
        val members = io.github.rupakc.travelplanner.feature.settings.features.FeatureGroup.entries.flatMap { it.members }
        assertEquals(FeatureId.entries.toSet(), members.toSet())
        assertEquals(members.size, members.toSet().size)
    }
}
