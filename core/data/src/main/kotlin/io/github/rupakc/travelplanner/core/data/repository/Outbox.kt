package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import kotlinx.coroutines.flow.Flow

/** A write to replay later. [key] is unique per user: queueing the same key again replaces the earlier request. */
data class OutboxRequest(
    /** POST, PUT, PATCH or DELETE. */
    val method: String,
    /** Relative to the server root, e.g. `api/trips/12/expenses`. */
    val path: String,
    /** JSON body, null for DELETE. */
    val body: String?,
    val key: String,
)

/** What one flush did: [sent] (2xx only), [failed] (kept for a later try), [dropped] (permanent 4xx, 409 included). */
data class FlushResult(val sent: Int = 0, val failed: Int = 0, val dropped: Int = 0)

/**
 * Per-user queue of writes made offline (expenses, journal entries, snapshot uploads), shared by Tasks 11-13.
 * Everything is purged on sign-out. Resending is safe because creates carry a `client_id` (a repeat answers 200).
 */
interface Outbox {
    /** Queues [request] for the signed-in user and schedules a flush; an earlier request with the same key is replaced. */
    suspend fun enqueue(request: OutboxRequest): AppResult<Unit>

    /** Keys still waiting for the signed-in user (drives "waiting to sync" markers); empty when signed out. */
    fun pendingKeys(): Flow<Set<String>>

    /** Emits the number of queued writes a flush dropped because the server rejected them for good (the shell shows a notice). */
    val dropNotices: Flow<Int>

    /**
     * Sends the waiting requests of [userId] (default: the signed-in user) in order. Single-flight per user: a
     * concurrent call waits and then finds nothing left, so each request is sent once. Another user's requests
     * are never sent with this session.
     */
    suspend fun flush(userId: String? = null): FlushResult
}
