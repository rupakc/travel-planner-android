package io.github.rupakc.travelplanner.feature.settings

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAppLanguage
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeConsentRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import io.github.rupakc.travelplanner.feature.settings.root.SettingsEvent
import io.github.rupakc.travelplanner.feature.settings.root.SettingsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val consent = FakeConsentRepository(initiallyAllowed = true)
    private val telemetry = FakeTelemetry()
    private val language = FakeAppLanguage("es")

    private fun vm() =
        SettingsViewModel(
            FakeAuthRepository(Session("t", TestUser)),
            FakeProfileRepository(TravellerProfile(onboardingCompleted = true, primaryType = "LUXURY")),
            FakeSettingsRepository(),
            FakeCatalogue(setOf(FeatureId.CHAT)),
            FakeAppLocales(),
            TestBuildInfo,
            consent,
            telemetry,
            language,
        )

    @Test
    fun `SetLanguage de emits language_change de`() =
        runTest {
            val vm = vm()
            vm.onEvent(SettingsEvent.SetLanguage("de"))
            assertEquals(listOf("language_change" to mapOf<String, Any?>("lang" to "de")), telemetry.events)
        }

    @Test
    fun `SetLanguage system emits the current app language`() =
        runTest {
            vm().onEvent(SettingsEvent.SetLanguage(null))
            assertEquals(listOf("language_change" to mapOf<String, Any?>("lang" to "es")), telemetry.events)
        }

    @Test
    fun `SetLanguage system reports the language in effect after apply`() =
        runTest {
            val locales = FakeAppLocales()
            val switching =
                object : AppLanguage {
                    override fun current() = if (locales.applied.isEmpty()) "es" else "en"
                }
            SettingsViewModel(
                FakeAuthRepository(Session("t", TestUser)),
                FakeProfileRepository(TravellerProfile(onboardingCompleted = true, primaryType = "LUXURY")),
                FakeSettingsRepository(),
                FakeCatalogue(setOf(FeatureId.CHAT)),
                locales,
                TestBuildInfo,
                consent,
                telemetry,
                switching,
            ).onEvent(SettingsEvent.SetLanguage(null))
            assertEquals(listOf("language_change" to mapOf<String, Any?>("lang" to "en")), telemetry.events)
        }

    @Test
    fun `privacy switch off chooses DENIED`() =
        runTest {
            val vm = vm()
            vm.onEvent(SettingsEvent.SetPrivacy(false))
            assertEquals(listOf(ConsentChoice.DENIED), consent.chosen)
            vm.onEvent(SettingsEvent.SetPrivacy(true))
            assertEquals(listOf(ConsentChoice.DENIED, ConsentChoice.GRANTED), consent.chosen)
        }

    @Test
    fun `privacy switch state follows consent`() =
        runTest {
            val vm = vm()
            vm.state.test {
                assertTrue(awaitItem().let { if (it.loading) awaitItem() else it }.privacyAllowed)
                consent.allowedValue = false
                assertFalse(awaitItem().privacyAllowed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `privacy choices opens the prompt`() =
        runTest {
            vm().onEvent(SettingsEvent.OpenPrivacyChoices)
            assertEquals(1, consent.openCount)
        }
}
