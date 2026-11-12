package io.github.rupakc.travelplanner.feature.share.open

import io.github.rupakc.travelplanner.feature.share.ShareLinks

/** What the "paste a share link" box accepts (R6): a share link of any host, a `travelplanner://share/<token>` link or a bare token. */
internal object ShareLinkInput {
    /** Shortest bare token taken for a token (real ones are 22 URL-safe characters), so a stray word is not looked up. */
    private const val MIN_BARE_TOKEN = 8

    /** The share token in [pasted] (whitespace trimmed), or null when it is none. Only the token is used: no pasted host is ever contacted. */
    fun token(pasted: String?): String? {
        val text = pasted?.trim().orEmpty()
        if (text.isEmpty()) return null
        ShareLinks.tokenFrom(text)?.let { return it }
        return text.takeIf { it.length >= MIN_BARE_TOKEN && ShareLinks.isValidToken(it) }
    }
}
