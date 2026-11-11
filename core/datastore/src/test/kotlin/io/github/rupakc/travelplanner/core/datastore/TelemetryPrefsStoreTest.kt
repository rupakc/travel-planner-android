package io.github.rupakc.travelplanner.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TelemetryPrefsStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun TestScope.dataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "telemetry.preferences_pb") }

    @Test
    fun `anonId is stable across calls and a UUID`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = TelemetryPrefsStoreImpl(dataStore())
            val first = store.anonId()
            assertEquals(first, UUID.fromString(first).toString())
            assertEquals(first, store.anonId())
        }

    @Test
    fun `a non-UUID stored value is replaced`() =
        runTest(UnconfinedTestDispatcher()) {
            val ds = dataStore()
            ds.edit { it[stringPreferencesKey("anon_id")] = "not-a-uuid" }
            val store = TelemetryPrefsStoreImpl(ds)
            val id = store.anonId()
            assertNotEquals("not-a-uuid", id)
            assertEquals(id, UUID.fromString(id).toString())
            assertEquals(id, store.anonId())
        }

    @Test
    fun `consent round-trips and starts null`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = TelemetryPrefsStoreImpl(dataStore())
            assertNull(store.consent.first())
            store.setConsent(ConsentChoice.GRANTED)
            assertEquals(ConsentChoice.GRANTED, store.consent.first())
            store.setConsent(ConsentChoice.DENIED)
            assertEquals(ConsentChoice.DENIED, store.consent.first())
        }

    @Test
    fun `session round-trips`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = TelemetryPrefsStoreImpl(dataStore())
            assertNull(store.session())
            store.setSession(StoredSession("abc", 1234L))
            assertEquals(StoredSession("abc", 1234L), store.session())
        }
}
