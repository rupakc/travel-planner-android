package io.github.rupakc.travelplanner.feature.alerts

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeNotificationsRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The shell bell asks for the unread count at most once a minute, plus after the alerts list was opened (performance M7). */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationBellViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock =
        object : AppClock {
            var instant: Instant = Instant.parse("2026-11-05T10:00:00Z")

            override fun now(): Instant = instant

            override fun zone(): ZoneId = ZoneId.of("UTC")
        }

    private class Counting(private val inner: FakeNotificationsRepository = FakeNotificationsRepository()) : NotificationsRepository by inner {
        var calls = 0

        override suspend fun unreadCount(): AppResult<Int> {
            calls++
            return AppResult.Success(calls)
        }
    }

    @Test
    fun `tab returns within a minute reuse the count, opening the list or a minute passing refreshes it`() =
        runTest {
            val repo = Counting()
            val vm = NotificationBellViewModel(repo, clock)
            vm.refresh()
            advanceUntilIdle()
            vm.refresh()
            clock.instant = clock.instant.plusSeconds(30)
            vm.refresh()
            advanceUntilIdle()
            assertEquals(1, repo.calls)
            assertEquals(1, vm.unread.value)
            vm.invalidate()
            vm.refresh()
            advanceUntilIdle()
            assertEquals(2, repo.calls)
            clock.instant = clock.instant.plus(NotificationBellViewModel.REFRESH_TTL).plus(Duration.ofSeconds(1))
            vm.refresh()
            advanceUntilIdle()
            assertEquals(3, repo.calls)
            assertEquals(3, vm.unread.value)
        }
}
