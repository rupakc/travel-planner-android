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

/**
 * Tink AES-256-GCM keyset stored in SharedPreferences [KEYSET_PREFS] and wrapped by an
 * Android Keystore master key. Both the prefs file and the session file are excluded from backup,
 * so a restored ciphertext is never paired with a key. If the Keystore key is lost (some OEM restores),
 * decrypt fails and the session store treats the user as signed out.
 */
object TinkSessionCrypto {
    const val KEYSET_PREFS = "tp_keys"
    private const val KEYSET_NAME = "tp_session_keyset"
    private const val MASTER_KEY_ALIAS = "tp_session_master"
    private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
    private const val TAG = "SessionCrypto"

    /**
     * Builds the cipher. On failure (broken/reset Keystore, keyset prefs without their master key) the
     * keyset prefs and the Keystore alias are deleted and creation is retried once; if that fails too,
     * returns null and the session store keeps the session in memory only (review R2-I1).
     */
    @Suppress("TooGenericExceptionCaught")
    fun create(context: Context): SessionCipher? =
        try {
            build(context)
        } catch (first: Exception) {
            AppLogger.w(TAG, "Session keyset unusable; resetting it", first)
            reset(context)
            try {
                build(context)
            } catch (second: Exception) {
                AppLogger.e(TAG, "Session keyset could not be created", second)
                null
            }
        }

    private fun build(context: Context): SessionCipher {
        AeadConfig.register()
        val handle =
            AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
        return AeadSessionCipher(handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java))
    }

    @Suppress("TooGenericExceptionCaught")
    private fun reset(context: Context) {
        try {
            context.deleteSharedPreferences(KEYSET_PREFS)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not delete keyset prefs", e)
        }
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not delete the Keystore alias", e)
        }
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
