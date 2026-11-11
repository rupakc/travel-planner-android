package io.github.rupakc.travelplanner.telemetry

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.rupakc.travelplanner.core.data.telemetry.TelemetryLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Process foreground/background for telemetry (added to `ProcessLifecycleOwner` on the main thread).
 *
 * Every call runs on [scope] through one serial dispatcher: the telemetry graph ([lifecycle] is resolved lazily) is
 * never built on the main thread, and start → foreground → background keep their order. `start()` is idempotent,
 * so a process started for a worker or a push starts telemetry only when it first comes to the front.
 */
class TelemetryProcessObserver(private val scope: CoroutineScope, private val lifecycle: () -> TelemetryLifecycle) :
    DefaultLifecycleObserver {
    private val serial = Dispatchers.Default.limitedParallelism(1)

    /** Starts telemetry now (a process started in the foreground). */
    fun start() {
        scope.launch(serial) { lifecycle().start() }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch(serial) {
            val telemetry = lifecycle()
            telemetry.start()
            telemetry.onForeground()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.launch(serial) { lifecycle().onBackground() }
    }
}
