package io.github.rupakc.travelplanner.core.domain.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE (RFC 7636, S256) for the OAuth system-browser leg. */
object Pkce {
    /** Length of a generated verifier: 48 random bytes in unpadded base64url. Within the RFC's 43 to 128. */
    const val VERIFIER_LENGTH = 64
    private const val RANDOM_BYTES = 48
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** A fresh high-entropy verifier ([VERIFIER_LENGTH] URL-safe characters). */
    fun newVerifier(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(RANDOM_BYTES)
        random.nextBytes(bytes)
        return encoder.encodeToString(bytes)
    }

    /** `b64url(sha256(verifier))`, no padding. */
    fun challenge(verifier: String): String = encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
