package io.github.rupakc.travelplanner.core.datastore

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
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
    private const val MASTER_KEY_URI = "android-keystore://tp_session_master"

    fun create(context: Context): SessionCipher {
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
