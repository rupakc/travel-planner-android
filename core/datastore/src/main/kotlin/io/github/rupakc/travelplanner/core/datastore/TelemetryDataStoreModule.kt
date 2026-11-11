package io.github.rupakc.travelplanner.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TelemetryDataStoreModule {
    @Provides @Singleton
    fun telemetryPrefsStore(
        @ApplicationContext context: Context,
    ): TelemetryPrefsStore =
        TelemetryPrefsStoreImpl(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(TelemetryDataStoreFiles.TELEMETRY) },
        )
}
