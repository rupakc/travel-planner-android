package io.github.rupakc.travelplanner.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TravellerProfileStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `home airport round-trips and is removed when null`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val store =
                PreferencesTravellerProfileStore(PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(folder.root, "p.preferences_pb") })
            store.save(TravellerProfile(homeAirport = "BLR"))
            assertEquals("BLR", store.profile.first().homeAirport)
            store.save(TravellerProfile())
            assertNull(store.profile.first().homeAirport)
        }
}
