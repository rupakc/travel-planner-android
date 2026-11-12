package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto
import io.github.rupakc.travelplanner.core.model.config.FeaturesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertsFlagsTest {
    private fun flags(f: FeaturesDto?) = AlertsFlags(ClientConfigDto(features = f))

    @Test
    fun `missing config or features is everything off`() {
        assertEquals(AlertsFlags.OFF, AlertsFlags(null))
        assertEquals(AlertsFlags.OFF, flags(null))
        assertFalse(flags(FeaturesDto()).v2Available)
    }

    @Test
    fun `v2 needs price_alerts_v2 or alerts_api 2`() {
        assertTrue(flags(FeaturesDto(priceAlertsV2 = true)).v2Available)
        assertTrue(flags(FeaturesDto(alertsApi = 2)).v2Available)
        assertFalse(flags(FeaturesDto(alertsApi = 1)).v2Available)
        assertFalse(flags(FeaturesDto(livePrices = true)).v2Available)
    }

    @Test
    fun `one tap needs v2 and live prices, stories do not`() {
        val noLive = flags(FeaturesDto(alertsApi = 2))
        assertTrue(noLive.v2Available)
        assertFalse(noLive.livePricesAvailable)
        assertFalse(noLive.oneTapAvailable)
        val live = flags(FeaturesDto(alertsApi = 2, livePrices = true))
        assertTrue(live.livePricesAvailable && live.oneTapAvailable)
        assertFalse(flags(FeaturesDto(livePrices = true)).oneTapAvailable)
    }

    @Test
    fun `journey flags carry the alerts gates`() {
        val j = JourneyFlags(ClientConfigDto(features = FeaturesDto(alertsApi = 2, livePrices = true)))
        assertTrue(j.alerts.oneTapAvailable)
        assertEquals(AlertsFlags.OFF, JourneyFlags.OFF.alerts)
    }
}
