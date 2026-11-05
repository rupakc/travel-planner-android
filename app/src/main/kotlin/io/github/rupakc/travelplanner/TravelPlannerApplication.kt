package io.github.rupakc.travelplanner

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.notifications.NotificationChannels
import javax.inject.Inject

@HiltAndroidApp
class TravelPlannerApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            AppLogger.sink = AppLogger.Sink { priority, tag, message, throwable ->
                val level = when (priority) {
                    AppLogger.Priority.DEBUG -> Log.DEBUG
                    AppLogger.Priority.INFO -> Log.INFO
                    AppLogger.Priority.WARN -> Log.WARN
                    AppLogger.Priority.ERROR -> Log.ERROR
                }
                Log.println(level, tag, message + (throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""))
            }
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build())
        }
        NotificationChannels.createAll(this)
    }
}
