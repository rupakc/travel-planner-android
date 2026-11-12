package io.github.rupakc.travelplanner.core.domain.journey

import java.text.Normalizer
import java.util.Locale

/** Place keys (Trips-API, Place key): `<kind>:<slug>`, identical on every client and the server. */
object PlaceKey {
    private const val MAX_SLUG = 60
    private const val HASHED_SLUG = 51
    private const val FNV_OFFSET = 0x811c9dc5L
    private const val FNV_PRIME = 0x01000193L
    private const val MASK = 0xffffffffL
    private const val BYTE_MASK = 0xffL
    private const val ASCII_MAX = 127
    private val marks = Regex("\\p{M}+")
    private val runs = Regex("[^a-z0-9]+")

    /** The key for a place of [kind] named [name]. Pure ASCII-foldable names get the plain slug; others add `-<fnv1a hex>`. */
    fun of(kind: String, name: String): String {
        val folded = Normalizer.normalize(name, Normalizer.Form.NFKD).replace(marks, "").lowercase(Locale.ROOT)
        val slug = folded.replace(runs, "-").trim('-')
        val needsHash = folded.any { it.code > ASCII_MAX && it.isLetterOrDigit() }
        if (!needsHash) return "$kind:${slug.take(MAX_SLUG).ifEmpty { "x" }}"
        val head = slug.take(HASHED_SLUG).trim('-').ifEmpty { "x" }
        return "$kind:$head-${fnv1a(Normalizer.normalize(name.trim(), Normalizer.Form.NFC))}"
    }

    /** FNV-1a 32-bit over the UTF-8 bytes, as 8 lowercase hex digits. */
    private fun fnv1a(text: String): String {
        var h = FNV_OFFSET
        for (b in text.toByteArray(Charsets.UTF_8)) {
            h = ((h xor (b.toLong() and BYTE_MASK)) * FNV_PRIME) and MASK
        }
        return "%08x".format(h)
    }
}
