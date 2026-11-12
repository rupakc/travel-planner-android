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
import java.security.GeneralSecurityException
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

/** Review I4: the session is encrypted at rest, migrates from plaintext, and expires client-side. */
@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedSessionStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val aead: Aead by lazy {
        AeadConfig.register()
        KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }
    private val user = User("alice")

    /** Clock driven by the test scheduler (virtual ms since epoch 0 + [startSeconds]). */
    private class SchedulerClock(private val scope: TestScope, private val startSeconds: Long) : AppClock {
        override fun now(): Instant = Instant.ofEpochMilli(startSeconds * 1000 + scope.testScheduler.currentTime)

        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    private fun jwt(expSeconds: Long): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = enc.encodeToString("""{"sub":"alice","exp":$expSeconds}""".toByteArray())
        return "$header.$payload.signature"
    }

    private fun TestScope.newStore(cipher: SessionCipher = AeadSessionCipher(aead), startSeconds: Long = 1_000): Pair<EncryptedSessionStore, java.io.File> {
        val file = tmp.newFile("session-${System.nanoTime()}.preferences_pb").also { it.delete() }
        val ds = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        return EncryptedSessionStore(ds, { cipher }, SchedulerClock(this, startSeconds), StandardTestDispatcher(testScheduler)) to file
    }

    @Test
    fun `token is never written in clear and round-trips`() =
        runTest {
            val (store, file) = newStore()
            val token = jwt(10_000)
            store.save(Session(token, user, "https://api.example.com"))
            assertEquals(Session(token, user, "https://api.example.com"), store.current())
            assertFalse(file.readBytes().decodeToString().contains(token.substringBefore('.')))
            assertEquals("alice", store.lastUsername())
        }

    @Test
    fun `undecryptable value reads as signed out and is removed`() =
        runTest {
            val broken =
                object : SessionCipher {
                    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = aead.encrypt(plaintext, associatedData)

                    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = throw GeneralSecurityException("key lost")
                }
            val (store, _) = newStore(broken)
            store.save(Session(jwt(10_000), user))
            assertNull(store.session.first())
            assertNull(store.current())
        }

    @Test
    fun `plaintext session from an earlier build is migrated`() =
        runTest {
            val file = tmp.newFile("legacy.preferences_pb").also { it.delete() }
            val ds = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            ds.edit {
                it[stringPreferencesKey("token")] = "legacy-token"
                it[stringPreferencesKey("user_json")] = """{"username":"alice"}"""
            }
            val store = EncryptedSessionStore(ds, { AeadSessionCipher(aead) }, SchedulerClock(this, 0), StandardTestDispatcher(testScheduler))
            assertEquals("legacy-token", store.session.first()?.token)
            val raw = ds.data.first()
            assertNull(raw[stringPreferencesKey("token")])
            assertFalse(file.readBytes().decodeToString().contains("legacy-token"))
        }

    @Test
    fun `expired token reads as signed out`() =
        runTest {
            val (store, _) = newStore(startSeconds = 2_000)
            store.save(Session(jwt(1_500), user))
            assertNull(store.session.first())
            assertNull(store.current())
        }

    @Test
    fun `live session flips to signed out at its exp`() =
        runTest {
            val (store, _) = newStore(startSeconds = 1_000)
            store.save(Session(jwt(1_060), user))
            val seen = mutableListOf<Session?>()
            val job = launch { store.session.toList(seen) }
            runCurrent()
            assertEquals("alice", seen.single()?.user?.username)
            advanceTimeBy(61_000)
            runCurrent()
            assertNull(seen.last())
            job.cancel()
        }

    @Test
    fun `clear keeps the last username`() =
        runTest {
            val (store, _) = newStore()
            store.save(Session(jwt(10_000), user))
            store.clear()
            assertNull(store.current())
            assertEquals("alice", store.lastUsername())
        }

    @Test
    fun `new Phase 1 fields round-trip`() =
        runTest {
            val (store, _) = newStore()
            val full = Session(jwt(10_000), user, "https://api.example.com", refreshToken = "rt_1", expiresAtEpochMs = 9_999_000L, userId = "u-1")
            store.save(full)
            assertEquals(full, store.current())
        }

    @Test
    fun `a session sealed by an earlier build without the new fields still decodes`() =
        runTest {
            val file = tmp.newFile("old.preferences_pb").also { it.delete() }
            val ds = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
            val oldJson = """{"token":"${jwt(
                10_000,
            )}","user":{"username":"alice","is_admin":false,"requires_password_change":false,"email":null},"baseUrl":"https://api.example.com"}"""
            val sealed = Base64.getEncoder().encodeToString(aead.encrypt(oldJson.encodeToByteArray(), "travelplanner.session.v2".encodeToByteArray()))
            ds.edit { it[stringPreferencesKey("session_v2")] = sealed }
            val store = EncryptedSessionStore(ds, { AeadSessionCipher(aead) }, SchedulerClock(this, 1_000), StandardTestDispatcher(testScheduler))
            val s = store.current()
            assertEquals("alice", s?.user?.username)
            assertEquals("https://api.example.com", s?.baseUrl)
            assertNull(s?.refreshToken)
            assertNull(s?.expiresAtEpochMs)
            assertNull(s?.userId)
        }
}
