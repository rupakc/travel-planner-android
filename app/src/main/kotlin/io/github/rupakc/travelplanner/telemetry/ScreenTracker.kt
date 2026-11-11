package io.github.rupakc.travelplanner.telemetry

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryRoutes

/** The telemetry route template of [destination]: the first [TelemetryRoutes.table] match, else [TelemetryRoutes.OTHER]. */
fun routeTemplate(destination: NavDestination?): String {
    destination ?: return TelemetryRoutes.OTHER
    return TelemetryRoutes.table.firstOrNull { (routeClass, _) -> destination.hasRoute(routeClass) }?.second ?: TelemetryRoutes.OTHER
}

/** Reports a `screen_view` for every destination change, by template only (arguments are never read). */
class ScreenTracker(private val telemetry: Telemetry) : NavController.OnDestinationChangedListener {
    override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
        telemetry.screenView(routeTemplate(destination))
    }
}
