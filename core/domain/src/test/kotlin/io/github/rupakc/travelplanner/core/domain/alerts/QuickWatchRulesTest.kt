package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefs
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefsUpdate
import io.github.rupakc.travelplanner.core.model.alerts.DailyCap
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchResponse
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickWatchRulesTest {
    private fun ok(existing: Boolean?, kind: String? = "hotel", id: Long? = 4) =
        AppResult.Success(
            QuickWatchResponse(kind, if (id == null) JsonObject(emptyMap()) else JsonObject(mapOf("id" to JsonPrimitive(id))), existing),
        )

    @Test fun `a 201 is Added and a 200 existing is Existing`() {
        assertEquals(QuickWatchOutcome.Added("hotel", 4), ok(false).toQuickWatchOutcome("x"))
        assertEquals(QuickWatchOutcome.Added("hotel", 4), ok(null).toQuickWatchOutcome("x"))
        assertEquals(QuickWatchOutcome.Existing("hotel", 4), ok(true).toQuickWatchOutcome("x"))
        assertEquals(QuickWatchOutcome.Added("trip", null), ok(false, kind = null, id = null).toQuickWatchOutcome("trip"))
    }

    @Test fun `server codes map to outcomes and keep the backend detail`() {
        fun fail(e: AppError) = AppResult.Failure(e).toQuickWatchOutcome("flight")
        assertEquals(QuickWatchOutcome.LivePricesOff, fail(AppError.Server(503, "off", "live_prices_off")))
        assertEquals(QuickWatchOutcome.Limit("Too many"), fail(AppError.Client(409, "Too many", "watch_limit")))
        assertEquals(QuickWatchOutcome.Invalid("bad"), fail(AppError.Client(422, "bad", "spec_invalid")))
        assertEquals(QuickWatchOutcome.Failed("slow down"), fail(AppError.RateLimited("slow down", 30)))
        assertEquals(QuickWatchOutcome.Failed(null), fail(AppError.Network()))
        assertTrue(QuickWatchOutcome.Added("flight", 1).watching)
        assertFalse(QuickWatchOutcome.LivePricesOff.watching)
    }

    @Test fun `telemetry action tokens are in the closed set`() {
        listOf(
            QuickWatchOutcome.Added("hotel", 1),
            QuickWatchOutcome.Existing("hotel", 1),
            QuickWatchOutcome.Limit(null),
            QuickWatchOutcome.LivePricesOff,
        ).forEach { assertTrue(AlertTelemetry.quickAction(it) in AlertTelemetry.QUICK_ACTIONS) }
    }

    @Test fun `time validation and clamps follow the contract`() {
        assertTrue(AlertPrefsRules.isValidTime("00:00"))
        assertTrue(AlertPrefsRules.isValidTime("23:59"))
        assertFalse(AlertPrefsRules.isValidTime("24:00"))
        assertFalse(AlertPrefsRules.isValidTime("7:00"))
        assertFalse(AlertPrefsRules.isValidTime(null))
        assertEquals(1, AlertPrefsRules.clampCap(0))
        assertEquals(10, AlertPrefsRules.clampCap(11))
        assertEquals(0, AlertPrefsRules.clampGroup(-5))
        assertEquals(120, AlertPrefsRules.clampGroup(500))
    }

    @Test fun `withCap sends all four caps and defaults missing ones to 3`() {
        val prefs = AlertPrefs(dailyCap = DailyCap(flight = 5, hotel = null, activity = 2, trip = 9))
        assertEquals(DailyCap(flight = 5, hotel = 10, activity = 2, trip = 9), AlertPrefsRules.withCap(prefs, "hotel", 99))
        assertEquals(DailyCap(3, 3, 3, 3), AlertPrefsRules.withCap(AlertPrefs(), "trip", 3))
    }

    @Test fun `quiet hours fall back to defaults for bad times`() {
        assertEquals(QuietHours(true, "22:00", "07:00"), AlertPrefsRules.quietHours(true, "bad", null))
        assertEquals(QuietHours(false, "21:30", "06:00"), AlertPrefsRules.quietHours(false, "21:30", "06:00"))
    }

    @Test fun `quiet hours without a timezone are flagged`() {
        assertTrue(AlertPrefsRules.quietHoursNeedTimezone(AlertPrefs(quietHours = QuietHours(true, "22:00", "07:00"))))
        assertFalse(AlertPrefsRules.quietHoursNeedTimezone(AlertPrefs(quietHours = QuietHours(true, "22:00", "07:00"), timezone = "Asia/Kolkata")))
        assertFalse(AlertPrefsRules.quietHoursNeedTimezone(AlertPrefs(quietHours = QuietHours(false))))
    }

    @Test fun `each update maps to one telemetry field from the closed set`() {
        val fields =
            listOf(
                AlertPrefsUpdate(digestWeekly = true),
                AlertPrefsUpdate(digestDay = 2),
                AlertPrefsUpdate(instant = "all"),
                AlertPrefsUpdate(quietHours = QuietHours(true, "22:00", "07:00")),
                AlertPrefsUpdate(dailyCap = DailyCap(1, 1, 1, 1)),
                AlertPrefsUpdate(groupWindowMin = 15),
            ).map { AlertPrefsRules.telemetryField(it) }
        assertEquals(listOf("digest", "digest", "instant", "quiet_hours", "cap", "group"), fields)
        assertTrue(fields.all { it in AlertTelemetry.PREF_FIELDS })
        assertNull(AlertPrefsRules.telemetryField(AlertPrefsUpdate()))
    }
}
