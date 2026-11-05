package io.github.rupakc.travelplanner.core.data.repository

/**
 * Extra per-user device state owned by modules that core:data cannot see (e.g. the alert poller's
 * baseline in core:notifications). Contribute with `@Binds @IntoSet`; [SessionCleaner] runs every
 * one on logout, on login as a different user and on a server change.
 */
fun interface UserStateCleaner {
    suspend fun clear()
}
