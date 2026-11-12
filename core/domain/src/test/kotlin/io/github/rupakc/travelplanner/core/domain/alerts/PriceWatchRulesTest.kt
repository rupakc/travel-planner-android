package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors frontend/src/utils/priceWatch.test.js and components/alerts/alertKinds.test.js. */
class PriceWatchRulesTest {
    private fun json(text: String) = TravelJson.parseToJsonElement(text) as JsonObject

    private val fare = FareWatch(id = 7, departureDate = "2026-12-01")
    private val hotel = PriceWatch(id = 7, kind = "hotel", expiresOn = "2026-11-20")
    private val trip = PriceWatch(id = 8, kind = "trip", expiresOn = "2026-12-20")

    @Test
    fun `tabs counts and filters`() {
        val list = mergeWatches(listOf(fare), listOf(hotel, trip))
        assertEquals(listOf("price-watch-7", "watch-7", "price-watch-8"), list.map { it.key })
        assertEquals(
            mapOf(AlertTab.ALL to 3, AlertTab.FLIGHTS to 1, AlertTab.HOTELS to 1, AlertTab.ACTIVITIES to 0, AlertTab.TRIPS to 1),
            countByTab(list),
        )
        assertEquals(listOf("hotel"), list.filter { inTab(it, AlertTab.HOTELS) }.map { it.kind })
        assertEquals(AlertTab.TRIPS, AlertTab.forKind("trip"))
        assertNull(AlertTab.forKind(null))
    }

    @Test
    fun unavailable() {
        assertTrue(isUnavailable(hotel.copy(lastObservation = json("""{"status":"unavailable"}"""))))
        assertFalse(isUnavailable(hotel.copy(lastObservation = json("""{"status":"ok"}"""))))
        assertFalse(isUnavailable(hotel))
    }

    @Test
    fun `check outcomes`() {
        val n = AppNotification(id = 1, title = "Hotel Lumen dropped to €90")
        assertEquals(CheckOutcome(CheckType.ALERT, Tone.GOOD, notificationTitle = n.title), checkOutcome(hotel.copy(checkStatus = "checked", notification = n)))
        assertEquals(CheckOutcome(CheckType.CHECKED_PRICE, Tone.NEUTRAL, price = 9.9), checkOutcome(hotel.copy(checkStatus = "checked", lastPrice = 9.9)))
        assertEquals(CheckType.CHECKED_NOTHING, checkOutcome(hotel.copy(checkStatus = "checked")).type)
        assertEquals(Tone.WARN, checkOutcome(hotel.copy(checkStatus = "unavailable")).tone)
        assertEquals(CheckType.FAILED, checkOutcome(fare.copy(checkStatus = "failed")).type)
        assertEquals(CheckType.ERRORED, checkOutcome(fare.copy(checkStatus = "errored")).type)
        assertEquals(CheckType.EXPIRED, checkOutcome(fare.copy(checkStatus = "expired")).type)
        assertEquals(CheckType.POSTPONED, checkOutcome(fare.copy(checkStatus = "postponed")).type)
        assertEquals(CheckType.DELETED, checkOutcome(fare.copy(checkStatus = "deleted")).type)
        assertEquals(CheckType.NOT_WATCHED, checkOutcome(fare.copy(checkStatus = "skipped")).type)
    }

    @Test
    fun `price change keeps cents`() {
        assertEquals(PriceChange(-1.73, Direction.DOWN), priceWatchChange(PriceWatch(id = 1, baselinePrice = 11.63, lastPrice = 9.9)))
        assertEquals(Direction.UP, priceWatchChange(PriceWatch(id = 1, baselinePrice = 24.0, lastPrice = 24.4))?.direction)
        assertEquals(Direction.SAME, priceWatchChange(PriceWatch(id = 1, baselinePrice = 398.0, lastPrice = 398.0))?.direction)
        assertNull(priceWatchChange(PriceWatch(id = 1, lastPrice = 3.0)))
    }

    @Test
    fun `currency and target`() {
        assertEquals("€", currencySymbol("EUR"))
        assertEquals("$", currencySymbol("USD"))
        assertEquals("$", currencySymbol(null))
        assertEquals("XYZ1", currencySymbol("XYZ1"))
        assertEquals(TargetCheck(1250.0, null), checkPriceTarget("€ 1,250", Locale.US))
        assertEquals(TargetError.NOT_A_PRICE, checkPriceTarget("", Locale.US).error)
        assertEquals(TargetError.NOT_A_PRICE, checkPriceTarget("-3", Locale.US).error)
    }

    @Test
    fun `notification links`() {
        val linked = AppNotification(id = 1, data = json("""{"watch_id":7,"links":{"alerts":"/alerts?kind=hotel&watch=7"}}"""))
        assertEquals(AlertLink("hotel", 7), notificationLink(linked))
        assertEquals(AlertLink(null, 9), notificationLink(AppNotification(id = 2, data = json("""{"watch_id":9}"""))))
        assertEquals(AlertLink(null, null), notificationLink(AppNotification(id = 3)))
        assertEquals(AlertLink(null, 7), parseAlertsPath("/alerts?watch=7"))
        assertNull(parseAlertsPath("/plans"))
    }

    @Test
    fun `alert kinds read per watch kind`() {
        assertEquals(AlertAction.SEE_FARE, alertKindFor(AppNotification(id = 1, kind = "fare_drop")).action)
        assertEquals(AlertAction.SEE_DATES, alertKindFor(AppNotification(id = 1, kind = "fare_cheaper_date")).action)
        assertEquals(AlertIcon.ENDED, alertKindFor(AppNotification(id = 1, kind = "mystery")).icon)
        assertEquals(AlertIcon.SPARKLE, alertKindFor(AppNotification(id = 1, kind = "price_digest")).icon)
        val hotelEnded = AppNotification(id = 1, kind = "watch_expired", data = json("""{"watch_kind":"hotel"}"""))
        assertEquals(AlertAction.SEE_HOTEL, alertKindFor(hotelEnded).action)
        val activityEnded = AppNotification(id = 1, kind = "watch_expired", data = json("""{"watch_kind":"activity"}"""))
        assertEquals(AlertIcon.TICKET, alertKindFor(activityEnded).icon)
        val tripPaused = AppNotification(id = 1, kind = "watch_error", data = json("""{"watch_kind":"trip"}"""))
        assertEquals(AlertAction.CHECK_TRIP, alertKindFor(tripPaused).action)
        assertEquals("Lisbon (−⁠$70)", keepDeltaTogether("Lisbon (−$70)"))
    }

    @Test
    fun `edge cases from the web suite`() {
        val now = java.time.Instant.parse("2026-11-04T12:00:00Z")
        // untilTime rounds like Math.round: 89 min → 1 h, 90 min → 2 h, 36 h → 2 days.
        assertEquals(TimeUntil.Hours(1), timeUntil("2026-11-04T13:29:00Z", now))
        assertEquals(TimeUntil.Hours(2), timeUntil("2026-11-04T13:30:00Z", now))
        assertEquals(TimeUntil.Days(2), timeUntil("2026-11-06T00:00:00Z", now))
        assertEquals(TargetError.NOT_A_PRICE, checkPriceTarget("1.2.3", Locale.US).error)
        assertEquals(TargetCheck(9.5, null), checkPriceTarget("9.5", Locale.US))
        assertEquals("28 Dec 2026 – 4 Jan 2027", formatDateRange("2026-12-28", "2027-01-04"))
        assertNull(formatDateRange("not a date", null))
        assertEquals("100%", percent(1.0))
    }

    @Test
    fun `price targets are read in the user's language`() {
        val es = Locale.forLanguageTag("es-ES")
        assertEquals(1200.0, checkPriceTarget("1.200", es).value)
        assertEquals(450.5, checkPriceTarget("450,50", es).value)
        assertEquals(1500.5, checkPriceTarget("1.500,50 €", es).value)
        assertEquals(1500.5, checkPriceTarget("1,500.50", Locale.US).value)
    }
}
