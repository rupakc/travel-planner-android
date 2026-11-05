package io.github.rupakc.travelplanner.core.network

import okhttp3.logging.HttpLoggingInterceptor

/**
 * Masks secrets in debug HTTP log lines (security review S-M3): the share token in `/share/<token>`,
 * any other token-like path segment (24+ URL-safe characters with a digit), and `token`-named query values.
 * Plan ids, airport codes and words stay readable.
 */
object LogRedaction {
    const val MASK = "<redacted>"

    private val sharePath = Regex("""(/share/)[^/?#\s]+""")
    private val tokenSegment = Regex("""(?<=/)(?=[A-Za-z0-9_-]*\d)[A-Za-z0-9_-]{24,}(?=[/?#\s]|$)""")
    private val tokenQuery = Regex("""([?&][A-Za-z_]*token=)[^&#\s]+""", RegexOption.IGNORE_CASE)

    fun redact(line: String): String =
        line
            .replace(sharePath, "$1$MASK")
            .replace(tokenSegment, MASK)
            .replace(tokenQuery, "$1$MASK")
}

/** Debug HTTP logger that passes every line through [LogRedaction] before OkHttp's default sink. */
class RedactingHttpLogger(
    private val delegate: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
) : HttpLoggingInterceptor.Logger {
    override fun log(message: String) = delegate.log(LogRedaction.redact(message))
}
