package io.github.rupakc.travelplanner.core.network.sse

import okio.BufferedSource

/**
 * Server-sent-events framing as the backend uses it (api-contract §3.1):
 * `data: <json>` lines, events separated by a blank line, `:` comment lines
 * (keepalives) ignored, no `event:`/`id:` fields relied upon.
 *
 * Pure: works over any line sequence, so it is unit-testable without a network.
 * Consecutive `data:` lines of one event are joined with `\n` (SSE spec);
 * a final event without its trailing blank line is still delivered at EOF.
 */
object SseParser {
    private const val DATA = "data:"

    /** The data payloads of [lines], in order. */
    fun payloads(lines: Sequence<String>): Sequence<String> = sequence {
        val pending = StringBuilder()
        var hasData = false
        for (rawLine in lines) {
            val line = rawLine.removeSuffix("\r")
            when {
                line.isEmpty() -> {
                    if (hasData) yield(pending.toString())
                    pending.setLength(0)
                    hasData = false
                }
                line.startsWith(":") -> Unit // comment / keepalive
                line.startsWith(DATA) -> {
                    if (hasData) pending.append('\n')
                    pending.append(line.removePrefix(DATA).removePrefix(" "))
                    hasData = true
                }
                else -> Unit // event:, id:, retry: and unknown fields are ignored
            }
        }
        if (hasData) yield(pending.toString())
    }

    /** Reads lines lazily (blocking) from [source]; chunk boundaries are handled by Okio's buffering. */
    fun lines(source: BufferedSource): Sequence<String> = generateSequence { source.readUtf8Line() }

    fun payloads(source: BufferedSource): Sequence<String> = payloads(lines(source))
}
