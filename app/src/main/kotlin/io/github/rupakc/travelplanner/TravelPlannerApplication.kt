package io.github.rupakc.travelplanner

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.telemetry.TelemetryLifecycle
import io.github.rupakc.travelplanner.core.notifications.NotificationChannels
import io.github.rupakc.travelplanner.core.notifications.NotificationsInitializer
import io.github.rupakc.travelplanner.telemetry.ColdStart
import io.github.rupakc.travelplanner.telemetry.TelemetryProcessObserver
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class TravelPlannerApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * Lazy (performance M1): building it constructs the session DataStore, Retrofit, Room and the push
     * registrar. `get()` runs on [appScope] (Dispatchers.Default), so that graph is built off the main thread
     * in every process start (UI, worker, FCM, broadcast); it is still built in each of them. Avoiding the
     * network calls in non-UI starts is NotificationsInitializer's foreground gate, not this.
     */
    @Inject lateinit var notifications: dagger.Lazy<NotificationsInitializer>

    /** Lazy for the same reason: resolved on [appScope] by [TelemetryProcessObserver], never on the main thread. */
    @Inject lateinit var telemetryLifecycle: dagger.Lazy<TelemetryLifecycle>

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        // First, before anything can raise the process importance: is this start for a foreground UI?
        ColdStart.captureProcessImportance()
        super.onCreate()
        if (BuildConfig.DEBUG) {
            AppLogger.sink =
                AppLogger.Sink { priority, tag, message, throwable ->
                    val level =
                        when (priority) {
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
        // Alert poll, trip reminders, nearby worker and FCM registration (all off the main thread).
        appScope.launch { notifications.get().start() }
        // Telemetry: app_open / app_background follow the process lifecycle. A process started for a worker or a
        // push starts telemetry only when it first comes to the foreground (no config call in the background).
        val telemetryObserver = TelemetryProcessObserver(appScope) { telemetryLifecycle.get() }
        if (ColdStart.startedInForeground) telemetryObserver.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(telemetryObserver)
    }
}
