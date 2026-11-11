package io.github.rupakc.travelplanner.core.domain.telemetry

import kotlin.math.abs

/** The session after a [TelemetrySession.touch]: [persist] says the caller should store id + time. */
data class SessionTouch(val id: String, val isNew: Boolean, val persist: Boolean)

/** Session tracking: a new session after 30 min idle (or a clock that jumped back); writes throttled to 10 s. */
class TelemetrySession(private val newId: () -> String) {
    private var id: String? = null
    private var last = 0L
    private var lastWritten = 0L

    fun restore(id: String?, lastMs: Long?) {
        if (id == null || lastMs == null) return
        this.id = id
        last = lastMs
        lastWritten = lastMs
    }

    fun touch(nowMs: Long): SessionTouch {
        val current = id
        val isNew = current == null || nowMs - last > IDLE_MS || nowMs < last - IDLE_MS
        if (isNew) {
            id = newId()
            lastWritten = 0L
        }
        last = nowMs
        val persist = isNew || abs(nowMs - lastWritten) > WRITE_THROTTLE_MS
        if (persist) lastWritten = nowMs
        return SessionTouch(id!!, isNew, persist)
    }

    private companion object {
        const val IDLE_MS = 30L * 60 * 1000
        const val WRITE_THROTTLE_MS = 10_000L
    }
}
