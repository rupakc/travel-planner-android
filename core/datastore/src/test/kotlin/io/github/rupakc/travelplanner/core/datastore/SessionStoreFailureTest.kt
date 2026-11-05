package io.github.rupakc.travelplanner.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.settings.Session
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Review R2-I1 / R2-M2: a broken Keystore never crashes or locks the user out; expiry survives deep sleep. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionStoreFailureTest {
    @get:Rule val tmp = TemporaryFolder()

    private val user = User("alice")
    private val failures: List<() -> Throwable> =
        listOf({ GeneralSecurityException("keystore") }, { ProviderException("provider") }, { IOException("prefs io") })

    private val aead: Aead by lazy {
        AeadConfig.register()
        @Suppress("DEPRECATION")
        KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    /** Wall clock = virtual time + [offsetMs] (bump the offset to simulate deep sleep, which delay() doesn't see). */
    private class WallClock(private val scope: TestScope) : AppClock {
        var offsetMs = 1_000_000L

        override fun now(): Instant = Instant.ofEpochMilli(offsetMs + scope.testScheduler.currentTime)

        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    private fun jwt(expSeconds: Long): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("{}".toByteArray()) + "." + enc.encodeToString("""{"exp":$expSeconds}""".toByteArray()) + ".s"
    }

    private fun TestScope.dataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("s-${System.nanoTime()}.preferences_pb").also { it.delete() } }

    private fun TestScope.store(ds: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>, factory: () -> SessionCipher?) =
        EncryptedSessionStore(ds, factory, WallClock(this), StandardTestDispatcher(testScheduler))

    @Test
    fun `upgrade with a failing cipher keeps the legacy session in memory and removes the plaintext`() =
        runTest {
            for (failure in failures) {
                val ds = dataStore()
                ds.edit {
                    it[stringPreferencesKey("token")] = "legacy"
                    it[stringPreferencesKey("user_json")] = """{"username":"alice"}"""
                }
                val store = store(ds) { throw failure() }
                assertEquals("legacy", store.session.first()?.token)
                assertEquals("legacy", store.current()?.token)
                assertNull(ds.data.first()[stringPreferencesKey("token")])
            }
        }

    @Test
    fun `fresh login with a failing cipher still signs in for this process`() =
        runTest {
            for (failure in failures) {
                val store = store(dataStore()) { throw failure() }
                assertNull(store.session.first())
                store.save(Session("tok", user))
                assertEquals("tok", store.current()?.token)
                assertEquals("tok", store.session.first()?.token)
                store.clear()
                assertNull(store.current())
            }
            val nullCipher = store(dataStore()) { null }
            nullCipher.save(Session("tok", user))
            assertEquals("tok", nullCipher.current()?.token)
        }

    @Test
    fun `any exception while decrypting reads as signed out`() =
        runTest {
            for (failure in failures) {
                val ds = dataStore()
                store(ds) { AeadSessionCipher(aead) }.save(Session("tok", user))
                val broken =
                    object : SessionCipher {
                        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = aead.encrypt(plaintext, associatedData)

                        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = throw failure()
                    }
                val store = store(ds) { broken }
                assertNull(store.session.first())
                assertNull(store.current())
            }
        }

    @Test
    fun `an encryption failure on save falls back to memory, never throws`() =
        runTest {
            val failing =
                object : SessionCipher {
                    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = throw ProviderException("tee")

                    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray) = aead.decrypt(ciphertext, associatedData)
                }
            val ds = dataStore()
            val store = store(ds) { failing }
            store.save(Session("tok", user))
            assertEquals("tok", store.current()?.token)
            assertNull(ds.data.first()[stringPreferencesKey("session_v2")])
            assertEquals("alice", store.lastUsername())
        }

    @Test
    fun `expiry is noticed within one tick after deep sleep`() =
        runTest {
            val clock = WallClock(this)
            val store = EncryptedSessionStore(dataStore(), { AeadSessionCipher(aead) }, clock, StandardTestDispatcher(testScheduler))
            val expSeconds = clock.now().epochSecond + 3_600
            store.save(Session(jwt(expSeconds), user))
            val seen = mutableListOf<Session?>()
            val job = launch { store.session.toList(seen) }
            runCurrent()
            assertEquals("alice", seen.last()?.user?.username)
            clock.offsetMs += 8 * 3_600_000L // the device slept for 8 h: no uptime passed
            advanceTimeBy(EncryptedSessionStore.EXPIRY_TICK_MS + 1)
            runCurrent()
            assertNull(seen.last())
            assertFalse(seen.size > 2)
            job.cancel()
        }
}
