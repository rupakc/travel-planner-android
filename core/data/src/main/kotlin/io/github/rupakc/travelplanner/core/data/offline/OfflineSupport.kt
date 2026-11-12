package io.github.rupakc.travelplanner.core.data.offline

import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.settings.Session

/** The key local per-user data is stored under: the server user id, else the username. */
internal fun Session.userKey(): String = userId ?: user.username

internal suspend fun SessionStore.currentUserKey(): String? = current()?.userKey()

/** Sends one queued request and returns the HTTP status; network failures are thrown as `IOException`. */
internal fun interface OutboxSender {
    suspend fun send(method: String, path: String, body: String?): Int
}

/** Schedules background flushes of the outbox. */
internal interface OutboxFlushScheduler {
    /** One flush as soon as the network is up, plus a 15 minute periodic retry while requests wait. */
    fun schedule()

    /** Stops the periodic retry (nothing is waiting any more). */
    fun cancelPeriodic()
}
