package io.github.rupakc.travelplanner.core.datastore

import java.io.File
import java.security.GeneralSecurityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Review R3-I1: a keyset Tink stored without the Keystore (in clear) is never used and never left on disk. */
class KeystoreOnlyCipherTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cipher =
        object : SessionCipher {
            override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = plaintext

            override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray) = ciphertext
        }

    /** Simulates Tink: each build writes the keyset file; [usingKeystore] says whether it is Keystore-wrapped. */
    private inner class ProbeSource(private val outcomes: List<() -> Boolean>) : SessionKeysetSource {
        val prefs: File = File(tmp.root, "tp_keys.xml")
        var builds = 0
        var resets = 0

        override fun build(): BuiltKeyset {
            val usingKeystore = outcomes[builds++]()
            prefs.writeText(if (usingKeystore) "wrapped-keyset" else "CLEARTEXT-keyset")
            return BuiltKeyset(cipher, usingKeystore)
        }

        override fun reset() {
            resets++
            prefs.delete()
        }
    }

    @Test
    fun `keystore-backed keyset is used`() {
        val source = ProbeSource(listOf({ true }))
        assertNotNull(TinkSessionCrypto.create(source))
        assertEquals(0, source.resets)
    }

    @Test
    fun `isUsingKeystore false means memory-only and no plaintext keyset on disk`() {
        val source = ProbeSource(listOf({ false }))
        assertNull(TinkSessionCrypto.create(source))
        assertFalse(source.prefs.exists())
        assertEquals(1, source.builds)
    }

    @Test
    fun `a retry that falls back to cleartext is rejected too`() {
        val source = ProbeSource(listOf({ throw GeneralSecurityException("existing key unusable") }, { false }))
        assertNull(TinkSessionCrypto.create(source))
        assertFalse(source.prefs.exists())
        assertEquals(2, source.builds)
    }

    @Test
    fun `a retry that gets a keystore-backed keyset succeeds`() {
        val source = ProbeSource(listOf({ throw GeneralSecurityException("stale") }, { true }))
        assertNotNull(TinkSessionCrypto.create(source))
        assertEquals(1, source.resets)
    }

    @Test
    fun `two failures leave nothing behind`() {
        val source = ProbeSource(listOf({ throw GeneralSecurityException("a") }, { throw IllegalStateException("b") }))
        assertNull(TinkSessionCrypto.create(source))
        assertFalse(source.prefs.exists())
    }
}
