package io.github.rupakc.travelplanner.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreThemeTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `missing theme key reads LIGHT`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val ds = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(folder.root, "s.preferences_pb") }
            val store = PreferencesSettingsStore(ds)
            assertEquals(ThemeMode.LIGHT, store.current().themeMode)
            ds.edit { it[stringPreferencesKey("theme_mode")] = "bogus" }
            assertEquals(ThemeMode.LIGHT, store.current().themeMode)
            store.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, store.settings.first().themeMode)
        }
}
