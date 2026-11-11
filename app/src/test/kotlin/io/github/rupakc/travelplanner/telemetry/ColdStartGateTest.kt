package io.github.rupakc.travelplanner.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColdStartGateTest {
    @Test
    fun `a foreground first activity within a minute is reported`() {
        assertTrue(ColdStartGate.shouldReport(startedInForeground = true, firstActivity = true, restored = false, ms = 850))
        assertTrue(ColdStartGate.shouldReport(startedInForeground = true, firstActivity = true, restored = false, ms = 60_000))
    }

    @Test
    fun `restored activity is not reported`() {
        assertFalse(ColdStartGate.shouldReport(startedInForeground = true, firstActivity = true, restored = true, ms = 850))
    }

    @Test
    fun `worker-started process is not reported`() {
        assertFalse(ColdStartGate.shouldReport(startedInForeground = false, firstActivity = true, restored = false, ms = 850))
    }

    @Test
    fun `a second activity in the same process is not reported`() {
        assertFalse(ColdStartGate.shouldReport(startedInForeground = true, firstActivity = false, restored = false, ms = 850))
    }

    @Test
    fun `61 s is not reported`() {
        assertFalse(ColdStartGate.shouldReport(startedInForeground = true, firstActivity = true, restored = false, ms = 61_000))
    }

    @Test
    fun `a negative duration is not reported`() {
        assertFalse(ColdStartGate.shouldReport(startedInForeground = true, firstActivity = true, restored = false, ms = -1))
    }
}
