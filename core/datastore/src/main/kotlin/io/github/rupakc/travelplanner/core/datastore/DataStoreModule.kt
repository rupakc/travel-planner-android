package io.github.rupakc.travelplanner.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.common.AppClock
import javax.inject.Singleton

/**
 * File names (+ the Tink keyset prefs `tp_keys`); excluded from cloud backup in the app's
 * data_extraction_rules (the session token must not leave the device).
 */
object DataStoreFiles {
    const val SESSION = "session"
    const val SETTINGS = "settings"
    const val PROFILE = "traveller_profile"
    const val FEATURES = "feature_overrides"
    const val LAST_LOCATION = "last_location"
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    private fun Context.store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { preferencesDataStoreFile(name) }

    @Provides @Singleton
    fun sessionStore(
        @ApplicationContext context: Context,
        clock: AppClock,
    ): SessionStore = EncryptedSessionStore(context.store(DataStoreFiles.SESSION), { TinkSessionCrypto.create(context) }, clock)

    @Provides @Singleton
    fun settingsStore(
        @ApplicationContext context: Context,
    ): SettingsStore = PreferencesSettingsStore(context.store(DataStoreFiles.SETTINGS))

    @Provides @Singleton
    fun profileStore(
        @ApplicationContext context: Context,
    ): TravellerProfileStore =
        PreferencesTravellerProfileStore(context.store(DataStoreFiles.PROFILE))

    @Provides @Singleton
    fun featureOverridesStore(
        @ApplicationContext context: Context,
    ): FeatureOverridesStore =
        PreferencesFeatureOverridesStore(context.store(DataStoreFiles.FEATURES))

    @Provides @Singleton
    fun lastLocationStore(
        @ApplicationContext context: Context,
    ): LastLocationStore = PreferencesLastLocationStore(context.store(DataStoreFiles.LAST_LOCATION))
}
