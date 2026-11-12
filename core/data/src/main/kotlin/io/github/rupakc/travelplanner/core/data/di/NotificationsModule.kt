package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.NotificationCatalogRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.NotificationSettingsRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.NotificationCatalogRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationSettingsRepository

/** Notifications 2.0: server-side categories, quiet hours and cap, and the shared text catalog. */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds abstract fun notificationSettings(impl: NotificationSettingsRepositoryImpl): NotificationSettingsRepository

    @Binds abstract fun notificationCatalog(impl: NotificationCatalogRepositoryImpl): NotificationCatalogRepository
}
