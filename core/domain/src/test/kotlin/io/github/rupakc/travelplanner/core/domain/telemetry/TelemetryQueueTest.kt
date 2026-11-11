package io.github.rupakc.travelplanner.core.domain.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TelemetryQueueTest {
    private fun ev(ts: Long = 0, sid: String = "s", name: String = "ui_tap", screen: String = "home") =
        QueuedEvent(name, ts, emptyMap(), screen, sid)

    @Test fun `cap 500 drops the oldest`() {
        val q = TelemetryQueue()
        val all = (0 until 501).map { ev(it.toLong()) }
        all.forEach(q::push)
        assertEquals(500, q.size)
        assertSame(all[1], q.pending(emptySet()).first())
    }

    @Test fun `takeBatch takes one session and skips in-flight`() {
        val q = TelemetryQueue()
        val a1 = ev(1, "a")
        val b1 = ev(2, "b")
        val a2 = ev(3, "a")
        val a3 = ev(4, "a")
        listOf(a1, b1, a2, a3).forEach(q::push)
        assertEquals(listOf(a1, a2, a3), q.takeBatch(10, emptySet()))
        assertEquals(listOf(a1, a2), q.takeBatch(2, emptySet()))
        assertEquals(listOf(b1), q.takeBatch(10, setOf(a1, a2, a3)))
        assertEquals(emptyList<QueuedEvent>(), q.takeBatch(10, setOf(a1, a2, a3, b1)))
    }

    @Test fun `two equal-looking events are both kept`() {
        val q = TelemetryQueue()
        val x = ev(1)
        val y = ev(1)
        q.push(x)
        q.push(y)
        assertEquals(2, q.size)
        q.remove(listOf(x))
        assertEquals(1, q.size)
        assertSame(y, q.pending(emptySet()).single())
    }

    @Test fun `prepend drops events older than 7 days and keeps the newest 500`() {
        val now = 10L * 24 * 3600 * 1000
        val q = TelemetryQueue()
        val old = ev(now - 7L * 24 * 3600 * 1000)
        val bad = ev(now, screen = "no spaces")
        val saved = (0 until 501).map { ev(now - 500 + it) }
        q.prepend(listOf(old, bad) + saved, now)
        assertEquals(500, q.size)
        assertEquals(now - 499, q.pending(emptySet()).first().ts)
        q.clear()
        assertEquals(0, q.size)
    }
}
