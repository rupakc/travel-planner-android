package io.github.rupakc.travelplanner.telemetry

import android.app.ActivityManager
import java.util.concurrent.atomic.AtomicBoolean

/** Whether a launch counts as a reportable cold start (web `perf cold_start` semantics, spec §8). */
object ColdStartGate {
    private const val MAX_MS = 60_000L

    /** True only for a process started in the foreground, its first activity, not restored, and 0..60 s. */
    fun shouldReport(startedInForeground: Boolean, firstActivity: Boolean, restored: Boolean, ms: Long): Boolean =
        startedInForeground && firstActivity && !restored && ms in 0..MAX_MS
}

/** Process-wide cold-start facts, captured in `Application.onCreate` and read by the first activity. */
object ColdStart {
    @Volatile var startedInForeground: Boolean = false
        private set

    private val activityCreated = AtomicBoolean(false)

    /** Records whether the process was started for a foreground UI (not a worker, push or broadcast). */
    fun captureProcessImportance() {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        startedInForeground = info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /** True for the first activity created in this process, false afterwards. */
    fun claimFirstActivity(): Boolean = activityCreated.compareAndSet(false, true)
}
