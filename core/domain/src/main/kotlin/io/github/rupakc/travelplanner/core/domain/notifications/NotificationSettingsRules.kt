package io.github.rupakc.travelplanner.core.domain.notifications

/** Client-side bounds of the notification settings (the server answers `notification_settings_invalid` outside them). */
object NotificationSettingsRules {
    const val MIN_CAP = 1
    const val MAX_CAP = 10
    const val DEFAULT_CAP = 3
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE = 59

    /** [cap] limited to 1..10. */
    fun clampCap(cap: Int): Int = cap.coerceIn(MIN_CAP, MAX_CAP)

    /** `HH:MM` for a time of day. */
    fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour.coerceIn(0, MAX_HOUR), minute.coerceIn(0, MAX_MINUTE))

    /** Hour and minute of a valid `HH:MM`, or null. */
    fun parseTime(value: String?): Pair<Int, Int>? {
        val m = TIME.matchEntire(value.orEmpty()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h <= MAX_HOUR && min <= MAX_MINUTE) h to min else null
    }

    private val TIME = Regex("(\\d{2}):(\\d{2})")
}
