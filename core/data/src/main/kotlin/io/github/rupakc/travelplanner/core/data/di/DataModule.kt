package io.github.rupakc.travelplanner.core.data.di

import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.rupakc.travelplanner.core.data.impl.AuthRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.ChatRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.CompareRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.DevicesRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.DiscoverRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.FareCalendarRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.FareWatchesRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.FeedbackRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.HealthRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.ImportRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.NearbyRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.NotificationsRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.PlansRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.PreferencesRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.PriceWatchesRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.ProfileRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.RecentSearchesRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.SearchRepositoryImpl
import io.github.rupakc.travelplanner.core.data.impl.SessionCleanerImpl
import io.github.rupakc.travelplanner.core.data.impl.SettingsRepositoryImpl
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStoreImpl
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.ChatRepository
import io.github.rupakc.travelplanner.core.data.repository.CompareRepository
import io.github.rupakc.travelplanner.core.data.repository.DevicesRepository
import io.github.rupakc.travelplanner.core.data.repository.DiscoverRepository
import io.github.rupakc.travelplanner.core.data.repository.FareCalendarRepository
import io.github.rupakc.travelplanner.core.data.repository.FareWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.FeedbackRepository
import io.github.rupakc.travelplanner.core.data.repository.HealthRepository
import io.github.rupakc.travelplanner.core.data.repository.ImportRepository
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationsRepository
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.PriceWatchesRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.PushTokenSource
import io.github.rupakc.travelplanner.core.data.repository.RecentSearchesRepository
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun auth(impl: AuthRepositoryImpl): AuthRepository

    @Binds abstract fun search(impl: SearchRepositoryImpl): SearchRepository

    @Binds abstract fun chat(impl: ChatRepositoryImpl): ChatRepository

    @Binds abstract fun plans(impl: PlansRepositoryImpl): PlansRepository

    @Binds abstract fun preferences(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds abstract fun recentSearches(impl: RecentSearchesRepositoryImpl): RecentSearchesRepository

    @Binds abstract fun fareWatches(impl: FareWatchesRepositoryImpl): FareWatchesRepository

    @Binds abstract fun priceWatches(impl: PriceWatchesRepositoryImpl): PriceWatchesRepository

    @Binds abstract fun notifications(impl: NotificationsRepositoryImpl): NotificationsRepository

    @Binds abstract fun devices(impl: DevicesRepositoryImpl): DevicesRepository

    @Binds abstract fun nearby(impl: NearbyRepositoryImpl): NearbyRepository

    @Binds abstract fun compare(impl: CompareRepositoryImpl): CompareRepository

    @Binds abstract fun discover(impl: DiscoverRepositoryImpl): DiscoverRepository

    @Binds abstract fun importLink(impl: ImportRepositoryImpl): ImportRepository

    @Binds abstract fun fareCalendar(impl: FareCalendarRepositoryImpl): FareCalendarRepository

    @Binds abstract fun health(impl: HealthRepositoryImpl): HealthRepository

    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds abstract fun profile(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds abstract fun feedback(impl: FeedbackRepositoryImpl): FeedbackRepository

    @Binds abstract fun currentPlan(impl: CurrentPlanStoreImpl): CurrentPlanStore

    @Binds abstract fun sessionCleaner(impl: SessionCleanerImpl): SessionCleaner

    /** Bound by core:notifications when Firebase is configured. */
    @BindsOptionalOf abstract fun pushTokenSource(): PushTokenSource
}
