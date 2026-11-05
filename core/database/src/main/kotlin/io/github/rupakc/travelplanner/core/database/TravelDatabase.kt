package io.github.rupakc.travelplanner.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Local cache only (spec §3 anti-goal: the backend is the source of truth). Schemas exported to core/database/schemas. */
@Database(
    entities = [
        CachedPlanEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class,
        RecentSearchEntity::class,
        SeenNotificationEntity::class,
        PlanDraftEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TravelDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao

    abstract fun chatDao(): ChatDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun seenNotificationDao(): SeenNotificationDao

    abstract fun planDraftDao(): PlanDraftDao

    companion object {
        const val NAME = "travel-planner.db"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): TravelDatabase =
        Room.databaseBuilder(context, TravelDatabase::class.java, TravelDatabase.NAME).build()

    @Provides fun planDao(db: TravelDatabase): PlanDao = db.planDao()

    @Provides fun chatDao(db: TravelDatabase): ChatDao = db.chatDao()

    @Provides fun recentSearchDao(db: TravelDatabase): RecentSearchDao = db.recentSearchDao()

    @Provides fun seenNotificationDao(db: TravelDatabase): SeenNotificationDao = db.seenNotificationDao()

    @Provides fun planDraftDao(db: TravelDatabase): PlanDraftDao = db.planDraftDao()
}
