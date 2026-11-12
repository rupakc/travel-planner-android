package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.config.ClientConfigDto

/**
 * Alerts 2.0 gates from `GET /config` `features` (ruling R3). Everything is off for an older backend or a missing config.
 *
 * - [v2Available]: the v2 routes may be called (`price_alerts_v2` or `alerts_api >= 2`).
 * - [livePricesAvailable]: a price source is configured (`live_prices`).
 * - [oneTapAvailable]: the one-tap watch button and suggestions show (both of the above). Stories, savings and the
 *   digest need only [v2Available] and work from stored data when live prices are off.
 */
data class AlertsFlags(
    val alertsApi: Int = 0,
    val priceAlertsV2: Boolean = false,
    val livePrices: Boolean = false,
) {
    val v2Available: Boolean get() = priceAlertsV2 || alertsApi >= V2_API

    val livePricesAvailable: Boolean get() = livePrices

    val oneTapAvailable: Boolean get() = v2Available && livePrices

    companion object {
        private const val V2_API = 2

        /** Everything off. */
        val OFF = AlertsFlags()

        /** Derives the gates from [config]; null means everything off. */
        operator fun invoke(config: ClientConfigDto?): AlertsFlags {
            val f = config?.features ?: return OFF
            return AlertsFlags(alertsApi = f.alertsApi ?: 0, priceAlertsV2 = f.priceAlertsV2, livePrices = f.livePrices)
        }
    }
}
