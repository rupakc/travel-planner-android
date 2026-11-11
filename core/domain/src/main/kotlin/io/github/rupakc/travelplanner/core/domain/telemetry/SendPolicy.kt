package io.github.rupakc.travelplanner.core.domain.telemetry

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

/** Maps an HTTP result to what the flusher does next (web `handleResponse`). */
object SendPolicy {
    private const val MAX_RETRY_AFTER_MS = 3_600_000L
    private const val MS_PER_S = 1000L
    private const val STATUS_REQUEST_TIMEOUT = 408
    private const val STATUS_NOT_FOUND = 404
    private const val STATUS_TOO_MANY = 429
    private const val SERVER_ERROR_MIN = 500
    private val SUCCESS = 200..299
    private val CLIENT_ERROR = 400..499

    /** [status] null means a network error. */
    fun classify(status: Int?, retryAfter: String?, nowMs: Long): SendOutcome =
        when {
            status == null || status == STATUS_REQUEST_TIMEOUT || status >= SERVER_ERROR_MIN -> SendOutcome.Retry
            status in SUCCESS -> SendOutcome.Sent
            status == STATUS_NOT_FOUND -> SendOutcome.Disabled
            status == STATUS_TOO_MANY -> parseRetryAfter(retryAfter, nowMs).let { if (it == 0L) SendOutcome.Retry else SendOutcome.RetryAfter(it) }
            status in CLIENT_ERROR -> SendOutcome.Dropped
            else -> SendOutcome.Retry
        }

    /** `Retry-After` as seconds or an HTTP date, in ms from [nowMs], capped at 1 h; absent or invalid gives 0. */
    fun parseRetryAfter(value: String?, nowMs: Long): Long {
        val v = value?.trim()
        if (v.isNullOrEmpty()) return 0L
        val ms =
            if (v.all { it in '0'..'9' }) {
                val secs = v.toLongOrNull() ?: return MAX_RETRY_AFTER_MS
                if (secs > MAX_RETRY_AFTER_MS / MS_PER_S) MAX_RETRY_AFTER_MS else secs * MS_PER_S
            } else {
                try {
                    ZonedDateTime.parse(v, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMs
                } catch (_: java.time.format.DateTimeParseException) {
                    return 0L
                }
            }
        return if (ms > 0) min(ms, MAX_RETRY_AFTER_MS) else 0L
    }
}

/** Exponential backoff after repeated failures (web `onFailure`): from the 3rd in a row, up to 5 min. */
class Backoff {
    private var failures = 0

    var blockedUntil: Long = 0L
        private set

    fun onSuccess() {
        failures = 0
        blockedUntil = 0L
    }

    fun onFailure(intervalMs: Long, nowMs: Long) {
        failures += 1
        if (failures >= FAILS_BEFORE_BACKOFF) {
            val shift = min(failures - 2, MAX_SHIFT)
            blockedUntil = nowMs + min(MAX_BACKOFF_MS, intervalMs * (1L shl shift))
        }
    }

    /** Blocks until [untilMs]; keeps the later of the two times. */
    fun block(untilMs: Long) {
        blockedUntil = maxOf(blockedUntil, untilMs)
    }

    private companion object {
        const val FAILS_BEFORE_BACKOFF = 3
        const val MAX_BACKOFF_MS = 300_000L
        const val MAX_SHIFT = 20
    }
}

private const val DEFAULT_FLUSH_S = 15
private const val MAX_FLUSH_S = 300
private const val MS_PER_S = 1000L
private const val DEFAULT_BATCH = 50
private const val MAX_BATCH = 100

/** Server-provided cadence, clamped: flush interval 1..300 s (default 15), batch 1..100 (default 50). */
fun TelemetrySettings.Companion.fromServer(flushIntervalS: Int?, maxBatch: Int?): TelemetrySettings =
    TelemetrySettings(
        flushIntervalMs = (flushIntervalS ?: DEFAULT_FLUSH_S).coerceIn(1, MAX_FLUSH_S) * MS_PER_S,
        maxBatch = (maxBatch ?: DEFAULT_BATCH).coerceIn(1, MAX_BATCH),
    )
