package io.github.rupakc.travelplanner

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.notifications.NotificationChannels
import io.github.rupakc.travelplanner.core.notifications.NotificationsInitializer
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
     * registrar. `get()` runs on [appScope] (Dispatchers.Default), so none of that graph is built on the
     * main thread before the first frame, nor in worker/FCM process starts.
     */
    @Inject lateinit var notifications: dagger.Lazy<NotificationsInitializer>

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
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
    }
}
