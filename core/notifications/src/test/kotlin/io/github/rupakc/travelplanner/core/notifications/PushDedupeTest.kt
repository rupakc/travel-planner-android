package io.github.rupakc.travelplanner.core.notifications

import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNotificationsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushDedupeTest {
    private val hour = Duration.ofHours(1).toMillis()

    @Test
    fun `a repeat inside the window is dropped and expires after 24 hours`() {
        val w = RecentKeyWindow()
        assertTrue(w.firstSeen("a", 0))
        assertFalse(w.firstSeen("a", 23 * hour))
        assertTrue(w.firstSeen("a", 25 * hour))
    }

    @Test
    fun `only the newest 100 keys are kept`() {
        val w = RecentKeyWindow()
        repeat(150) { assertTrue(w.firstSeen("k$it", it.toLong())) }
        assertEquals(100, w.snapshot().size)
        assertTrue(w.firstSeen("k0", 200))
        assertFalse(w.firstSeen("k149", 201))
    }

    @Test
    fun `the handler shows a resent push once`() {
        val notifier = RecordingNotifier()
        val handler =
            PushAlertHandler(FakeAuthRepository(Session("t", TestUser)), FakeNotificationsRepository(), FakeSettingsRepository(), notifier)
        val data = mapOf("notification_id" to "5", "kind" to "price_drop", "title" to "T", "body" to "B", "dedupe_key" to "k1")
        runBlocking {
            assertTrue(handler.handle(data))
            assertFalse(handler.handle(data))
            assertTrue(handler.handle(data + ("dedupe_key" to "k2")))
            assertTrue(handler.handle(data - "dedupe_key"))
        }
        assertEquals(3, notifier.posts.size)
    }
}
