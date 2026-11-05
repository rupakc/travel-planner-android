package io.github.rupakc.travelplanner.core.domain.alerts

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Port of web `relativeTime` (frontend/src/utils/fareWatch.js) as a value the UI
 * localises: "just now", "5 min ago", "2 h ago", "yesterday", "3 days ago".
 */
sealed interface TimeAgo {
    data object JustNow : TimeAgo

    data class Minutes(val count: Int) : TimeAgo

    data class Hours(val count: Int) : TimeAgo

    data object Yesterday : TimeAgo

    data class Days(val count: Int) : TimeAgo
}

/** Port of web `untilTime`: "soon", "in 20 min", "in 3 h", "in 2 days". */
sealed interface TimeUntil {
    data object Soon : TimeUntil

    data class Minutes(val count: Int) : TimeUntil

    data class Hours(val count: Int) : TimeUntil

    data class Days(val count: Int) : TimeUntil
}

/** Parses the backend's ISO-8601 timestamps (with offset; a bare one is read as UTC). Null when unreadable. */
fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (_: DateTimeParseException) {
        runCatching { Instant.parse(iso) }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(iso.replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC) }.getOrNull()
    }
}

/** How long ago [iso] was, or null when there is no time. */
fun timeAgo(iso: String?, now: Instant): TimeAgo? {
    val then = parseInstant(iso) ?: return null
    val secs = Duration.between(then, now).seconds
    if (secs < SECONDS_PER_MINUTE) return TimeAgo.JustNow
    val mins = (secs / SECONDS_PER_MINUTE).toInt()
    if (mins < MINUTES_PER_HOUR) return TimeAgo.Minutes(mins)
    val hours = mins / MINUTES_PER_HOUR
    if (hours < HOURS_PER_DAY) return TimeAgo.Hours(hours)
    val days = hours / HOURS_PER_DAY
    return if (days == 1) TimeAgo.Yesterday else TimeAgo.Days(days)
}

/** How long until [iso], or null when there is no time. Rounded like the web (`Math.round`). */
fun timeUntil(iso: String?, now: Instant): TimeUntil? {
    val then = parseInstant(iso) ?: return null
    val mins = jsRound(Duration.between(now, then).toMillis() / MILLIS_PER_MINUTE)
    if (mins <= 0) return TimeUntil.Soon
    if (mins < MINUTES_PER_HOUR) return TimeUntil.Minutes(mins.toInt())
    val hours = jsRound(mins / MINUTES_PER_HOUR.toDouble())
    if (hours < HOURS_PER_DAY) return TimeUntil.Hours(hours.toInt())
    return TimeUntil.Days(jsRound(hours / HOURS_PER_DAY.toDouble()).toInt())
}

/** JavaScript `Math.round` (halves round up, also for negatives). */
internal fun jsRound(value: Double): Long = kotlin.math.floor(value + HALF).toLong()

/**
 * Port of web `formatDateRange`: "1–8 Dec", "28 Nov – 5 Dec", "28 Dec 2026 – 4 Jan 2027", "1 Dec".
 * Month names follow [locale] (short form). Null when [departure] isn't an ISO date.
 */
fun formatDateRange(departure: String?, returnDate: String?, locale: Locale = Locale.UK): String? {
    val dep = parseIsoDate(departure) ?: return null
    val ret = parseIsoDate(returnDate)

    fun month(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')
    if (ret == null) return "${dep.dayOfMonth} ${month(dep)}"
    return when {
        dep.year != ret.year -> "${dep.dayOfMonth} ${month(dep)} ${dep.year} – ${ret.dayOfMonth} ${month(ret)} ${ret.year}"
        dep.month != ret.month -> "${dep.dayOfMonth} ${month(dep)} – ${ret.dayOfMonth} ${month(ret)}"
        else -> "${dep.dayOfMonth}–${ret.dayOfMonth} ${month(ret)}"
    }
}

/** Port of web `formatDay`: "Tue 3 Nov" (weekday and month in [locale]). */
fun formatDay(iso: String?, locale: Locale = Locale.UK): String? {
    val d = parseIsoDate(iso) ?: return null
    val weekday = d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')
    return "$weekday ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')}"
}

/** `YYYY-MM-DD` (a longer ISO string is cut to its date) → date, else null. */
fun parseIsoDate(iso: String?): LocalDate? = iso?.take(ISO_DATE_LENGTH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Whole dollars, US style: "$1,234" (web `formatUsd`). */
fun formatUsd(amount: Double): String = "$" + java.text.NumberFormat.getIntegerInstance(Locale.US).format(jsRound(amount))

internal fun Double.roundCents(): Double = (this * CENTS).roundToLong() / CENTS

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val MILLIS_PER_MINUTE = 60_000.0
private const val HALF = 0.5
private const val ISO_DATE_LENGTH = 10
private const val CENTS = 100.0
