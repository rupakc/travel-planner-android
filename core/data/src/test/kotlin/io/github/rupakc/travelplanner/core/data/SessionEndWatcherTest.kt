package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.data.impl.SessionEndWatcher
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.network.AuthEvent
import io.github.rupakc.travelplanner.core.network.AuthEvents
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Review finding 1: a server-ended session wipes the local user data instead of only clearing the token. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionEndWatcherTest {
    private class Counting : SessionCleaner {
        var calls = 0

        override suspend fun clearUserData() {
            calls++
        }
    }

    @Test
    fun `SessionExpired wipes local data once per event, and start is idempotent`() =
        runTest(timeout = 30.seconds) {
            val events = AuthEvents()
            val cleaner = Counting()
            val watcher = SessionEndWatcher(events, cleaner, TestScope(UnconfinedTestDispatcher(testScheduler)).backgroundScope)
            watcher.start()
            watcher.start()
            events.emit(AuthEvent.SessionExpired)
            assertEquals(1, cleaner.calls)
            events.emit(AuthEvent.SessionExpired)
            assertEquals(2, cleaner.calls)
        }

    @Test
    fun `a failing cleaner does not stop the watcher`() =
        runTest(timeout = 30.seconds) {
            val events = AuthEvents()
            var calls = 0
            val cleaner =
                object : SessionCleaner {
                    override suspend fun clearUserData() {
                        calls++
                        error("disk full")
                    }
                }
            SessionEndWatcher(events, cleaner, TestScope(UnconfinedTestDispatcher(testScheduler)).backgroundScope).start()
            events.emit(AuthEvent.SessionExpired)
            events.emit(AuthEvent.SessionExpired)
            assertEquals(2, calls)
        }
}
