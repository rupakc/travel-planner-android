package io.github.rupakc.travelplanner.core.domain.telemetry

import kotlin.math.abs

/** Bounded in-memory event queue. Not thread-safe: the telemetry engine confines it to one thread. */
class TelemetryQueue(private val cap: Int = 500) {
    private val items = ArrayDeque<QueuedEvent>()

    val size: Int get() = items.size

    /** Appends [event]; the oldest events beyond the cap are dropped. */
    fun push(event: QueuedEvent) {
        items.addLast(event)
        while (items.size > cap) items.removeFirst()
    }

    /** Events not currently being sent, oldest first. */
    fun pending(inFlight: Set<QueuedEvent>): List<QueuedEvent> = items.filter { it !in inFlight }

    /** Up to [max] events of one session (the first non-in-flight event's), skipping in-flight ones. */
    fun takeBatch(max: Int, inFlight: Set<QueuedEvent>): List<QueuedEvent> {
        val candidates = pending(inFlight)
        val sessionId = candidates.firstOrNull()?.sessionId ?: return emptyList()
        return candidates.filter { it.sessionId == sessionId }.take(max)
    }

    /** Removes [batch] by identity. */
    fun remove(batch: Collection<QueuedEvent>) {
        if (batch.isEmpty()) return
        val gone = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<QueuedEvent, Boolean>())
        gone.addAll(batch)
        items.removeAll { it in gone }
    }

    /**
     * Puts [saved] events (from disk) in front of the queue, keeping only those within 7 days of
     * [nowMs] (either direction) and with token screen/event; the cap keeps the newest.
     */
    fun prepend(saved: List<QueuedEvent>, nowMs: Long) {
        val keep =
            saved.filter {
                abs(nowMs - it.ts) < MAX_AGE_MS && TOKEN.matches(it.screen) && TOKEN.matches(it.event)
            }
        items.addAll(0, keep)
        while (items.size > cap) items.removeFirst()
    }

    fun clear() = items.clear()

    private companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        // TODO(T4): use TelemetryProps.isToken
        val TOKEN = Regex("^[A-Za-z0-9_.:/-]{1,64}$")
    }
}
