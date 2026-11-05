package io.github.rupakc.travelplanner.feature.results.sections.info

import android.text.format.DateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** "May 3" in the user's locale for an ISO `YYYY-MM-DD` date (web `toLocaleDateString(…, {month:'short', day:'numeric'})`); the raw text when unparseable. */
internal fun shortDate(iso: String?, locale: Locale): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val date = LocalDate.parse(iso.take(ISO_DATE_LENGTH))
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale).format(date)
    } catch (_: DateTimeParseException) {
        iso
    }
}

private const val ISO_DATE_LENGTH = 10
