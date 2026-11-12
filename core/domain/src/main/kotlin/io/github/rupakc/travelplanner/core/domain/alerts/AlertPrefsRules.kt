package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefs
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefsUpdate
import io.github.rupakc.travelplanner.core.model.alerts.DailyCap
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours

/** Pure rules of the alert preferences form (contract: caps 1..10, group window 0..120, `HH:MM`, ISO weekday). */
object AlertPrefsRules {
    const val DAY_MIN = 1
    const val DAY_MAX = 7
    const val CAP_MIN = 1
    const val CAP_MAX = 10
    const val GROUP_MIN = 0
    const val GROUP_MAX = 120
    const val GROUP_STEP = 15
    const val DEFAULT_CAP = 3
    const val DEFAULT_START = "22:00"
    const val DEFAULT_END = "07:00"
    const val INSTANT_ALL = "all"
    const val INSTANT_TARGETS_ONLY = "targets_only"
    private const val HOURS = 24
    private const val MINUTES = 60

    /** The four caps in contract order. */
    val CAP_KINDS = listOf("flight", "hotel", "activity", "trip")

    /** True for a `HH:MM` 24-hour time. */
    fun isValidTime(value: String?): Boolean {
        val m = value?.let { Regex("^(\\d{2}):(\\d{2})$").matchEntire(it) } ?: return false
        return m.groupValues[1].toInt() < HOURS && m.groupValues[2].toInt() < MINUTES
    }

    fun clampCap(value: Int): Int = value.coerceIn(CAP_MIN, CAP_MAX)

    fun clampGroup(value: Int): Int = value.coerceIn(GROUP_MIN, GROUP_MAX)

    /** The cap of [kind] (default 3 when the server sent none). */
    fun cap(prefs: AlertPrefs, kind: String): Int =
        when (kind) {
            "flight" -> prefs.dailyCap?.flight
            "hotel" -> prefs.dailyCap?.hotel
            "activity" -> prefs.dailyCap?.activity
            else -> prefs.dailyCap?.trip
        } ?: DEFAULT_CAP

    /** All four caps with [kind] set to [value] (clamped), so the PUT always sends a complete `daily_cap`. */
    fun withCap(prefs: AlertPrefs, kind: String, value: Int): DailyCap {
        val v = clampCap(value)

        fun of(k: String) = if (k == kind) v else cap(prefs, k)
        return DailyCap(flight = of("flight"), hotel = of("hotel"), activity = of("activity"), trip = of("trip"))
    }

    /** `quiet_hours` update; times are kept as sent when valid, else the defaults. Quiet hours with `start == end` count as off server side. */
    fun quietHours(enabled: Boolean, start: String?, end: String?): QuietHours =
        QuietHours(enabled = enabled, start = start.takeIf(::isValidTime) ?: DEFAULT_START, end = end.takeIf(::isValidTime) ?: DEFAULT_END)

    /** The telemetry `field` token of [update] (the first changed field), or null when it changes nothing. */
    fun telemetryField(update: AlertPrefsUpdate): String? =
        when {
            update.digestWeekly != null || update.digestPush != null || update.digestDay != null -> "digest"
            update.quietHours != null -> "quiet_hours"
            update.instant != null -> "instant"
            update.dailyCap != null -> "cap"
            update.groupWindowMin != null -> "group"
            else -> null
        }

    /** Quiet hours that the server will actually apply need a timezone; false explains the "no timezone" hint. */
    fun quietHoursNeedTimezone(prefs: AlertPrefs): Boolean = prefs.quietHours?.enabled == true && prefs.timezone.isNullOrBlank()
}
