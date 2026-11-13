package io.github.rupakc.travelplanner.core.notifications.nearby

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.domain.alerts.AlertPrefsRules
import io.github.rupakc.travelplanner.core.domain.notifications.NotificationText
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * What fills the nearby notification. [item] is the top pick, [reason] its category (or null). [minutes] is set only when a
 * walking time could really be computed from coordinates the item carries: the v1 answer carries none, so it is null and the
 * text never shows a time (the catalog `nearby_arrival` template is used only when [minutes] is known).
 */
data class NearbyArrival(val place: String, val item: String, val minutes: Int? = null, val reason: String? = null)

/**
 * Where an arrival comes from. The bound source calls only the non-grounded v1 `/api/nearby` (Google Maps grounded v2 may not
 * be called from a schedule nor kept, ruling R15); null means "post the generic invitation".
 */
interface NearbyArrivalSource {
    suspend fun arrival(cell: LocationCell): NearbyArrival?
}

/**
 * One v1 `/api/nearby` call per arrival (the server personalizes with the user's saved interests and taste profile), best
 * effort: a signed-out user, any error (4xx/5xx incl. 429 and 503 `ai_limit`), a timeout, or an empty answer gives null.
 * The coordinates go only in that request and are never logged.
 */
@Singleton
class ApiNearbyArrivalSource
    @Inject
    constructor(
        private val nearby: NearbyRepository,
        private val auth: AuthRepository,
    ) : NearbyArrivalSource {
        /** Overridable in tests. */
        internal var timeoutMs: Long = TIMEOUT_MS

        override suspend fun arrival(cell: LocationCell): NearbyArrival? {
            if (auth.session.first() == null) return null
            val result =
                try {
                    withTimeout(timeoutMs) { nearby.nearbyItems(cell.lat, cell.lng, 1) }
                } catch (_: TimeoutCancellationException) {
                    return null
                }
            val answer = (result as? AppResult.Success)?.value ?: return null
            val top = answer.items.firstOrNull { !it.name.isNullOrBlank() } ?: return null
            return NearbyArrival(
                place = answer.place?.name.orEmpty(),
                item = top.name.orEmpty().trim(),
                minutes = null,
                reason = top.category?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        companion object {
            const val TIMEOUT_MS = 10_000L
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class NearbyArrivalModule {
    @Binds abstract fun source(impl: ApiNearbyArrivalSource): NearbyArrivalSource
}

/** Pure text and quiet-hours rules of the nearby notifier. */
object NearbyArrivalText {
    /** The `nearby_arrival` text from the catalog [templates] (bundled English when the key is missing or blank). */
    fun body(templates: Map<String, String>, arrival: NearbyArrival): String {
        val minutes = requireNotNull(arrival.minutes) { "the catalog template needs a computed walking time" }
        val template =
            templates[NotificationText.NEARBY_ARRIVAL]?.takeIf { it.isNotBlank() }
                ?: NotificationText.BUNDLED_EN.getValue(NotificationText.NEARBY_ARRIVAL)
        return NotificationText.render(template, mapOf("place" to arrival.place, "item" to arrival.item, "minutes" to minutes))
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
