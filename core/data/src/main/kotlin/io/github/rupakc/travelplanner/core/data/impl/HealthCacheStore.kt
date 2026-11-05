package io.github.rupakc.travelplanner.core.data.impl

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/** The last `/health` capabilities seen for [server], and when (epoch millis). */
@Serializable
data class CachedHealth(
    val server: String,
    val health: Health,
    val savedAtMillis: Long,
)

/** Device-local copy of the last `/health` answer, so a fresh process knows the capabilities without a call. */
interface HealthCacheStore {
    /** Blocking read; call off the main thread. */
    fun read(): CachedHealth?

    fun write(value: CachedHealth)
}

@Singleton
class SharedPrefsHealthCacheStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : HealthCacheStore {
        private val prefs by lazy { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }

        override fun read(): CachedHealth? =
            prefs.getString(KEY, null)?.let { runCatching { TravelJson.decodeFromString(CachedHealth.serializer(), it) }.getOrNull() }

        override fun write(value: CachedHealth) = prefs.edit().putString(KEY, TravelJson.encodeToString(CachedHealth.serializer(), value)).apply()

        private companion object {
            const val FILE = "server_capabilities"
            const val KEY = "health"
        }
    }
