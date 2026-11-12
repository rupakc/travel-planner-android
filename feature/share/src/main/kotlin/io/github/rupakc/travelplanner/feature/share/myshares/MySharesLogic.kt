package io.github.rupakc.travelplanner.feature.share.myshares

/**
 * The story card of a share, derived from its public [url] (`<base>/share/<token>`): `<base>/api/share/<token>/card.png?size=story`.
 * Null when the url does not end in `/share/<token>` (the link is then shared without a card).
 */
internal fun storyCardUrl(url: String, token: String): String? {
    val suffix = "/share/$token"
    if (token.isBlank() || !url.endsWith(suffix)) return null
    return url.removeSuffix(suffix) + "/api/share/$token/card.png?size=story"
}

private const val DAY_CHARS = 10
private const val YEAR_END = 4
private const val MONTH_END = 7

/** `YYYY-MM-DD` of an ISO 8601 timestamp, or null when it is missing or not shaped like one. */
internal fun createdDay(iso: String?): String? =
    iso?.take(DAY_CHARS)?.takeIf { it.length == DAY_CHARS && it[YEAR_END] == '-' && it[MONTH_END] == '-' }
