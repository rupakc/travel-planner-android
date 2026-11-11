package io.github.rupakc.travelplanner.core.model

import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsDefaultsTest {
    @Test fun `default theme is light`() = assertEquals(ThemeMode.LIGHT, AppSettings().themeMode)
}
