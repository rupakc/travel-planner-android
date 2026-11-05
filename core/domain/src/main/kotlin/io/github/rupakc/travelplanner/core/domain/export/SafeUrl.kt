package io.github.rupakc.travelplanner.core.domain.export

import java.net.URI

/**
 * Port of the web's `safeUrl` (`frontend/src/utils/planPrint.js`): the URL if it is a web link
 * (http/https with a host), normalised like `new URL(value).href` for the common cases
 * ("http://example.com" → "http://example.com/"), else null (no `javascript:`, `data:`, …).
 * Everything in a plan comes from AI agents or user input, so every exported or opened link goes through this.
 */
fun safeUrl(value: String?): String? {
    val uri = runCatching { URI(value?.trim() ?: return null) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrEmpty()) return null
    val raw = uri.toString()
    return if (uri.rawPath.isNullOrEmpty()) {
        val afterAuthority = raw.indexOf(uri.rawAuthority) + uri.rawAuthority.length
        raw.substring(0, afterAuthority) + "/" + raw.substring(afterAuthority)
    } else {
        raw
    }
}
