package io.github.rupakc.travelplanner.core.model.telemetry

import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryWireTest {
    @Test
    fun `client block encodes snake case and android platform`() {
        val json = TravelJson.encodeToString(TelemetryClientDto("a", "s", appVersion = "0.1.5", locale = "zh-CN"))
        assertTrue(json, json.contains("\"anon_id\":\"a\""))
        assertTrue(json, json.contains("\"session_id\":\"s\""))
        assertTrue(json, json.contains("\"platform\":\"android\""))
        assertTrue(json, json.contains("\"app_version\":\"0.1.5\""))
    }

    @Test
    fun `config decodes the live shape`() {
        val cfg =
            TravelJson.decodeFromString<ClientConfigDto>(
                """{"public_base_url":"http://x","telemetry":{"enabled":true,"flush_interval_s":15,"max_batch":50},"features":{"telemetry":true}}""",
            )
        assertEquals(true, cfg.telemetry?.enabled)
        assertEquals(15, cfg.telemetry?.flushIntervalS)
        assertEquals(50, cfg.telemetry?.maxBatch)
    }

    @Test
    fun `missing telemetry block decodes as null`() {
        val cfg = TravelJson.decodeFromString<ClientConfigDto>("""{"public_base_url":"http://x"}""")
        assertNull(cfg.telemetry)
    }
}
