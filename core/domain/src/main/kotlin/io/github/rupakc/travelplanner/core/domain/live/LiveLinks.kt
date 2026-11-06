package io.github.rupakc.travelplanner.core.domain.live

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * [uri] when it is an absolute http(s) link with a host, else null. Grounded answers are shown
 * as links only through this check (no `intent:`, `javascript:` or `file:` schemes).
 */
fun safeWebUri(uri: String?): String? {
    val text = uri?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val parsed = runCatching { URI(text) }.getOrNull() ?: return null
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    return text.takeIf { (scheme == "https" || scheme == "http") && !parsed.host.isNullOrBlank() }
}

/** A Google Search link for one "Searched for" query (the spec's Search Suggestions alternative). */
fun googleSearchUrl(query: String): String = "https://www.google.com/search?q=" + URLEncoder.encode(query.trim(), "UTF-8")
