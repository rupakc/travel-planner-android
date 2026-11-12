package io.github.rupakc.travelplanner.core.data.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.data.impl.ShareCardStoreImpl
import io.github.rupakc.travelplanner.core.data.impl.SharesRepositoryImpl
import io.github.rupakc.travelplanner.core.data.repository.ShareCardStore
import io.github.rupakc.travelplanner.core.data.repository.SharesRepository
import io.github.rupakc.travelplanner.core.data.repository.UserStateCleaner
import io.github.rupakc.travelplanner.core.network.di.DefaultHttp
import java.io.File
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Share links 2.0 bindings (kept apart from [DataModule], a shared hotspot). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ShareDataModule {
    @Binds abstract fun shares(impl: SharesRepositoryImpl): SharesRepository

    @Binds abstract fun cardStore(impl: ShareCardStoreImpl): ShareCardStore

    @Binds @IntoSet
    abstract fun cardCleaner(impl: ShareCardStoreImpl): UserStateCleaner

    companion object {
        @Provides @Singleton
        fun shareCardStoreImpl(
            @ApplicationContext context: Context,
            @DefaultHttp client: OkHttpClient,
            dispatchers: AppDispatchers,
        ): ShareCardStoreImpl = ShareCardStoreImpl(client, dispatchers, File(context.cacheDir, ShareCardStoreImpl.DIR_NAME))
    }
}
