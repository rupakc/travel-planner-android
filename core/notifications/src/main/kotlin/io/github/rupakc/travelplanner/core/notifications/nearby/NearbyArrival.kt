package io.github.rupakc.travelplanner.core.notifications.nearby

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.domain.alerts.AlertPrefsRules
import io.github.rupakc.travelplanner.core.domain.notifications.NotificationText
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** What fills the `nearby_arrival` template: `{place}`, `{item}`, `{minutes}`. All three come from the existing Near me result. */
data class NearbyArrival(val place: String, val item: String, val minutes: Int)

/**
 * Where an arrival comes from. Near me answers are Google Maps grounded: their terms keep them to the requesting user, so
 * the background worker may not call `/nearby` on a schedule nor keep an answer (ruling R15). The default source therefore
 * answers null and the worker posts the generic invitation; a source may be bound once the backend offers a non-grounded
 * answer. Whatever it returns, the cell is never sent anywhere new.
 */
interface NearbyArrivalSource {
    suspend fun arrival(cell: LocationCell): NearbyArrival?
}

@Singleton
class NoNearbyArrivalSource
    @Inject
    constructor() : NearbyArrivalSource {
        override suspend fun arrival(cell: LocationCell): NearbyArrival? = null
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class NearbyArrivalModule {
    @Binds abstract fun source(impl: NoNearbyArrivalSource): NearbyArrivalSource
}

/** Pure text and quiet-hours rules of the nearby notifier. */
object NearbyArrivalText {
    /** The `nearby_arrival` text from the catalog [templates] (bundled English when the key is missing or blank). */
    fun body(templates: Map<String, String>, arrival: NearbyArrival): String {
        val template =
            templates[NotificationText.NEARBY_ARRIVAL]?.takeIf { it.isNotBlank() }
                ?: NotificationText.BUNDLED_EN.getValue(NotificationText.NEARBY_ARRIVAL)
        return NotificationText.render(template, mapOf("place" to arrival.place, "item" to arrival.item, "minutes" to arrival.minutes))
    }

    /** Whether [time] is inside the user's own enabled quiet window (start == end, or an invalid time, counts as off). */
    fun inUserQuietHours(quiet: QuietHours?, time: LocalTime): Boolean {
        if (quiet?.enabled != true) return false
        if (!AlertPrefsRules.isValidTime(quiet.start) || !AlertPrefsRules.isValidTime(quiet.end)) return false
        val start = LocalTime.parse(quiet.start)
        val end = LocalTime.parse(quiet.end)
        return when {
            start == end -> false
            start.isBefore(end) -> !time.isBefore(start) && time.isBefore(end)
            else -> !time.isBefore(start) || time.isBefore(end)
        }
    }
}
