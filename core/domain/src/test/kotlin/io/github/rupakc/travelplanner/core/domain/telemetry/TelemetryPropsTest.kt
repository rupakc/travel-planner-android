package io.github.rupakc.travelplanner.core.domain.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryPropsTest {
    private val max = 1L shl 53

    @Test fun `free text with a space is dropped`() = assertFalse("q" in TelemetryProps.clean(mapOf("q" to "hello world")))

    @Test fun `email is dropped`() = assertFalse("q" in TelemetryProps.clean(mapOf("q" to "a@b.c")))

    @Test fun `65 chars dropped, 64 kept`() {
        val r = TelemetryProps.clean(mapOf("a" to "x".repeat(64), "b" to "x".repeat(65)))
        assertTrue("a" in r)
        assertFalse("b" in r)
    }

    @Test fun `2^53 kept, 2^53+1 dropped`() {
        val r = TelemetryProps.clean(mapOf("a" to max, "b" to max + 1, "c" to -max, "d" to -max - 1))
        assertEquals(setOf("a", "c"), r.keys)
    }

    @Test fun `NaN and Infinity dropped`() {
        val r =
            TelemetryProps.clean(
                mapOf("a" to Double.NaN, "b" to Double.POSITIVE_INFINITY, "c" to Float.NaN, "d" to 1.5, "e" to 2.5f),
            )
        assertEquals(setOf("d", "e"), r.keys)
        assertEquals(2.5, r["e"])
    }

    @Test fun `Int is widened to Long`() {
        val v = TelemetryProps.clean(mapOf("n" to 5))["n"]
        assertEquals(5L, v)
        assertTrue(v is Long)
    }

    @Test fun `booleans kept, null and other types dropped`() {
        val r = TelemetryProps.clean(mapOf("t" to true, "n" to null, "l" to listOf(1)))
        assertEquals(mapOf<String, Any>("t" to true), r)
    }

    @Test fun `cleanVersion replaces bad chars`() {
        assertEquals("0.1.5__debug_", TelemetryProps.cleanVersion("0.1.5 (debug)"))
        assertEquals("0.0.0", TelemetryProps.cleanVersion("  "))
        assertEquals(64, TelemetryProps.cleanVersion("1".repeat(100)).length)
    }
}
