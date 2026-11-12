package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.data.alerts.QuickWatcher
import io.github.rupakc.travelplanner.core.domain.alerts.AlertsFlags
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags

/**
 * A real [QuickWatcher] over fakes. [flags] defaults to Alerts 2.0 off (callers keep their sheets); use
 * [FakeQuickWatch.on] for one-tap on. The fakes are exposed for scripting and assertions.
 */
class FakeQuickWatch(
    val flags: FakeJourneyFlagsRepository = FakeJourneyFlagsRepository(JourneyFlags.OFF),
    val alerts: FakeAlertsV2Repository = FakeAlertsV2Repository(),
    val fare: FakeFareWatchesRepository = FakeFareWatchesRepository(),
    val price: FakePriceWatchesRepository = FakePriceWatchesRepository(),
    val telemetry: FakeTelemetry = FakeTelemetry(),
) {
    val watcher = QuickWatcher(flags, alerts, fare, price, telemetry)

    companion object {
        /** One-tap available (`alerts_api` 2 and `live_prices`). */
        fun on() = FakeQuickWatch(FakeJourneyFlagsRepository(JourneyFlags(alerts = AlertsFlags(alertsApi = 2, priceAlertsV2 = true, livePrices = true))))
    }
}
