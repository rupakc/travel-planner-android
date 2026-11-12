package io.github.rupakc.travelplanner.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
class CoachMarkStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test(timeout = 30_000)
    fun `seen marks are kept per user and accumulate`() =
        runTest(UnconfinedTestDispatcher()) {
            val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val store =
                PreferencesCoachMarkStore(
                    PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { File(folder.root, "c.preferences_pb") },
                )
            assertEquals(emptySet<String>(), store.seen("u1").first())
            store.markSeen("u1", setOf("trips"))
            store.markSeen("u1", setOf("play"))
            store.markSeen("u2", setOf("explore"))
            assertEquals(setOf("trips", "play"), store.seen("u1").first())
            assertEquals(setOf("explore"), store.seen("u2").first())
        }
}
