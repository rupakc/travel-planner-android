package io.github.rupakc.travelplanner.core.domain.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetrySessionTest {
    private val min = 60_000L

    private fun session(): TelemetrySession {
        var n = 0
        return TelemetrySession { "id${++n}" }
    }

    @Test fun `same id within 30 min`() {
        val s = session()
        s.restore("old", 1_000_000)
        val t = s.touch(1_000_000 + 30 * min)
        assertEquals("old", t.id)
        assertFalse(t.isNew)
    }

    @Test fun `new id after 30 min + 1 ms idle`() {
        val s = session()
        s.restore("old", 1_000_000)
        val t = s.touch(1_000_000 + 30 * min + 1)
        assertEquals("id1", t.id)
        assertTrue(t.isNew)
    }

    @Test fun `new id when the clock moves back more than 30 min`() {
        val s = session()
        s.restore("old", 100 * min)
        assertTrue(s.touch(100 * min - 30 * min - 1).isNew)
        val s2 = session()
        s2.restore("old", 100 * min)
        assertFalse(s2.touch(100 * min - 30 * min).isNew)
    }

    @Test fun `persist only when new or after 10 s`() {
        val s = session()
        val first = s.touch(1_000_000)
        assertTrue(first.isNew && first.persist)
        assertFalse(s.touch(1_005_000).persist)
        assertFalse(s.touch(1_010_000).persist)
        assertTrue(s.touch(1_010_001).persist)
        assertFalse(s.touch(1_015_000).persist)
    }
}
