package io.github.rupakc.travelplanner.feature.settings

import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.feature.settings.appearance.AppearanceEvent
import io.github.rupakc.travelplanner.feature.settings.appearance.AppearanceViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppearanceViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `SetTheme DARK emits theme_change dark`() =
        runTest {
            val telemetry = FakeTelemetry()
            AppearanceViewModel(FakeSettingsRepository(), telemetry).onEvent(AppearanceEvent.SetTheme(ThemeMode.DARK))
            assertEquals(listOf("theme_change" to mapOf<String, Any?>("theme" to "dark")), telemetry.events)
        }
}
