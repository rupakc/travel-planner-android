package io.github.rupakc.travelplanner.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.rupakc.travelplanner.core.model.settings.FeatureOverrides
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
class FeatureOverridesStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `replace swaps both sets in one write`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val store =
                PreferencesFeatureOverridesStore(
                    PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(folder.root, "o.preferences_pb") },
                )
            store.set("sim", false)
            store.set("chat", true)
            store.replace(FeatureOverrides(enabled = setOf("forex"), disabled = setOf("events", "forex")))
            assertEquals(FeatureOverrides(enabled = setOf("forex"), disabled = setOf("events")), store.overrides.first())
        }
}
