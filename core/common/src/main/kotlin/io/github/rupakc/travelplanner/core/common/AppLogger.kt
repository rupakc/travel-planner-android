package io.github.rupakc.travelplanner.core.common

/**
 * Minimal logging seam. The app installs an Android `Log` backed sink in debug
 * builds; release keeps the no-op sink (no PII in logs, ever).
 */
object AppLogger {
    fun interface Sink {
        fun log(priority: Priority, tag: String, message: String, throwable: Throwable?)
    }

    enum class Priority { DEBUG, INFO, WARN, ERROR }

    @Volatile
    var sink: Sink = Sink { _, _, _, _ -> }

    fun d(tag: String, message: String) = sink.log(Priority.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = sink.log(Priority.INFO, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) = sink.log(Priority.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = sink.log(Priority.ERROR, tag, message, throwable)
}
