package io.github.rupakc.travelplanner.feature.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.common.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Result of asking for the device's position. */
sealed interface LocationFix {
    data class Found(val lat: Double, val lng: Double) : LocationFix

    data object NoPermission : LocationFix

    /** Location services are switched off in system settings. */
    data object Off : LocationFix

    /** On and permitted, but no fix came back (indoors, no cached position). */
    data object Unavailable : LocationFix
}

/** Foreground location for Near me. */
interface LocationClient {
    fun hasPermission(): Boolean

    suspend fun current(): LocationFix
}

@Singleton
class FusedLocationClient
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : LocationClient {
        override fun hasPermission(): Boolean =
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

        /** A fresh balanced-power fix, falling back to the last known one. */
        @SuppressLint("MissingPermission") // hasPermission() is checked first
        @Suppress("TooGenericExceptionCaught") // Play services can throw anything on odd devices
        override suspend fun current(): LocationFix {
            if (!hasPermission()) return LocationFix.NoPermission
            val manager = context.getSystemService(LocationManager::class.java)
            if (manager != null && !LocationManagerCompat.isLocationEnabled(manager)) return LocationFix.Off
            val client = LocationServices.getFusedLocationProviderClient(context)
            return try {
                val tokens = CancellationTokenSource()
                val fresh =
                    withTimeoutOrNull(FIX_TIMEOUT_MILLIS) {
                        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokens.token).await()
                    } ?: run {
                        tokens.cancel()
                        null
                    }
                val location = fresh ?: client.lastLocation.await()
                location?.let { LocationFix.Found(it.latitude, it.longitude) } ?: LocationFix.Unavailable
            } catch (e: Exception) {
                AppLogger.w(TAG, "No location fix", e)
                LocationFix.Unavailable
            }
        }

        private companion object {
            const val TAG = "NearMe"
            const val FIX_TIMEOUT_MILLIS = 15_000L
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {
    @Binds abstract fun locationClient(impl: FusedLocationClient): LocationClient
}
