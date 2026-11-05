package io.github.rupakc.travelplanner.core.datastore

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import io.github.rupakc.travelplanner.core.common.AppLogger
import java.security.KeyStore
import java.util.Base64

/** Authenticated encryption for the session at rest (review I4). */
interface SessionCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /** Throws [java.security.GeneralSecurityException] when the ciphertext or key is not valid. */
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

class AeadSessionCipher(private val aead: Aead) : SessionCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = aead.encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = aead.decrypt(ciphertext, associatedData)
}

/** A built keyset: the cipher plus whether its keyset is wrapped by an Android Keystore key. */
data class BuiltKeyset(val cipher: SessionCipher, val usingKeystore: Boolean)

/** Where the session keyset comes from (Tink + Keystore in the app; fakes in tests). */
interface SessionKeysetSource {
    fun build(): BuiltKeyset

    /** Deletes the keyset prefs file and the Keystore alias. Must not throw. */
    fun reset()
}

object TinkSessionCrypto {
    const val KEYSET_PREFS = "tp_keys"
    private const val TAG = "SessionCrypto"

    fun create(context: Context): SessionCipher? = create(AndroidKeystoreKeysetSource(context))

    /**
     * Returns a cipher only if its keyset is protected by the Android Keystore; otherwise null, and the
     * session store keeps the session in memory only.
     * - Tink 1.24 does not throw when the Keystore is broken: it logs, disables the Keystore and writes the
     *   keyset **in clear** to the prefs file. So after every build `usingKeystore` is checked; if false the
     *   prefs file (cleartext keyset) and alias are deleted and null is returned (review R3-I1).
     * - If the build throws (unusable existing key, corrupt prefs), the keyset and alias are reset and the build
     *   is retried once, with the same check (review R2-I1). A transient Keystore error therefore also resets
     *   the keyset and signs the user out — accepted: sign-out beats a crash or a cleartext key (review R3-M3).
     */
    @Suppress("TooGenericExceptionCaught")
    fun create(source: SessionKeysetSource): SessionCipher? =
        try {
            keystoreOnly(source, source.build())
        } catch (first: Exception) {
            AppLogger.w(TAG, "Session keyset unusable; resetting it", first)
            source.reset()
            try {
                keystoreOnly(source, source.build())
            } catch (second: Exception) {
                AppLogger.e(TAG, "Session keyset could not be created", second)
                source.reset()
                null
            }
        }

    private fun keystoreOnly(source: SessionKeysetSource, built: BuiltKeyset): SessionCipher? {
        if (built.usingKeystore) return built.cipher
        AppLogger.w(TAG, "Android Keystore unavailable: deleting the unprotected keyset; session kept in memory only")
        source.reset()
        return null
    }
}

/**
 * Tink AES-256-GCM keyset in SharedPreferences [TinkSessionCrypto.KEYSET_PREFS], wrapped by an Android Keystore
 * master key. Both the prefs file and the session file are excluded from backup, so a restored ciphertext is never
 * paired with a key. If the Keystore key is lost (some OEM restores), decrypt fails and the user is signed out.
 */
class AndroidKeystoreKeysetSource(private val context: Context) : SessionKeysetSource {
    override fun build(): BuiltKeyset {
        AeadConfig.register()
        val manager =
            AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, TinkSessionCrypto.KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
        val aead = manager.keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        return BuiltKeyset(AeadSessionCipher(aead), manager.isUsingKeystore)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun reset() {
        try {
            context.deleteSharedPreferences(TinkSessionCrypto.KEYSET_PREFS)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not delete keyset prefs", e)
        }
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not delete the Keystore alias", e)
        }
    }

    private companion object {
        const val KEYSET_NAME = "tp_session_keyset"
        const val MASTER_KEY_ALIAS = "tp_session_master"
        const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
        const val TAG = "SessionCrypto"
    }
}

/** Reads the `exp` claim (seconds since epoch) of a JWT without verifying it (the server verifies). */
object JwtExpiry {
    fun expiresAtEpochSeconds(token: String): Long? {
        val payload = token.split('.').getOrNull(1) ?: return null
        val json = runCatching { String(Base64.getUrlDecoder().decode(payload.padBase64()), Charsets.UTF_8) }.getOrNull() ?: return null
        return EXP.find(json)?.groupValues?.get(1)?.toDoubleOrNull()?.toLong()
    }

    private val EXP = Regex(""""exp"\s*:\s*([0-9]+(?:\.[0-9]+)?)""")

    private const val BASE64_BLOCK = 4

    private fun String.padBase64(): String = this + "=".repeat((BASE64_BLOCK - length % BASE64_BLOCK) % BASE64_BLOCK)
}
